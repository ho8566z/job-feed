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
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 중복 판단 기준
    @Column(nullable = false, unique = true, length = 500)
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
    private Job(String url, String company, String title, String region,
                LocalDate deadline, Source source) {
        this.url = url;
        this.company = company;
        this.title = title;
        this.region = region;
        this.deadline = deadline;
        this.source = source;
        this.collectedAt = LocalDateTime.now();
    }
}
