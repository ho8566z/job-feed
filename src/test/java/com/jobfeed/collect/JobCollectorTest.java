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
    void 기본_제외어는_실제_수집_결과의_오탐을_거르고_개발_공고는_남긴다() {
        // 2026-11-11 실제 수집 결과 21건의 제목. 앞의 7건은 개발과 무관하고 나머지 14건은 남아야 한다.
        List<String> unrelated = List.of(
                "신입/경력 채용(Global/관리/마케팅/영업/디자인/개발 등 모집)",
                "11월 사무직 수시채용(개발팀, 설계공법팀)",
                "지게차 정비 및 순회관리자(MHE엔지니어) 채용",
                "신입사원 수시채용 [설비엔지니어(계기), 구매]",
                "[신입] 송도2공장 공정엔지니어 채용(~11/11)",
                "2026년 제37회 정규직 보안경비원 직원 모집 공고",
                "웹 서비스 영업/마케팅(신입)");
        List<String> related = List.of(
                "웹 서비스(Back-end, Front-end) 개발 및 운영(신입/경력)",
                "[AI 개발] AI 에이전트 개발자 경력 추가 채용",
                "[신입] CGMS 알고리즘 개발 채용(~11/11)",
                "2026년 하반기 신입사원 정기채용(IT)",
                "IT 인프라/네트워크 담당 경력직 채용",
                "[IT솔루션팀] Platform Operations_인턴(체험형)",
                "본사 : 정보보호/IT운영 신입/경력 채용",
                "경영지원부문 IT AI 엔지니어 채용",
                "[신입] [채용연계형] 클라우드 엔지니어(클라우드 시스템 구축/운영)",
                "2026년 하반기 AI Director 신입사원 채용",
                "청년인턴(AI 인재양성 전형) 채용공고",
                "웹 기획자 경력직 채용",
                "2026년 5차 수시채용_정보보안(팀원) 부문",
                "정보보안전문가(전문계약직) 공개채용 공고");
        List<CollectedJob> all = new ArrayList<>();
        int id = 0;
        for (String title : unrelated) {
            all.add(job(String.valueOf(++id), title));
        }
        for (String title : related) {
            all.add(job(String.valueOf(++id), title));
        }
        // 제외어를 지정하지 않으면 기본값이 적용된다
        WorknetProperties props = new WorknetProperties("key", null, List.of("개발"), null, 100, 5);
        FakeClient client = new FakeClient(props, Map.of("개발", List.of(new WorknetClient.Page(all.size(), all))));

        List<CollectedJob> result = new JobCollector(client, props).collect();

        assertThat(result).extracting(CollectedJob::title).containsExactlyElementsOf(related);
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
