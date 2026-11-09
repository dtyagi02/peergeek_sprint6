import { apiRequest } from "../../shared/api/client";

export const startReadinessRun = (courseId) =>
  apiRequest(`instructor/courses/${courseId}/readiness/runs`, {
    method: "POST",
  });

export const getReadinessRun = (courseId, runId) =>
  apiRequest(`instructor/courses/${courseId}/readiness/runs/${runId}`);

export const getLatestReadinessRun = (courseId) =>
  apiRequest(`instructor/courses/${courseId}/readiness/runs/latest`);

export const publishCourse = (courseId) =>
  apiRequest(`instructor/courses/${courseId}/publish`, { method: "POST" });

export const unpublishCourse = (courseId) =>
  apiRequest(`instructor/courses/${courseId}/unpublish`, { method: "POST" });

export const getInstructorPreview = (courseId) =>
  apiRequest(`instructor/courses/${courseId}/preview`);
