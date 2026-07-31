챗봇 검색
 
/api/v1/fo/search/chatbot
 
 
Azure AI 검색(문서 검색)
 
/api/v1/fo/search/integrated
 
FO에서 챗봇 검색 호출 시 절대경로로 호출해야 합니다.
 
검색 -> 챗봇 검색 -> 첫 키워드 반환 -> 계속 결과 반환
                                  반환된 키워드로 Azure AI 검색 및 내부 DB 검색


CHATBOT_SEACH_API_KEY="<CHATBOT_SEACH_API_KEY>"
AZURE_SEARCH_API_KEY="<AZURE_SEARCH_API_KEY>"