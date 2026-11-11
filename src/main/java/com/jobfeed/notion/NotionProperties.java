package com.jobfeed.notion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 노션 연동 설정. 값은 환경변수(NOTION_TOKEN, NOTION_DATABASE_ID)로만 주입한다.
 */
@ConfigurationProperties(prefix = "notion")
public record NotionProperties(
        String token,
        String databaseId,
        String baseUrl,
        String version
) {

    public NotionProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.notion.com";
        }
        // 공식 문서에서 현재 권장 버전을 확인한 뒤 필요하면 바꾼다.
        if (version == null || version.isBlank()) {
            version = "2022-06-28";
        }
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank()
                && databaseId != null && !databaseId.isBlank();
    }
}
