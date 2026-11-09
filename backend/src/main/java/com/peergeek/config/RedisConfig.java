package com.peergeek.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;

@Configuration
public class RedisConfig {

	@Bean
	LettuceConnectionFactory redisConnectionFactory(RedisSettings settings) {
		RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(settings.host(), settings.port());
		LettuceClientConfiguration client = LettuceClientConfiguration.builder()
				.commandTimeout(settings.commandTimeout())
				.clientOptions(ClientOptions.builder()
						.socketOptions(SocketOptions.builder()
								.connectTimeout(settings.connectTimeout())
								.build())
						.build())
				.build();
		return new LettuceConnectionFactory(server, client);
	}
}
