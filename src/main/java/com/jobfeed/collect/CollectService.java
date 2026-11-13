package com.jobfeed.collect;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 수집 1회: 조회(외부 호출) 후 저장. 외부 호출이 DB 트랜잭션 안에 들어가지 않도록
 * 이 클래스는 트랜잭션을 걸지 않고, 저장 단계(JobStoreService)에서만 건다.
 * 스케줄러와 수동 실행(API)이 겹치지 않도록 한 번에 하나만 실행한다.
 */
@Service
@RequiredArgsConstructor
public class CollectService {

    private final JobCollector collector;
    private final JobStoreService storeService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** @throws CollectInProgressException 이미 수집 중이면 */
    public JobStoreService.StoreResult run() {
        if (!running.compareAndSet(false, true)) {
            throw new CollectInProgressException();
        }
        try {
            return storeService.store(collector.collect());
        } finally {
            running.set(false);
        }
    }
}
