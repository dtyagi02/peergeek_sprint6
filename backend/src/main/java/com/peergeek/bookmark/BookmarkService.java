package com.peergeek.bookmark;

import com.peergeek.bookmark.dto.BookmarkDto;
import com.peergeek.bookmark.dto.BookmarkStatusDto;
import java.util.List;

public interface BookmarkService {

	BookmarkDto bookmarkCourse(long courseId, long userId);

	void removeCourseBookmark(long courseId, long userId);

	BookmarkStatusDto getCourseBookmarkStatus(long courseId, long userId);

	BookmarkDto bookmarkLesson(long courseId, long lessonId, long userId);

	void removeLessonBookmark(long courseId, long lessonId, long userId);

	BookmarkStatusDto getLessonBookmarkStatus(long courseId, long lessonId, long userId);

	List<BookmarkDto> listUserBookmarks(long userId, BookmarkTargetType type);

	void deleteBookmarkById(long bookmarkId, long userId);
}
