package com.peergeek.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReadinessSafetyConfigurationTest {
	@Test
	void productionStartupRejectsPrivateHostLinkChecks() {
		new ApplicationContextRunner()
				.withUserConfiguration(ReadinessSafetyConfiguration.class)
				.withPropertyValues(
						"spring.profiles.active=prod",
						"readiness.links.allow-private-hosts=true")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(rootMessage(context.getStartupFailure()))
							.contains("READINESS_LINKS_ALLOW_PRIVATE_HOSTS must be false in the prod profile.");
				});
	}

	private String rootMessage(Throwable failure) {
		Throwable root = failure;
		while (root.getCause() != null) root = root.getCause();
		return root.getMessage();
	}
}
