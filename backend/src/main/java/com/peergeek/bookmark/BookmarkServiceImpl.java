package com.peergeek.bookmark;

import com.peergeek.bookmark.dto.BookmarkDto;
import com.peergeek.bookmark.dto.BookmarkStatusDto;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStatus;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.course.LessonLocation;
import com.peergeek.enrollment.EnrollmentQueryService;
import com.peergeek.enrollment.EnrollmentRequiredException;
import com.peergeek.exception.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BookmarkServiceImpl implements BookmarkService {

	private final BookmarkRepository bookmarkRepository;
	private final CourseStructureQueryService courseStructure;
	private final EnrollmentQueryService enrollmentQueryService;

	public BookmarkServiceImpl(
			BookmarkRepository bookmarkRepository,
			CourseStructureQueryService courseStructure,
			EnrollmentQueryService enrollmentQueryService) {
		this.bookmarkRepository = bookmarkRepository;
		this.courseStructure = courseStructure;
		this.enrollmentQueryService = enrollmentQueryService;
	}

	@Override
	public BookmarkDto bookmarkCourse(long courseId, long userId) {
		CourseMetadataSnapshot metadata = courseStructure.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("The requested course was not found"));

		if (metadata.deleted()) {
			throw new NotFoundException("The requested course was not found");
		}

		if (metadata.status() != CourseStatus.PUBLISHED) {
			boolean isOwner = metadata.ownerId() != null && metadata.ownerId().equals(userId);
			boolean isEnrolled = enrollmentQueryService.isActivelyEnrolled(userId, courseId);
			if (!isOwner && !isEnrolled) {
				throw new NotFoundException("The requested course was not found");
			}
		}

		Optional<BookmarkEntity> existing = bookmarkRepository
				.findByUserIdAndTargetTypeAndCourseIdAndLessonIdIsNull(userId, BookmarkTargetType.COURSE, courseId);
		if (existing.isPresent()) {
			return toDto(existing.get(), metadata);
		}

		BookmarkEntity saved = bookmarkRepository.save(
				new BookmarkEntity(userId, BookmarkTargetType.COURSE, courseId, null));
		return toDto(saved, metadata);
	}

	@Override
	public void removeCourseBookmark(long courseId, long userId) {
		bookmarkRepository.deleteByUserIdAndTargetTypeAndCourseIdAndLessonIdIsNull(
				userId, BookmarkTargetType.COURSE, courseId);
	}

	@Override
	@Transactional(readOnly = true)
	public BookmarkStatusDto getCourseBookmarkStatus(long courseId, long userId) {
		Optional<BookmarkEntity> existing = bookmarkRepository
				.findByUserIdAndTargetTypeAndCourseIdAndLessonIdIsNull(userId, BookmarkTargetType.COURSE, courseId);
		return new BookmarkStatusDto(existing.isPresent(), existing.map(BookmarkEntity::getId).orElse(null));
	}

	@Override
	public BookmarkDto bookmarkLesson(long courseId, long lessonId, long userId) {
		CourseMetadataSnapshot metadata = courseStructure.getCourseMetadata(courseId)
				.orElseThrow(() -> new NotFoundException("The requested course was not found"));

		if (metadata.deleted()) {
			throw new NotFoundException("The requested course was not found");
		}

		Optional<LessonLocation> locationOpt = courseStructure.getLessonLocation(lessonId);
		if (locationOpt.isEmpty() || locationOpt.get().courseDeleted() || !courseOptMatch(locationOpt.get(), courseId)) {
			throw new NotFoundException("The requested lesson was not found");
		}

		boolean isOwner = metadata.ownerId() != null && metadata.ownerId().equals(userId);
		boolean isEnrolled = enrollmentQueryService.isActivelyEnrolled(userId, courseId);
		if (!isOwner && !isEnrolled) {
			throw new EnrollmentRequiredException();
		}

		Optional<BookmarkEntity> existing = bookmarkRepository
				.findByUserIdAndTargetTypeAndCourseIdAndLessonId(userId, BookmarkTargetType.LESSON, courseId, lessonId);
		if (existing.isPresent()) {
			return toLessonDto(existing.get(), metadata, lessonId);
		}

		BookmarkEntity saved = bookmarkRepository.save(
				new BookmarkEntity(userId, BookmarkTargetType.LESSON, courseId, lessonId));
		return toLessonDto(saved, metadata, lessonId);
	}

	@Override
	public void removeLessonBookmark(long courseId, long lessonId, long userId) {
		bookmarkRepository.deleteByUserIdAndTargetTypeAndCourseIdAndLessonId(
				userId, BookmarkTargetType.LESSON, courseId, lessonId);
	}

	@Override
	@Transactional(readOnly = true)
	public BookmarkStatusDto getLessonBookmarkStatus(long courseId, long lessonId, long userId) {
		Optional<BookmarkEntity> existing = bookmarkRepository
				.findByUserIdAndTargetTypeAndCourseIdAndLessonId(userId, BookmarkTargetType.LESSON, courseId, lessonId);
		return new BookmarkStatusDto(existing.isPresent(), existing.map(BookmarkEntity::getId).orElse(null));
	}

	@Override
	@Transactional(readOnly = true)
	public List<BookmarkDto> listUserBookmarks(long userId, BookmarkTargetType type) {
		List<BookmarkEntity> entities = type != null
				? bookmarkRepository.findByUserIdAndTargetTypeOrderByCreatedAtDesc(userId, type)
				: bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId);

		List<BookmarkDto> dtos = new ArrayList<>();
		for (BookmarkEntity b : entities) {
			Optional<CourseMetadataSnapshot> metaOpt = courseStructure.getCourseMetadata(b.getCourseId());
			if (metaOpt.isEmpty() || metaOpt.get().deleted()) {
				continue;
			}
			CourseMetadataSnapshot meta = metaOpt.get();

			if (b.getTargetType() == BookmarkTargetType.COURSE) {
				dtos.add(toDto(b, meta));
			} else {
				Optional<LessonLocation> locOpt = courseStructure.getLessonLocation(b.getLessonId());
				if (locOpt.isEmpty() || locOpt.get().courseDeleted()) {
					continue;
				}
				dtos.add(toLessonDto(b, meta, b.getLessonId()));
			}
		}
		return dtos;
	}

	@Override
	public void deleteBookmarkById(long bookmarkId, long userId) {
		BookmarkEntity b = bookmarkRepository.findById(bookmarkId)
				.orElseThrow(() -> new NotFoundException("The requested bookmark was not found"));
		if (!b.getUserId().equals(userId)) {
			throw new NotFoundException("The requested bookmark was not found");
		}
		bookmarkRepository.delete(b);
	}

	private boolean courseOptMatch(LessonLocation location, long courseId) {
		return location.courseId() != null && location.courseId().equals(courseId);
	}

	private BookmarkDto toDto(BookmarkEntity b, CourseMetadataSnapshot meta) {
		return new BookmarkDto(
				b.getId(),
				BookmarkTargetType.COURSE,
				b.getCourseId(),
				meta.title(),
				meta.difficulty() != null ? meta.difficulty().name() : null,
				null,
				null,
				null,
				b.getCreatedAt());
	}

	private BookmarkDto toLessonDto(BookmarkEntity b, CourseMetadataSnapshot meta, long lessonId) {
		String lessonTitle = "Lesson #" + lessonId;
		String moduleTitle = null;

		CourseStructureQueryService.CourseStructure structure = courseStructure.getStructure(b.getCourseId());
		if (structure.modules() != null) {
			for (CourseStructureQueryService.ModuleSnapshot m : structure.modules()) {
				if (m.lessons() != null) {
					for (CourseStructureQueryService.LessonSnapshot l : m.lessons()) {
						if (l.id().equals(lessonId)) {
							lessonTitle = l.title();
							moduleTitle = m.title();
							break;
						}
					}
				}
			}
		}

		return new BookmarkDto(
				b.getId(),
				BookmarkTargetType.LESSON,
				b.getCourseId(),
				meta.title(),
				meta.difficulty() != null ? meta.difficulty().name() : null,
				lessonId,
				lessonTitle,
				moduleTitle,
				b.getCreatedAt());
	}
}
