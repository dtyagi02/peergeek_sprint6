import { apiRequest } from "../../shared/api/client";

// --- Instructor API ---

export async function listInstructorAssignments(
  courseId,
  { status, page = 0, size = 20, sort = "createdAt,desc" } = {},
) {
  const params = new URLSearchParams();
  if (status) params.set("status", status);
  params.set("page", String(page));
  params.set("size", String(size));
  if (sort) params.set("sort", sort);
  return apiRequest(
    `/instructor/courses/${courseId}/assignments?${params.toString()}`,
  );
}

export async function createAssignment(courseId, data) {
  return apiRequest(`/instructor/courses/${courseId}/assignments`, {
    method: "POST",
    body: data,
  });
}

export async function getInstructorAssignment(assignmentId) {
  return apiRequest(`/instructor/assignments/${assignmentId}`);
}

export async function updateAssignment(assignmentId, data) {
  return apiRequest(`/instructor/assignments/${assignmentId}`, {
    method: "PATCH",
    body: data,
  });
}

export async function replaceQuestions(assignmentId, questions) {
  return apiRequest(`/instructor/assignments/${assignmentId}/questions`, {
    method: "PUT",
    body: questions,
  });
}

export async function publishAssignment(assignmentId) {
  return apiRequest(`/instructor/assignments/${assignmentId}/publish`, {
    method: "POST",
  });
}

export async function unpublishAssignment(assignmentId) {
  return apiRequest(`/instructor/assignments/${assignmentId}/unpublish`, {
    method: "POST",
  });
}

export async function deleteAssignment(assignmentId) {
  return apiRequest(`/instructor/assignments/${assignmentId}`, {
    method: "DELETE",
  });
}

export async function listSubmissions(
  courseId,
  {
    assignmentId,
    status = "SUBMITTED",
    page = 0,
    size = 20,
    sort = "submittedAt,asc",
  } = {},
) {
  const params = new URLSearchParams();
  if (assignmentId) params.set("assignmentId", String(assignmentId));
  if (status) params.set("status", status);
  params.set("page", String(page));
  params.set("size", String(size));
  if (sort) params.set("sort", sort);
  return apiRequest(
    `/instructor/courses/${courseId}/submissions?${params.toString()}`,
  );
}

export async function getInstructorSubmission(submissionId) {
  return apiRequest(`/instructor/submissions/${submissionId}`);
}

export async function getInstructorDownloadUrl(submissionId, fileId) {
  return apiRequest(
    `/instructor/submissions/${submissionId}/files/${fileId}/download-url`,
  );
}

export async function gradeSubmission(submissionId, data) {
  return apiRequest(`/instructor/submissions/${submissionId}/grade`, {
    method: "PUT",
    body: data,
  });
}

// --- Learner API ---

export async function getCourseAssignments(
  courseId,
  { lessonId, moduleId } = {},
) {
  const params = new URLSearchParams();
  if (lessonId) params.set("lessonId", String(lessonId));
  if (moduleId) params.set("moduleId", String(moduleId));
  const query = params.toString();
  return apiRequest(
    `/learning/courses/${courseId}/assignments${query ? `?${query}` : ""}`,
  );
}

export async function getCrossCourseAssignments({
  filter = "todo",
  page = 0,
  size = 20,
} = {}) {
  const params = new URLSearchParams();
  if (filter) params.set("filter", filter);
  params.set("page", String(page));
  params.set("size", String(size));
  return apiRequest(`/learning/assignments?${params.toString()}`);
}

export async function getLearnerAssignmentDetail(assignmentId) {
  return apiRequest(`/learning/assignments/${assignmentId}`);
}

export async function startAttempt(assignmentId) {
  return apiRequest(`/learning/assignments/${assignmentId}/start`, {
    method: "POST",
  });
}

export async function getAttempt(assignmentId) {
  return apiRequest(`/learning/assignments/${assignmentId}/attempt`);
}

export async function saveDraft(assignmentId, data) {
  return apiRequest(`/learning/assignments/${assignmentId}/draft`, {
    method: "PUT",
    body: data,
  });
}

export async function submitAssignment(assignmentId, data) {
  return apiRequest(`/learning/assignments/${assignmentId}/submit`, {
    method: "POST",
    body: data,
  });
}

export async function getLearnerSubmission(assignmentId) {
  return apiRequest(`/learning/assignments/${assignmentId}/submission`);
}

export async function initiateFileUpload(assignmentId, data) {
  return apiRequest(`/learning/assignments/${assignmentId}/files/uploads`, {
    method: "POST",
    body: data,
  });
}

export async function confirmFileUpload(assignmentId, fileId) {
  return apiRequest(
    `/learning/assignments/${assignmentId}/files/${fileId}/confirm`,
    {
      method: "POST",
    },
  );
}

export async function deleteFile(assignmentId, fileId) {
  return apiRequest(`/learning/assignments/${assignmentId}/files/${fileId}`, {
    method: "DELETE",
  });
}

export async function getLearnerDownloadUrl(assignmentId, fileId) {
  return apiRequest(
    `/learning/assignments/${assignmentId}/files/${fileId}/download-url`,
  );
}
