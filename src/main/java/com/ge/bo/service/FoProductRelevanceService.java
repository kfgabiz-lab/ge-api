package com.ge.bo.service;

import com.ge.bo.dto.FoProductRelevanceRowResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoProductRelevanceService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<FoProductRelevanceRowResponse> findRelevantProducts(String slug, Long siteId) {
        String selfProductSiteCond = siteId != null ? " AND (site_id = :siteId OR site_id IS NULL)" : "";
        String selfLeafSiteCond = siteId != null ? " AND (cd.site_id = :siteId OR cd.site_id IS NULL)" : "";
        String lv2SiteCond = siteId != null ? " AND (cd.site_id = :siteId OR cd.site_id IS NULL)" : "";
        String lv3SiteCond = siteId != null ? " AND (p.site_id = :siteId OR p.site_id IS NULL)" : "";

        // attribute01(관련 카테고리 코드 목록)은 해당 제품을 카테고리 트리에 연결하는
        // category-data의 leaf 행(product.depth='3')에 있다 — product-data 자신의 attribute01이 아니다.
        // 코드가 2세그먼트(예: L05-01)면 LV2 카테고리 자신(대표이미지 포함)을, 3세그먼트(예: L06-01-01)면
        // 그 코드를 product_code로 갖는 특정 제품(LV3)을 각각 1코드당 1행으로 반환한다.
        String sql = "WITH self_product AS ("
            + " SELECT id"
            + " FROM page_data"
            + " WHERE data_slug = 'product-data'"
            + "  AND is_deleted = false"
            + "  AND data_json->'seo'->>'slug' = :slug"
            + selfProductSiteCond
            + " LIMIT 1"
            + " ),"
            + " self_leaf AS ("
            + " SELECT cd.attribute01"
            + " FROM page_data cd, self_product sp"
            + " WHERE cd.data_slug = 'category-data'"
            + "  AND cd.is_deleted = false"
            + "  AND cd.data_json->'product'->>'depth' = '3'"
            + "  AND cd.data_json->'product'->>'id' = sp.id::text"
            + selfLeafSiteCond
            + " LIMIT 1"
            + " ),"
            + " self_codes AS ("
            + " SELECT trim(code) AS code,"
            + "  array_length(string_to_array(trim(code), '-'), 1) AS seg_count"
            + " FROM self_leaf, unnest(string_to_array(self_leaf.attribute01, ',')) AS code"
            + " )"
            + " SELECT sc.code AS code, 'LV2' AS level,"
            + "  cd.id AS id,"
            + "  cd.data_json->'category'->>'title'      AS title,"
            + "  cd.data_json->'seo'->>'slug'             AS slug,"
            + "  cd.data_json->'device_systems'->>'image' AS image,"
            + "  NULL AS awards"
            + " FROM self_codes sc"
            + " JOIN page_data cd"
            + "  ON cd.data_json->'category'->>'code' = sc.code"
            + "  AND sc.seg_count = 2"
            + " WHERE cd.data_slug = 'category-data'"
            + "  AND cd.is_deleted = false"
            + "  AND cd.data_json->'category'->>'depth'       = '2'"
            + "  AND cd.data_json->'category'->>'is_visible'  = '001'"
            + lv2SiteCond
            + " UNION ALL"
            + " SELECT sc.code AS code, 'LV3' AS level,"
            + "  p.id AS id,"
            + "  p.data_json->'product'->>'product_name' AS title,"
            + "  p.data_json->'seo'->>'slug'             AS slug,"
            + "  p.data_json->'product_info'->>'image'   AS image,"
            + "  p.data_json->'product'->>'awards'       AS awards"
            + " FROM self_codes sc"
            + " JOIN page_data p"
            + "  ON p.data_json->'product'->>'product_code' = sc.code"
            + "  AND sc.seg_count = 3"
            + " WHERE p.data_slug = 'product-data'"
            + "  AND p.is_deleted = false"
            + "  AND p.data_json->'product'->>'is_visible'   = '001'"
            + "  AND p.data_json->'product'->>'order_status' = '01'"
            + lv3SiteCond
            + " ORDER BY code";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("slug", slug);
        if (siteId != null) {
            query.setParameter("siteId", siteId);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<FoProductRelevanceRowResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new FoProductRelevanceRowResponse(
                row[2] != null ? ((Number) row[2]).longValue() : null,
                row[3] != null ? row[3].toString() : null,
                row[4] != null ? row[4].toString() : null,
                row[5] != null ? row[5].toString() : null,
                row[6] != null ? row[6].toString() : null,
                row[1] != null ? row[1].toString() : null
            ));
        }
        return result;
    }
}
