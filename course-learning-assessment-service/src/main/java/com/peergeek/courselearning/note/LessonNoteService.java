package com.peergeek.courselearning.note;

import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.exception.NotFoundException;
import com.peergeek.courselearning.lesson.LessonEntity;
import com.peergeek.courselearning.lesson.LessonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class LessonNoteService {

    private final LessonNoteRepository noteRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;

    public LessonNoteService(
            LessonNoteRepository noteRepository,
            CourseRepository courseRepository,
            LessonRepository lessonRepository) {
        this.noteRepository = noteRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
    }

    @Transactional(readOnly = true)
    public Optional<LessonNoteDtos.LessonNoteDto> getNote(long courseId, long lessonId, long userId) {
        return noteRepository.findByUserIdAndLessonId(userId, lessonId)
                .map(LessonNoteDtos.LessonNoteDto::from);
    }

    @Transactional
    public LessonNoteDtos.LessonNoteDto saveNote(long courseId, long lessonId, long userId, String content) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));

        LessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new NotFoundException("Lesson not found: " + lessonId));

        String safeContent = content != null ? content : "";
        Instant now = Instant.now();

        LessonNoteEntity entity = noteRepository.findByUserIdAndLessonId(userId, lessonId).orElse(null);
        if (entity == null) {
            entity = new LessonNoteEntity(userId, courseId, lessonId, safeContent);
        } else {
            entity.setContent(safeContent);
            entity.setUpdatedAt(now);
        }

        entity = noteRepository.save(entity);
        return LessonNoteDtos.LessonNoteDto.from(entity);
    }

    @Transactional
    public void deleteNote(long courseId, long lessonId, long userId) {
        noteRepository.deleteByUserIdAndLessonId(userId, lessonId);
    }
}

