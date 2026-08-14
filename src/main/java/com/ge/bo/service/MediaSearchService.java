package com.ge.bo.service;

import com.ge.bo.common.context.SiteTimeZoneResolver;
import com.ge.bo.common.search.SearchSqlSupport;
import com.ge.bo.dto.MediaSearchItemResponse;
import com.ge.bo.dto.MediaSearchResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaSearchService {

    @PersistenceContext
    private EntityManager entityManager;

    private final SiteTimeZoneResolver siteTimeZoneResolver;

    private static final Pattern YOUTUBE_ID =
        Pattern.compile("(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|v/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    private static final int SNIPPET_MAX_CHARS = 320;

    private static final int SNIPPET_EARLY_TRUNCATE_CHARS = 4000;

    private static final List<String> SOURCE_KEYS = List.of("TECH_HUB", "BLOG", "PRESS", "ARTICLE", "EVENT");

    private static final DateTimeFormatter SORT_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional(readOnly = true)
    public MediaSearchResponse search(String q, String sources, int page, int size, Long siteId) {
        int safeSize = size <= 0 ? 20 : size;
        int safePage = Math.max(page, 0);

        boolean hasKeyword = q != null && !q.isBlank();
        Set<String> tokens = parseSources(sources);

        if (tokens.isEmpty()) {
            return new MediaSearchResponse(Collections.emptyList(), 0L, 0, safePage, safeSize, emptyCounts());
        }

        String kw = null;
        String kwHtml = null;
        String kwExact = null;
        String kwPrefix = null;
        String kwRegex = null;
        if (hasKeyword) {
            String trimmed = q.trim();
            kw = SearchSqlSupport.toLikePattern(trimmed);
            kwHtml = SearchSqlSupport.toHtmlLikePattern(trimmed);
            kwExact = SearchSqlSupport.toLikeExactPattern(trimmed);
            kwPrefix = SearchSqlSupport.toLikePrefixPattern(trimmed);
            kwRegex = SearchSqlSupport.toWordStartRegex(trimmed);
        }

        boolean hasSite = siteId != null;

        String base = buildTechHubBlock(hasKeyword)
                + " UNION ALL "
                + buildIntegrationBlock(hasKeyword, hasSite);

        String sql = "WITH base AS (" + base + "),"
            + " agg AS ("
            + "   SELECT count(*) FILTER (WHERE source_type='TECH_HUB') AS c_tech_hub,"
            + "          count(*) FILTER (WHERE source_type='BLOG')     AS c_blog,"
            + "          count(*) FILTER (WHERE source_type='PRESS')    AS c_press,"
            + "          count(*) FILTER (WHERE source_type='ARTICLE')  AS c_article,"
            + "          count(*) FILTER (WHERE source_type='EVENT')    AS c_event"
            + "   FROM base"
            + " ),"
            + " pg AS ("
            + "   SELECT b.* FROM base b"
            + "   WHERE b.source_type IN (:sources)"
            + "   ORDER BY b.score DESC, b.sort_ts DESC NULLS LAST, b.source_type, b.row_id DESC"
            + "   LIMIT :size OFFSET :offset"
            + " )"
            + " SELECT a.c_tech_hub, a.c_blog, a.c_press, a.c_article, a.c_event,"
            + "        p.source_type, p.id, p.title, " + buildFinalSnippetExpr() + " AS snippet,"
            + "        p.video_url, p.file_id, p.sort_ts"
            + " FROM agg a"
            + " LEFT JOIN pg p ON true"
            + " ORDER BY p.score DESC, p.sort_ts DESC NULLS LAST, p.source_type, p.row_id DESC";

        Query query = entityManager.createNativeQuery(sql);
        bindCommon(query, hasKeyword, kw, kwHtml, kwExact, kwPrefix, kwRegex,
                hasSite, siteId, tokens, safeSize, safePage);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Map<String, Long> sourceCounts = emptyCounts();
        if (!rows.isEmpty()) {
            Object[] head = rows.get(0);
            sourceCounts.put("TECH_HUB", toLong(head[0]));
            sourceCounts.put("BLOG", toLong(head[1]));
            sourceCounts.put("PRESS", toLong(head[2]));
            sourceCounts.put("ARTICLE", toLong(head[3]));
            sourceCounts.put("EVENT", toLong(head[4]));
        }

        ZoneId zone = siteTimeZoneResolver.resolve(siteId);
        List<MediaSearchItemResponse> content = new ArrayList<>();
        for (Object[] r : rows) {
            if (r[5] == null) continue;

            String sourceType = r[5].toString();
            Long id = r[6] != null ? ((Number) r[6]).longValue() : null;
            String title = r[7] != null ? r[7].toString() : null;
            String snippet = r[8] != null ? r[8].toString() : null;
            String videoUrl = r[9] != null ? r[9].toString() : null;
            Long fileId = r[10] != null ? ((Number) r[10]).longValue() : null;
            String sortDate = formatSortDate(r[11], zone);

            String imageUrl = "TECH_HUB".equals(sourceType)
                    ? youtubeThumbnail(videoUrl)
                    : resolveMediaProxyUrl(fileId);

            content.add(new MediaSearchItemResponse(
                sourceType, id, title, snippet, imageUrl, sortDate, buildLink(sourceType, id)));
        }

        long total = 0L;
        for (String token : tokens) {
            total += sourceCounts.getOrDefault(token, 0L);
        }
        int totalPages = (int) Math.ceil((double) total / safeSize);

        return new MediaSearchResponse(content, total, totalPages, safePage, safeSize, sourceCounts);
    }

    private String buildTechHubBlock(boolean hasKeyword) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT 'TECH_HUB'::text AS source_type,")
          .append("  m.id AS row_id,")
          .append("  m.id AS id,")
          .append("  COALESCE(m.nahp_title, m.doc_title)::text AS title,")
          .append("  NULL::text AS snippet,")
          .append("  rep.video_url::text AS video_url,")
          .append("  NULL::bigint AS file_id,")
          .append("  m.source_updated_at AS sort_ts,");
        if (hasKeyword) {
            sb.append("  (CASE")
              .append("    WHEN COALESCE(m.nahp_title, m.doc_title) ILIKE :qExact  ESCAPE '\\' THEN 100")
              .append("    WHEN COALESCE(m.nahp_title, m.doc_title) ILIKE :qPrefix ESCAPE '\\' THEN 80")
              .append("    WHEN COALESCE(m.nahp_title, m.doc_title) ~* :qRegex THEN 60")
              .append("    ELSE 40 END)::int AS score");
        } else {
            sb.append("  0::int AS score");
        }
        sb.append(" FROM contents_master m")
          .append(" JOIN LATERAL (")
          .append("   SELECT v.video_url FROM contents_version v")
          .append("   WHERE v.contents_id = m.id AND v.version_expose = true AND v.is_deleted = false")
          .append("     AND v.video_url IS NOT NULL AND v.video_url <> ''")
          .append("   ORDER BY v.sort_key DESC LIMIT 1")
          .append(" ) rep ON true")
          .append(" WHERE").append(TechHubService.MASTER_GATE);
        if (hasKeyword) {
            sb.append(" AND COALESCE(m.nahp_title, m.doc_title) ILIKE :q ESCAPE '\\'");
        }
        return sb.toString();
    }

    private String buildIntegrationBlock(boolean hasKeyword, boolean hasSite) {
        String snippetExpr = "left(ic.content, " + SNIPPET_EARLY_TRUNCATE_CHARS + ")::text";

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT CASE ic.type WHEN 'B' THEN 'BLOG' WHEN 'P' THEN 'PRESS'")
          .append("                    WHEN 'A' THEN 'ARTICLE' WHEN 'E' THEN 'EVENT' END::text AS source_type,")
          .append("  ic.id AS row_id,")
          .append("  NULLIF(regexp_replace(ic.content_id,'[^0-9]','','g'),'')::bigint AS id,")
          .append("  ic.title::text AS title,")
          .append("  ").append(snippetExpr).append(" AS snippet,")
          .append("  NULL::text AS video_url,")
          .append("  ic.file_id AS file_id,")
          .append("  ic.updated_at AS sort_ts,");
        if (hasKeyword) {
            sb.append("  (CASE")
              .append("    WHEN ic.title ILIKE :qExact  ESCAPE '\\' THEN 100")
              .append("    WHEN ic.title ILIKE :qPrefix ESCAPE '\\' THEN 80")
              .append("    WHEN ic.title ~* :qRegex THEN 60")
              .append("    WHEN ic.title ILIKE :q ESCAPE '\\' THEN 40")
              .append("    ELSE 10 END)::int AS score");
        } else {
            sb.append("  0::int AS score");
        }
        sb.append(" FROM integration_contents ic")
          .append(" WHERE ic.is_visible = true AND ic.type IN ('B','P','A','E')");
        if (hasSite) {
            sb.append(" AND (ic.site_id = :siteId OR ic.site_id IS NULL)");
        }
        if (hasKeyword) {
            sb.append(" AND (ic.title ILIKE :q ESCAPE '\\' OR ic.content COLLATE \"C\" ILIKE :qHtml ESCAPE '\\')");
        }
        return sb.toString();
    }

    private String buildFinalSnippetExpr() {
        String plainExpr = SearchSqlSupport.buildPlainTextExpr("p.snippet");
        return SNIPPET_MAX_CHARS > 0
                ? "btrim(left(" + plainExpr + ", :snippetCap))::text"
                : plainExpr + "::text";
    }

    private void bindCommon(Query query, boolean hasKeyword, String kw, String kwHtml,
                            String kwExact, String kwPrefix, String kwRegex,
                            boolean hasSite, Long siteId, Set<String> tokens,
                            int safeSize, int safePage) {
        if (hasKeyword) {
            query.setParameter("q", kw);
            query.setParameter("qHtml", kwHtml);
            query.setParameter("qExact", kwExact);
            query.setParameter("qPrefix", kwPrefix);
            query.setParameter("qRegex", kwRegex);
        }
        if (hasSite) query.setParameter("siteId", siteId);
        if (SNIPPET_MAX_CHARS > 0) query.setParameter("snippetCap", SNIPPET_MAX_CHARS);
        query.setParameter("sources", new ArrayList<>(tokens));
        query.setParameter("size", safeSize);
        query.setParameter("offset", safePage * safeSize);
    }

    private Set<String> parseSources(String sources) {
        Set<String> all = new LinkedHashSet<>(SOURCE_KEYS);
        if (sources == null || sources.isBlank()) {
            return all;
        }
        Set<String> result = new LinkedHashSet<>();
        for (String raw : sources.split(",")) {
            String tok = raw.trim().toUpperCase();
            if (all.contains(tok)) result.add(tok);
        }
        return result;
    }

    private String youtubeThumbnail(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) return null;
        Matcher m = YOUTUBE_ID.matcher(videoUrl);
        if (m.find()) {
            return "https://img.youtube.com/vi/" + m.group(1) + "/hqdefault.jpg";
        }
        return null;
    }

    private String resolveMediaProxyUrl(Long fileId) {
        if (fileId == null) return null;
        return "/api/v1/fo/page-files/" + fileId;
    }

    private String buildLink(String sourceType, Long id) {
        if (sourceType == null || id == null) return null;
        return switch (sourceType) {
            case "TECH_HUB" -> "/support/tech-hub/view/" + id;
            case "BLOG" -> "/company/blog/detail/" + id;
            case "PRESS" -> "/company/press/detail/" + id;
            case "ARTICLE" -> "/company/articles/detail/" + id;
            case "EVENT" -> "/company/events/detail/" + id;
            default -> null;
        };
    }

    private Map<String, Long> emptyCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String key : SOURCE_KEYS) {
            counts.put(key, 0L);
        }
        return counts;
    }

    private long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private String formatSortDate(Object value, ZoneId zone) {
        if (value == null) return null;
        Instant instant;
        if (value instanceof java.sql.Timestamp ts) {
            instant = ts.toInstant();
        } else if (value instanceof OffsetDateTime odt) {
            instant = odt.toInstant();
        } else if (value instanceof Instant i) {
            instant = i;
        } else if (value instanceof java.util.Date d) {
            instant = d.toInstant();
        } else if (value instanceof LocalDateTime ldt) {
            return ldt.toLocalDate().format(SORT_DATE_FMT);
        } else {
            return value.toString();
        }
        return SORT_DATE_FMT.format(instant.atZone(zone));
    }
}
