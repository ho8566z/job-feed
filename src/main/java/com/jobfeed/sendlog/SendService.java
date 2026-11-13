package com.jobfeed.sendlog;

import com.jobfeed.job.Job;
import com.jobfeed.notion.NotionApiException;
import com.jobfeed.notion.NotionClient;
import com.jobfeed.notion.NotionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Consumer;

/**
 * 전송 대기(PENDING) 공고를 오래된 순으로 몇 건씩 노션에 보낸다.
 * 노션 호출은 트랜잭션 밖에서 하고, 결과 기록만 건별 트랜잭션으로 한다.
 */
@Service
@Slf4j
public class SendService {

    private static final int ERROR_LIMIT = 1000;

    private final SendLogRepository sendLogRepository;
    private final NotionClient notionClient;
    private final NotionProperties notionProperties;
    private final SendProperties properties;
    private final TransactionTemplate tx;

    public SendService(SendLogRepository sendLogRepository, NotionClient notionClient,
                       NotionProperties notionProperties, SendProperties properties,
                       PlatformTransactionManager transactionManager) {
        this.sendLogRepository = sendLogRepository;
        this.notionClient = notionClient;
        this.notionProperties = notionProperties;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * @param attempted 노션 호출을 시도한 건수
     * @param success   성공
     * @param failed    실패(재시도 대상이거나 최종 실패)
     * @param stopped   속도 제한·인증 문제로 이번 실행을 중간에 멈췄는지
     */
    public record SendResult(int attempted, int success, int failed, boolean stopped) {
    }

    public SendResult sendPending() {
        if (!notionProperties.isConfigured()) {
            log.warn("NOTION_TOKEN 또는 NOTION_DATABASE_ID가 없어 전송을 건너뜁니다.");
            return new SendResult(0, 0, 0, true);
        }

        List<SendLog> batch = tx.execute(status -> sendLogRepository.findByStatusOrderByIdAsc(
                SendStatus.PENDING, PageRequest.of(0, properties.batchSize())));

        int attempted = 0;
        int success = 0;
        int failed = 0;
        boolean stopped = false;

        for (SendLog sendLog : batch) {
            if (attempted > 0 && !pause()) {
                stopped = true;
                break;
            }
            attempted++;
            Job job = sendLog.getJob();
            try {
                String pageId = notionClient.createPage(job);
                update(sendLog.getId(), l -> l.markSuccess(pageId));
                success++;
            } catch (NotionApiException e) {
                if (isStopCondition(e)) {
                    // 429(속도 제한), 401·403(토큰·권한)은 공고의 잘못이 아니므로 실패 횟수를 올리지 않는다.
                    log.warn("노션 오류 {}로 이번 전송을 멈춥니다: {}", e.getStatus(), e.getMessage());
                    attempted--;
                    stopped = true;
                    break;
                }
                String error = cut(e.getMessage());
                update(sendLog.getId(), l -> l.markFailure(error));
                failed++;
                log.warn("공고 전송 실패 (jobId={}): {}", job.getId(), error);
            }
        }
        log.info("전송: 시도 {}건, 성공 {}건, 실패 {}건, 중단 {}", attempted, success, failed, stopped);
        return new SendResult(attempted, success, failed, stopped);
    }

    private void update(Long id, Consumer<SendLog> change) {
        tx.executeWithoutResult(status -> sendLogRepository.findById(id).ifPresent(change));
    }

    private static boolean isStopCondition(NotionApiException e) {
        return e.getStatus() == 429 || e.getStatus() == 401 || e.getStatus() == 403;
    }

    /** @return 계속해도 되면 true, 인터럽트되면 false */
    private boolean pause() {
        long interval = properties.intervalMs();
        if (interval == 0) {
            return true;
        }
        try {
            Thread.sleep(interval);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String cut(String message) {
        if (message == null) {
            return "알 수 없는 오류";
        }
        return message.length() <= ERROR_LIMIT ? message : message.substring(0, ERROR_LIMIT);
    }
}
