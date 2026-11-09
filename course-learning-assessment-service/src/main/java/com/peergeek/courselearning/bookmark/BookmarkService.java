package com.peergeek.courselearning.bookmark;

import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.lesson.LessonEntity;
import com.peergeek.courselearning.lesson.LessonRepository;
import com.peergeek.courselearning.module.CourseModuleEntity;
import com.peergeek.courselearning.module.CourseModuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;

    public BookmarkService(
            BookmarkRepository bookmarkRepository,
            CourseRepository courseRepository,
            CourseModuleRepository moduleRepository,
            LessonRepository lessonRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
    }

    @Transactional(readOnly = true)
    public List<BookmarkDtos.BookmarkDto> listUserBookmarks(Long userId, String type) {
        List<BookmarkEntity> all = bookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<BookmarkDtos.BookmarkDto> result = new ArrayList<>();

        for (BookmarkEntity b : all) {
            if (type != null && !type.isBlank() && !b.getTargetType().equalsIgnoreCase(type)) {
                continue;
            }

            CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(b.getCourseId()).orElse(null);
            if (course == null) continue;

            String lessonTitle = null;
            String moduleTitle = null;
            if (b.getLessonId() != null) {
                LessonEntity lesson = lessonRepository.findById(b.getLessonId()).orElse(null);
                if (lesson != null) {
                    lessonTitle = lesson.getTitle();
                    CourseModuleEntity mod = moduleRepository.findById(lesson.getModuleId()).orElse(null);
                    if (mod != null) {
                        moduleTitle = mod.getTitle();
                    }
                }
            }

            result.add(new BookmarkDtos.BookmarkDto(
                    b.getId(),
                    b.getTargetType(),
                    course.getId(),
                    course.getTitle(),
                    course.getDifficulty() != null ? course.getDifficulty().name() : null,
                    b.getLessonId(),
                    lessonTitle,
                    moduleTitle,
                    b.getCreatedAt()
            ));
        }

        return result;
    }

    @Transactional
    public void deleteBookmarkById(Long bookmarkId, Long userId) {
        BookmarkEntity bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new NotFoundException("Bookmark not found: " + bookmarkId));

        if (!bookmark.getUserId().equals(userId)) {
            throw new ForbiddenException("Cannot delete another user's bookmark");
        }

        bookmarkRepository.delete(bookmark);
    }

    @Transactional
    public BookmarkDtos.BookmarkDto bookmarkCourse(Long courseId, Long userId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));

        Optional<BookmarkEntity> existing = bookmarkRepository.findByUserIdAndCourseIdAndLessonIdIsNull(userId, courseId);
        BookmarkEntity entity = existing.orElseGet(() ->
                bookmarkRepository.save(new BookmarkEntity(userId, "COURSE", courseId, null)));

        return new BookmarkDtos.BookmarkDto(
                entity.getId(),
                "COURSE",
                course.getId(),
                course.getTitle(),
                course.getDifficulty() != null ? course.getDifficulty().name() : null,
                null,
                null,
                null,
                entity.getCreatedAt()
        );
    }

    @Transactional
    public void removeCourseBookmark(Long courseId, Long userId) {
        bookmarkRepository.deleteByUserIdAndCourseIdAndLessonIdIsNull(userId, courseId);
    }

    @Transactional(readOnly = true)
    public BookmarkDtos.BookmarkStatusDto getCourseBookmarkStatus(Long courseId, Long userId) {
        Optional<BookmarkEntity> existing = bookmarkRepository.findByUserIdAndCourseIdAndLessonIdIsNull(userId, courseId);
        return new BookmarkDtos.BookmarkStatusDto(existing.isPresent(), existing.map(BookmarkEntity::getId).orElse(null));
    }

    @Transactional
    public BookmarkDtos.BookmarkDto bookmarkLesson(Long courseId, Long lessonId, Long userId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));

        LessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson not found: " + lessonId));

        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId()).orElse(null);

        Optional<BookmarkEntity> existing = bookmarkRepository.findByUserIdAndCourseIdAndLessonId(userId, courseId, lessonId);
        BookmarkEntity entity = existing.orElseGet(() ->
                bookmarkRepository.save(new BookmarkEntity(userId, "LESSON", courseId, lessonId)));

        return new BookmarkDtos.BookmarkDto(
                entity.getId(),
                "LESSON",
                course.getId(),
                course.getTitle(),
                course.getDifficulty() != null ? course.getDifficulty().name() : null,
                lesson.getId(),
                lesson.getTitle(),
                module != null ? module.getTitle() : null,
                entity.getCreatedAt()
        );
    }

    @Transactional
    public void removeLessonBookmark(Long courseId, Long lessonId, Long userId) {
        bookmarkRepository.deleteByUserIdAndCourseIdAndLessonId(userId, courseId, lessonId);
    }

    @Transactional(readOnly = true)
    public BookmarkDtos.BookmarkStatusDto getLessonBookmarkStatus(Long courseId, Long lessonId, Long userId) {
        Optional<BookmarkEntity> existing = bookmarkRepository.findByUserIdAndCourseIdAndLessonId(userId, courseId, lessonId);
        return new BookmarkDtos.BookmarkStatusDto(existing.isPresent(), existing.map(BookmarkEntity::getId).orElse(null));
    }
}

