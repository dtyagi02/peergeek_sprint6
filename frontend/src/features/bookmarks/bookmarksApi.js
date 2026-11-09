import { apiRequest } from "../../shared/api/client";

export async function listBookmarks(type) {
  const query = type ? `?type=${encodeURIComponent(type)}` : "";
  return apiRequest(`/bookmarks${query}`);
}

export async function deleteBookmark(bookmarkId) {
  return apiRequest(`/bookmarks/${bookmarkId}`, {
    method: "DELETE",
  });
}

export async function getCourseBookmarkStatus(courseId) {
  return apiRequest(`/courses/${courseId}/bookmarks/status`);
}

export async function bookmarkCourse(courseId) {
  return apiRequest(`/courses/${courseId}/bookmarks`, {
    method: "POST",
  });
}

export async function removeCourseBookmark(courseId) {
  return apiRequest(`/courses/${courseId}/bookmarks`, {
    method: "DELETE",
  });
}

export async function getLessonBookmarkStatus(courseId, lessonId) {
  return apiRequest(
    `/courses/${courseId}/lessons/${lessonId}/bookmarks/status`,
  );
}

export async function bookmarkLesson(courseId, lessonId) {
  return apiRequest(`/courses/${courseId}/lessons/${lessonId}/bookmarks`, {
    method: "POST",
  });
}

export async function removeLessonBookmark(courseId, lessonId) {
  return apiRequest(`/courses/${courseId}/lessons/${lessonId}/bookmarks`, {
    method: "DELETE",
  });
}
