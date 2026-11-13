package com.jobfeed;

import com.jobfeed.collect.CollectService;
import com.jobfeed.collect.JobStoreService;
import com.jobfeed.collect.WorknetApiException;
import com.jobfeed.collect.WorknetClient;
import com.jobfeed.collect.WorknetProperties;
import com.jobfeed.job.JobRepository;
import com.jobfeed.notion.NotionClient;
import com.jobfeed.notion.NotionProperties;
import com.jobfeed.sendlog.SendLog;
import com.jobfeed.sendlog.SendLogRepository;
import com.jobfeed.sendlog.SendService;
import com.jobfeed.sendlog.SendStatus;
import com.jobfeed.source.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 수집 → 저장 → 전송 → 재시도를 실제 서비스와 H2 DB로 이어서 확인한다(WBS 3.1).
 * 외부 호출(고용24, 노션)만 가짜 서버로 바꾸고 실패를 주입한다. 키워드는 "개발"과 "IT" 두 개로 줄였다.
 */
@SpringBootTest(properties = {
        "app.scheduling.enabled=false",
        "worknet.api-key=test-worknet-key",
        "worknet.keywords=개발,IT",
        "notion.token=secret-notion-token",
        "notion.database-id=test-db",
        "app.send.interval-ms=0",
        "app.send.batch-size=10"
})
@RequiredArgsConstructor
class PipelineIntegrationTest {

    /** 호출 순서대로 응답을 지정하는 가짜 서버 두 개와 그에 묶인 클라이언트 */
    static class Mocks {
        final MockRestServiceServer worknet;
        final MockRestServiceServer notion;
        final RestClient worknetRest;
        final RestClient notionRest;

        Mocks() {
            RestClient.Builder w = RestClient.builder();
            worknet = MockRestServiceServer.bindTo(w).build();
            worknetRest = w.build();
            RestClient.Builder n = RestClient.builder();
            notion = MockRestServiceServer.bindTo(n).build();
            notionRest = n.build();
        }
    }

    @TestConfiguration
    static class MockClients {
        @Bean
        Mocks mocks() {
            return new Mocks();
        }

        @Bean
        @Primary
        WorknetClient mockWorknetClient(Mocks mocks, WorknetProperties properties) {
            return new WorknetClient(mocks.worknetRest, properties);
        }

        @Bean
        @Primary
        NotionClient mockNotionClient(Mocks mocks, NotionProperties properties) {
            return new NotionClient(mocks.notionRest, properties);
        }
    }

    private final Mocks mocks;
    private final CollectService collectService;
    private final SendService sendService;
    private final JobRepository jobRepository;
    private final SendLogRepository sendLogRepository;
    private final SourceRepository sourceRepository;

    @BeforeEach
    @AfterEach
    void reset() {
        mocks.worknet.reset();
        mocks.notion.reset();
        // 다른 테스트와 같은 인메모리 DB를 쓰므로 앞뒤로 비운다.
        sendLogRepository.deleteAll();
        jobRepository.deleteAll();
        sourceRepository.deleteAll();
    }

    // ---- 응답 만들기 ----

    private static String item(String id, String title, String url) {
        return "<dhsOpenEmpInfo><empSeqno>" + id + "</empSeqno><empWantedTitle>" + title + "</empWantedTitle>"
                + "<empBusiNm>회사" + id + "</empBusiNm><empWantedEndt>20261130</empWantedEndt>"
                + "<empWantedHomepgDetail>" + url + "</empWantedHomepgDetail></dhsOpenEmpInfo>";
    }

    private static String item(String id, String title) {
        return item(id, title, "https://example.com/" + id);
    }

    private static String xml(String... items) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><dhsOpenEmpInfoList><total>" + items.length
                + "</total><startPage>1</startPage><display>100</display>" + String.join("", items)
                + "</dhsOpenEmpInfoList>";
    }

    // ---- 외부 호출 기대 지정 (키워드·전송 순서대로) ----

    private void worknetReturns(String keywordInUrl, String body) {
        mocks.worknet.expect(requestTo(containsString("empWantedTitle=" + keywordInUrl)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_XML));
    }

    private void worknetFails(String keywordInUrl, HttpStatus status) {
        mocks.worknet.expect(requestTo(containsString("empWantedTitle=" + keywordInUrl)))
                .andRespond(withStatus(status));
    }

    private void notionOk(String pageId) {
        mocks.notion.expect(requestTo("https://api.notion.com/v1/pages")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"" + pageId + "\"}", MediaType.APPLICATION_JSON));
    }

    private void notionFails(HttpStatus status) {
        mocks.notion.expect(requestTo("https://api.notion.com/v1/pages")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"injected\"}"));
    }

    /** 가짜 서버는 요청이 한 번 일어나면 기대를 더 추가할 수 없으므로, 앞 라운드를 검증하고 비운 뒤 다음 라운드를 지정한다. */
    private void nextRound() {
        mocks.worknet.verify();
        mocks.notion.verify();
        mocks.worknet.reset();
        mocks.notion.reset();
    }

    /** "개발" 키워드 결과와 "IT" 키워드 결과를 주고 수집·저장을 한 번 실행한다. */
    private JobStoreService.StoreResult collect(String devXml, String itXml) {
        worknetReturns("%EA%B0%9C%EB%B0%9C", devXml);
        worknetReturns("IT", itXml);
        return collectService.run();
    }

    private List<SendLog> logs() {
        return sendLogRepository.findAll(Sort.by("id"));
    }

    // ---- 시나리오 ----

    @Test
    @DisplayName("수집부터 전송까지 정상 흐름")
    void 수집부터_전송까지_정상_흐름() {
        // 1과 2는 같은 URL(회사 공통 페이지), 2는 두 키워드에 모두 나오고, 4는 제외어(영업) 때문에 버려진다.
        String common = "https://example.com/common";
        JobStoreService.StoreResult stored = collect(
                xml(item("1", "백엔드 개발자", common), item("2", "AI 개발자", common)),
                xml(item("2", "AI 개발자", common), item("3", "IT 운영"), item("4", "영업 지원 IT")));

        assertThat(stored.received()).isEqualTo(3);
        assertThat(stored.saved()).isEqualTo(3);
        assertThat(logs()).extracting(SendLog::getStatus).containsOnly(SendStatus.PENDING);

        notionOk("page-1");
        notionOk("page-2");
        notionOk("page-3");
        SendService.SendResult sent = sendService.sendPending();

        assertThat(sent).isEqualTo(new SendService.SendResult(3, 3, 0, false));
        assertThat(logs()).extracting(SendLog::getStatus).containsOnly(SendStatus.SUCCESS);
        assertThat(logs()).extracting(SendLog::getNotionPageId).containsExactly("page-1", "page-2", "page-3");
        mocks.worknet.verify();
        mocks.notion.verify();
    }

    @Test
    @DisplayName("같은 공고를 다시 수집하면 저장도 전송도 하지 않는다")
    void 같은_공고를_다시_수집하면_저장도_전송도_하지_않는다() {
        collect(xml(item("1", "백엔드 개발자")), xml());
        notionOk("page-1");
        sendService.sendPending();
        nextRound();

        JobStoreService.StoreResult second = collect(xml(item("1", "백엔드 개발자")), xml());
        SendService.SendResult sent = sendService.sendPending();

        assertThat(second.saved()).isZero();
        assertThat(sent.attempted()).isZero();
        assertThat(jobRepository.count()).isEqualTo(1);
        mocks.notion.verify();
    }

    @Test
    @DisplayName("노션 장애가 반복되면 세 번째에 FAILED가 되고 수집은 계속된다")
    void 노션_장애가_반복되면_세_번째에_FAILED가_되고_수집은_계속된다() {
        collect(xml(item("1", "백엔드 개발자")), xml());

        for (int i = 1; i <= 3; i++) {
            nextRound();
            notionFails(HttpStatus.INTERNAL_SERVER_ERROR);
            sendService.sendPending();
            assertThat(logs().get(0).getRetryCount()).isEqualTo(i);
        }
        SendLog failed = logs().get(0);
        assertThat(failed.getStatus()).isEqualTo(SendStatus.FAILED);
        // 오류 기록에 토큰·인증키가 섞이지 않는다
        assertThat(failed.getLastError()).contains("500")
                .doesNotContain("secret-notion-token").doesNotContain("test-worknet-key");

        // 전송 실패가 수집을 막지 않고, 최종 실패한 공고는 다시 보내지 않으며, 새 공고는 정상 처리된다
        nextRound();
        JobStoreService.StoreResult next = collect(xml(item("1", "백엔드 개발자"), item("2", "AI 개발자")), xml());
        notionOk("page-2");
        SendService.SendResult sent = sendService.sendPending();

        assertThat(next.saved()).isEqualTo(1);
        assertThat(sent).isEqualTo(new SendService.SendResult(1, 1, 0, false));
        assertThat(logs()).extracting(SendLog::getStatus).containsExactly(SendStatus.FAILED, SendStatus.SUCCESS);
        mocks.notion.verify();
    }

    @Test
    @DisplayName("노션이 429면 멈추고 복구되면 남은 건을 이어서 보낸다")
    void 노션이_429면_멈추고_복구되면_남은_건을_이어서_보낸다() {
        collect(xml(item("1", "a 개발"), item("2", "b 개발"), item("3", "c 개발")), xml());

        notionOk("page-1");
        notionFails(HttpStatus.TOO_MANY_REQUESTS);
        SendService.SendResult first = sendService.sendPending();

        assertThat(first.stopped()).isTrue();
        assertThat(logs()).extracting(SendLog::getStatus)
                .containsExactly(SendStatus.SUCCESS, SendStatus.PENDING, SendStatus.PENDING);
        // 속도 제한은 공고의 잘못이 아니므로 실패 횟수를 올리지 않는다
        assertThat(logs()).extracting(SendLog::getRetryCount).containsOnly(0);

        // 복구 후(앱이 재시작돼도 PENDING부터 이어진다)
        nextRound();
        notionOk("page-2");
        notionOk("page-3");
        SendService.SendResult second = sendService.sendPending();

        assertThat(second).isEqualTo(new SendService.SendResult(2, 2, 0, false));
        assertThat(logs()).extracting(SendLog::getStatus).containsOnly(SendStatus.SUCCESS);
        mocks.notion.verify();
    }

    @Test
    @DisplayName("한 건만 400이면 나머지는 성공하고 그 건은 재시도 대상으로 남는다")
    void 한_건만_400이면_나머지는_성공하고_그_건은_재시도_대상으로_남는다() {
        collect(xml(item("1", "a 개발"), item("2", "b 개발"), item("3", "c 개발")), xml());

        notionOk("page-1");
        notionFails(HttpStatus.BAD_REQUEST);
        notionOk("page-3");
        SendService.SendResult sent = sendService.sendPending();

        assertThat(sent).isEqualTo(new SendService.SendResult(3, 2, 1, false));
        assertThat(logs()).extracting(SendLog::getStatus)
                .containsExactly(SendStatus.SUCCESS, SendStatus.PENDING, SendStatus.SUCCESS);
        assertThat(logs().get(1).getRetryCount()).isEqualTo(1);
        mocks.notion.verify();
    }

    @Test
    @DisplayName("고용24 키워드 하나가 장애여도 나머지 결과는 저장한다")
    void 고용24_키워드_하나가_장애여도_나머지_결과는_저장한다() {
        worknetFails("%EA%B0%9C%EB%B0%9C", HttpStatus.INTERNAL_SERVER_ERROR);
        worknetReturns("IT", xml(item("3", "IT 운영")));

        JobStoreService.StoreResult stored = collectService.run();

        assertThat(stored.saved()).isEqualTo(1);
        mocks.worknet.verify();
    }

    @Test
    @DisplayName("고용24가 전부 장애면 예외를 던지고 아무것도 저장하지 않는다")
    void 고용24가_전부_장애면_예외를_던지고_아무것도_저장하지_않는다() {
        worknetFails("%EA%B0%9C%EB%B0%9C", HttpStatus.INTERNAL_SERVER_ERROR);
        worknetFails("IT", HttpStatus.BAD_GATEWAY);

        assertThatThrownBy(() -> collectService.run())
                .isInstanceOfSatisfying(WorknetApiException.class, e -> assertThat(e.isRetryable()).isTrue());

        assertThat(jobRepository.count()).isZero();
        assertThat(sendService.sendPending().attempted()).isZero();
    }

    @Test
    @DisplayName("고용24 인증키 오류 응답이면 예외를 던진다")
    void 고용24_인증키_오류_응답이면_예외를_던진다() {
        String error = "<?xml version='1.0' encoding='UTF-8'?><GO24><error>인증키가 존재하지 않습니다</error></GO24>";
        worknetReturns("%EA%B0%9C%EB%B0%9C", error);
        worknetReturns("IT", error);

        assertThatThrownBy(() -> collectService.run())
                .isInstanceOf(WorknetApiException.class)
                .hasMessageContaining("인증키가 존재하지 않습니다");
        assertThat(jobRepository.count()).isZero();
    }
}
