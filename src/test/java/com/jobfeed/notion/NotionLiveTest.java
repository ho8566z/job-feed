package com.jobfeed.notion;

import com.jobfeed.job.Job;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 노션 연동 확인용 (수동 실행). 환경변수가 없으면 건너뛴다.
 *
 * 준비: 노션 통합(Integration) 생성 → 공고 DB를 통합에 공유(연결 추가)
 *       → DB 속성: 회사(제목) 직무(텍스트) 지역(선택) 마감일(날짜) 상태(선택) 수집일(날짜) 링크(URL)
 *       → 지역 선택 값에 "서울"과 상태 선택 값에 "신규"가 있어야 한다.
 * 실행: NOTION_TOKEN, NOTION_DATABASE_ID를 설정하고 ./gradlew test --tests "*NotionLiveTest"
 *
 * 실행할 때마다 공고 DB에 테스트 페이지가 1건 생긴다. 확인 후 노션에서 직접 지운다.
 * 이 테스트가 통과해도 "푸시 알림이 오는지"는 노션 앱에서 직접 확인해야 한다.
 */
@EnabledIfEnvironmentVariable(named = "NOTION_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "NOTION_DATABASE_ID", matches = ".+")
@Slf4j
class NotionLiveTest {

    @Test
    @DisplayName("공고 DB에 테스트 페이지 1건 생성")
    void 공고_DB에_테스트_페이지_1건_생성() {
        NotionProperties props = new NotionProperties(
                System.getenv("NOTION_TOKEN"), System.getenv("NOTION_DATABASE_ID"), null, null);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        NotionClient client = new NotionClient(RestClient.builder().requestFactory(factory).build(), props);

        Job job = Job.builder()
                .externalId("live-test")
                .url("https://example.com/job-feed-live-test/" + System.currentTimeMillis())
                .company("[테스트] job-feed 연동 확인")
                .title("노션 연동 테스트 페이지 (삭제해도 됨)")
                .region("서울")
                .deadline(LocalDate.now().plusDays(7))
                .build();

        String pageId = client.createPage(job);

        assertThat(pageId).isNotBlank();
        log.info("노션 페이지 생성 완료: {}", pageId);
    }
}
