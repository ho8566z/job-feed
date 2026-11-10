package com.jobfeed.notion;

import com.jobfeed.job.Job;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 노션 공고 DB에 페이지(행)를 만든다. 속성 이름은 노션 DB와 정확히 일치해야 한다.
 * (회사=제목, 직무, 지역, 마감일, 수집일, 링크, 상태)
 */
public class NotionClient {

    static final String PROP_COMPANY = "회사";
    static final String PROP_TITLE = "직무";
    static final String PROP_REGION = "지역";
    static final String PROP_DEADLINE = "마감일";
    static final String PROP_COLLECTED = "수집일";
    static final String PROP_LINK = "링크";
    static final String PROP_STATUS = "상태";
    static final String STATUS_NEW = "신규";

    private static final int ERROR_BODY_LIMIT = 500;

    private final RestClient restClient;
    private final NotionProperties properties;

    public NotionClient(RestClient restClient, NotionProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** @return 생성된 노션 페이지 ID (send_log.notion_page_id에 저장) */
    public String createPage(Job job) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("NOTION_TOKEN 또는 NOTION_DATABASE_ID가 설정되지 않았습니다.");
        }
        try {
            Map<?, ?> response = restClient.post()
                    .uri(properties.baseUrl() + "/v1/pages")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                    .header("Notion-Version", properties.version())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildBody(job))
                    .retrieve()
                    .body(Map.class);
            Object id = response == null ? null : response.get("id");
            if (id == null) {
                throw new NotionApiException(200, "노션 응답에 page id가 없습니다.", null);
            }
            return id.toString();
        } catch (RestClientResponseException e) {
            throw new NotionApiException(e.getStatusCode().value(), summarize(e), e);
        } catch (RestClientException e) {
            // 타임아웃, 연결 실패 등
            throw new NotionApiException(0, "노션 호출 실패: " + e.getClass().getSimpleName(), e);
        }
    }

    Map<String, Object> buildBody(Job job) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(PROP_COMPANY, Map.of("title", text(job.getCompany())));
        props.put(PROP_TITLE, Map.of("rich_text", text(job.getTitle())));
        if (job.getRegion() != null) {
            props.put(PROP_REGION, Map.of("select", Map.of("name", job.getRegion())));
        }
        if (job.getDeadline() != null) {
            props.put(PROP_DEADLINE, date(job.getDeadline()));
        }
        if (job.getCollectedAt() != null) {
            props.put(PROP_COLLECTED, date(job.getCollectedAt().toLocalDate()));
        }
        props.put(PROP_LINK, Map.of("url", job.getUrl()));
        props.put(PROP_STATUS, Map.of("select", Map.of("name", STATUS_NEW)));

        return Map.of(
                "parent", Map.of("database_id", properties.databaseId()),
                "properties", props);
    }

    private static List<Map<String, Object>> text(String value) {
        return List.of(Map.of("text", Map.of("content", value)));
    }

    private static Map<String, Object> date(LocalDate value) {
        return Map.of("date", Map.of("start", value.toString()));
    }

    private static String summarize(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body.length() > ERROR_BODY_LIMIT) {
            body = body.substring(0, ERROR_BODY_LIMIT);
        }
        return "노션 API 오류 " + e.getStatusCode().value() + ": " + body;
    }
}
