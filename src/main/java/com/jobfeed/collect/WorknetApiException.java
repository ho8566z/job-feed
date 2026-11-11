package com.jobfeed.collect;

import lombok.Getter;

/**
 * 고용24 호출·응답 실패. 인증키가 요청 URL에 들어가므로 메시지와 원인에 URL을 담지 않는다.
 */
@Getter
public class WorknetApiException extends RuntimeException {

    private final int status;

    public WorknetApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** 네트워크 오류(status 0)와 5xx는 재시도할 만한 실패로 본다. */
    public boolean isRetryable() {
        return status == 0 || status >= 500;
    }
}
