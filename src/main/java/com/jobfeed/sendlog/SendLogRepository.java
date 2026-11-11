package com.jobfeed.sendlog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SendLogRepository extends JpaRepository<SendLog, Long> {

    // 전송은 트랜잭션 밖에서 job 내용을 쓰므로 함께 읽어 온다.
    @EntityGraph(attributePaths = "job")
    List<SendLog> findByStatusOrderByIdAsc(SendStatus status, Pageable pageable);

    // 현황 조회 API용. 목록에 회사·제목이 필요해 job을 함께 읽는다.
    @EntityGraph(attributePaths = "job")
    Page<SendLog> findByStatus(SendStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "job")
    Page<SendLog> findAll(Pageable pageable);

    long countByStatus(SendStatus status);
}
