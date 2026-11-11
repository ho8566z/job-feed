package com.jobfeed.schedule;

import com.jobfeed.collect.CollectService;
import com.jobfeed.collect.JobStoreService;
import com.jobfeed.collect.WorknetProperties;
import com.jobfeed.sendlog.SendService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class JobFeedSchedulerTest {

    private static class FakeCollect extends CollectService {
        int calls;
        RuntimeException error;

        FakeCollect() {
            super(null, null);
        }

        @Override
        public JobStoreService.StoreResult run() {
            calls++;
            if (error != null) {
                throw error;
            }
            return new JobStoreService.StoreResult(3, 1);
        }
    }

    private static class FakeSend extends SendService {
        int calls;
        RuntimeException error;

        FakeSend() {
            super(null, null, null, null, null);
        }

        @Override
        public SendResult sendPending() {
            calls++;
            if (error != null) {
                throw error;
            }
            return new SendResult(0, 0, 0, false);
        }
    }

    private static WorknetProperties props(String key) {
        return new WorknetProperties(key, null, null, null, null, null);
    }

    @Test
    void 인증키가_있으면_수집을_실행한다() {
        FakeCollect collect = new FakeCollect();

        new JobFeedScheduler(collect, new FakeSend(), props("key")).collect();

        assertThat(collect.calls).isEqualTo(1);
    }

    @Test
    void 인증키가_없으면_수집을_건너뛴다() {
        FakeCollect collect = new FakeCollect();

        new JobFeedScheduler(collect, new FakeSend(), props("")).collect();

        assertThat(collect.calls).isZero();
    }

    @Test
    void 수집이_실패해도_예외를_밖으로_던지지_않는다() {
        FakeCollect collect = new FakeCollect();
        collect.error = new IllegalStateException("boom");
        JobFeedScheduler scheduler = new JobFeedScheduler(collect, new FakeSend(), props("key"));

        assertThatCode(scheduler::collect).doesNotThrowAnyException();
        // 실패 후에도 다음 실행은 다시 시도한다
        scheduler.collect();
        assertThat(collect.calls).isEqualTo(2);
    }

    @Test
    void 전송이_실패해도_예외를_밖으로_던지지_않는다() {
        FakeSend send = new FakeSend();
        send.error = new IllegalStateException("boom");

        assertThatCode(() -> new JobFeedScheduler(new FakeCollect(), send, props("key")).send())
                .doesNotThrowAnyException();
        assertThat(send.calls).isEqualTo(1);
    }
}
