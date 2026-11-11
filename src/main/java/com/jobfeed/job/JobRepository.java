package com.jobfeed.job;

import com.jobfeed.source.Source;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, Long> {

    boolean existsBySourceAndExternalId(Source source, String externalId);

    List<Job> findBySourceAndExternalIdIn(Source source, Collection<String> externalIds);
}
