import { apiRequest } from "../../shared/api/client";

export function listPosts({ page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiRequest(`community/posts?${params}`);
}

export function getPost(postId) {
  return apiRequest(`community/posts/${postId}`);
}

export function createPost({ content }) {
  return apiRequest("community/posts", {
    method: "POST",
    body: { content },
  });
}

export function deletePost(postId) {
  return apiRequest(`community/posts/${postId}`, {
    method: "DELETE",
  });
}

export function listComments(postId) {
  return apiRequest(`community/posts/${postId}/comments`);
}

export function createComment(postId, { content, parentCommentId = null }) {
  return apiRequest(`community/posts/${postId}/comments`, {
    method: "POST",
    body: { content, parentCommentId },
  });
}

export function deleteComment(commentId) {
  return apiRequest(`community/comments/${commentId}`, {
    method: "DELETE",
  });
}

export function getMentionSuggestions(query) {
  const safeQuery = encodeURIComponent(query?.trim() || "");
  return apiRequest(`community/users/mention-suggestions?q=${safeQuery}`);
}

export function getDomainCommunity(slug) {
  return apiRequest(`domains/${slug}/community`);
}

export function listDomainCommunities() {
  return apiRequest("domains/communities");
}

export function joinDomainCommunity(slug) {
  return apiRequest(`domains/${slug}/community/join`, {
    method: "POST",
  });
}

export function leaveDomainCommunity(slug) {
  return apiRequest(`domains/${slug}/community/leave`, {
    method: "POST",
  });
}

export function getDomainMembers(slug, { page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiRequest(`domains/${slug}/community/members?${params}`);
}

export function getDomainMessages(slug, { beforeId, afterId, limit = 50 } = {}) {
  const params = new URLSearchParams({ limit: String(limit) });
  if (beforeId) params.set("beforeId", String(beforeId));
  if (afterId) params.set("afterId", String(afterId));
  return apiRequest(`domains/${slug}/community/messages?${params}`);
}

export function sendDomainMessage(slug, { content }) {
  return apiRequest(`domains/${slug}/community/messages`, {
    method: "POST",
    body: { content },
  });
}

export function deleteDomainMessage(slug, messageId) {
  return apiRequest(`domains/${slug}/community/messages/${messageId}`, {
    method: "DELETE",
  });
}

/**
 * Global PeerGeek Community APIs
 */
export function getGlobalCommunityMessages({ beforeId, afterId, limit = 50 } = {}) {
  const params = new URLSearchParams({ limit: String(limit) });
  if (beforeId) params.set("beforeId", String(beforeId));
  if (afterId) params.set("afterId", String(afterId));
  return apiRequest(`community/global/messages?${params}`);
}

export function sendGlobalCommunityMessage({ content }) {
  return apiRequest("community/global/messages", {
    method: "POST",
    body: { content },
  });
}
export const createGlobalCommunityMessage = sendGlobalCommunityMessage;

export function deleteGlobalCommunityMessage(messageId, { reason } = {}) {
  return apiRequest(`community/global/messages/${messageId}`, {
    method: "DELETE",
    body: reason ? { reason } : undefined,
  });
}

export function getCommunityBlockStatus() {
  return apiRequest("community/global/status");
}
export const getGlobalCommunityStatus = getCommunityBlockStatus;

/**
 * Admin Global Community Moderation APIs
 */
export function getAdminCommunityMessages({ page = 0, size = 20, includeDeleted = true, userId, query } = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
    includeDeleted: String(includeDeleted),
  });
  if (userId) params.set("userId", String(userId));
  if (query) params.set("query", query);
  return apiRequest(`admin/community/messages?${params}`);
}

export function adminDeleteCommunityMessage(messageId, { reason } = {}) {
  return apiRequest(`admin/community/messages/${messageId}`, {
    method: "DELETE",
    body: reason ? { reason } : undefined,
  });
}

export function adminBlockCommunityUser(userId, { reason } = {}) {
  return apiRequest(`admin/community/users/${userId}/block`, {
    method: "POST",
    body: reason ? { reason } : undefined,
  });
}

export function adminUnblockCommunityUser(userId, { reason } = {}) {
  return apiRequest(`admin/community/users/${userId}/unblock`, {
    method: "POST",
    body: reason ? { reason } : undefined,
  });
}

export function getAdminBlockedCommunityUsers() {
  return apiRequest("admin/community/blocked-users");
}

