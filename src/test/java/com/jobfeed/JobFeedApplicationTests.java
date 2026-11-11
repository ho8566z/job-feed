package com.jobfeed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 스케줄러를 끄지 않으면 환경변수에 키가 있을 때 테스트가 실제 수집·전송을 시작한다.
@SpringBootTest(properties = "app.scheduling.enabled=false")
class JobFeedApplicationTests {

	@Test
	void contextLoads() {
	}

}
