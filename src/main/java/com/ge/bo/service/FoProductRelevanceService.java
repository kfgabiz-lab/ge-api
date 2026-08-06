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
        String productSiteCond = siteId != null ? " AND (p.site_id = :siteId OR p.site_id IS NULL)" : "";
        String selfSiteCond = siteId != null ? " AND (site_id = :siteId OR site_id IS NULL)" : "";
        String targetLv2SiteCond = siteId != null ? " AND (cd.site_id = :siteId OR cd.site_id IS NULL)" : "";
        String mappingSiteCond = siteId != null ? " AND (map.site_id = :siteId OR map.site_id IS NULL)" : "";

        String sql = "WITH self AS ("
            + " SELECT string_to_array(attribute01, ',') AS codes"
            + " FROM page_data"
            + " WHERE data_slug = 'product-data'"
            + "  AND data_json->'seo'->>'slug' = :slug"
            + selfSiteCond
            + " LIMIT 1"
            + " ),"
            + " target_lv2 AS ("
            + " SELECT cd.id"
            + " FROM page_data cd, self"
            + " WHERE cd.data_slug = 'category-data'"
            + "  AND cd.data_json->'category'->>'code' = ANY(self.codes)"
            + "  AND cd.data_json->'category'->>'depth' = '2'"
            + targetLv2SiteCond
            + " ),"
            + " mapping AS ("
            + " SELECT map.data_json->'product'->>'id' AS product_id"
            + " FROM page_data map"
            + " JOIN target_lv2 t ON map.data_json->'product'->>'parentId' = t.id::text"
            + " WHERE map.data_slug = 'category-data'"
            + "  AND map.data_json->'product'->>'depth' = '3'"
            + mappingSiteCond
            + " )"
            + " SELECT DISTINCT p.id,"
            + "  p.data_json->'product'->>'product_name' AS title,"
            + "  p.data_json->'seo'->>'slug'             AS slug,"
            + "  p.data_json->'product_info'->>'image'   AS image,"
            + "  p.data_json->'product'->>'awards'       AS awards"
            + " FROM mapping m"
            + " JOIN page_data p ON p.id::text = m.product_id"
            + " WHERE p.data_slug = 'product-data'"
            + "  AND p.data_json->'product'->>'is_visible'   = '001'"
            + "  AND p.data_json->'product'->>'order_status' = '01'"
            + productSiteCond
            + " ORDER BY p.id";

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
                row[0] != null ? ((Number) row[0]).longValue() : null,
                row[1] != null ? row[1].toString() : null,
                row[2] != null ? row[2].toString() : null,
                row[3] != null ? row[3].toString() : null,
                row[4] != null ? row[4].toString() : null
            ));
        }
        return result;
    }
}
