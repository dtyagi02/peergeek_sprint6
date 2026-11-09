package com.peergeek.analytics;

import com.peergeek.analytics.dto.InstructorCourseAnalyticsDto;
import com.peergeek.common.SafeCache;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsCacheService {

	private static final Logger log = LoggerFactory.getLogger(AnalyticsCacheService.class);
	private static final String KEY_PREFIX = "analytics:course:";

	private final SafeCache safeCache;
	private final Duration ttl;

	public AnalyticsCacheService(
			SafeCache safeCache,
			@Value("${peergeek.analytics.cache-ttl:300s}") Duration ttl) {
		this.safeCache = safeCache;
		this.ttl = ttl;
	}

	public InstructorCourseAnalyticsDto getCourseAnalytics(
			long courseId,
			Supplier<InstructorCourseAnalyticsDto> sourceOfTruth) {
		String key = KEY_PREFIX + courseId;
		return safeCache.getOrLoad(key, InstructorCourseAnalyticsDto.class, ttl, sourceOfTruth);
	}

	public void evictCourseAnalytics(long courseId) {
		String key = KEY_PREFIX + courseId;
		log.info("Evicting course analytics cache key={}", key);
		safeCache.evict(key);
	}
}
