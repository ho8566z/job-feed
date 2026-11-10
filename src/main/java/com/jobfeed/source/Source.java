package com.jobfeed.source;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType type;

    private LocalDateTime lastCollectedAt;

    public Source(String name, SourceType type) {
        this.name = name;
        this.type = type;
    }

    public void markCollected(LocalDateTime at) {
        this.lastCollectedAt = at;
    }
}
