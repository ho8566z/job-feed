package com.jobfeed.collect;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 고용24를 호출하지 않고 요청 형식과 응답 해석을 확인한다. 샘플은 2026-11-11 실제 응답을 줄인 것이다.
 */
class WorknetClientTest {

    private static final String KEY = "test+key/with=special";

    private static final String SAMPLE = """
            <?xml version="1.0" encoding="UTF-8"?><dhsOpenEmpInfoList><total>238</total><startPage>1</startPage><display>10</display>\
            <dhsOpenEmpInfo><empSeqno>184575</empSeqno><empWantedTitle>2026년 하반기 대졸 신입사원 모집</empWantedTitle>\
            <empBusiNm>삼원강재</empBusiNm><coClcdNm></coClcdNm><empWantedStdt>20261109</empWantedStdt><empWantedEndt>20261123</empWantedEndt>\
            <empWantedTypeNm>정규직</empWantedTypeNm><regLogImgNm>http://www.work.go.kr/img</regLogImgNm>\
            <empWantedHomepgDetail>https://recruit.ehyundai.com/view.nhd?hireId=2026648&amp;regCoCd=HDSWST</empWantedHomepgDetail>\
            <empWantedMobileUrl></empWantedMobileUrl></dhsOpenEmpInfo>\
            <dhsOpenEmpInfo><empSeqno>184570</empSeqno><empWantedTitle>마케팅 영상 크리에이터 채용</empWantedTitle>\
            <empBusiNm>에코마케팅</empBusiNm><empWantedStdt>20261110</empWantedStdt><empWantedEndt></empWantedEndt>\
            <empWantedHomepgDetail>https://career.echomarketing.co.kr/ko/o/243010</empWantedHomepgDetail></dhsOpenEmpInfo>\
            <dhsOpenEmpInfo><empSeqno>184569</empSeqno><empWantedTitle>URL 없는 공고</empWantedTitle>\
            <empBusiNm>없음</empBusiNm><empWantedHomepgDetail></empWantedHomepgDetail></dhsOpenEmpInfo>\
            </dhsOpenEmpInfoList>""";

    private MockRestServiceServer server;
    private WorknetClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WorknetClient(builder.build(),
                new WorknetProperties(KEY, null, null, null, null, null));
    }

    @Test
    void 응답을_정규화한다() {
        WorknetClient.Page page = WorknetClient.parse(SAMPLE);

        assertThat(page.total()).isEqualTo(238);
        // URL 없는 공고는 건너뛴다
        assertThat(page.items()).hasSize(2);

        CollectedJob first = page.items().get(0);
        assertThat(first.externalId()).isEqualTo("184575");
        assertThat(first.company()).isEqualTo("삼원강재");
        assertThat(first.title()).isEqualTo("2026년 하반기 대졸 신입사원 모집");
        assertThat(first.region()).isNull();
        assertThat(first.deadline()).isEqualTo(LocalDate.of(2026, 11, 23));
        // &amp;는 XML 파서가 풀어 준다
        assertThat(first.url()).isEqualTo("https://recruit.ehyundai.com/view.nhd?hireId=2026648&regCoCd=HDSWST");

        assertThat(page.items().get(1).deadline()).isNull();
    }

    @Test
    void Content_Type에_charset이_없어도_한글이_깨지지_않는다() {
        // charset 없는 application/xml. 문자열로 받으면 ISO-8859-1로 풀려 한글이 깨진다.
        server.expect(requestTo(containsString("empWantedTitle=")))
                .andRespond(withSuccess(SAMPLE, MediaType.APPLICATION_XML));

        WorknetClient.Page page = client.search("개발", 1);

        assertThat(page.items().get(0).title()).isEqualTo("2026년 하반기 대졸 신입사원 모집");
        assertThat(page.items().get(0).company()).isEqualTo("삼원강재");
    }

    @Test
    void 오류_응답은_예외로_바꾼다() {
        String error = "<?xml version='1.0' encoding='UTF-8'?><GO24><error>인증키가 존재하지 않습니다</error></GO24>";

        assertThatThrownBy(() -> WorknetClient.parse(error))
                .isInstanceOf(WorknetApiException.class)
                .hasMessageContaining("인증키가 존재하지 않습니다");
    }

    @Test
    void XML이_아니면_예외로_바꾼다() {
        assertThatThrownBy(() -> WorknetClient.parse("<html>점검 중"))
                .isInstanceOf(WorknetApiException.class);
        assertThatThrownBy(() -> WorknetClient.parse(" "))
                .isInstanceOf(WorknetApiException.class);
    }

    @Test
    void DOCTYPE이_있는_XML은_거부한다() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE a [<!ENTITY x \"y\">]><dhsOpenEmpInfoList/>";

        assertThatThrownBy(() -> WorknetClient.parse(xxe))
                .isInstanceOf(WorknetApiException.class);
    }

    @Test
    void 요청_URL은_키워드와_인증키를_인코딩한다() {
        String uri = client.buildUri("Back-end", 2).toString();

        assertThat(uri)
                .contains("authKey=test%2Bkey%2Fwith%3Dspecial")
                .contains("callTp=L", "returnType=XML", "startPage=2", "display=100", "empWantedTitle=Back-end");
        assertThat(client.buildUri("개발", 1).toString()).contains("empWantedTitle=%EA%B0%9C%EB%B0%9C");
    }

    @Test
    void 조회_요청을_보내고_결과를_돌려준다() {
        server.expect(requestTo(containsString("empWantedTitle=%EA%B0%9C%EB%B0%9C")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SAMPLE, MediaType.APPLICATION_XML));

        WorknetClient.Page page = client.search("개발", 1);

        assertThat(page.items()).hasSize(2);
        server.verify();
    }

    @Test
    void 서버_오류는_재시도_가능한_예외이고_인증키를_노출하지_않는다() {
        server.expect(requestTo(not(containsString("never"))))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.search("개발", 1))
                .isInstanceOfSatisfying(WorknetApiException.class, e -> {
                    assertThat(e.isRetryable()).isTrue();
                    assertThat(e.getMessage()).doesNotContain("authKey").doesNotContain(KEY);
                });
    }

    @Test
    void 인증키가_없으면_호출하지_않고_실패한다() {
        WorknetClient noKey = new WorknetClient(RestClient.create(),
                new WorknetProperties("", null, null, null, null, null));

        assertThatThrownBy(() -> noKey.search("개발", 1))
                .isInstanceOf(IllegalStateException.class);
    }
}
