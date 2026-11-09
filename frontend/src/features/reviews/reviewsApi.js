import { apiRequest } from "../../shared/api/client";

export async function getCourseReviewSummary(courseId) {
  return apiRequest(`/public/courses/${courseId}/reviews/summary`);
}

export async function getCourseReviews(
  courseId,
  { page = 0, size = 10, sort = "recent", rating = null, search = "" } = {},
) {
  const searchParams = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  if (sort) {
    searchParams.set("sort", sort);
  }
  if (rating !== null && rating !== undefined && rating !== "") {
    searchParams.set("rating", String(rating));
  }
  if (search && search.trim()) {
    searchParams.set("search", search.trim());
  }

  return apiRequest(
    `/public/courses/${courseId}/reviews?${searchParams.toString()}`,
  );
}

export async function getMyReview(courseId) {
  try {
    return await apiRequest(`/courses/${courseId}/reviews/my`);
  } catch (err) {
    if (err.status === 204) return null;
    throw err;
  }
}

export async function submitReview(courseId, { rating, reviewText }) {
  return apiRequest(`/courses/${courseId}/reviews`, {
    method: "POST",
    body: { rating, reviewText },
  });
}

export async function deleteReview(courseId) {
  return apiRequest(`/courses/${courseId}/reviews`, {
    method: "DELETE",
  });
}
