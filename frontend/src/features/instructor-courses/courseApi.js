import { apiRequest } from "../../shared/api/client";

export function listCourses(filters) {
  const params = new URLSearchParams({
    page: String(filters.page ?? 0),
    size: String(filters.size ?? 20),
    sort: filters.sort ?? "updatedAt,desc",
  });
  if (filters.status) params.set("status", filters.status);
  if (filters.query?.trim()) params.set("q", filters.query.trim());
  return apiRequest(`instructor/courses?${params}`);
}

export const listCategories = () => apiRequest("categories");
export const getCourse = (courseId) =>
  apiRequest(`instructor/courses/${courseId}`);
export const createCourse = (body) =>
  apiRequest("instructor/courses", { method: "POST", body });
export const updateCourse = (courseId, body) =>
  apiRequest(`instructor/courses/${courseId}`, { method: "PATCH", body });
export const deleteCourse = (courseId) =>
  apiRequest(`instructor/courses/${courseId}`, { method: "DELETE" });
export const getOutline = (courseId) =>
  apiRequest(`instructor/courses/${courseId}/outline`);
export const createModule = (courseId, body) =>
  apiRequest(`instructor/courses/${courseId}/modules`, {
    method: "POST",
    body,
  });
export const updateModule = (moduleId, body) =>
  apiRequest(`instructor/modules/${moduleId}`, { method: "PATCH", body });
export const deleteModule = (moduleId) =>
  apiRequest(`instructor/modules/${moduleId}`, { method: "DELETE" });
export const reorderModules = (courseId, moduleIds) =>
  apiRequest(`instructor/courses/${courseId}/modules/order`, {
    method: "PUT",
    body: { moduleIds },
  });
export const createLesson = (moduleId, body) =>
  apiRequest(`instructor/modules/${moduleId}/lessons`, {
    method: "POST",
    body,
  });
export const updateLesson = (lessonId, body) =>
  apiRequest(`instructor/lessons/${lessonId}`, { method: "PATCH", body });
export const deleteLesson = (lessonId) =>
  apiRequest(`instructor/lessons/${lessonId}`, { method: "DELETE" });
export const reorderLessons = (moduleId, lessonIds) =>
  apiRequest(`instructor/modules/${moduleId}/lessons/order`, {
    method: "PUT",
    body: { lessonIds },
  });
export const moveLesson = (lessonId, targetModuleId, position) =>
  apiRequest(`instructor/lessons/${lessonId}/move`, {
    method: "POST",
    body: { targetModuleId, position },
  });
export const getLessonContent = (lessonId) =>
  apiRequest(`instructor/lessons/${lessonId}/content`);
export const saveLessonContent = (lessonId, body) =>
  apiRequest(`instructor/lessons/${lessonId}/content`, {
    method: "PUT",
    body,
  });
