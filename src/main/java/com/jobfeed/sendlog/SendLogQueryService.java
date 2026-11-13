package com.jobfeed.sendlog;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전송 기록 조회(현황 API용). 컨트롤러가 리포지토리를 직접 부르지 않도록 service로 감싼다.
 */
@Service
@RequiredArgsConstructor
public class SendLogQueryService {

    private final SendLogRepository sendLogRepository;

    /** status가 null이면 전체를 최신 id 순서가 담긴 pageable대로 돌려준다. */
    public Page<SendLog> findLogs(SendStatus status, Pageable pageable) {
        return status == null
                ? sendLogRepository.findAll(pageable)
                : sendLogRepository.findByStatus(status, pageable);
    }

    /** 전송 상태별 건수. */
    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (SendStatus s : SendStatus.values()) {
            counts.put(s.name(), sendLogRepository.countByStatus(s));
        }
        return counts;
    }
}
