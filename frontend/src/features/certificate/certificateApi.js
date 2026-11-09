import { apiRequest } from "../../shared/api/client";

export function getMyCertificates() {
  return apiRequest("certificates");
}

export function reconcileCertificates() {
  return apiRequest("certificates/reconcile", { method: "POST" });
}

export function retryCertificate(id) {
  return apiRequest(`certificates/${id}/retry`, { method: "POST" });
}

export function getCertificateDownloadUrl(id) {
  return apiRequest(`certificates/${id}/download-url`);
}

export function verifyCertificate(code) {
  return apiRequest(`certificates/verify/${encodeURIComponent(code)}`, { skipAuthRecovery: true });
}

export function getAdminCertificates({ status, search, courseId, userId, page = 0, size = 20 } = {}) {
  const query = new URLSearchParams();
  if (status && status !== "ALL") query.set("status", status);
  if (search) query.set("search", search);
  if (courseId) query.set("courseId", courseId);
  if (userId) query.set("userId", userId);
  query.set("page", String(page));
  query.set("size", String(size));

  return apiRequest(`admin/certificates?${query.toString()}`);
}

export function revokeAdminCertificate(id, reason) {
  return apiRequest(`admin/certificates/${id}/revoke`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

export function retryAdminCertificate(id) {
  return apiRequest(`admin/certificates/${id}/retry`, { method: "POST" });
}

export function reconcileAdminCertificates({ courseId, userId } = {}) {
  const query = new URLSearchParams();
  if (courseId) query.set("courseId", courseId);
  if (userId) query.set("userId", userId);
  return apiRequest(`admin/certificates/reconcile?${query.toString()}`, { method: "POST" });
}
