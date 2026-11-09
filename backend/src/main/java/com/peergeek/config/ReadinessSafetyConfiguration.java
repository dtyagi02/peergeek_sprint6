package com.peergeek.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class ReadinessSafetyConfiguration {
	public ReadinessSafetyConfiguration(
			@Value("${readiness.links.allow-private-hosts:false}") boolean allowPrivateHosts,
			Environment environment) {
		if (environment.acceptsProfiles(Profiles.of("prod"))
				&& allowPrivateHosts) {
			throw new IllegalStateException(
					"READINESS_LINKS_ALLOW_PRIVATE_HOSTS must be false in the prod profile.");
		}
	}
}
