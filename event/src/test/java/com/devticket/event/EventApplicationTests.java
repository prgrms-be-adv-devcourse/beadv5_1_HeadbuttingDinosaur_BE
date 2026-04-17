package com.devticket.event;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
//@Disabled("ES testcontainers 설정 필요 - develop/event 팀 후속 처리")
class EventApplicationTests {

	@Test
	void contextLoads() {
	}

}
