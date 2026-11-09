import { apiRequest } from "../../shared/api/client";

const instructor = "instructor";

export const listResources = (lessonId) =>
  apiRequest(`${instructor}/lessons/${lessonId}/resources`);
export const addResourceLink = (lessonId, body) =>
  apiRequest(`${instructor}/lessons/${lessonId}/resources/links`, {
    method: "POST",
    body,
  });
export const createResourceUpload = (lessonId, body) =>
  apiRequest(`${instructor}/lessons/${lessonId}/resources/uploads`, {
    method: "POST",
    body,
  });
export const createThumbnailUpload = (courseId, body) =>
  apiRequest(`${instructor}/courses/${courseId}/thumbnail/uploads`, {
    method: "POST",
    body,
  });
export const confirmResourceUpload = (resourceId) =>
  apiRequest(`${instructor}/resources/${resourceId}/confirm`, {
    method: "POST",
  });
export const getResourceDownloadUrl = (resourceId) =>
  apiRequest(`${instructor}/resources/${resourceId}/download-url`);
export const updateResource = (resourceId, body) =>
  apiRequest(`${instructor}/resources/${resourceId}`, {
    method: "PATCH",
    body,
  });
export const deleteResource = (resourceId) =>
  apiRequest(`${instructor}/resources/${resourceId}`, { method: "DELETE" });
export const reorderResources = (lessonId, resourceIds) =>
  apiRequest(`${instructor}/lessons/${lessonId}/resources/order`, {
    method: "PUT",
    body: { resourceIds },
  });
export const listExpectedResources = (lessonId) =>
  apiRequest(`${instructor}/lessons/${lessonId}/expected-resources`);
export const replaceExpectedResources = (lessonId, body) =>
  apiRequest(`${instructor}/lessons/${lessonId}/expected-resources`, {
    method: "PUT",
    body,
  });
export const getThumbnail = (courseId) =>
  apiRequest(`${instructor}/courses/${courseId}/thumbnail`);
export const deleteThumbnail = (courseId) =>
  apiRequest(`${instructor}/courses/${courseId}/thumbnail`, {
    method: "DELETE",
  });

export async function uploadFile(upload, file) {
  const response = await fetch(upload.url, {
    method: upload.method,
    headers: upload.headers,
    body: file,
  });
  if (!response.ok) {
    throw new Error("The file could not be uploaded to storage.");
  }
}

export async function uploadLessonResource(lessonId, file, type, title) {
  const created = await createResourceUpload(lessonId, {
    filename: file.name,
    mimeType: file.type,
    sizeBytes: file.size,
    type,
    ...(title ? { title } : {}),
  });
  await uploadFile(created.upload, file);
  return confirmResourceUpload(created.resourceId);
}

export async function uploadCourseThumbnail(courseId, file) {
  const created = await createThumbnailUpload(courseId, {
    filename: file.name,
    mimeType: file.type,
    sizeBytes: file.size,
  });
  await uploadFile(created.upload, file);
  return confirmResourceUpload(created.resourceId);
}

export function resourceTypeForFile(file) {
  if (file.type === "application/pdf") return "PDF";
  if (file.type.startsWith("image/")) return "IMAGE";
  if (file.type.startsWith("video/")) return "VIDEO";
  return "DOCUMENT";
}
