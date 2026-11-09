package com.peergeek.bookmark;

import com.peergeek.bookmark.dto.BookmarkDto;
import com.peergeek.bookmark.dto.BookmarkStatusDto;
import com.peergeek.security.CurrentUser;
import com.peergeek.security.CurrentUserProvider;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookmarkController {

	private final BookmarkService bookmarkService;
	private final CurrentUserProvider currentUserProvider;

	public BookmarkController(BookmarkService bookmarkService, CurrentUserProvider currentUserProvider) {
		this.bookmarkService = bookmarkService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping("/api/v1/bookmarks")
	public ResponseEntity<List<BookmarkDto>> listMyBookmarks(
			@RequestParam(value = "type", required = false) BookmarkTargetType type) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		List<BookmarkDto> bookmarks = bookmarkService.listUserBookmarks(user.id(), type);
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(bookmarks);
	}

	@DeleteMapping("/api/v1/bookmarks/{bookmarkId}")
	public ResponseEntity<Void> deleteBookmark(@PathVariable("bookmarkId") long bookmarkId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		bookmarkService.deleteBookmarkById(bookmarkId, user.id());
		return ResponseEntity.noContent()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.build();
	}

	@PostMapping("/api/v1/courses/{courseId}/bookmarks")
	public ResponseEntity<BookmarkDto> bookmarkCourse(@PathVariable("courseId") long courseId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		BookmarkDto dto = bookmarkService.bookmarkCourse(courseId, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(dto);
	}

	@DeleteMapping("/api/v1/courses/{courseId}/bookmarks")
	public ResponseEntity<Void> removeCourseBookmark(@PathVariable("courseId") long courseId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		bookmarkService.removeCourseBookmark(courseId, user.id());
		return ResponseEntity.noContent()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.build();
	}

	@GetMapping("/api/v1/courses/{courseId}/bookmarks/status")
	public ResponseEntity<BookmarkStatusDto> getCourseBookmarkStatus(@PathVariable("courseId") long courseId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		BookmarkStatusDto status = bookmarkService.getCourseBookmarkStatus(courseId, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(status);
	}

	@PostMapping("/api/v1/courses/{courseId}/lessons/{lessonId}/bookmarks")
	public ResponseEntity<BookmarkDto> bookmarkLesson(
			@PathVariable("courseId") long courseId,
			@PathVariable("lessonId") long lessonId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		BookmarkDto dto = bookmarkService.bookmarkLesson(courseId, lessonId, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(dto);
	}

	@DeleteMapping("/api/v1/courses/{courseId}/lessons/{lessonId}/bookmarks")
	public ResponseEntity<Void> removeLessonBookmark(
			@PathVariable("courseId") long courseId,
			@PathVariable("lessonId") long lessonId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		bookmarkService.removeLessonBookmark(courseId, lessonId, user.id());
		return ResponseEntity.noContent()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.build();
	}

	@GetMapping("/api/v1/courses/{courseId}/lessons/{lessonId}/bookmarks/status")
	public ResponseEntity<BookmarkStatusDto> getLessonBookmarkStatus(
			@PathVariable("courseId") long courseId,
			@PathVariable("lessonId") long lessonId) {
		CurrentUser user = currentUserProvider.requireCurrentUser();
		BookmarkStatusDto status = bookmarkService.getLessonBookmarkStatus(courseId, lessonId, user.id());
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(status);
	}
}
