package com.peergeek.enrollment;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.peergeek.config.ResourceSettings;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.course.LessonLocation;
import com.peergeek.exception.NotFoundException;
import com.peergeek.file.ObjectStorageService;
import com.peergeek.file.ObjectStorageService.Disposition;
import com.peergeek.file.ResourceAccessContext;
import com.peergeek.file.ResourceAccessDeniedMode;
import com.peergeek.file.ResourceAccessService;
import com.peergeek.file.ResourceAction;
import com.peergeek.file.ResourceDownloadService;
import com.peergeek.file.ResourceEntity;
import com.peergeek.file.ResourcePurpose;
import com.peergeek.file.ResourceRepository;
import com.peergeek.file.ResourceStatus;
import com.peergeek.file.ResourceSummary;
import com.peergeek.file.ResourceType;
import com.peergeek.user.PublicProfileDto;
import com.peergeek.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningContentService {

	private final CourseStructureQueryService courseStructure;
	private final EnrollmentRepository enrollmentRepository;
	private final LessonProgressRepository lessonProgressRepository;
	private final ProgressService progressService;
	private final ResourceRepository resourceRepository;
	private final ResourceAccessService resourceAccessService;
	private final ResourceDownloadService resourceDownloadService;
	private final ObjectStorageService objectStorage;
	private final ResourceSettings resourceSettings;
	private final UserService userService;

	public LearningContentService(
			CourseStructureQueryService courseStructure,
			EnrollmentRepository enrollmentRepository,
			LessonProgressRepository lessonProgressRepository,
			ProgressService progressService,
			ResourceRepository resourceRepository,
			ResourceAccessService resourceAccessService,
			ResourceDownloadService resourceDownloadService,
			ObjectStorageService objectStorage,
			ResourceSettings resourceSettings,
			UserService userService) {
		this.courseStructure = courseStructure;
		this.enrollmentRepository = enrollmentRepository;
		this.lessonProgressRepository = lessonProgressRepository;
		this.progressService = progressService;
		this.resourceRepository = resourceRepository;
		this.resourceAccessService = resourceAccessService;
		this.resourceDownloadService = resourceDownloadService;
		this.objectStorage = objectStorage;
		this.resourceSettings = resourceSettings;
		this.userService = userService;
	}

	@Transactional
	public LearnerCourseOutlineDto getLearnerOutline(long courseId, long userId) {
		CourseMetadataSnapshot metadata = courseStructure.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));

		if (metadata.deleted()) {
			throw new NotFoundException("The requested resource was not found");
		}

		Optional<EnrollmentEntity> enrollmentOpt =
				enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE);

		if (metadata.status() != CourseStatus.PUBLISHED) {
			if (enrollmentOpt.isEmpty()) {
				throw new NotFoundException("The requested resource was not found");
			}
		} else {
			if (enrollmentOpt.isEmpty()) {
				throw new EnrollmentRequiredException();
			}
		}

		EnrollmentEntity enrollment = enrollmentOpt.get();
		CourseStructureQueryService.CourseStructure structure = courseStructure.getStructure(courseId);
		CourseProgressDto progress = progressService.getOrRecomputeProgress(enrollment);
		ContinueLessonDto continueLesson = progressService.getContinueLesson(enrollment);

		Set<Long> completedLessonIds = lessonProgressRepository.findByEnrollmentId(enrollment.getId()).stream()
				.map(LessonProgressEntity::getLessonId)
				.collect(Collectors.toSet());

		PublicProfileDto instructor = userService.getPublicProfiles(List.of(metadata.ownerId())).get(metadata.ownerId());

		LearnerCourseSummaryDto courseSummary = new LearnerCourseSummaryDto(
				courseId,
				metadata.title(),
				metadata.description(),
				metadata.categoryName(),
				metadata.difficulty(),
				metadata.topics(),
				instructor,
				metadata.status().name());

		List<LearnerModuleDto> moduleDtos = structure.modules().stream().map(module -> {
			List<LearnerLessonSummaryDto> lessonDtos = module.lessons().stream()
					.map(lesson -> new LearnerLessonSummaryDto(
							lesson.id(),
							lesson.title(),
							lesson.summary(),
							lesson.position(),
							completedLessonIds.contains(lesson.id())))
					.toList();
			return new LearnerModuleDto(module.id(), module.title(), module.description(), module.position(), lessonDtos);
		}).toList();

		return new LearnerCourseOutlineDto(courseSummary, moduleDtos, progress, continueLesson);
	}

	@Transactional(readOnly = true)
	public LearnerLessonDto getLearnerLesson(long courseId, long lessonId, long userId) {
		CourseMetadataSnapshot metadata = courseStructure.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));

		if (metadata.deleted()) {
			throw new NotFoundException("The requested resource was not found");
		}

		Optional<EnrollmentEntity> enrollmentOpt =
				enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE);

		if (metadata.status() != CourseStatus.PUBLISHED) {
			if (enrollmentOpt.isEmpty()) {
				throw new NotFoundException("The requested resource was not found");
			}
		} else {
			if (enrollmentOpt.isEmpty()) {
				throw new EnrollmentRequiredException();
			}
		}

		Optional<LessonLocation> locationOpt = courseStructure.getLessonLocation(lessonId);
		if (locationOpt.isEmpty() || locationOpt.get().courseDeleted() || !locationOpt.get().courseId().equals(courseId)) {
			throw new NotFoundException("The requested resource was not found");
		}

		CourseStructureQueryService.CourseStructure structure = courseStructure.getStructure(courseId);
		List<CourseStructureQueryService.LessonSnapshot> allLessonsInOrder = new ArrayList<>();
		Long moduleId = locationOpt.get().moduleId();
		String lessonTitle = "";
		String lessonSummary = "";
		int currentIndex = -1;

		for (CourseStructureQueryService.ModuleSnapshot module : structure.modules()) {
			for (CourseStructureQueryService.LessonSnapshot lesson : module.lessons()) {
				if (lesson.id().equals(lessonId)) {
					currentIndex = allLessonsInOrder.size();
					lessonTitle = lesson.title();
					lessonSummary = lesson.summary();
				}
				allLessonsInOrder.add(lesson);
			}
		}

		Long previousLessonId = currentIndex > 0 ? allLessonsInOrder.get(currentIndex - 1).id() : null;
		Long nextLessonId = (currentIndex >= 0 && currentIndex < allLessonsInOrder.size() - 1)
				? allLessonsInOrder.get(currentIndex + 1).id()
				: null;

		CourseStructureQueryService.LessonContentSnapshot content = courseStructure.getLessonContent(lessonId);

		List<ResourceEntity> resourceEntities =
				resourceRepository.findByLessonIdAndDeletedAtIsNullAndPurposeOrderByPositionAsc(
						lessonId, ResourcePurpose.LESSON_RESOURCE);

		List<LearnerResourceDto> resources = new ArrayList<>(resourceEntities.size());
		for (ResourceEntity res : resourceEntities) {
			if (res.getStatus() != ResourceStatus.READY) {
				continue;
			}
			if (res.getType() == ResourceType.LINK) {
				resources.add(new LearnerResourceDto(
						res.getId(),
						res.getType(),
						res.getTitle(),
						res.getMimeType(),
						res.getSizeBytes(),
						null,
						null,
						res.getExternalUrl()));
			} else {
				Disposition disposition = (res.getType() == ResourceType.DOCUMENT)
						? Disposition.ATTACHMENT
						: Disposition.INLINE;
				ResourceAction action = (disposition == Disposition.ATTACHMENT)
						? ResourceAction.DOWNLOAD
						: ResourceAction.VIEW;

				ResourceSummary summary = ResourceSummary.from(res);
				resourceAccessService.authorize(
						new ResourceAccessContext(userId, summary, action),
						ResourceAccessDeniedMode.NOT_FOUND);

				URI presigned = objectStorage.presignGet(
						res.getObjectKey(), res.getOriginalFilename(), resourceSettings.getUrlTtl(), disposition).url();
				Instant expiresAt = Instant.now().plus(resourceSettings.getUrlTtl());

				resources.add(new LearnerResourceDto(
						res.getId(),
						res.getType(),
						res.getTitle(),
						res.getMimeType(),
						res.getSizeBytes(),
						presigned.toString(),
						expiresAt,
						null));
			}
		}

		boolean completed = lessonProgressRepository.findByEnrollmentIdAndLessonId(
				enrollmentOpt.get().getId(), lessonId).isPresent();

		LearnerLessonDetailDto lessonDetail = new LearnerLessonDetailDto(
				lessonId, lessonTitle, lessonSummary, moduleId);

		return new LearnerLessonDto(
				lessonDetail,
				content.document(),
				resources,
				completed,
				previousLessonId,
				nextLessonId);
	}

	@Transactional(readOnly = true)
	public PresignedUrlDto getFreshResourceUrl(long resourceId, String dispositionParam, long userId) {
		ResourceEntity resource = resourceRepository.findByIdAndDeletedAtIsNull(resourceId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));

		if (resource.getStatus() != ResourceStatus.READY || resource.getPurpose() != ResourcePurpose.LESSON_RESOURCE) {
			throw new NotFoundException("The requested resource was not found");
		}

		CourseMetadataSnapshot metadata = courseStructure.getCourseMetadata(resource.getCourseId())
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));

		if (metadata.deleted()) {
			throw new NotFoundException("The requested resource was not found");
		}

		boolean isEnrolled = enrollmentRepository.existsByUserIdAndCourseIdAndStatus(
				userId, resource.getCourseId(), EnrollmentStatus.ACTIVE);
		if (!isEnrolled) {
			throw new NotFoundException("The requested resource was not found");
		}

		Disposition disposition;
		if (dispositionParam != null && !dispositionParam.isBlank()) {
			disposition = "attachment".equalsIgnoreCase(dispositionParam.trim())
					? Disposition.ATTACHMENT
					: Disposition.INLINE;
		} else {
			disposition = (resource.getType() == ResourceType.DOCUMENT)
					? Disposition.ATTACHMENT
					: Disposition.INLINE;
		}

		ResourceAction action = (disposition == Disposition.ATTACHMENT)
				? ResourceAction.DOWNLOAD
				: ResourceAction.VIEW;

		ResourceSummary summary = ResourceSummary.from(resource);
		resourceAccessService.authorize(
				new ResourceAccessContext(userId, summary, action),
				ResourceAccessDeniedMode.NOT_FOUND);

		URI presigned = resourceDownloadService.presignedGetUrl(resourceId, disposition);
		Instant expiresAt = Instant.now().plus(resourceSettings.getUrlTtl());

		return new PresignedUrlDto(presigned.toString(), expiresAt);
	}

	@Transactional
	public void recordVisit(long courseId, long lessonId, long userId) {
		Optional<LessonLocation> locationOpt = courseStructure.getLessonLocation(lessonId);
		if (locationOpt.isEmpty() || locationOpt.get().courseDeleted() || !locationOpt.get().courseId().equals(courseId)) {
			throw new NotFoundException("The requested resource was not found");
		}

		CourseMetadataSnapshot metadata = courseStructure.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));
		if (metadata.deleted()) {
			throw new NotFoundException("The requested resource was not found");
		}

		Optional<EnrollmentEntity> enrollmentOpt =
				enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE);
		if (enrollmentOpt.isEmpty()) {
			if (metadata.status() != CourseStatus.PUBLISHED) {
				throw new NotFoundException("The requested resource was not found");
			}
			throw new EnrollmentRequiredException();
		}

		EnrollmentEntity enrollment = enrollmentOpt.get();
		enrollment.setLastLessonId(lessonId);
		enrollment.setLastAccessedAt(Instant.now());
		enrollmentRepository.save(enrollment);
	}

	@Transactional(readOnly = true)
	public ContinueLessonDto getContinueLesson(long courseId, long userId) {
		CourseMetadataSnapshot metadata = courseStructure.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));

		if (metadata.deleted()) {
			throw new NotFoundException("The requested resource was not found");
		}

		Optional<EnrollmentEntity> enrollmentOpt =
				enrollmentRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE);

		if (metadata.status() != CourseStatus.PUBLISHED) {
			if (enrollmentOpt.isEmpty()) {
				throw new NotFoundException("The requested resource was not found");
			}
		} else {
			if (enrollmentOpt.isEmpty()) {
				throw new EnrollmentRequiredException();
			}
		}

		return progressService.getContinueLesson(enrollmentOpt.get());
	}
}
