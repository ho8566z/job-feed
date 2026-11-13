package com.jobfeed.collect;

import com.jobfeed.common.Kst;
import com.jobfeed.job.Job;
import com.jobfeed.job.JobRepository;
import com.jobfeed.sendlog.SendLog;
import com.jobfeed.sendlog.SendLogRepository;
import com.jobfeed.source.Source;
import com.jobfeed.source.SourceRepository;
import com.jobfeed.source.SourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 수집한 공고 중 처음 보는 것만 job으로 저장하고, 전송 대기열(send_log, PENDING)에 올린다.
 * 중복 판단 기준은 (source, external_id)이다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobStoreService {

    public static final String SOURCE_NAME = "고용24 공채속보";

    // job 테이블 컬럼 길이. 넘으면 저장이 실패하므로 미리 맞춘다.
    private static final int EXTERNAL_ID_MAX = 100;
    private static final int COMPANY_MAX = 255;
    private static final int TITLE_MAX = 255;
    private static final int URL_MAX = 500;

    private final SourceRepository sourceRepository;
    private final JobRepository jobRepository;
    private final SendLogRepository sendLogRepository;

    /**
     * @param received 입력 건수
     * @param saved    새로 저장한 건수(= 대기열에 올린 건수)
     */
    public record StoreResult(int received, int saved) {
    }

    @Transactional
    public StoreResult store(List<CollectedJob> collected) {
        Source source = sourceRepository.findByName(SOURCE_NAME)
                .orElseGet(() -> sourceRepository.save(new Source(SOURCE_NAME, SourceType.API)));

        // 입력 안에서의 중복도 한 건으로 본다. 순서는 유지한다(오래된 순 전송의 기준).
        Map<String, CollectedJob> unique = new LinkedHashMap<>();
        for (CollectedJob c : collected) {
            unique.putIfAbsent(c.externalId(), c);
        }

        int saved = 0;
        if (!unique.isEmpty()) {
            Set<String> existing = jobRepository.findBySourceAndExternalIdIn(source, unique.keySet()).stream()
                    .map(Job::getExternalId)
                    .collect(Collectors.toSet());

            for (CollectedJob c : unique.values()) {
                if (existing.contains(c.externalId())) {
                    continue;
                }
                if (c.externalId().length() > EXTERNAL_ID_MAX || c.url().length() > URL_MAX) {
                    log.warn("길이 제한을 넘는 공고를 건너뜁니다. externalId={}", c.externalId());
                    continue;
                }
                Job job = jobRepository.save(Job.builder()
                        .externalId(c.externalId())
                        .url(c.url())
                        .company(cut(c.company(), COMPANY_MAX))
                        .title(cut(c.title(), TITLE_MAX))
                        .region(c.region())
                        .deadline(c.deadline())
                        .source(source)
                        .build());
                sendLogRepository.save(new SendLog(job));
                saved++;
            }
        }
        source.markCollected(Kst.now());
        log.info("공고 저장: 입력 {}건, 신규 {}건", collected.size(), saved);
        return new StoreResult(collected.size(), saved);
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
