package com.jobfeed.sendlog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 노션 전송 설정.
 *
 * @param batchSize  한 번 실행에 보낼 최대 건수
 * @param intervalMs 건 사이 대기(ms). 노션 속도 제한(평균 초당 3회)을 넘지 않게 기본 400ms
 */
@ConfigurationProperties(prefix = "send")
public record SendProperties(
        Integer batchSize,
        Long intervalMs
) {

    public SendProperties {
        if (batchSize == null || batchSize < 1) {
            batchSize = 10;
        }
        if (intervalMs == null || intervalMs < 0) {
            intervalMs = 400L;
        }
    }
}
