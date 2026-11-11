package com.jobfeed.collect;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 고용24 공채속보 API 설정. 인증키는 환경변수(WORKNET_API_KEY)로만 주입한다.
 *
 * @param keywords     제목 검색 키워드. 키워드별로 호출한 결과를 합친다.
 * @param excludeWords 제목에 포함되면 버리는 단어(개발과 무관한 오탐 제거)
 * @param display      한 번에 가져올 건수(API 최대 100)
 * @param maxPages     키워드당 최대 페이지 수(무한 반복 방지)
 */
@ConfigurationProperties(prefix = "worknet")
public record WorknetProperties(
        String apiKey,
        String url,
        List<String> keywords,
        List<String> excludeWords,
        Integer display,
        Integer maxPages
) {

    static final List<String> DEFAULT_KEYWORDS = List.of(
            "개발", "IT", "엔지니어", "AI", "Back-end", "웹", "보안", "정보보호", "시스템", "개발자");
    // 11/11 실제 수집 결과(21건)에서 개발과 무관한 공고가 절반가량이라 넓은 키워드("엔지니어", "보안")의 오탐을 거른다.
    static final List<String> DEFAULT_EXCLUDE_WORDS = List.of(
            "연구개발", "사업개발", "설비", "공정", "정비", "경비", "영업", "마케팅", "사무직");

    public WorknetProperties {
        if (url == null || url.isBlank()) {
            url = "https://www.work24.go.kr/cm/openApi/call/wk/callOpenApiSvcInfo210L21.do";
        }
        if (keywords == null || keywords.isEmpty()) {
            keywords = DEFAULT_KEYWORDS;
        }
        if (excludeWords == null) {
            excludeWords = DEFAULT_EXCLUDE_WORDS;
        }
        if (display == null || display < 1 || display > 100) {
            display = 100;
        }
        if (maxPages == null || maxPages < 1) {
            maxPages = 5;
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
