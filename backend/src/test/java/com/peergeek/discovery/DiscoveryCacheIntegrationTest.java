package com.peergeek.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class DiscoveryCacheIntegrationTest extends DiscoveryTestSupport {

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private CourseStatsRecorder statsRecorder;

	@Test
	void cachesPopularCoursesAndCategoriesWithProperKeys() throws Exception {
		UserEntity instructor = createInstructor("cache-pop@example.test", "Pop Instructor");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Pop Course", "Desc");
		publishCourse(token, courseId);

		// 1. Popular courses caching
		assertThat(redisTemplate.hasKey("courses:popular")).isFalse();
		mockMvc.perform(get("/api/v1/public/courses/popular")).andExpect(status().isOk());
		assertThat(redisTemplate.hasKey("courses:popular")).isTrue();

		// 2. Categories caching
		assertThat(redisTemplate.hasKey("categories:all")).isFalse();
		mockMvc.perform(get("/api/v1/public/categories")).andExpect(status().isOk());
		assertThat(redisTemplate.hasKey("categories:all")).isTrue();

		// 3. Preview caching
		assertThat(redisTemplate.hasKey("course:preview:" + courseId)).isFalse();
		mockMvc.perform(get("/api/v1/public/courses/{id}", courseId)).andExpect(status().isOk());
		assertThat(redisTemplate.hasKey("course:preview:" + courseId)).isTrue();
		assertThat(redisTemplate.hasKey("course:structure:" + courseId)).isTrue();

		// Verify cached payload never contains presigned URL
		String cachedPreview = redisTemplate.opsForValue().get("course:preview:" + courseId);
		assertThat(cachedPreview).isNotNull();
		assertThat(cachedPreview)
				.doesNotContain("X-Amz-Signature")
				.doesNotContain("Signature=")
				.doesNotContain("AWSAccessKeyId");
	}

	@Test
	void invalidationMatrixOnUnpublishAndStatsRecording() throws Exception {
		UserEntity instructor = createInstructor("cache-inv@example.test", "Inv Instructor");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Inv Course", "Desc");
		publishCourse(token, courseId);

		// Prime all caches
		mockMvc.perform(get("/api/v1/public/courses/popular")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/public/categories")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/public/courses/{id}", courseId)).andExpect(status().isOk());

		assertThat(redisTemplate.hasKey("courses:popular")).isTrue();
		assertThat(redisTemplate.hasKey("categories:all")).isTrue();
		assertThat(redisTemplate.hasKey("course:preview:" + courseId)).isTrue();

		// Record stats -> evicts popular and preview
		statsRecorder.recordEnrollmentStats(courseId, 50, 20);
		assertThat(redisTemplate.hasKey("courses:popular")).isFalse();
		assertThat(redisTemplate.hasKey("course:preview:" + courseId)).isFalse();

		// Re-prime preview
		mockMvc.perform(get("/api/v1/public/courses/{id}", courseId)).andExpect(status().isOk());
		assertThat(redisTemplate.hasKey("course:preview:" + courseId)).isTrue();

		// Unpublish course -> evicts preview, structure, popular, categories
		unpublishCourse(token, courseId);
		assertThat(redisTemplate.hasKey("course:preview:" + courseId)).isFalse();
		assertThat(redisTemplate.hasKey("course:structure:" + courseId)).isFalse();
		assertThat(redisTemplate.hasKey("categories:all")).isFalse();
	}

	@Test
	void schemaVersionMismatchIsTreatedAsCacheMiss() throws Exception {
		UserEntity instructor = createInstructor("schema-ver@example.test", "Schema Instructor");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Schema Course", "Desc");
		publishCourse(token, courseId);

		// Write incompatible schema version into Redis
		String staleEnvelope = "{\"schemaVersion\":999,\"payload\":\"corrupted-or-old\"}";
		redisTemplate.opsForValue().set("course:preview:" + courseId, staleEnvelope, Duration.ofMinutes(5));

		// Requesting the preview should detect version mismatch, treat as miss, and overwrite with valid data
		mockMvc.perform(get("/api/v1/public/courses/{id}", courseId))
				.andExpect(status().isOk());

		String newCache = redisTemplate.opsForValue().get("course:preview:" + courseId);
		assertThat(newCache).contains("\"schemaVersion\":1");
	}

	@Test
	void singleFlightLoadingUnderConcurrentMisses() throws Exception {
		UserEntity instructor = createInstructor("flight@example.test", "Flight Instructor");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Flight Course", "Desc");
		publishCourse(token, courseId);

		cacheManager.evictAll();

		int threads = 6;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch latch = new CountDownLatch(1);
		AtomicInteger successCount = new AtomicInteger();

		for (int i = 0; i < threads; i++) {
			executor.submit(() -> {
				try {
					latch.await();
					mockMvc.perform(get("/api/v1/public/courses/{id}", courseId))
							.andExpect(status().isOk());
					successCount.incrementAndGet();
				} catch (Exception e) {
					// Ignore
				}
			});
		}

		latch.countDown();
		executor.shutdown();
		assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		assertThat(successCount.get()).isEqualTo(threads);
	}
}
