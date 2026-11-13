package com.jobfeed.api;

import com.jobfeed.collect.CollectInProgressException;
import com.jobfeed.collect.CollectService;
import com.jobfeed.collect.CollectedJob;
import com.jobfeed.collect.JobStoreService;
import com.jobfeed.collect.WorknetApiException;
import com.jobfeed.job.JobRepository;
import com.jobfeed.sendlog.SendLogRepository;
import com.jobfeed.source.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내부 API(수동 수집, 전송 현황)를 H2 DB와 함께 확인한다. 수집 호출만 가짜로 바꾼다.
 */
@SpringBootTest(properties = "app.scheduling.enabled=false")
@RequiredArgsConstructor
class OpsApiTest {

    /** run() 결과나 예외를 미리 정해 두는 가짜 수집 서비스 */
    private static class FakeCollect extends CollectService {
        JobStoreService.StoreResult result = new JobStoreService.StoreResult(0, 0);
        RuntimeException error;

        FakeCollect() {
            super(null, null);
        }

        @Override
        public JobStoreService.StoreResult run() {
            if (error != null) {
                throw error;
            }
            return result;
        }
    }

    private final JobStoreService storeService;
    private final SendLogRepository sendLogRepository;
    private final JobRepository jobRepository;
    private final SourceRepository sourceRepository;
    private final PlatformTransactionManager transactionManager;

    private FakeCollect collect;
    private MockMvc mockMvc;

    @BeforeEach
    @AfterEach
    void clean() {
        sendLogRepository.deleteAll();
        jobRepository.deleteAll();
        sourceRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
        collect = new FakeCollect();
        mockMvc = MockMvcBuilders.standaloneSetup(new OpsApiController(collect, new com.jobfeed.sendlog.SendLogQueryService(sendLogRepository)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    /** 공고 n건을 저장한다. 1번은 SUCCESS, 2번은 재시도 1회 뒤 PENDING 유지, 3번은 3회 실패로 FAILED, 나머지는 PENDING. */
    private void seed(int n) {
        List<CollectedJob> jobs = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            jobs.add(new CollectedJob("id" + i, "회사" + i, "제목" + i, null, LocalDate.of(2026, 11, 30), "https://example.com/" + i));
        }
        storeService.store(jobs);
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            var logs = sendLogRepository.findAll(org.springframework.data.domain.Sort.by("id"));
            logs.get(0).markSuccess("page-1");
            if (n >= 2) {
                logs.get(1).markFailure("노션 API 오류 500");
            }
            if (n >= 3) {
                logs.get(2).markFailure("e1");
                logs.get(2).markFailure("e2");
                logs.get(2).markFailure("e3");
            }
        });
    }

    // ---- POST /api/v1/collect ----

    @Test
    @DisplayName("수동 수집은 조회 신규 중복 건수를 돌려준다")
    void 수동_수집은_조회_신규_중복_건수를_돌려준다() throws Exception {
        collect.result = new JobStoreService.StoreResult(5, 2);

        mockMvc.perform(post("/api/v1/collect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].name").value("고용24 공채속보"))
                .andExpect(jsonPath("$.sources[0].fetched").value(5))
                .andExpect(jsonPath("$.sources[0].created").value(2))
                .andExpect(jsonPath("$.sources[0].duplicated").value(3));
    }

    @Test
    @DisplayName("이미 수집 중이면 409")
    void 이미_수집_중이면_409() throws Exception {
        collect.error = new CollectInProgressException();

        mockMvc.perform(post("/api/v1/collect"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("이미 수집 중입니다."));
    }

    @Test
    @DisplayName("수집이 실패하면 500과 원인을 돌려주고 인증키는 담지 않는다")
    void 수집이_실패하면_500과_원인을_돌려주고_인증키는_담지_않는다() throws Exception {
        collect.error = new WorknetApiException(502, "고용24 API 오류 502");

        mockMvc.perform(post("/api/v1/collect"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("고용24 API 오류 502"));
    }

    @Test
    @DisplayName("인증키가 없으면 500과 안내 메시지")
    void 인증키가_없으면_500과_안내_메시지() throws Exception {
        collect.error = new IllegalStateException("WORKNET_API_KEY가 설정되지 않았습니다.");

        mockMvc.perform(post("/api/v1/collect"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("WORKNET_API_KEY가 설정되지 않았습니다."));
    }

    // ---- GET /api/v1/send-logs ----

    @Test
    @DisplayName("전송 현황은 상태별 건수와 목록을 최신순으로 돌려준다")
    void 전송_현황은_상태별_건수와_목록을_최신순으로_돌려준다() throws Exception {
        seed(4);

        mockMvc.perform(get("/api/v1/send-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.PENDING").value(2))
                .andExpect(jsonPath("$.counts.SUCCESS").value(1))
                .andExpect(jsonPath("$.counts.FAILED").value(1))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                // 최신(id가 큰) 것이 먼저
                .andExpect(jsonPath("$.items[0].company").value("회사4"))
                .andExpect(jsonPath("$.items[3].company").value("회사1"))
                .andExpect(jsonPath("$.items[3].status").value("SUCCESS"))
                .andExpect(jsonPath("$.items[3].notionPageId").value("page-1"))
                .andExpect(jsonPath("$.items[1].status").value("FAILED"))
                .andExpect(jsonPath("$.items[1].retryCount").value(3))
                .andExpect(jsonPath("$.items[1].lastError").value("e3"));
    }

    @Test
    @DisplayName("status로 걸러 볼 수 있다")
    void status로_걸러_볼_수_있다() throws Exception {
        seed(4);

        mockMvc.perform(get("/api/v1/send-logs").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                // 건수는 필터와 무관하게 전체 기준
                .andExpect(jsonPath("$.counts.PENDING").value(2));
    }

    @Test
    @DisplayName("잘못된 status는 400")
    void 잘못된_status는_400() throws Exception {
        mockMvc.perform(get("/api/v1/send-logs").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("페이지 크기는 최대 100으로 제한하고 범위를 벗어난 값은 400")
    void 페이지_크기는_최대_100으로_제한하고_범위를_벗어난_값은_400() throws Exception {
        seed(3);

        mockMvc.perform(get("/api/v1/send-logs").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        mockMvc.perform(get("/api/v1/send-logs").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/send-logs").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("페이지를 나눠 조회한다")
    void 페이지를_나눠_조회한다() throws Exception {
        seed(3);

        mockMvc.perform(get("/api/v1/send-logs").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].company").value("회사1"));
    }

    @Test
    @DisplayName("데이터가 없어도 건수는 0으로 돌려준다")
    void 데이터가_없어도_건수는_0으로_돌려준다() throws Exception {
        mockMvc.perform(get("/api/v1/send-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.PENDING").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }
}
