package com.jobfeed.collect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 고용24 공채속보 호출 확인용 (수동 실행). WORKNET_API_KEY가 없으면 건너뛴다.
 * 실행: ./gradlew test --tests "*WorknetLiveTest" -i   (-i를 붙여야 출력이 보인다)
 * 하루 호출 한도를 쓰므로 필요할 때만 실행한다. 인증키는 출력하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "WORKNET_API_KEY", matches = ".+")
class WorknetLiveTest {

    @Test
    void 키워드_수집_결과_확인() {
        WorknetProperties props = new WorknetProperties(
                System.getenv("WORKNET_API_KEY"), null, null, null, null, null);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        WorknetClient client = new WorknetClient(RestClient.builder().requestFactory(factory).build(), props);

        List<CollectedJob> jobs = new JobCollector(client, props).collect();

        System.out.println("수집 결과: " + jobs.size() + "건");
        jobs.forEach(j -> System.out.println(" - " + j.company() + " | " + j.title() + " | ~" + j.deadline()));

        assertThat(jobs).isNotEmpty();
        assertThat(jobs).allSatisfy(j -> {
            assertThat(j.externalId()).isNotBlank();
            assertThat(j.url()).startsWith("http");
        });
    }
}
