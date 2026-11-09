package com.peergeek.discovery;

import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryCacheManager {
	private static final Logger log = LoggerFactory.getLogger(DiscoveryCacheManager.class);

	private final StringRedisTemplate redis;

	public DiscoveryCacheManager(StringRedisTemplate redis) {
		this.redis = redis;
	}

	public void evictCourse(long courseId) {
		evictKeys(List.of(
				"course:preview:" + courseId,
				"course:structure:" + courseId,
				"courses:popular",
				"categories:all"));
	}

	public void evictCourseContent(long courseId) {
		evictKeys(List.of(
				"course:preview:" + courseId,
				"course:structure:" + courseId));
	}

	public void evictPopularAndCategories() {
		evictKeys(List.of("courses:popular", "categories:all"));
	}

	public void evictStats(long courseId) {
		evictKeys(List.of("courses:popular", "course:preview:" + courseId));
	}

	public void evictCourseStats(long courseId) {
		evictStats(courseId);
	}

	public void evictAll() {
		evictKeys(List.of("courses:popular", "categories:all"));
		try {
			java.util.Set<String> keys = redis.keys("course:*");
			if (keys != null && !keys.isEmpty()) {
				redis.delete(keys);
			}
			keys = redis.keys("courses:*");
			if (keys != null && !keys.isEmpty()) {
				redis.delete(keys);
			}
			keys = redis.keys("categories:*");
			if (keys != null && !keys.isEmpty()) {
				redis.delete(keys);
			}
		} catch (DataAccessException exception) {
			log.warn("Cache evictAll failed; continuing safely", exception);
		}
	}

	private void evictKeys(Collection<String> keys) {
		try {
			redis.delete(keys);
		} catch (DataAccessException exception) {
			log.warn("Cache eviction failed; continuing safely keys={}", keys, exception);
		}
	}
}
