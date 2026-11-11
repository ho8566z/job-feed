package com.jobfeed.job;

import com.jobfeed.source.Source;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_job_source_external_id", columnNames = {"source_id", "external_id"}))
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 소스가 부여한 공고 식별자(고용24 공채속보는 empSeqno). source와 함께 중복 판단 기준
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    // 원문 링크. 회사 공통 채용 페이지가 올 수 있어 중복 판단에는 쓰지 않는다
    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String title;

    private String region;

    private LocalDate deadline;

    @Column(nullable = false)
    private LocalDateTime collectedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id")
    private Source source;

    @Builder
    private Job(String externalId, String url, String company, String title, String region,
                LocalDate deadline, Source source) {
        this.externalId = externalId;
        this.url = url;
        this.company = company;
        this.title = title;
        this.region = region;
        this.deadline = deadline;
        this.source = source;
        this.collectedAt = LocalDateTime.now();
    }
}
