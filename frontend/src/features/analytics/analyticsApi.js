import { apiRequest } from "../../shared/api/client";

/**
 * Fetches instructor course analytics for the specified course.
 * @param {string|number} courseId - The course identifier
 * @returns {Promise<Object>} The aggregated analytics payload
 */
export async function getCourseAnalytics(courseId) {
  return apiRequest(`/instructor/courses/${courseId}/analytics`);
}
