package com.peergeek.discovery;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import com.peergeek.course.CourseStructureQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DiscoveryReconciliationService {
	private static final Logger log = LoggerFactory.getLogger(DiscoveryReconciliationService.class);
	private static final int BATCH_SIZE = 50;

	private final CourseStructureQueryService courseStructure;
	private final PublicCourseIndexRepository indexRepository;
	private final IndexService indexService;
	private final AtomicBoolean isRunning = new AtomicBoolean(false);

	public DiscoveryReconciliationService(
			CourseStructureQueryService courseStructure,
			PublicCourseIndexRepository indexRepository,
			IndexService indexService) {
		this.courseStructure = courseStructure;
		this.indexRepository = indexRepository;
		this.indexService = indexService;
	}

	@Scheduled(fixedDelayString = "${discovery.reconcile-interval:5m}")
	public void scheduledReconcile() {
		reconcileNow();
	}

	public void reconcile() {
		reconcileNow();
	}

	public void reconcileNow() {
		if (!isRunning.compareAndSet(false, true)) {
			log.info("Discovery reconciliation is already in progress; skipping concurrent trigger.");
			return;
		}
		try {
			reconcilePublishedCourses();
			reconcileStaleIndexEntries();
		} catch (Exception exception) {
			log.error("Error during discovery reconciliation", exception);
		} finally {
			isRunning.set(false);
		}
	}

	private void reconcilePublishedCourses() {
		Long afterId = null;
		while (true) {
			List<Long> courseIds = courseStructure.listPublishedCourseIds(afterId, BATCH_SIZE);
			if (courseIds.isEmpty()) {
				break;
			}
			for (Long courseId : courseIds) {
				try {
					indexService.reconcileCourse(courseId);
				} catch (Exception exception) {
					log.warn("Failed to reconcile published course courseId={}", courseId, exception);
				}
				afterId = courseId;
			}
			if (courseIds.size() < BATCH_SIZE) {
				break;
			}
		}
	}

	private void reconcileStaleIndexEntries() {
		Long afterId = null;
		while (true) {
			List<Long> indexedCourseIds = indexRepository.findIndexedCourseIdsAfter(
					afterId, PageRequest.of(0, BATCH_SIZE));
			if (indexedCourseIds.isEmpty()) {
				break;
			}
			for (Long courseId : indexedCourseIds) {
				try {
					if (!courseStructure.isPublished(courseId)) {
						indexService.unindexCourse(courseId);
					}
				} catch (Exception exception) {
					log.warn("Failed to check stale index entry courseId={}", courseId, exception);
				}
				afterId = courseId;
			}
			if (indexedCourseIds.size() < BATCH_SIZE) {
				break;
			}
		}
	}
}
