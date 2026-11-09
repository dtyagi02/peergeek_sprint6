import { apiRequest } from "../../shared/api/client";

export async function getNotifications({
  page = 0,
  size = 20,
  unreadOnly,
} = {}) {
  const params = new URLSearchParams();
  if (page !== undefined && page !== null) params.set("page", String(page));
  if (size !== undefined && size !== null) params.set("size", String(size));
  if (unreadOnly) params.set("unreadOnly", "true");

  const query = params.toString();
  return apiRequest(`notifications${query ? `?${query}` : ""}`);
}

export async function getUnreadCount() {
  return apiRequest("notifications/unread-count");
}

export async function markAsRead(notificationId) {
  return apiRequest(`notifications/${notificationId}/read`, {
    method: "PATCH",
  });
}

export async function markAllAsRead() {
  return apiRequest("notifications/mark-all-read", {
    method: "POST",
  });
}
