package com.jobfeed.collect;

/** 이미 수집이 진행 중일 때 새 수집을 시작하려 하면 던진다. */
public class CollectInProgressException extends RuntimeException {

    public CollectInProgressException() {
        super("이미 수집 중입니다.");
    }
}
