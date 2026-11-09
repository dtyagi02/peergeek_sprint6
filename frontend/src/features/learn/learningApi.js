import { apiRequest } from "../../shared/api/client";

export async function getEnrollmentStatus(courseId) {
  return apiRequest(`/learning/courses/${courseId}/enrollment`);
}

export async function enrollInCourse(courseId) {
  return apiRequest(`/learning/courses/${courseId}/enroll`, {
    method: "POST",
  });
}

export async function withdrawFromCourse(courseId) {
  return apiRequest(`/learning/courses/${courseId}/enrollment`, {
    method: "DELETE",
  });
}

export async function getMyCourses({
  status = "all",
  q = "",
  page = 0,
  size = 12,
  sort = "lastAccessedAt,desc",
} = {}) {
  const searchParams = new URLSearchParams();
  if (status) searchParams.set("status", status);
  if (q) searchParams.set("q", q);
  searchParams.set("page", String(page));
  searchParams.set("size", String(size));
  if (sort) searchParams.set("sort", sort);

  return apiRequest(`/learning/courses?${searchParams.toString()}`);
}

export async function getLearnerOutline(courseId) {
  return apiRequest(`/learning/courses/${courseId}`);
}

export async function getLearnerLesson(courseId, lessonId) {
  return apiRequest(`/learning/courses/${courseId}/lessons/${lessonId}`);
}

export async function getFreshResourceUrl(resourceId, disposition = "inline") {
  return apiRequest(
    `/learning/resources/${resourceId}/url?disposition=${encodeURIComponent(disposition)}`,
  );
}

export async function visitLesson(courseId, lessonId) {
  return apiRequest(`/learning/courses/${courseId}/lessons/${lessonId}/visit`, {
    method: "POST",
  });
}

export async function setLessonCompletion(courseId, lessonId, completed) {
  return apiRequest(
    `/learning/courses/${courseId}/lessons/${lessonId}/completion`,
    {
      method: "PUT",
      body: { completed },
    },
  );
}

export async function getContinueLesson(courseId) {
  return apiRequest(`/learning/courses/${courseId}/continue`);
}
