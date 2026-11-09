package com.peergeek.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.peergeek.user.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DiscoveryReconciliationIntegrationTest extends DiscoveryTestSupport {

	@Autowired
	private DiscoveryReconciliationService reconciliationService;

	@Test
	void reconciliationHealsMissedMetadataCategoryAndInstructorChangesAndRemovesStaleRows() throws Exception {
		UserEntity instructor = createInstructor("recon-inst@example.test", "Original Instructor");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Original Course Title", "Original description");
		publishCourse(token, courseId);

		assertThat(indexRepository.findById(courseId)).isPresent();

		// 1. Simulate missed events: change instructor display name in user table
		instructor.updateProfile("Renamed Instructor", instructor.getHeadline(), instructor.getBio());
		userRepository.saveAndFlush(instructor);

		// 2. Change course title directly in courses table (simulating missed event)
		jdbc.update("update courses set title = 'Directly Updated Title' where id = ?", courseId);

		// 3. Change category name directly
		long categoryId = getDefaultCategoryId();
		String originalCatName = jdbc.queryForObject("select name from categories where id = ?", String.class, categoryId);
		jdbc.update("update categories set name = 'Modern Computer Science' where id = ?", categoryId);

		// 4. Create an artificial stale index row for a non-existent / unpublished course
		PublicCourseIndexEntity stale = new PublicCourseIndexEntity();
		stale.setCourseId(888_888L);
		stale.setTitle("Stale Course");
		stale.setShortDescription("Stale");
		stale.setSearchText("Stale");
		stale.setCategoryId(categoryId);
		stale.setCategoryName("Modern Computer Science");
		stale.setCategorySlug("cs");
		stale.setDifficulty("BEGINNER");
		stale.setInstructorId(instructor.getId());
		stale.setInstructorName("Renamed Instructor");
		stale.setModuleCount(1);
		stale.setLessonCount(1);
		stale.setPublishedAt(java.time.Instant.now());
		stale.setSourceFingerprint("stale-fingerprint");
		stale.setIndexedAt(java.time.Instant.now());
		indexRepository.saveAndFlush(stale);

		// 5. Delete the index row for the published course to simulate a completely missed publish
		jdbc.update("delete from public_course_index where course_id = ?", courseId);
		assertThat(indexRepository.findById(courseId)).isEmpty();

		// Run reconciliation
		reconciliationService.reconcile();

		// Verify missing row was recreated with updated info
		Optional<PublicCourseIndexEntity> healed = indexRepository.findById(courseId);
		assertThat(healed).isPresent();
		PublicCourseIndexEntity row = healed.get();
		assertThat(row.getTitle()).isEqualTo("Directly Updated Title");
		assertThat(row.getInstructorName()).isEqualTo("Renamed Instructor");
		assertThat(row.getCategoryName()).isEqualTo("Modern Computer Science");

		// Verify stale row was removed
		assertThat(indexRepository.findById(888_888L)).isEmpty();

		// Restore category name
		jdbc.update("update categories set name = ? where id = ?", originalCatName, categoryId);
	}

	@Test
	void runningReconciliationTwiceAndConcurrentlyIsSafe() throws Exception {
		UserEntity instructor = createInstructor("recon-concurrent@example.test", "Concurrent Instructor");
		String token = tokenFor(instructor);
		long courseId = createReadyCourse(token, "Concurrent Course", "Description for concurrency test");
		publishCourse(token, courseId);

		int threadCount = 4;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(1);
		AtomicInteger successCount = new AtomicInteger();

		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					latch.await();
					reconciliationService.reconcile();
					successCount.incrementAndGet();
				} catch (Exception e) {
					// Ignore
				}
			});
		}

		latch.countDown();
		executor.shutdown();
		assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
		assertThat(successCount.get()).isEqualTo(threadCount);

		assertThat(indexRepository.findById(courseId)).isPresent();
	}
}
