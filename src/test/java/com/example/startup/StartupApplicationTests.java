package com.example.startup;

import com.example.startup.service.ReportReadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.url=jdbc:h2:mem:startuptest;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"seoul.api.key=test-seoul-key",
		"auth.token.secret=test-secret-that-is-at-least-32-characters-long",
		"cloud.aws.credentials.access-key=test-access-key",
		"cloud.aws.credentials.secret-key=test-secret-key"
})
class StartupApplicationTests {
	@Autowired
	private ReportReadService reportReadService;

	@Test
	void contextLoads() {
	}

	@Test
	void reportCursorQueryRunsAgainstGeneratedSchema() {
		assertTrue(reportReadService.findPage(null, 10).items().isEmpty());
	}

}
