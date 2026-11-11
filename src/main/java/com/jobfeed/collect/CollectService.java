package com.jobfeed.collect;

import org.springframework.stereotype.Service;

/**
 * 수집 1회: 조회(외부 호출) 후 저장. 외부 호출이 DB 트랜잭션 안에 들어가지 않도록
 * 이 클래스는 트랜잭션을 걸지 않고, 저장 단계(JobStoreService)에서만 건다.
 */
@Service
public class CollectService {

    private final JobCollector collector;
    private final JobStoreService storeService;

    public CollectService(JobCollector collector, JobStoreService storeService) {
        this.collector = collector;
        this.storeService = storeService;
    }

    public JobStoreService.StoreResult run() {
        return storeService.store(collector.collect());
    }
}
