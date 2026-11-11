package com.jobfeed.common;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 날짜·시각은 모두 한국 시간(KST)으로 다룬다(tim-001). 서버의 기본 시간대에 기대지 않는다.
 */
public final class Kst {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private Kst() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
