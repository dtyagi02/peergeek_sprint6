import { apiRequest } from "../../shared/api/client";

export async function getLessonNote(courseId, lessonId) {
  try {
    const res = await apiRequest(
      `/courses/${courseId}/lessons/${lessonId}/notes`,
    );
    return res ?? null;
  } catch (err) {
    if (err.status === 204 || err.status === 404) return null;
    throw err;
  }
}

export async function saveLessonNote(courseId, lessonId, content) {
  return apiRequest(`/courses/${courseId}/lessons/${lessonId}/notes`, {
    method: "PUT",
    body: { content },
  });
}

export async function deleteLessonNote(courseId, lessonId) {
  return apiRequest(`/courses/${courseId}/lessons/${lessonId}/notes`, {
    method: "DELETE",
  });
}
