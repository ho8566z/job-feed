package com.jobfeed.notion;

import com.jobfeed.job.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 노션을 호출하지 않고 요청 형식과 오류 처리를 확인한다. (NotionLiveTest는 실제 호출)
 */
class NotionClientTest {

    private static final String TOKEN = "test-token";
    private static final String DB_ID = "test-db-id";

    private MockRestServiceServer server;
    private NotionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        NotionProperties props = new NotionProperties(TOKEN, DB_ID, null, null);
        client = new NotionClient(builder.build(), props);
    }

    private Job sampleJob() {
        return Job.builder()
                .externalId("1")
                .url("https://example.com/jobs/1")
                .company("테스트회사")
                .title("백엔드 개발자")
                .region("서울")
                .deadline(LocalDate.of(2026, 11, 30))
                .build();
    }

    @Test
    @DisplayName("페이지 생성 요청 형식과 page id 반환")
    void 페이지_생성_요청_형식과_page_id_반환() {
        server.expect(requestTo("https://api.notion.com/v1/pages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(header("Notion-Version", "2022-06-28"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"database_id\":\"" + DB_ID + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("테스트회사")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"name\":\"서울\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"name\":\"신규\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-11-30")))
                .andRespond(withSuccess("{\"id\":\"page-123\"}", MediaType.APPLICATION_JSON));

        String pageId = client.createPage(sampleJob());

        assertThat(pageId).isEqualTo("page-123");
        server.verify();
    }

    @Test
    @DisplayName("지역과 마감일이 없으면 속성을 보내지 않는다")
    void 지역과_마감일이_없으면_속성을_보내지_않는다() {
        Job job = Job.builder().externalId("2").url("https://example.com/jobs/2").company("회사").title("직무").build();

        assertThat(client.buildBody(job).toString())
                .doesNotContain(NotionClient.PROP_REGION)
                .doesNotContain(NotionClient.PROP_DEADLINE);
    }

    @Test
    @DisplayName("속도 제한 429는 재시도 가능한 실패")
    void 속도_제한_429는_재시도_가능한_실패() {
        server.expect(requestTo("https://api.notion.com/v1/pages"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("rate limited"));

        assertThatThrownBy(() -> client.createPage(sampleJob()))
                .isInstanceOfSatisfying(NotionApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(429);
                    assertThat(e.isRetryable()).isTrue();
                });
    }

    @Test
    @DisplayName("속성 불일치 400은 재시도해도 소용없는 실패")
    void 속성_불일치_400은_재시도해도_소용없는_실패() {
        server.expect(requestTo("https://api.notion.com/v1/pages"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"message\":\"지역 is not a property\"}"));

        assertThatThrownBy(() -> client.createPage(sampleJob()))
                .isInstanceOfSatisfying(NotionApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(400);
                    assertThat(e.isRetryable()).isFalse();
                    assertThat(e.getMessage()).contains("400").doesNotContain(TOKEN);
                });
    }

    @Test
    @DisplayName("응답에 id가 없으면 실패")
    void 응답에_id가_없으면_실패() {
        server.expect(requestTo("https://api.notion.com/v1/pages"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.createPage(sampleJob()))
                .isInstanceOf(NotionApiException.class);
    }

    @Test
    @DisplayName("토큰이 없으면 호출하지 않고 실패")
    void 토큰이_없으면_호출하지_않고_실패() {
        NotionClient unconfigured = new NotionClient(RestClient.create(), new NotionProperties("", "", null, null));

        assertThatThrownBy(() -> unconfigured.createPage(sampleJob()))
                .isInstanceOf(IllegalStateException.class);
    }
}
