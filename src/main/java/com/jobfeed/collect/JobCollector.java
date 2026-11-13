package com.jobfeed.collect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 키워드별로 공고를 조회해 합치고(합집합), 제외어가 들어간 제목을 걸러낸다.
 * 저장과 중복 제거는 2.2에서 한다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JobCollector {

    private final WorknetClient client;
    private final WorknetProperties properties;

    /**
     * 키워드 하나가 실패해도 나머지는 계속한다. 모든 키워드가 실패하면 마지막 오류를 던진다.
     */
    public List<CollectedJob> collect() {
        Map<String, CollectedJob> byExternalId = new LinkedHashMap<>();
        int failed = 0;
        WorknetApiException lastError = null;

        for (String keyword : properties.keywords()) {
            try {
                for (CollectedJob job : fetchAll(keyword)) {
                    if (!isExcluded(job)) {
                        byExternalId.putIfAbsent(job.externalId(), job);
                    }
                }
            } catch (WorknetApiException e) {
                failed++;
                lastError = e;
                log.warn("키워드 '{}' 조회 실패: {}", keyword, e.getMessage());
            }
        }
        if (failed == properties.keywords().size() && lastError != null) {
            throw lastError;
        }
        log.info("공채속보 수집: 키워드 {}개, 실패 {}개, 공고 {}건", properties.keywords().size(), failed, byExternalId.size());
        return List.copyOf(byExternalId.values());
    }

    private List<CollectedJob> fetchAll(String keyword) {
        List<CollectedJob> all = new ArrayList<>();
        for (int page = 1; page <= properties.maxPages(); page++) {
            WorknetClient.Page result = client.search(keyword, page);
            all.addAll(result.items());
            if (result.items().isEmpty() || all.size() >= result.total()) {
                break;
            }
        }
        return all;
    }

    private boolean isExcluded(CollectedJob job) {
        return properties.excludeWords().stream().anyMatch(job.title()::contains);
    }
}
