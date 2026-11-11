package com.jobfeed;

import com.jobfeed.collect.CollectedJob;
import com.jobfeed.collect.JobStoreService;
import com.jobfeed.job.JobRepository;
import com.jobfeed.sendlog.SendLogRepository;
import com.jobfeed.sendlog.SendStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.domain.PageRequest;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * file 프로필(H2 파일 DB)에서 앱을 껐다 켜도 공고와 전송 대기열이 남는지 확인한다(rcv-001).
 */
class FileProfilePersistenceTest {

    @TempDir
    Path dataDir;

    private ConfigurableApplicationContext start() {
        return SpringApplication.run(JobFeedApplication.class,
                "--spring.profiles.active=file",
                "--JOBFEED_DATA_DIR=" + dataDir.toString().replace('\\', '/'),
                "--app.scheduling.enabled=false",
                "--server.port=0");
    }

    private static CollectedJob job(String id) {
        return new CollectedJob(id, "회사" + id, "제목" + id, null, LocalDate.of(2026, 11, 30), "https://example.com/" + id);
    }

    @Test
    void 앱을_껐다_켜도_공고와_전송_대기열이_남고_같은_공고는_다시_저장하지_않는다() {
        try (ConfigurableApplicationContext first = start()) {
            JobStoreService.StoreResult saved = first.getBean(JobStoreService.class).store(List.of(job("1"), job("2")));
            assertThat(saved.saved()).isEqualTo(2);
        }

        try (ConfigurableApplicationContext second = start()) {
            assertThat(second.getBean(JobRepository.class).count()).isEqualTo(2);
            assertThat(second.getBean(SendLogRepository.class)
                    .findByStatusOrderByIdAsc(SendStatus.PENDING, PageRequest.of(0, 10))).hasSize(2);

            // 재시작 뒤 같은 공고를 다시 수집해도 새 공고로 취급하지 않는다(노션 중복 전송 방지)
            JobStoreService.StoreResult again = second.getBean(JobStoreService.class).store(List.of(job("1"), job("3")));
            assertThat(again.saved()).isEqualTo(1);
        }
    }
}
