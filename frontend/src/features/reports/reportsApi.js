import { apiRequest } from "../../shared/api/client";

export async function submitReport(courseId, { category, details }) {
  return apiRequest(`/courses/${courseId}/reports`, {
    method: "POST",
    body: { category, details },
  });
}

export async function getInstructorCourseReports(
  courseId,
  { status = "", page = 0, size = 20 } = {},
) {
  const searchParams = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (status) searchParams.set("status", status);

  return apiRequest(
    `/instructor/courses/${courseId}/reports?${searchParams.toString()}`,
  );
}

export async function updateReportStatus(
  reportId,
  { status, resolutionNotes = "" },
) {
  return apiRequest(`/instructor/reports/${reportId}/status`, {
    method: "PUT",
    body: { status, resolutionNotes },
  });
}
