package com.jobfeed.collect;

import com.jobfeed.job.Job;
import com.jobfeed.job.JobRepository;
import com.jobfeed.sendlog.SendLog;
import com.jobfeed.sendlog.SendLogRepository;
import com.jobfeed.sendlog.SendStatus;
import com.jobfeed.source.Source;
import com.jobfeed.source.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JobStoreService.class)
@RequiredArgsConstructor
class JobStoreServiceTest {

    private final JobStoreService service;
    private final JobRepository jobRepository;
    private final SendLogRepository sendLogRepository;
    private final SourceRepository sourceRepository;

    private static CollectedJob job(String id) {
        return new CollectedJob(id, "회사" + id, "백엔드 개발자 " + id, null,
                LocalDate.of(2026, 11, 30), "https://example.com/" + id);
    }

    @Test
    @DisplayName("새 공고는 job과 PENDING send log로 저장한다")
    void 새_공고는_job과_PENDING_send_log로_저장한다() {
        JobStoreService.StoreResult result = service.store(List.of(job("1"), job("2")));

        assertThat(result.received()).isEqualTo(2);
        assertThat(result.saved()).isEqualTo(2);
        assertThat(jobRepository.count()).isEqualTo(2);

        List<SendLog> queue = sendLogRepository.findByStatusOrderByIdAsc(SendStatus.PENDING, PageRequest.of(0, 10));
        assertThat(queue).hasSize(2);
        // 입력 순서대로 대기열에 올라간다
        assertThat(queue.get(0).getJob().getExternalId()).isEqualTo("1");

        Job saved = queue.get(0).getJob();
        assertThat(saved.getCompany()).isEqualTo("회사1");
        assertThat(saved.getRegion()).isNull();
        assertThat(saved.getDeadline()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(saved.getSource().getName()).isEqualTo(JobStoreService.SOURCE_NAME);
    }

    @Test
    @DisplayName("이미 저장한 공고는 다시 저장하지 않는다")
    void 이미_저장한_공고는_다시_저장하지_않는다() {
        service.store(List.of(job("1"), job("2")));

        JobStoreService.StoreResult second = service.store(List.of(job("2"), job("3")));

        assertThat(second.saved()).isEqualTo(1);
        assertThat(jobRepository.count()).isEqualTo(3);
        assertThat(sendLogRepository.count()).isEqualTo(3);
        assertThat(sourceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("입력 안의 중복도 한 건으로 본다")
    void 입력_안의_중복도_한_건으로_본다() {
        JobStoreService.StoreResult result = service.store(List.of(job("1"), job("1")));

        assertThat(result.saved()).isEqualTo(1);
    }

    @Test
    @DisplayName("URL이 같아도 external id가 다르면 다른 공고다")
    void URL이_같아도_external_id가_다르면_다른_공고다() {
        CollectedJob a = new CollectedJob("1", "A사", "공고 A", null, null, "https://example.com/common");
        CollectedJob b = new CollectedJob("2", "B사", "공고 B", null, null, "https://example.com/common");

        assertThat(service.store(List.of(a, b)).saved()).isEqualTo(2);
    }

    @Test
    @DisplayName("긴 제목은 컬럼 길이에 맞춰 자르고 너무 긴 URL은 건너뛴다")
    void 긴_제목은_컬럼_길이에_맞춰_자르고_너무_긴_URL은_건너뛴다() {
        CollectedJob longTitle = new CollectedJob("1", "회사", "가".repeat(300), null, null, "https://example.com/1");
        CollectedJob longUrl = new CollectedJob("2", "회사", "제목", null, null, "https://example.com/" + "a".repeat(500));

        JobStoreService.StoreResult result = service.store(List.of(longTitle, longUrl));

        assertThat(result.saved()).isEqualTo(1);
        assertThat(jobRepository.findAll().get(0).getTitle()).hasSize(255);
    }

    @Test
    @DisplayName("빈 입력이어도 수집 시각을 기록한다")
    void 빈_입력이어도_수집_시각을_기록한다() {
        service.store(List.of());

        Source source = sourceRepository.findByName(JobStoreService.SOURCE_NAME).orElseThrow();
        assertThat(source.getLastCollectedAt()).isNotNull();
        assertThat(jobRepository.count()).isZero();
    }
}
