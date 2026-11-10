package com.jobfeed.sendlog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SendLogRepository extends JpaRepository<SendLog, Long> {

    List<SendLog> findByStatusOrderByIdAsc(SendStatus status, Pageable pageable);
}
