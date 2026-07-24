package com.ge.bo.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 페이지 데이터 비즈니스 로직
 * - 목록 조회: EntityManager 네이티브 쿼리로 동적 JSONB 검색
 * - 단건 CRUD: JPA Repository 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrDtlExportService {

  @PersistenceContext
    private EntityManager entityManager;

    /**
     * 커리큘럼 상세 교육 일정 csv 다운로드 전용
     *
     * @return 전체 데이터 목록 (Map<키, 값> 형태)
     */
  @Transactional(readOnly = true)
    public List<Map<String, Object>> getTrnSchedulesList(Map<String, String> allParams) {

    StringBuilder addWhere = new StringBuilder();
    makeAddWhere(allParams, addWhere);

//    String dataSql = "SELECT "
//	        + "  curr.data_json -> 'curriculum' ->> 'title' AS curr_title "
//	        + " , NULLIF(pd.data_json -> 'curriculum_detail2' ->> 'training_date_from', '')::date AS training_date_from "
//            + " , NULLIF(pd.data_json -> 'curriculum_detail2' ->> 'training_date_to', '')::date AS training_date_to"
//            + " , NULLIF(training.item ->> 'date', '')::date AS training_date "
//            + " , NULLIF(training.item ->> 'time_from', '')::time AS time_from "
//            + " , NULLIF(training.item ->> 'time_to', '')::time AS time_to "
//            + " , training.item ->> 'title' AS title "
//            + " , training.item ->> 'description' AS description "
//            + " , training.item ->> 'trainer' AS trainer "
//            + " , CASE WHEN pd.data_json -> 'curriculum_detail3' ->> 'training_fee_type' = '001' THEN 'free' "
//            + " WHEN NULLIF(btrim(pd.data_json -> 'curriculum_detail3' ->> 'training_fee'), '') IS NOT NULL "
//            + " THEN '$' || btrim(pd.data_json -> 'curriculum_detail3' ->> 'training_fee') "
//            + " ELSE NULL END AS training_fee "
//            + " FROM page_data pd "
//            + " LEFT JOIN page_data curr ON curr.id::text = pd.data_json -> 'curriculum_detail1' ->> 'curriculum_id' "
//            + "                         AND curr.data_slug = 'currMgmt-data' "
//            + " LEFT JOIN LATERAL jsonb_array_elements( "
//            + "         COALESCE (pd.data_json -> 'training_schedule', '[]'::jsonb) "
//            + " ) AS training(item) ON TRUE "
//            + " WHERE pd.data_json IS NOT NULL "
//            + " AND pd.data_slug = 'currDtlMgmt-data' "
//            + " AND NULLIF(pd.data_json -> 'curriculum_detail1' ->> 'curriculum_id', '') IS NOT NULL "
//            + addWhere
//            + " ORDER BY curr_title, training_date_from, training_date_to, training_date, time_from, time_to";

    StringBuilder dataSql = new StringBuilder("SELECT ");
    dataSql.append("  curr.data_json -> 'curriculum' ->> 'title' AS curr_title ")
            .append(" , NULLIF(pd.data_json -> 'curriculum_detail2' ->> 'training_date_from', '')::date AS training_date_from ")
            .append(" , NULLIF(pd.data_json -> 'curriculum_detail2' ->> 'training_date_to', '')::date AS training_date_to")
            .append(" , NULLIF(training.item ->> 'date', '')::date AS training_date ")
            .append(" , NULLIF(training.item ->> 'time_from', '')::time AS time_from ")
            .append(" , NULLIF(training.item ->> 'time_to', '')::time AS time_to ")
            .append(" , training.item ->> 'title' AS title ")
            .append(" , training.item ->> 'description' AS description ")
            .append(" , training.item ->> 'trainer' AS trainer ")
            .append(" , CASE WHEN pd.data_json -> 'curriculum_detail3' ->> 'training_fee_type' = '001' THEN 'free' ")
            .append(" WHEN NULLIF(btrim(pd.data_json -> 'curriculum_detail3' ->> 'training_fee'), '') IS NOT NULL ")
            .append(" THEN '$' || btrim(pd.data_json -> 'curriculum_detail3' ->> 'training_fee') ")
            .append(" ELSE NULL END AS training_fee ")
            .append(" FROM page_data pd ")
            .append(" LEFT JOIN page_data curr ON curr.id::text = pd.data_json -> 'curriculum_detail1' ->> 'curriculum_id' ")
            .append("                         AND curr.data_slug = 'currMgmt-data' ")
            .append(" LEFT JOIN LATERAL jsonb_array_elements( ")
            .append("         COALESCE (pd.data_json -> 'training_schedule', '[]'::jsonb) ")
            .append(" ) AS training(item) ON TRUE ")
            .append(" WHERE pd.data_json IS NOT NULL ")
            .append(" AND pd.data_slug = 'currDtlMgmt-data' ")
            .append(" AND NULLIF(pd.data_json -> 'curriculum_detail1' ->> 'curriculum_id', '') IS NOT NULL ")
            .append(addWhere)
            .append(" ORDER BY curr_title, training_date_from, training_date_to, training_date, time_from, time_to");

//    Query dataQuery = entityManager.createNativeQuery(String.valueOf(dataSql));

//    @SuppressWarnings("unchecked")
//    List<Map<String, Object>> rows = dataQuery
//            .unwrap(NativeQuery.class)
//            .setTupleTransformer(AliasToEntityMapResultTransformer.INSTANCE)
//            .getResultList();

    @SuppressWarnings("unchecked")
    NativeQuery<Map<String, Object>> dataQuery = entityManager
            .createNativeQuery(String.valueOf(dataSql))
            .unwrap(NativeQuery.class);

    dataQuery.setTupleTransformer((tuple, aliases) -> {
      Map<String, Object> row = new LinkedHashMap<>();

      for (int i = 0; i < aliases.length; i++) {
        row.put(aliases[i], tuple[i]);
      }

      return row;
    });

    return dataQuery.getResultList();
  }

  /**
   * 커리큘럼 상세 내역 csv 다운로드 전용
   *
   * @return 전체 데이터 목록 (Map<키, 값> 형태)
   */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> getCurrDetailList(Map<String, String> allParams) {

    StringBuilder addWhere = new StringBuilder();
    makeAddWhere(allParams, addWhere);

//    String dataSql = " SELECT "
//            + " ( "
//            + "     SELECT cd.name "
//            + "     FROM code_group cg "
//            + "     JOIN code_detail cd "
//            + "     ON cd.group_id = cg.id "
//            + "     WHERE cg.group_code = 'TRAININGCOURSE' "
//            + "     AND cd.code = trim(pd.data_json -> 'curriculum_detail1' ->> 'training_course') "
//            + "     AND cg.is_active = true "
//            + "     AND cd.is_active = true "
//            + " ) AS training_course, "
//            + " curr.data_json -> 'curriculum' ->> 'title' AS curr_title, "
//            + " ( "
//            + "   SELECT string_agg( "
//            + "      cd.name, "
//            + "      ',' "
//            + "   ORDER BY codes.ord "
//            + "   ) "
//            + "   FROM string_to_table(pd.data_json -> 'curriculum_detail1' ->> 'training_type', ',') "
//            + "   WITH ORDINALITY AS codes(code, ord) "
//            + "   JOIN code_group cg "
//            + "   ON cg.group_code = 'TRAININGTYPE' "
//            + "   JOIN code_detail cd "
//            + "   ON cd.group_id = cg.id "
//            + "   AND cd.code = TRIM(codes.code) "
//            + "   AND cg.is_active = true "
//            + "   AND cd.is_active = true "
//            + " ) AS training_type, "
//            + " COALESCE( "
//            + "     ( "
//            + "       SELECT string_agg(value, ',') "
//            + "       FROM jsonb_array_elements_text(COALESCE(pd.data_json -> 'power_list','[]'::jsonb)) AS arr(value) "
//            + "     ), "
//            + " '') AS power_list, "
//            + " COALESCE( "
//            + "     ( "
//            + "       SELECT string_agg(value, ',') "
//            + "       FROM jsonb_array_elements_text(COALESCE(pd.data_json -> 'automation_list', '[]'::jsonb)) AS arr(value) "
//            + "     ), "
//            + " '') AS automation_list, "
//            + " pd.data_json -> 'curriculum_detail2' ->> 'title' AS title, "
//            + " NULLIF(pd.data_json -> 'curriculum_detail2' ->> 'training_date_from', '')::date AS training_date_from, "
//            + " NULLIF(pd.data_json -> 'curriculum_detail2' ->> 'training_date_to', '')::date AS training_date_to, "
//            + " pd.data_json -> 'curriculum_detail2' ->> 'duration' AS duration, "
//            + " (pd.data_json -> 'curriculum_detail2' ->> 'capacity') || ' ppl' AS capacity, "
//            + " CONCAT_WS( "
//            + "     ', ', "
//            + "     NULLIF(TRIM(pd.data_json -> 'curriculum_detail2' ->> 'address_detail'), ''), "
//            + "     NULLIF(TRIM(pd.data_json -> 'curriculum_detail2' ->> 'address'), '') "
//            + " ) AS address, "
//            + " CASE "
//            + "   WHEN REGEXP_REPLACE(pd.data_json -> 'curriculum_detail2' ->> 'phone', '-', '', 'g') ~ '^[0-9]{10}$' "
//            + "   THEN REGEXP_REPLACE( "
//            + "           REGEXP_REPLACE(pd.data_json -> 'curriculum_detail2' ->> 'phone', '-', '', 'g'), "
//            + "           '^([0-9]{3})([0-9]{3})([0-9]{4})$', "
//            + "           '\\1-\\2-\\3' "
//            + "   ) "
//            + "   ELSE pd.data_json -> 'curriculum_detail2' ->> 'phone' "
//            + " END AS phone, "
//            + " pd.data_json -> 'curriculum_detail2' ->> 'email' AS email "
//            + " FROM page_data pd "
//            + " LEFT JOIN page_data curr "
//            + " ON curr.id::text = pd.data_json -> 'curriculum_detail1' ->> 'curriculum_id' "
//            + " AND curr.data_slug = 'currMgmt-data' "
//            + " WHERE pd.data_json IS NOT NULL "
//            + " AND pd.data_slug = 'currDtlMgmt-data' "
//            + " AND NULLIF(pd.data_json -> 'curriculum_detail1' ->> 'curriculum_id', '') IS NOT NULL "
//            + addWhere
//            + " ORDER BY training_course, curr_title ";
    StringBuilder dataSql = new StringBuilder(" SELECT ");
                  dataSql.append(" ( ")
                          .append("     SELECT cd.name ")
                          .append("     FROM code_group cg ")
                          .append("     JOIN code_detail cd ")
                          .append("     ON cd.group_id = cg.id ")
                          .append("     WHERE cg.group_code = 'TRAININGCOURSE' ")
                          .append("     AND cd.code = trim(pd.data_json -> 'curriculum_detail1' ->> 'training_course') ")
                          .append("     AND cg.is_active = true ")
                          .append("     AND cd.is_active = true ")
                          .append(" ) AS training_course, ")
                          .append(" curr.data_json -> 'curriculum' ->> 'title' AS curr_title, ")
                          .append(" ( ")
                          .append("   SELECT string_agg( ")
                          .append("      cd.name, ")
                          .append("      ',' ")
                          .append("   ORDER BY codes.ord ")
                          .append("   ) ")
                          .append("   FROM string_to_table(pd.data_json -> 'curriculum_detail1' ->> 'training_type', ',') ")
                          .append("   WITH ORDINALITY AS codes(code, ord) ")
                          .append("   JOIN code_group cg ")
                          .append("   ON cg.group_code = 'TRAININGTYPE' ")
                          .append("   JOIN code_detail cd ")
                          .append("   ON cd.group_id = cg.id ")
                          .append("   AND cd.code = TRIM(codes.code) ")
                          .append("   AND cg.is_active = true ")
                          .append("   AND cd.is_active = true ")
                          .append(" ) AS training_type, ")
                          .append(" COALESCE( ")
                          .append("     ( ")
                          .append("       SELECT string_agg(value, ',') ")
                          .append("       FROM jsonb_array_elements_text(COALESCE(pd.data_json -> 'power_list','[]'::jsonb)) AS arr(value) ")
                          .append("     ), ")
                          .append(" '') AS power_list, ")
                          .append(" COALESCE( ")
                          .append("     ( ")
                          .append("       SELECT string_agg(value, ',') ")
                          .append("       FROM jsonb_array_elements_text(COALESCE(pd.data_json -> 'automation_list', '[]'::jsonb)) AS arr(value) ")
                          .append("     ), ")
                          .append(" '') AS automation_list, ")
                          .append(" pd.data_json -> 'curriculum_detail2' ->> 'title' AS title, ")
                          .append(" NULLIF(pd.data_json -> 'curriculum_detail2' ->> 'training_date_from', '')::date AS training_date_from, ")
                          .append(" NULLIF(pd.data_json -> 'curriculum_detail2' ->> 'training_date_to', '')::date AS training_date_to, ")
                          .append(" pd.data_json -> 'curriculum_detail2' ->> 'duration' AS duration, ")
                          .append(" (pd.data_json -> 'curriculum_detail2' ->> 'capacity') || ' ppl' AS capacity, ")
                          .append(" CONCAT_WS( ")
                          .append("     ', ', ")
                          .append("     NULLIF(TRIM(pd.data_json -> 'curriculum_detail2' ->> 'address_detail'), ''), ")
                          .append("     NULLIF(TRIM(pd.data_json -> 'curriculum_detail2' ->> 'address'), '') ")
                          .append(" ) AS address, ")
                          .append(" CASE ")
                          .append("   WHEN REGEXP_REPLACE(pd.data_json -> 'curriculum_detail2' ->> 'phone', '-', '', 'g') ~ '^[0-9]{10}$' ")
                          .append("   THEN REGEXP_REPLACE( ")
                          .append("           REGEXP_REPLACE(pd.data_json -> 'curriculum_detail2' ->> 'phone', '-', '', 'g'), ")
                          .append("           '^([0-9]{3})([0-9]{3})([0-9]{4})$', ")
                          .append("           '\\1-\\2-\\3' ")
                          .append("   ) ")
                          .append("   ELSE pd.data_json -> 'curriculum_detail2' ->> 'phone' ")
                          .append(" END AS phone, ")
                          .append(" pd.data_json -> 'curriculum_detail2' ->> 'email' AS email ")
                          .append(" FROM page_data pd ")
                          .append(" LEFT JOIN page_data curr ")
                          .append(" ON curr.id::text = pd.data_json -> 'curriculum_detail1' ->> 'curriculum_id' ")
                          .append(" AND curr.data_slug = 'currMgmt-data' ")
                          .append(" WHERE pd.data_json IS NOT NULL ")
                          .append(" AND pd.data_slug = 'currDtlMgmt-data' ")
                          .append(" AND NULLIF(pd.data_json -> 'curriculum_detail1' ->> 'curriculum_id', '') IS NOT NULL ")
                          .append(addWhere)
                          .append(" ORDER BY training_course, curr_title, training_date_from, training_date_to ");

//    Query dataQuery = entityManager.createNativeQuery(String.valueOf(dataSql));
//
//    @SuppressWarnings("unchecked")
//    List<Map<String, Object>> rows = dataQuery
//            .unwrap(NativeQuery.class)
//            .setTupleTransformer(AliasToEntityMapResultTransformer.INSTANCE)
//            .getResultList();
    @SuppressWarnings("unchecked")
    NativeQuery<Map<String, Object>> dataQuery = entityManager
            .createNativeQuery(String.valueOf(dataSql))
            .unwrap(NativeQuery.class);

    dataQuery.setTupleTransformer((tuple, aliases) -> {
      Map<String, Object> row = new LinkedHashMap<>();

      for (int i = 0; i < aliases.length; i++) {
        row.put(aliases[i], tuple[i]);
      }

      return row;
    });

    return dataQuery.getResultList();
  }

  private void makeAddWhere(Map<String, String> allParams, StringBuilder addWhere){

    allParams.forEach((key, value) -> {

      if (value == null || value.isBlank()){

      } else {
        if(key.contains("_gte")){
          if(key.startsWith("updatedAt")){
            addWhere.append(" AND pd.updated_at >= CAST('").append(value).append("' AS date) ");
          }else{
            addWhere.append(" AND pd.data_json -> 'curriculum_detail2' ->> '").append(key).append("' >= '").append(value).append("' ");
          }
        }else if(key.contains("_lte")){
          if(key.startsWith("updatedAt")){
            addWhere.append(" AND pd.updated_at < CAST('").append(value).append("' AS date) + INTERVAL '1 day'");
          }else{
            addWhere.append(" AND pd.data_json -> 'curriculum_detail2' ->> '").append(key).append("' <= '").append(value).append("' ");
          }
        }
      }
    });
  }
}
