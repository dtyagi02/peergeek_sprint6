import { apiRequest } from "../../shared/api/client";

export function getConversations() {
  return apiRequest("direct-messages/conversations");
}

export function getOrCreateConversation(participantId) {
  return apiRequest(`direct-messages/conversations/${participantId}`, {
    method: "POST",
  });
}

export function getConversationMessages(conversationId, params = {}) {
  const search = new URLSearchParams();
  if (params.page != null) search.set("page", params.page);
  if (params.limit) search.set("size", params.limit);
  const suffix = search.toString() ? `?${search}` : "";
  return apiRequest(`direct-messages/conversations/${conversationId}/messages${suffix}`);
}

export function sendDirectMessage(conversationId, content) {
  return apiRequest(`direct-messages/conversations/${conversationId}/messages`, {
    method: "POST",
    body: { content },
  });
}

export function markConversationRead(conversationId) {
  return apiRequest(`direct-messages/conversations/${conversationId}/read`, {
    method: "POST",
  });
}
