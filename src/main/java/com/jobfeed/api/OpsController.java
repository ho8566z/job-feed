package com.jobfeed.api;

import com.jobfeed.collect.CollectService;
import com.jobfeed.collect.JobStoreService;
import com.jobfeed.sendlog.SendLog;
import com.jobfeed.sendlog.SendLogRepository;
import com.jobfeed.sendlog.SendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시연·점검용 내부 API. 인증이 없으므로 외부에 공개하지 않는다(서버는 127.0.0.1에만 바인딩).
 */
@RestController
@RequestMapping("/api/v1")
public class OpsController {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final CollectService collectService;
    private final SendLogRepository sendLogRepository;

    public OpsController(CollectService collectService, SendLogRepository sendLogRepository) {
        this.collectService = collectService;
        this.sendLogRepository = sendLogRepository;
    }

    public record SourceResult(String name, int fetched, int created, int duplicated) {
    }

    public record CollectResponse(List<SourceResult> sources) {
    }

    public record SendLogItem(Long id, String company, String title, SendStatus status,
                              int retryCount, String lastError, String notionPageId) {
    }

    public record SendLogResponse(Map<String, Long> counts, List<SendLogItem> items,
                                  int page, int size, long totalElements, int totalPages) {
    }

    /** 수집을 즉시 한 번 실행하고 결과를 돌려준다. 이미 수집 중이면 409, 수집 자체가 실패하면 500. */
    @PostMapping("/collect")
    public CollectResponse collect() {
        JobStoreService.StoreResult result = collectService.run();
        return new CollectResponse(List.of(new SourceResult(JobStoreService.SOURCE_NAME,
                result.received(), result.saved(), result.received() - result.saved())));
    }

    /** 전송 상태별 건수와 목록(최신순). status를 생략하면 전체. */
    @GetMapping("/send-logs")
    public SendLogResponse sendLogs(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(required = false) Integer size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 0 이상이어야 합니다.");
        }
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageSize < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size는 1 이상이어야 합니다.");
        }
        pageSize = Math.min(pageSize, MAX_SIZE);

        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<SendLog> result = status == null || status.isBlank()
                ? sendLogRepository.findAll(pageable)
                : sendLogRepository.findByStatus(parseStatus(status), pageable);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (SendStatus s : SendStatus.values()) {
            counts.put(s.name(), sendLogRepository.countByStatus(s));
        }
        List<SendLogItem> items = result.getContent().stream()
                .map(l -> new SendLogItem(l.getId(), l.getJob().getCompany(), l.getJob().getTitle(),
                        l.getStatus(), l.getRetryCount(), l.getLastError(), l.getNotionPageId()))
                .toList();
        return new SendLogResponse(counts, items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private static SendStatus parseStatus(String status) {
        try {
            return SendStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status는 " + Arrays.toString(SendStatus.values()) + " 중 하나여야 합니다.");
        }
    }
}
