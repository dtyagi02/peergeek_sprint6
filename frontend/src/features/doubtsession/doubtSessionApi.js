import { apiRequest } from "../../shared/api/client";

/**
 * Public endpoint to fetch instructor availability and hourly rate for doubt sessions on a course.
 */
export async function getCourseAvailability(courseId) {
  return apiRequest(`public/courses/${courseId}/doubt-session-availability`);
}

/**
 * Student requests a live doubt clearing session.
 * @param {number|string} courseId
 * @param {number} durationMinutes (5, 10, 15, or 30)
 */
export async function requestSession(courseId, durationMinutes) {
  return apiRequest("doubt-sessions/request", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ courseId: Number(courseId), durationMinutes: Number(durationMinutes) }),
  });
}

/**
 * Fetches the current state of a doubt session.
 */
export async function getSession(sessionId) {
  return apiRequest(`doubt-sessions/${sessionId}`);
}

/**
 * Fetches LiveKit token for the session.
 */
export async function getSessionToken(sessionId) {
  return apiRequest(`doubt-sessions/${sessionId}/token`);
}

/**
 * Student cancels a requested session before acceptance.
 */
export async function cancelSession(sessionId) {
  return apiRequest(`doubt-sessions/${sessionId}/cancel`, {
    method: "POST",
  });
}

/**
 * Participant ends the session.
 */
export async function endSession(sessionId) {
  return apiRequest(`doubt-sessions/${sessionId}/end`, {
    method: "POST",
  });
}

/**
 * Fetches student's current active session, if any.
 */
export async function getActiveStudentSession() {
  return apiRequest("doubt-sessions/active");
}

/**
 * Instructor: Fetches doubt session settings for a course.
 */
export async function getInstructorCourseSettings(courseId) {
  return apiRequest(`instructor/courses/${courseId}/doubt-session-settings`);
}

/**
 * Instructor: Updates doubt session settings for a course.
 * @param {number|string} courseId
 * @param {{ enabled?: boolean, available?: boolean, hourlyRatePgc?: number }} data
 */
export async function updateInstructorCourseSettings(courseId, data) {
  return apiRequest(`instructor/courses/${courseId}/doubt-session-settings`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
}

/**
 * Instructor: Fetches current active or incoming requested session.
 */
export async function getActiveInstructorSession() {
  return apiRequest("instructor/doubt-sessions/active");
}

/**
 * Instructor: Accepts an incoming doubt session request.
 */
export async function acceptSession(sessionId) {
  return apiRequest(`instructor/doubt-sessions/${sessionId}/accept`, {
    method: "POST",
  });
}

/**
 * Instructor: Rejects an incoming doubt session request.
 */
export async function rejectSession(sessionId) {
  return apiRequest(`instructor/doubt-sessions/${sessionId}/reject`, {
    method: "POST",
  });
}
