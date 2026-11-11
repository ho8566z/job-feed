package com.jobfeed.schedule;

import com.jobfeed.collect.CollectService;
import com.jobfeed.collect.JobStoreService;
import com.jobfeed.collect.WorknetProperties;
import com.jobfeed.sendlog.SendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 수집과 전송을 주기적으로 실행한다. fixedDelay라 앞 실행이 끝나야 다음이 시작된다.
 * 스케줄러 스레드가 하나라 수집과 전송은 동시에 돌지 않는다.
 * 예외는 여기서 로그만 남기고 삼켜, 한 번의 실패가 이후 실행을 막지 않게 한다.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class JobFeedScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobFeedScheduler.class);

    private final CollectService collectService;
    private final SendService sendService;
    private final WorknetProperties worknetProperties;

    public JobFeedScheduler(CollectService collectService, SendService sendService,
                            WorknetProperties worknetProperties) {
        this.collectService = collectService;
        this.sendService = sendService;
        this.worknetProperties = worknetProperties;
    }

    @Scheduled(initialDelayString = "${app.collect.initial-delay-ms:10000}",
            fixedDelayString = "${app.collect.delay-ms:3600000}")
    public void collect() {
        if (!worknetProperties.isConfigured()) {
            log.warn("WORKNET_API_KEY가 없어 수집을 건너뜁니다.");
            return;
        }
        try {
            JobStoreService.StoreResult result = collectService.run();
            log.info("수집 완료: 입력 {}건, 신규 {}건", result.received(), result.saved());
        } catch (RuntimeException e) {
            log.error("수집 실패: {}", e.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${app.send.initial-delay-ms:20000}",
            fixedDelayString = "${app.send.delay-ms:60000}")
    public void send() {
        try {
            sendService.sendPending();
        } catch (RuntimeException e) {
            log.error("전송 실행 실패: {}", e.getMessage());
        }
    }
}
