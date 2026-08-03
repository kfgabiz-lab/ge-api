package com.ge.bo.service;

import com.ge.bo.dto.FoMarketProductRowResponse;
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
public class FoMarketService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<FoMarketProductRowResponse> findMarketProducts(String name, Long siteId) {
        String marketSiteCond = siteId != null ? " AND (m.site_id = :siteId OR m.site_id IS NULL)" : "";
        String productSiteCond = siteId != null ? " AND (pd.site_id = :siteId OR pd.site_id IS NULL)" : "";
        String categorySiteCond = siteId != null ? " AND (cd.site_id = :siteId OR cd.site_id IS NULL)" : "";
        String mapSiteCond = siteId != null ? " AND (j.site_id = :siteId OR j.site_id IS NULL)" : "";
        String lv2SiteCond = siteId != null ? " AND (l2.site_id = :siteId OR l2.site_id IS NULL)" : "";

        String sql = "WITH codes AS ("
            + " SELECT btrim(c.code) AS code, c.ord"
            + " FROM page_data m,"
            + "      LATERAL unnest(string_to_array(m.attribute01, ',')) WITH ORDINALITY AS c(code, ord)"
            + " WHERE m.data_slug = 'market-data'"
            + "  AND m.data_json->>'name' = :name"
            + marketSiteCond
            + " )"
            + " SELECT COALESCE(p.id, cat.id)                                       AS id,"
            + "  CASE WHEN p.id IS NOT NULL THEN 'product' ELSE 'category' END       AS type,"
            + "  c.code                                                             AS code,"
            + "  COALESCE(p.data_json->'product'->>'product_name',"
            + "           cat.data_json->'category'->>'title')                      AS title,"
            + "  COALESCE(lv2.data_json->'category'->>'title',"
            + "           cat.data_json->'category'->>'title')                     AS category_title,"
            + "  COALESCE(p.data_json->'seo'->>'slug',"
            + "           cat.data_json->'seo'->>'slug')                            AS slug,"
            + "  COALESCE(p.data_json->'product_info'->>'image',"
            + "           cat.data_json->'device_systems'->>'image')                AS image,"
            + "  p.data_json->'product'->>'awards'                                  AS awards"
            + " FROM codes c"
            + " LEFT JOIN LATERAL ("
            + "  SELECT pd.* FROM page_data pd"
            + "  WHERE pd.data_slug = 'product-data'"
            + "   AND pd.data_json->'product'->>'product_code' = c.code"
            + "   AND pd.data_json->'product'->>'is_visible'   = '001'"
            + "   AND pd.data_json->'product'->>'order_status' = '01'"
            + productSiteCond
            + "  ORDER BY pd.id LIMIT 1"
            + " ) p ON TRUE"
            + " LEFT JOIN LATERAL ("
            + "  SELECT cd.* FROM page_data cd"
            + "  WHERE cd.data_slug = 'category-data'"
            + "   AND cd.data_json->'category'->>'code'       = c.code"
            + "   AND cd.data_json->'category'->>'is_visible' = '001'"
            + categorySiteCond
            + "  ORDER BY cd.id LIMIT 1"
            + " ) cat ON p.id IS NULL"
            + " LEFT JOIN LATERAL ("
            + "  SELECT l2.data_json FROM page_data j"
            + "  JOIN page_data l2"
            + "    ON l2.data_slug = 'category-data'"
            + "   AND l2.id::text = j.data_json->'product'->>'parentId'"
            + "   AND l2.data_json->'category'->>'depth' = '2'"
            + lv2SiteCond
            + "  WHERE j.data_slug = 'category-data'"
            + "   AND j.data_json->'product'->>'depth' = '3'"
            + "   AND j.data_json->'product'->>'id'    = p.id::text"
            + mapSiteCond
            + "  ORDER BY l2.id LIMIT 1"
            + " ) lv2 ON p.id IS NOT NULL"
            + " WHERE COALESCE(p.id, cat.id) IS NOT NULL"
            + " ORDER BY c.ord";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("name", name);
        if (siteId != null) {
            query.setParameter("siteId", siteId);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<FoMarketProductRowResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new FoMarketProductRowResponse(
                row[0] != null ? ((Number) row[0]).longValue() : null,
                row[1] != null ? row[1].toString() : null,
                row[2] != null ? row[2].toString() : null,
                row[3] != null ? row[3].toString() : null,
                row[4] != null ? row[4].toString() : null,
                row[5] != null ? row[5].toString() : null,
                row[6] != null ? row[6].toString() : null,
                row[7] != null ? row[7].toString() : null
            ));
        }
        return result;
    }
}
