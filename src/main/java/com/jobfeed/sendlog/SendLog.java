package com.jobfeed.sendlog;

import com.jobfeed.job.Job;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SendLog {

    public static final int MAX_RETRY = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", unique = true)
    private Job job;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SendStatus status = SendStatus.PENDING;

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(length = 1000)
    private String lastError;

    private String notionPageId;

    public SendLog(Job job) {
        this.job = job;
    }

    public void markSuccess(String notionPageId) {
        this.status = SendStatus.SUCCESS;
        this.notionPageId = notionPageId;
        this.lastError = null;
    }

    /** 실패 시 재시도 횟수를 올리고, 최대 횟수에 도달하면 최종 실패로 기록한다. */
    public void markFailure(String error) {
        this.retryCount++;
        this.lastError = error;
        if (this.retryCount >= MAX_RETRY) {
            this.status = SendStatus.FAILED;
        }
    }
}
