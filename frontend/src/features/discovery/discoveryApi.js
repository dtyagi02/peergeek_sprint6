import { apiRequest } from "../../shared/api/client";

export async function listPublishedCourses({
  q,
  category,
  difficulty,
  page = 0,
  size = 12,
  sort,
} = {}) {
  const params = new URLSearchParams();

  if (q && q.trim()) {
    params.set("q", q.trim());
  }

  if (category && category.trim()) {
    params.set("category", category.trim());
  }

  if (Array.isArray(difficulty)) {
    difficulty.forEach((diff) => {
      if (diff) params.append("difficulty", diff);
    });
  } else if (difficulty && typeof difficulty === "string") {
    difficulty.split(",").forEach((diff) => {
      const trimmed = diff.trim();
      if (trimmed) params.append("difficulty", trimmed);
    });
  }

  if (page !== undefined && page !== null) {
    params.set("page", String(page));
  }

  if (size !== undefined && size !== null) {
    params.set("size", String(size));
  }

  if (sort && sort.trim()) {
    params.set("sort", sort.trim());
  }

  const query = params.toString();
  return apiRequest(query ? `public/courses?${query}` : "public/courses");
}

export async function getPopularCourses() {
  return apiRequest("public/courses/popular");
}

export async function getPublicCategories() {
  return apiRequest("public/categories");
}

export async function getPublicCoursePreview(courseId) {
  return apiRequest(`public/courses/${courseId}`);
}
