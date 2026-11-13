package com.jobfeed.sendlog;

/** 전송 현황 한 건(조회 응답용). 엔티티를 컨트롤러 밖으로 내보내지 않도록 service에서 만든다. */
public record SendLogItem(Long id, String company, String title, SendStatus status,
                          int retryCount, String lastError, String notionPageId) {

    public static SendLogItem from(SendLog log) {
        return new SendLogItem(log.getId(), log.getJob().getCompany(), log.getJob().getTitle(),
                log.getStatus(), log.getRetryCount(), log.getLastError(), log.getNotionPageId());
    }
}
