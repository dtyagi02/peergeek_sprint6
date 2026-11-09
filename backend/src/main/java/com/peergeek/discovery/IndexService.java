package com.peergeek.discovery;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.course.PublishedCourseSnapshot;
import com.peergeek.user.PublicProfileDto;
import com.peergeek.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndexService {
	private static final Logger log = LoggerFactory.getLogger(IndexService.class);

	private final PublicCourseIndexRepository indexRepository;
	private final CourseStructureQueryService courseStructure;
	private final UserService userService;
	private final IndexBuilder indexBuilder;
	private final DiscoveryCacheManager cacheManager;

	public IndexService(
			PublicCourseIndexRepository indexRepository,
			CourseStructureQueryService courseStructure,
			UserService userService,
			IndexBuilder indexBuilder,
			DiscoveryCacheManager cacheManager) {
		this.indexRepository = indexRepository;
		this.courseStructure = courseStructure;
		this.userService = userService;
		this.indexBuilder = indexBuilder;
		this.cacheManager = cacheManager;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void indexCourse(long courseId) {
		Optional<PublishedCourseSnapshot> snapshotOpt = courseStructure.getPublishedCourse(courseId);
		if (snapshotOpt.isEmpty()) {
			unindexCourse(courseId);
			return;
		}
		PublishedCourseSnapshot snapshot = snapshotOpt.get();
		Map<Long, PublicProfileDto> profiles = userService.getPublicProfiles(Set.of(snapshot.ownerId()));
		PublicProfileDto instructor = profiles.get(snapshot.ownerId());
		PublicCourseIndexEntity entity = indexBuilder.build(snapshot, instructor, Instant.now());
		indexRepository.save(entity);
		cacheManager.evictCourse(courseId);
		log.info("Indexed published course courseId={}", courseId);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void unindexCourse(long courseId) {
		if (indexRepository.existsByCourseId(courseId)) {
			indexRepository.deleteByCourseId(courseId);
			cacheManager.evictCourse(courseId);
			log.info("Unindexed course courseId={}", courseId);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void rebuildCourseIfIndexed(long courseId) {
		if (indexRepository.existsByCourseId(courseId)) {
			indexCourse(courseId);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void reconcileCourse(long courseId) {
		Optional<PublishedCourseSnapshot> snapshotOpt = courseStructure.getPublishedCourse(courseId);
		if (snapshotOpt.isEmpty()) {
			unindexCourse(courseId);
			return;
		}
		PublishedCourseSnapshot snapshot = snapshotOpt.get();
		Map<Long, PublicProfileDto> profiles = userService.getPublicProfiles(Set.of(snapshot.ownerId()));
		PublicProfileDto instructor = profiles.get(snapshot.ownerId());
		String newFingerprint = indexBuilder.computeFingerprint(
				snapshot, instructor == null ? null : instructor.displayName());

		Optional<PublicCourseIndexEntity> existing = indexRepository.findByCourseId(courseId);
		if (existing.isEmpty() || !newFingerprint.equals(existing.get().getSourceFingerprint())) {
			PublicCourseIndexEntity entity = indexBuilder.build(snapshot, instructor, Instant.now());
			indexRepository.save(entity);
			cacheManager.evictCourse(courseId);
			log.info("Reconciled public course index row courseId={}", courseId);
		}
	}

	@Transactional(readOnly = true)
	public boolean isIndexed(long courseId) {
		return indexRepository.existsByCourseId(courseId);
	}
}
