package com.jobfeed.common;

import com.jobfeed.job.Job;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class KstTest {

    @Test
    void 서버_기본_시간대가_달라도_수집_시각은_한국_시간이다() {
        TimeZone original = TimeZone.getDefault();
        try {
            // 서버가 UTC로 돌아도 한국 시간(+9시간)으로 기록돼야 한다
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocalDateTime utcNow = LocalDateTime.now();

            Job job = Job.builder().externalId("1").url("https://example.com/1").company("회사").title("제목").build();

            long hours = Duration.between(utcNow, job.getCollectedAt()).toHours();
            assertThat(hours).isBetween(8L, 9L);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
