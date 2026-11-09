package com.peergeek.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.fasterxml.jackson.databind.ObjectMapper;

class SafeCacheTest {

	@Test
	void returnsSourceOfTruthWhenRedisReadAndWriteFail() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		when(values.get("key")).thenThrow(new RedisConnectionFailureException("redis unavailable"));
		doThrow(new RedisConnectionFailureException("redis unavailable"))
				.when(values).set("key", "\"database-value\"", Duration.ofMinutes(1));

		SafeCache cache = new SafeCache(redis, new ObjectMapper());

		assertThat(cache.getOrLoad("key", String.class, Duration.ofMinutes(1), () -> "database-value"))
				.isEqualTo("database-value");
	}
}
