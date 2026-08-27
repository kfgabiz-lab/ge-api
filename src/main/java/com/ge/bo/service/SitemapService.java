package com.ge.bo.service;

import com.ge.bo.common.context.SiteTimeZoneResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * FO 공개 sitemap.xml 생성/캐시.
 *
 * <p>대상(사용자 확정 5종): 정적 FO 메뉴 페이지, 제품(product-data), 카테고리(category-data L1/L2),
 * 미디어(blog/press/articles/events-data), 교육 커리큘럼(currMgmt-data).</p>
 *
 * <p>스냅샷은 인메모리 보관(AtomicReference). 콜드스타트 시 첫 요청에서 1회 생성하고,
 * {@code SitemapScheduler}가 매일 refresh() 한다(멀티 인스턴스는 각자 생성 — write 없음, 락 불필요).</p>
 *
 * <p>공개 게이트 SQL(미디어 publish_dttm 정규화 CASE식)은 {@code PageDataService.publishGateSql()} /
 * {@code toRangeBoundExpr(expr, false)} 와 동일하게 유지해야 한다. 변경 시 양쪽을 함께 수정할 것.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SitemapService {

    @PersistenceContext
    private EntityManager entityManager;

    private final SiteTimeZoneResolver siteTimeZoneResolver;

    @Value("${ls.fo.base-url}")
    private String baseUrl;

    @Value("${ls.fo.site-id:1}")
    private Long siteId;

    private static final int URL_COUNT_WARN_THRESHOLD = 45_000;
    private static final DateTimeFormatter NOW_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 미디어 slug → data_json 섹션명 / FO 상세 경로 prefix. */
    private static final List<MediaType> MEDIA_TYPES = List.of(
            new MediaType("blog-data", "blog", "/company/blog"),
            new MediaType("press-data", "press", "/company/press"),
            new MediaType("articles-data", "articles", "/company/articles"),
            new MediaType("events-data", "events", "/company/events"));

    private final AtomicReference<Snapshot> cache = new AtomicReference<>();

    private record MediaType(String slug, String section, String pathPrefix) {}

    private record Entry(String loc, LocalDate lastmod) {}

    /** 생성 결과 스냅샷. */
    public record Snapshot(String xml, int urlCount, OffsetDateTime generatedAt) {}

    /** 캐시된 XML 반환, 없으면 즉시 1회 생성. */
    @Transactional(readOnly = true)
    public String getOrBuildXml() {
        Snapshot snap = cache.get();
        return (snap != null ? snap : rebuild()).xml();
    }

    /** 캐시된 스냅샷 반환, 없으면 즉시 1회 생성. */
    @Transactional(readOnly = true)
    public Snapshot getSnapshot() {
        Snapshot snap = cache.get();
        return snap != null ? snap : rebuild();
    }

    /** 강제 재생성 후 캐시 교체(배치/관리자 트리거). */
    @Transactional(readOnly = true)
    public Snapshot rebuild() {
        List<Entry> entries = collect();
        Map<String, LocalDate> deduped = new LinkedHashMap<>();
        for (Entry e : entries) {
            deduped.putIfAbsent(e.loc(), e.lastmod());
        }
        if (deduped.size() > URL_COUNT_WARN_THRESHOLD) {
            log.warn("sitemap URL {}건 — sitemap 프로토콜 상한(50000) 근접, 인덱스 분할 검토 필요", deduped.size());
        }
        String xml = renderXml(deduped);
        Snapshot snap = new Snapshot(xml, deduped.size(), OffsetDateTime.now());
        cache.set(snap);
        log.info("sitemap 재생성 완료 — URL {}건", deduped.size());
        return snap;
    }

    /* ──────────────────────────────── 수집 ──────────────────────────────── */

    private List<Entry> collect() {
        List<Entry> out = new ArrayList<>();
        out.addAll(collectStaticMenuPages());
        out.addAll(collectProducts());
        out.addAll(collectCategories());
        out.addAll(collectMedia());
        out.addAll(collectCurriculum());
        return out;
    }

    /** 정적 FO 메뉴 페이지 — menu.url(사이트 상대경로) 그대로. */
    private List<Entry> collectStaticMenuPages() {
        String sql = "SELECT url, updated_at FROM menu"
                + " WHERE menu_type = 'FO' AND is_visible = true AND is_deleted = false"
                + " AND url IS NOT NULL AND btrim(url) <> '' AND url NOT LIKE 'http%'"
                + " AND (site_id IS NULL OR site_id = :siteId)";
        Query q = entityManager.createNativeQuery(sql).setParameter("siteId", siteId);
        List<Entry> out = new ArrayList<>();
        for (Object[] row : rows(q)) {
            String url = normalizePath((String) row[0]);
            if (url == null) {
                continue;
            }
            out.add(new Entry(baseUrl + url, toLocalDate(row[1])));
        }
        return out;
    }

    /** 제품 — /product/{id}/{seo.slug}. is_visible=001 AND order_status<>99. */
    private List<Entry> collectProducts() {
        String sql = "SELECT id, data_json->'seo'->>'slug', updated_at FROM page_data"
                + " WHERE data_slug = 'product-data' AND is_deleted = false"
                + " AND data_json->'product'->>'is_visible' = '001'"
                + " AND data_json->'product'->>'order_status' <> '99'"
                + " AND COALESCE(btrim(data_json->'seo'->>'slug'), '') <> ''"
                + " AND (site_id IS NULL OR site_id = :siteId)";
        Query q = entityManager.createNativeQuery(sql).setParameter("siteId", siteId);
        List<Entry> out = new ArrayList<>();
        for (Object[] row : rows(q)) {
            String path = "/product/" + num(row[0]) + "/" + encodeSegment((String) row[1]);
            out.add(new Entry(baseUrl + path, toLocalDate(row[2])));
        }
        return out;
    }

    /**
     * 카테고리 — depth 1 → /product-category/{id}/{slug}, depth 2 → /product-range/{id}/{slug}.
     * TODO: ge-fo product-range 페이지가 "노출 제품 0개"일 때 notFound() 하면, 여기에도
     *       CATEGORY_LV2_CTE(PageDataService.java:131-140)처럼 "하위 노출 제품 EXISTS" 조건 추가 필요.
     */
    private List<Entry> collectCategories() {
        String sql = "SELECT id, data_json->'category'->>'depth', data_json->'seo'->>'slug', updated_at"
                + " FROM page_data"
                + " WHERE data_slug = 'category-data' AND is_deleted = false"
                + " AND data_json->'category'->>'is_visible' = '001'"
                + " AND data_json->'category'->>'depth' IN ('1', '2')"
                + " AND COALESCE(btrim(data_json->'seo'->>'slug'), '') <> ''"
                + " AND (site_id IS NULL OR site_id = :siteId)";
        Query q = entityManager.createNativeQuery(sql).setParameter("siteId", siteId);
        List<Entry> out = new ArrayList<>();
        for (Object[] row : rows(q)) {
            String depth = String.valueOf(row[1]);
            String base = "1".equals(depth) ? "/product-category/" : "/product-range/";
            String path = base + num(row[0]) + "/" + encodeSegment((String) row[2]);
            out.add(new Entry(baseUrl + path, toLocalDate(row[3])));
        }
        return out;
    }

    /**
     * 미디어(blog/press/articles/events) — /company/{type}/{id}/{seo.slug}.
     * 서버측 게시 게이트(is_visible=001 AND publish_dttm<=now)는 PageDataService.publishGateSql()와 동일.
     */
    private List<Entry> collectMedia() {
        String nowValue = LocalDateTime.now(zone()).format(NOW_FMT);
        List<Entry> out = new ArrayList<>();
        for (MediaType m : MEDIA_TYPES) {
            String pubExpr = normalizedLowerBoundExpr(
                    "data_json->'" + m.section() + "'->>'publish_dttm'");
            String sql = "SELECT id, data_json->'seo'->>'slug', updated_at FROM page_data"
                    + " WHERE data_slug = :slug AND is_deleted = false"
                    + " AND data_json->'" + m.section() + "'->>'is_visible' = '001'"
                    + " AND " + pubExpr + " <= :nowValue"
                    + " AND (site_id IS NULL OR site_id = :siteId)";
            Query q = entityManager.createNativeQuery(sql)
                    .setParameter("slug", m.slug())
                    .setParameter("nowValue", nowValue)
                    .setParameter("siteId", siteId);
            for (Object[] row : rows(q)) {
                String slug = (String) row[1];
                String path = m.pathPrefix() + "/" + num(row[0])
                        + (isBlank(slug) ? "" : "/" + encodeSegment(slug));
                out.add(new Entry(baseUrl + path, toLocalDate(row[2])));
            }
        }
        return out;
    }

    /**
     * 교육 커리큘럼 — /services/training/course/{id}/{seo.slug}. curriculum.is_visible=001.
     * TODO: ge-fo 목록(TrainingCurriculum.tsx)은 노출 세션 보유 코스만 표시(trainingHasSessionWhere).
     *       과다 색인이 문제되면 currDtlMgmt-data curriculum_detail3.is_visible='001' EXISTS 조건 추가.
     */
    private List<Entry> collectCurriculum() {
        String sql = "SELECT id, data_json->'seo'->>'slug', updated_at FROM page_data"
                + " WHERE data_slug = 'currMgmt-data' AND is_deleted = false"
                + " AND data_json->'curriculum'->>'is_visible' = '001'"
                + " AND (site_id IS NULL OR site_id = :siteId)";
        Query q = entityManager.createNativeQuery(sql).setParameter("siteId", siteId);
        List<Entry> out = new ArrayList<>();
        for (Object[] row : rows(q)) {
            String slug = (String) row[1];
            String path = "/services/training/course/" + num(row[0])
                    + (isBlank(slug) ? "" : "/" + encodeSegment(slug));
            out.add(new Entry(baseUrl + path, toLocalDate(row[2])));
        }
        return out;
    }

    /* ──────────────────────────────── 렌더 ──────────────────────────────── */

    private String renderXml(Map<String, LocalDate> entries) {
        StringBuilder sb = new StringBuilder(entries.size() * 96 + 128);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (Map.Entry<String, LocalDate> e : entries.entrySet()) {
            sb.append("  <url><loc>").append(xmlEscape(e.getKey())).append("</loc>");
            if (e.getValue() != null) {
                sb.append("<lastmod>").append(e.getValue().format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .append("</lastmod>");
            }
            sb.append("</url>\n");
        }
        sb.append("</urlset>\n");
        return sb.toString();
    }

    /* ──────────────────────────────── 헬퍼 ──────────────────────────────── */

    private ZoneId zone() {
        return siteTimeZoneResolver.resolve(siteId);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(Query q) {
        return q.getResultList();
    }

    /**
     * 저장값(숫자만 추출)의 자릿수(6/8/12/그외)에 따라 :nowValue(14자리 yyyyMMddHHmmss)와
     * 비교 가능한 14자리 하한(from) 문자열로 패딩. 값이 비면 NULL(무매칭).
     * PageDataService.toRangeBoundExpr(expr, false) 와 동일 로직.
     */
    private String normalizedLowerBoundExpr(String rawJsonPathExpr) {
        String digits = "regexp_replace(" + rawJsonPathExpr + ", '[^0-9]', '', 'g')";
        return "(CASE"
                + " WHEN " + digits + " = '' THEN NULL"
                + " WHEN char_length(" + digits + ") = 6 THEN " + digits + " || '01000000'"
                + " WHEN char_length(" + digits + ") = 8 THEN " + digits + " || '000000'"
                + " WHEN char_length(" + digits + ") = 12 THEN " + digits + " || '00'"
                + " ELSE left(rpad(" + digits + ", 14, '0'), 14)"
                + " END)";
    }

    /** menu.url 정규화 — 앞에 '/' 보장, 뒤 '/' 제거(루트 제외). */
    private String normalizePath(String raw) {
        if (raw == null) {
            return null;
        }
        String u = raw.trim();
        if (u.isEmpty()) {
            return null;
        }
        if (!u.startsWith("/")) {
            u = "/" + u;
        }
        while (u.length() > 1 && u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /** 경로 세그먼트 URL 인코딩 — unreserved(A-Za-z0-9-_.~) 외 문자는 %XX. */
    private String encodeSegment(String seg) {
        if (seg == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(seg.length() + 8);
        for (byte b : seg.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append((char) c);
            } else {
                sb.append('%').append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return sb.toString();
    }

    private String xmlEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String num(Object o) {
        return String.valueOf(((Number) o).longValue());
    }

    private LocalDate toLocalDate(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Timestamp ts) {
            return ts.toLocalDateTime().toLocalDate();
        }
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (o instanceof LocalDateTime ldt) {
            return ldt.toLocalDate();
        }
        if (o instanceof LocalDate ld) {
            return ld;
        }
        if (o instanceof OffsetDateTime odt) {
            return odt.toLocalDate();
        }
        if (o instanceof Instant ins) {
            return ins.atZone(zone()).toLocalDate();
        }
        return null;
    }
}
