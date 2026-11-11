package com.jobfeed.collect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobCollectorTest {

    private static CollectedJob job(String id, String title) {
        return new CollectedJob(id, "회사" + id, title, null, null, "https://example.com/" + id);
    }

    private static WorknetProperties props(List<String> keywords, int display, int maxPages) {
        return new WorknetProperties("key", null, keywords, List.of("연구개발"), display, maxPages);
    }

    /** 키워드·페이지별로 미리 정해 둔 결과를 돌려주는 가짜 클라이언트 */
    private static class FakeClient extends WorknetClient {
        private final Map<String, List<WorknetClient.Page>> pages;
        final List<String> calls = new ArrayList<>();

        FakeClient(WorknetProperties props, Map<String, List<WorknetClient.Page>> pages) {
            super(null, props);
            this.pages = pages;
        }

        @Override
        public Page search(String keyword, int startPage) {
            calls.add(keyword + "#" + startPage);
            List<Page> list = pages.get(keyword);
            if (list == null) {
                throw new WorknetApiException(502, "고용24 API 오류 502");
            }
            return startPage <= list.size() ? list.get(startPage - 1) : new Page(0, List.of());
        }
    }

    @Test
    void 키워드_결과를_합치고_중복과_제외어를_거른다() {
        WorknetProperties props = props(List.of("개발", "IT"), 100, 5);
        FakeClient client = new FakeClient(props, Map.of(
                "개발", List.of(new WorknetClient.Page(2, List.of(job("1", "백엔드 개발자"), job("2", "연구개발 신입")))),
                "IT", List.of(new WorknetClient.Page(2, List.of(job("1", "백엔드 개발자"), job("3", "IT 운영"))))));

        List<CollectedJob> result = new JobCollector(client, props).collect();

        assertThat(result).extracting(CollectedJob::externalId).containsExactly("1", "3");
    }

    @Test
    void 전체_건수까지_페이지를_넘겨_가져온다() {
        WorknetProperties props = props(List.of("개발"), 2, 5);
        FakeClient client = new FakeClient(props, Map.of(
                "개발", List.of(
                        new WorknetClient.Page(3, List.of(job("1", "a 개발"), job("2", "b 개발"))),
                        new WorknetClient.Page(3, List.of(job("3", "c 개발"))))));

        List<CollectedJob> result = new JobCollector(client, props).collect();

        assertThat(result).hasSize(3);
        assertThat(client.calls).containsExactly("개발#1", "개발#2");
    }

    @Test
    void 최대_페이지를_넘기지_않는다() {
        WorknetProperties props = props(List.of("개발"), 1, 2);
        FakeClient client = new FakeClient(props, Map.of(
                "개발", List.of(
                        new WorknetClient.Page(99, List.of(job("1", "개발"))),
                        new WorknetClient.Page(99, List.of(job("2", "개발"))),
                        new WorknetClient.Page(99, List.of(job("3", "개발"))))));

        new JobCollector(client, props).collect();

        assertThat(client.calls).hasSize(2);
    }

    @Test
    void 일부_키워드만_실패하면_나머지_결과를_돌려준다() {
        WorknetProperties props = props(List.of("개발", "실패"), 100, 5);
        FakeClient client = new FakeClient(props, Map.of(
                "개발", List.of(new WorknetClient.Page(1, List.of(job("1", "개발"))))));

        assertThat(new JobCollector(client, props).collect()).hasSize(1);
    }

    @Test
    void 모든_키워드가_실패하면_예외를_던진다() {
        WorknetProperties props = props(List.of("실패1", "실패2"), 100, 5);
        FakeClient client = new FakeClient(props, Map.of());

        assertThatThrownBy(() -> new JobCollector(client, props).collect())
                .isInstanceOf(WorknetApiException.class);
    }
}
