package com.peergeek.note;

import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.course.LessonLocation;
import com.peergeek.enrollment.EnrollmentRepository;
import com.peergeek.enrollment.EnrollmentRequiredException;
import com.peergeek.enrollment.EnrollmentStatus;
import com.peergeek.exception.NotFoundException;
import com.peergeek.note.dto.LessonNoteDto;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LessonNoteServiceImpl implements LessonNoteService {

	private static final Logger log = LoggerFactory.getLogger(LessonNoteServiceImpl.class);
	private static final int MAX_CONTENT_LENGTH = 50000;

	private final LessonNoteRepository lessonNoteRepository;
	private final CourseStructureQueryService courseStructureQueryService;
	private final EnrollmentRepository enrollmentRepository;

	public LessonNoteServiceImpl(
			LessonNoteRepository lessonNoteRepository,
			CourseStructureQueryService courseStructureQueryService,
			EnrollmentRepository enrollmentRepository) {
		this.lessonNoteRepository = lessonNoteRepository;
		this.courseStructureQueryService = courseStructureQueryService;
		this.enrollmentRepository = enrollmentRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<LessonNoteDto> getNote(long courseId, long lessonId, long userId) {
		validateAccess(courseId, lessonId, userId);
		return lessonNoteRepository.findByUserIdAndLessonId(userId, lessonId)
				.map(this::toDto);
	}

	@Override
	@Transactional
	public LessonNoteDto saveNote(long courseId, long lessonId, long userId, String content) {
		if (content == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Note content must not be null");
		}
		if (content.length() > MAX_CONTENT_LENGTH) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"Note content cannot exceed " + MAX_CONTENT_LENGTH + " characters");
		}

		validateAccess(courseId, lessonId, userId);

		LessonNoteEntity entity = lessonNoteRepository.findByUserIdAndLessonId(userId, lessonId)
				.orElseGet(() -> new LessonNoteEntity(userId, courseId, lessonId, content));

		entity.setContent(content);
		LessonNoteEntity saved = lessonNoteRepository.save(entity);
		log.debug("Saved lesson note for user={} course={} lesson={}", userId, courseId, lessonId);
		return toDto(saved);
	}

	@Override
	@Transactional
	public void deleteNote(long courseId, long lessonId, long userId) {
		validateAccess(courseId, lessonId, userId);
		lessonNoteRepository.deleteByUserIdAndLessonId(userId, lessonId);
		log.debug("Deleted lesson note for user={} course={} lesson={}", userId, courseId, lessonId);
	}

	private void validateAccess(long courseId, long lessonId, long userId) {
		Optional<LessonLocation> locationOpt = courseStructureQueryService.getLessonLocation(lessonId);
		if (locationOpt.isEmpty() || locationOpt.get().courseDeleted() || !locationOpt.get().courseId().equals(courseId)) {
			throw new NotFoundException("The requested resource was not found");
		}

		CourseMetadataSnapshot metadata = courseStructureQueryService.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("The requested resource was not found"));

		if (metadata.deleted()) {
			throw new NotFoundException("The requested resource was not found");
		}

		boolean isEnrolled = enrollmentRepository.existsByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ACTIVE);
		boolean isOwner = metadata.ownerId() != null && metadata.ownerId().equals(userId);

		if (metadata.status() != CourseStatus.PUBLISHED) {
			if (!isEnrolled && !isOwner) {
				throw new NotFoundException("The requested resource was not found");
			}
		} else {
			if (!isEnrolled && !isOwner) {
				throw new EnrollmentRequiredException();
			}
		}
	}

	private LessonNoteDto toDto(LessonNoteEntity entity) {
		return new LessonNoteDto(
				entity.getId(),
				entity.getUserId(),
				entity.getCourseId(),
				entity.getLessonId(),
				entity.getContent(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}
}
