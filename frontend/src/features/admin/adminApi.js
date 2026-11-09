import { apiRequest } from "../../shared/api/client";

export async function getDashboardSummary() {
  return apiRequest("/admin/dashboard/summary");
}

export async function getActivities({ page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiRequest(`/admin/dashboard/activities?${params.toString()}`);
}

export async function getAdminReports({
  status,
  query,
  page = 0,
  size = 20,
} = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (status && status !== "ALL") {
    params.set("status", status);
  }
  if (query && query.trim()) {
    params.set("query", query.trim());
  }
  return apiRequest(`/admin/reports?${params.toString()}`);
}

export async function updateAdminReportStatus(
  reportId,
  { status, resolutionNotes },
) {
  return apiRequest(`/admin/reports/${reportId}`, {
    method: "PATCH",
    body: { status, resolutionNotes },
  });
}

export async function getAdminUsers({
  role,
  mode,
  enabled,
  query,
  page = 0,
  size = 20,
} = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (role && role !== "ALL") {
    params.set("role", role);
  }
  if (mode && mode !== "ALL") {
    params.set("mode", mode);
  }
  if (enabled !== undefined && enabled !== null && enabled !== "ALL") {
    params.set("enabled", String(enabled));
  }
  if (query && query.trim()) {
    params.set("query", query.trim());
  }
  return apiRequest(`/admin/users?${params.toString()}`);
}

export async function updateAdminUserStatus(userId, { enabled, reason }) {
  return apiRequest(`/admin/users/${userId}/status`, {
    method: "PATCH",
    body: { enabled, reason },
  });
}

export async function getAdminCourses({
  status,
  query,
  page = 0,
  size = 20,
} = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (status && status !== "ALL") {
    params.set("status", status);
  }
  if (query && query.trim()) {
    params.set("query", query.trim());
  }
  return apiRequest(`/admin/courses?${params.toString()}`);
}

export async function getAdminCourseById(courseId) {
  return apiRequest(`/admin/courses/${courseId}`);
}

export async function updateAdminCourseStatus(
  courseId,
  { status, reason, reportId },
) {
  return apiRequest(`/admin/courses/${courseId}/status`, {
    method: "PATCH",
    body: { status, reason, reportId },
  });
}
