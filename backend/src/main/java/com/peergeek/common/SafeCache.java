package com.peergeek.common;

import java.time.Duration;
import java.util.function.Supplier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class SafeCache {
	private static final Logger log = LoggerFactory.getLogger(SafeCache.class);

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	public SafeCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
		this.redis = redis;
		this.objectMapper = objectMapper;
	}

	public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> sourceOfTruth) {
		try {
			String cached = redis.opsForValue().get(key);
			if (cached != null) {
				try {
					return objectMapper.readValue(cached, type);
				} catch (JsonProcessingException exception) {
					log.warn("Ignoring unreadable cache entry key={}", key);
				}
			}
		} catch (DataAccessException exception) {
			log.warn("Cache read failed; using source of truth key={}", key, exception);
		}

		T value = sourceOfTruth.get();
		if (value == null) {
			return null;
		}
		try {
			redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
		} catch (DataAccessException | JsonProcessingException exception) {
			log.warn("Cache write failed; source of truth remains authoritative key={}", key, exception);
		}
		return value;
	}

	public void evict(String key) {
		try {
			redis.delete(key);
		} catch (DataAccessException exception) {
			log.warn("Cache evict failed key={}", key, exception);
		}
	}
}
