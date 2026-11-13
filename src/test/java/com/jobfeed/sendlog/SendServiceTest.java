package com.jobfeed.sendlog;

import com.jobfeed.collect.CollectedJob;
import com.jobfeed.collect.JobStoreService;
import com.jobfeed.job.Job;
import com.jobfeed.notion.NotionApiException;
import com.jobfeed.notion.NotionClient;
import com.jobfeed.notion.NotionProperties;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 건별 트랜잭션으로 기록하는 동작을 보려고 테스트 자체의 트랜잭션은 끈다. 그래서 매번 직접 비운다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(JobStoreService.class)
@RequiredArgsConstructor
class SendServiceTest {

    private final JobStoreService storeService;
    private final SendLogRepository sendLogRepository;
    private final com.jobfeed.job.JobRepository jobRepository;
    private final com.jobfeed.source.SourceRepository sourceRepository;
    private final PlatformTransactionManager transactionManager;

    /** 호출마다 미리 정한 결과(page id 또는 예외)를 순서대로 돌려주는 가짜 노션 클라이언트 */
    private static class FakeNotion extends NotionClient {
        private final LinkedList<Object> script = new LinkedList<>();
        final List<String> sent = new ArrayList<>();

        FakeNotion(NotionProperties props) {
            super(null, props);
        }

        FakeNotion willReturn(String pageId) {
            script.add(pageId);
            return this;
        }

        FakeNotion willThrow(RuntimeException e) {
            script.add(e);
            return this;
        }

        @Override
        public String createPage(Job job) {
            sent.add(job.getExternalId());
            Object next = script.isEmpty() ? "page-default" : script.removeFirst();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return (String) next;
        }
    }

    private static final NotionProperties CONFIGURED = new NotionProperties("t", "db", null, null);

    // 커밋된 데이터가 같은 인메모리 DB를 쓰는 다른 테스트에 남지 않게 앞뒤로 모두 비운다.
    @BeforeEach
    @AfterEach
    void clean() {
        sendLogRepository.deleteAll();
        jobRepository.deleteAll();
        sourceRepository.deleteAll();
    }

    private SendService service(FakeNotion notion, NotionProperties props, int batchSize) {
        return new SendService(sendLogRepository, notion, props,
                new SendProperties(batchSize, 0L), transactionManager);
    }

    private void seed(String... ids) {
        List<CollectedJob> jobs = new ArrayList<>();
        for (String id : ids) {
            jobs.add(new CollectedJob(id, "회사" + id, "제목" + id, null, null, "https://example.com/" + id));
        }
        storeService.store(jobs);
    }

    private SendLog logOf(String externalId) {
        // job은 지연 로딩이라 트랜잭션 안에서 비교한다. 돌려주는 SendLog의 단순 필드만 쓴다.
        return new TransactionTemplate(transactionManager).execute(status ->
                sendLogRepository.findAll().stream()
                        .filter(l -> externalId.equals(l.getJob().getExternalId()))
                        .findFirst().orElseThrow());
    }

    @Test
    @DisplayName("대기 공고를 오래된 순으로 보내고 성공을 기록한다")
    void 대기_공고를_오래된_순으로_보내고_성공을_기록한다() {
        seed("1", "2");
        FakeNotion notion = new FakeNotion(CONFIGURED).willReturn("page-1").willReturn("page-2");

        SendService.SendResult result = service(notion, CONFIGURED, 10).sendPending();

        assertThat(result).isEqualTo(new SendService.SendResult(2, 2, 0, false));
        assertThat(notion.sent).containsExactly("1", "2");
        assertThat(logOf("1").getStatus()).isEqualTo(SendStatus.SUCCESS);
        assertThat(logOf("1").getNotionPageId()).isEqualTo("page-1");
        assertThat(logOf("2").getNotionPageId()).isEqualTo("page-2");
    }

    @Test
    @DisplayName("성공한 공고는 다시 보내지 않는다")
    void 성공한_공고는_다시_보내지_않는다() {
        seed("1");
        FakeNotion notion = new FakeNotion(CONFIGURED);
        SendService service = service(notion, CONFIGURED, 10);

        service.sendPending();
        SendService.SendResult second = service.sendPending();

        assertThat(second.attempted()).isZero();
        assertThat(notion.sent).hasSize(1);
    }

    @Test
    @DisplayName("한 번에 배치 크기만큼만 보낸다")
    void 한_번에_배치_크기만큼만_보낸다() {
        seed("1", "2", "3");
        FakeNotion notion = new FakeNotion(CONFIGURED);

        service(notion, CONFIGURED, 2).sendPending();

        assertThat(notion.sent).containsExactly("1", "2");
        assertThat(logOf("3").getStatus()).isEqualTo(SendStatus.PENDING);
    }

    @Test
    @DisplayName("실패하면 횟수를 올리고 세 번째에 최종 실패로 바꾼다")
    void 실패하면_횟수를_올리고_세_번째에_최종_실패로_바꾼다() {
        seed("1");
        FakeNotion notion = new FakeNotion(CONFIGURED)
                .willThrow(new NotionApiException(500, "노션 API 오류 500: boom", null))
                .willThrow(new NotionApiException(500, "노션 API 오류 500: boom", null))
                .willThrow(new NotionApiException(500, "노션 API 오류 500: boom", null));
        SendService service = service(notion, CONFIGURED, 10);

        service.sendPending();
        assertThat(logOf("1").getStatus()).isEqualTo(SendStatus.PENDING);
        assertThat(logOf("1").getRetryCount()).isEqualTo(1);
        assertThat(logOf("1").getLastError()).contains("500");

        service.sendPending();
        service.sendPending();
        assertThat(logOf("1").getStatus()).isEqualTo(SendStatus.FAILED);
        assertThat(logOf("1").getRetryCount()).isEqualTo(3);

        // 최종 실패한 공고는 더 이상 보내지 않는다
        assertThat(service.sendPending().attempted()).isZero();
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지는 계속 보낸다")
    void 한_건이_실패해도_나머지는_계속_보낸다() {
        seed("1", "2");
        FakeNotion notion = new FakeNotion(CONFIGURED)
                .willThrow(new NotionApiException(400, "노션 API 오류 400", null))
                .willReturn("page-2");

        SendService.SendResult result = service(notion, CONFIGURED, 10).sendPending();

        assertThat(result).isEqualTo(new SendService.SendResult(2, 1, 1, false));
        assertThat(logOf("2").getStatus()).isEqualTo(SendStatus.SUCCESS);
    }

    @Test
    @DisplayName("속도 제한이면 멈추고 실패 횟수를 올리지 않는다")
    void 속도_제한이면_멈추고_실패_횟수를_올리지_않는다() {
        seed("1", "2");
        FakeNotion notion = new FakeNotion(CONFIGURED)
                .willThrow(new NotionApiException(429, "노션 API 오류 429", null));

        SendService.SendResult result = service(notion, CONFIGURED, 10).sendPending();

        assertThat(result.stopped()).isTrue();
        assertThat(result.attempted()).isZero();
        assertThat(notion.sent).containsExactly("1");
        assertThat(logOf("1").getRetryCount()).isZero();
        assertThat(logOf("2").getStatus()).isEqualTo(SendStatus.PENDING);
    }

    @Test
    @DisplayName("인증 오류면 멈추고 실패 횟수를 올리지 않는다")
    void 인증_오류면_멈추고_실패_횟수를_올리지_않는다() {
        seed("1", "2");
        FakeNotion notion = new FakeNotion(CONFIGURED)
                .willThrow(new NotionApiException(401, "노션 API 오류 401", null));

        SendService.SendResult result = service(notion, CONFIGURED, 10).sendPending();

        assertThat(result.stopped()).isTrue();
        assertThat(logOf("1").getRetryCount()).isZero();
    }

    @Test
    @DisplayName("노션 설정이 없으면 호출하지 않는다")
    void 노션_설정이_없으면_호출하지_않는다() {
        seed("1");
        NotionProperties empty = new NotionProperties("", "", null, null);
        FakeNotion notion = new FakeNotion(empty);

        SendService.SendResult result = service(notion, empty, 10).sendPending();

        assertThat(result.stopped()).isTrue();
        assertThat(notion.sent).isEmpty();
        assertThat(logOf("1").getStatus()).isEqualTo(SendStatus.PENDING);
    }
}
