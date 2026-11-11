package com.jobfeed.collect;

import java.time.LocalDate;

/**
 * 소스에서 가져와 정규화한 공고. 저장(Job 변환·중복 제거)은 2.2에서 한다.
 *
 * @param externalId 소스가 부여한 공고 ID (고용24 공채속보는 empSeqno)
 * @param region     응답에 지역이 없는 소스는 null
 * @param deadline   없거나 해석할 수 없으면 null
 */
public record CollectedJob(
        String externalId,
        String company,
        String title,
        String region,
        LocalDate deadline,
        String url
) {
}
