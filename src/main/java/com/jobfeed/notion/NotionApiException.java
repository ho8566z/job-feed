package com.jobfeed.notion;

import lombok.Getter;

/**
 * 노션 API 호출 실패. send_log.last_error에 기록할 수 있도록 상태 코드와 본문 요약을 담는다.
 * 토큰 등 비밀값은 메시지에 넣지 않는다.
 */
@Getter
public class NotionApiException extends RuntimeException {

    private final int status;

    public NotionApiException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** 429(속도 제한)와 5xx, 네트워크 오류(status 0)는 재시도할 만한 실패로 본다. */
    public boolean isRetryable() {
        return status == 0 || status == 429 || status >= 500;
    }
}
