package com.jobfeed.collect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectServiceTest {

    private static final WorknetProperties PROPS = new WorknetProperties("key", null, null, null, null, null);

    /** collect()가 풀어 줄 때까지 멈춰 있는 수집기 */
    private static class BlockingCollector extends JobCollector {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        BlockingCollector() {
            super(null, PROPS);
        }

        @Override
        public List<CollectedJob> collect() {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    private static class FakeStore extends JobStoreService {
        FakeStore() {
            super(null, null, null);
        }

        @Override
        public StoreResult store(List<CollectedJob> collected) {
            return new StoreResult(collected.size(), 0);
        }
    }

    @Test
    void 수집_중에는_새_수집을_시작하지_않고_끝나면_다시_실행할_수_있다() throws Exception {
        BlockingCollector collector = new BlockingCollector();
        CollectService service = new CollectService(collector, new FakeStore());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<JobStoreService.StoreResult> first = executor.submit(service::run);
            assertThat(collector.started.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(service::run).isInstanceOf(CollectInProgressException.class);

            collector.release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).received()).isZero();

            // 끝난 뒤에는 다시 실행된다(플래그가 풀려 있다)
            assertThat(service.run().received()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 수집이_실패해도_플래그가_풀려_다음_수집이_가능하다() {
        JobCollector failing = new JobCollector(null, PROPS) {
            @Override
            public List<CollectedJob> collect() {
                throw new WorknetApiException(500, "고용24 API 오류 500");
            }
        };
        CollectService service = new CollectService(failing, new FakeStore());

        assertThatThrownBy(service::run).isInstanceOf(WorknetApiException.class);
        assertThatThrownBy(service::run).isInstanceOf(WorknetApiException.class);
    }
}
