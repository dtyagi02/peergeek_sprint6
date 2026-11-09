package com.peergeek.note;

import com.peergeek.note.dto.LessonNoteDto;
import java.util.Optional;

public interface LessonNoteService {

	Optional<LessonNoteDto> getNote(long courseId, long lessonId, long userId);

	LessonNoteDto saveNote(long courseId, long lessonId, long userId, String content);

	void deleteNote(long courseId, long lessonId, long userId);
}
