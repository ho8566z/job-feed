package com.jobfeed.api;

import com.jobfeed.collect.CollectInProgressException;
import com.jobfeed.collect.WorknetApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 내부 API 오류를 {"error": "..."} 형태로 돌려준다. 메시지에는 인증키·토큰이 들어가지 않는다.
 */
@RestControllerAdvice(basePackageClasses = OpsController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CollectInProgressException.class)
    public ResponseEntity<Map<String, String>> inProgress(CollectInProgressException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({WorknetApiException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> collectFailed(RuntimeException e) {
        log.warn("수동 수집 실패: {}", e.getMessage());
        return body(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
        return body(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason());
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message == null ? status.getReasonPhrase() : message));
    }
}
