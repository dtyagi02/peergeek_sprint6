import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "../auth/authStore";
import { UserAvatar } from "./UserAvatar";
import { MentionRenderer } from "./MentionRenderer";
import { MentionTextarea } from "./MentionTextarea";
import { formatRelativeTime } from "./formatRelativeTime";
import {
  getDomainCommunity,
  getDomainMembers,
  getDomainMessages,
  joinDomainCommunity,
  leaveDomainCommunity,
  sendDomainMessage,
  deleteDomainMessage,
} from "./communityApi";
import { useCommunityStompSubscription } from "./stompClient";

export function DomainChatPage() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const messagesEndRef = useRef(null);

  const authStatus = useAuthStore((s) => s.status);
  const currentUser = useAuthStore((s) => s.user);
  const isAuthenticated = authStatus === "authenticated";

  const [messageText, setMessageText] = useState("");
  const [isMembersDrawerOpen, setIsMembersDrawerOpen] = useState(false);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const hasScrolledInitially = useRef(false);

  // Query domain community info
  const communityQuery = useQuery({
    queryKey: ["domain", slug, "community"],
    queryFn: () => getDomainCommunity(slug),
    staleTime: 10 * 1000,
  });

  const community = communityQuery.data;

  // Query messages via REST for initial fetch & history
  const messagesQuery = useQuery({
    queryKey: ["domain", slug, "messages"],
    queryFn: () => getDomainMessages(slug, { limit: 100 }),
  });

  const messages = useMemo(() => messagesQuery.data || [], [messagesQuery.data]);

  // STOMP real-time subscription for domain messages
  useCommunityStompSubscription({
    destination: `/topic/community.domain.${slug}`,
    onMessage: (event) => {
      if (!event) return;
      const type = event.type || "MESSAGE_CREATED";
      const payload = event.payload || event;

      if (type === "MESSAGE_CREATED") {
        const newMsg = payload;
        if (!newMsg || !newMsg.id) return;
        queryClient.setQueryData(["domain", slug, "messages"], (prev = []) => {
          if (prev.some((m) => m.id === newMsg.id)) {
            return prev;
          }
          return [...prev, newMsg].sort((a, b) => a.id - b.id);
        });
        setTimeout(() => scrollToBottom(), 50);
      } else if (type === "MESSAGE_DELETED") {
        const deletedId = payload?.messageId;
        if (deletedId) {
          queryClient.setQueryData(["domain", slug, "messages"], (prev = []) =>
            prev.filter((m) => m.id !== deletedId)
          );
        }
      }
    },
    onReconnect: async () => {
      // Missed-message recovery on reconnect
      const currentMessages = queryClient.getQueryData(["domain", slug, "messages"]) || [];
      if (currentMessages.length > 0) {
        const maxId = Math.max(...currentMessages.map((m) => m.id));
        try {
          const newer = await getDomainMessages(slug, { afterId: maxId });
          if (newer && newer.length > 0) {
            queryClient.setQueryData(["domain", slug, "messages"], (prev = []) => {
              const map = new Map(prev.map((m) => [m.id, m]));
              newer.forEach((m) => map.set(m.id, m));
              return Array.from(map.values()).sort((a, b) => a.id - b.id);
            });
          }
        } catch {
          queryClient.invalidateQueries({ queryKey: ["domain", slug, "messages"] });
        }
      }
    },
    enabled: Boolean(slug && isAuthenticated),
  });

  // Query domain members
  const membersQuery = useQuery({
    queryKey: ["domain", slug, "members"],
    queryFn: () => getDomainMembers(slug, { page: 0, size: 50 }),
    enabled: isMembersDrawerOpen,
  });

  const members = membersQuery.data?.content || [];

  // Join mutation
  const joinMutation = useMutation({
    mutationFn: () => joinDomainCommunity(slug),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["domain", slug] });
    },
    onError: (err) => {
      setErrorMessage(err.message || "Failed to join community");
    },
  });

  // Leave mutation
  const leaveMutation = useMutation({
    mutationFn: () => leaveDomainCommunity(slug),
    onSuccess: () => {
      setConfirmLeave(false);
      queryClient.invalidateQueries({ queryKey: ["domain", slug] });
    },
    onError: (err) => {
      setErrorMessage(err.message || "Failed to leave community");
    },
  });

  // Send message mutation
  const sendMutation = useMutation({
    mutationFn: ({ content }) => sendDomainMessage(slug, { content }),
    onSuccess: (newMsg) => {
      setMessageText("");
      setErrorMessage("");
      if (newMsg && newMsg.id) {
        queryClient.setQueryData(["domain", slug, "messages"], (prev = []) => {
          if (prev.some((m) => m.id === newMsg.id)) return prev;
          return [...prev, newMsg].sort((a, b) => a.id - b.id);
        });
      }
      scrollToBottom();
    },
    onError: (err) => {
      setErrorMessage(err.message || "Failed to send message");
    },
  });

  // Delete message mutation
  const deleteMutation = useMutation({
    mutationFn: (messageId) => deleteDomainMessage(slug, messageId),
    onSuccess: (_, messageId) => {
      queryClient.setQueryData(["domain", slug, "messages"], (prev = []) =>
        prev.filter((m) => m.id !== messageId)
      );
    },
    onError: (err) => {
      setErrorMessage(err.message || "Failed to delete message");
    },
  });

  function scrollToBottom() {
    if (messagesEndRef.current && typeof messagesEndRef.current.scrollIntoView === "function") {
      messagesEndRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }

  // Scroll to bottom on initial message load
  useEffect(() => {
    if (messages.length > 0 && !hasScrolledInitially.current) {
      if (messagesEndRef.current && typeof messagesEndRef.current.scrollIntoView === "function") {
        messagesEndRef.current.scrollIntoView({ behavior: "auto" });
      }
      hasScrolledInitially.current = true;
    }
  }, [messages]);

  async function handleSendMessage(e) {
    if (e) e.preventDefault();
    const trimmed = messageText.trim();
    if (!trimmed || sendMutation.isPending) return;

    sendMutation.mutate({ content: trimmed });
  }

  if (communityQuery.isLoading) {
    return (
      <main className="flex h-[calc(100vh-64px)] items-center justify-center bg-neutral-50">
        <div className="flex flex-col items-center gap-3">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-[#06110C] border-t-transparent" />
          <p className="text-sm text-neutral-500">Loading domain community...</p>
        </div>
      </main>
    );
  }

  if (communityQuery.isError || !community) {
    return (
      <main className="flex h-[calc(100vh-64px)] items-center justify-center bg-neutral-50 p-4">
        <div className="max-w-md rounded-xl border border-neutral-200 bg-white p-6 text-center shadow-sm">
          <h2 className="text-lg font-bold text-[#171717]">Domain Community Not Found</h2>
          <p className="mt-2 text-sm text-neutral-500">
            The requested learning domain community does not exist.
          </p>
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="mt-4 rounded-lg bg-[#06110C] px-4 py-2 text-sm font-semibold text-white shadow-xs hover:bg-[#171717]"
          >
            ← Go Back
          </button>
        </div>
      </main>
    );
  }

  const isMember = community.isMember;

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[#f8f9fb]">
      {/* Community Header */}
      <header className="z-10 flex shrink-0 items-center justify-between border-b border-neutral-200 bg-white px-4 py-3 sm:px-6">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            onClick={() => navigate(-1)}
            aria-label="Return to previous page"
            className="rounded-lg p-1.5 text-neutral-500 hover:bg-neutral-100 hover:text-[#171717] focus:outline-none"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
          </button>

          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h1 className="text-base font-bold text-[#171717] truncate sm:text-lg">
                {community.name}
              </h1>
            </div>
            {community.description && (
              <p className="text-xs text-neutral-500 truncate max-w-md sm:max-w-lg hidden sm:block">
                {community.description}
              </p>
            )}
          </div>
        </div>

        {/* Header Right Actions */}
        <div className="flex items-center gap-2 sm:gap-3 shrink-0">
          {/* Member count & Drawer toggle */}
          <button
            type="button"
            onClick={() => setIsMembersDrawerOpen(true)}
            aria-label={`View members (${community.memberCount})`}
            className="inline-flex items-center gap-1.5 rounded-full border border-neutral-200 bg-white px-3 py-1 text-xs font-semibold text-neutral-700 hover:bg-neutral-50 transition-colors"
          >
            <svg className="h-3.5 w-3.5 text-neutral-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
            <span>{community.memberCount} {community.memberCount === 1 ? "member" : "members"}</span>
          </button>

          {/* Membership action */}
          {isAuthenticated && isMember && (
            <div>
              {confirmLeave ? (
                <div className="flex items-center gap-1.5 text-xs text-[#A31C1B]">
                  <span>Leave?</span>
                  <button
                    type="button"
                    disabled={leaveMutation.isPending}
                    onClick={() => leaveMutation.mutate()}
                    className="font-bold underline hover:text-[#801615]"
                  >
                    Yes
                  </button>
                  <button
                    type="button"
                    disabled={leaveMutation.isPending}
                    onClick={() => setConfirmLeave(false)}
                    className="text-neutral-500 hover:underline"
                  >
                    No
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setConfirmLeave(true)}
                  className="rounded-lg border border-neutral-200 px-2.5 py-1 text-xs font-medium text-neutral-600 hover:bg-neutral-50 hover:text-[#A31C1B] transition-colors"
                >
                  Leave
                </button>
              )}
            </div>
          )}
        </div>
      </header>

      {/* Main Conversation Stream */}
      <main
        aria-label={`${community.name} messages`}
        className="min-h-0 flex-1 overflow-y-auto bg-[#f8f9fb] px-4 py-5 sm:px-8"
      >
        {/* Empty state */}
        {messages.length === 0 && (
          <div className="flex h-full flex-col items-center justify-center py-12 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-neutral-200/60 text-[#06110C]">
              <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
              </svg>
            </div>
            <h2 className="mt-3 text-base font-semibold text-[#171717]">
              No messages in this domain community yet
            </h2>
            <p className="mt-1 text-sm text-neutral-500 max-w-sm">
              Be the first to share an idea, ask a question, or introduce yourself to fellow {community.name} learners!
            </p>
          </div>
        )}

        {/* Chronological message list */}
        {messages.map((msg) => {
          // System join message
          if (msg.messageType === "SYSTEM_JOIN") {
            return (
              <div key={msg.id} className="flex items-center justify-center my-3">
                <div className="inline-flex items-center gap-2 px-3.5 py-1 text-xs text-neutral-500">
                  <span>👋</span>
                  <span className="font-medium text-[#171717]">{msg.content}</span>
                  <span className="text-neutral-400">· {formatRelativeTime(msg.createdAt)}</span>
                </div>
              </div>
            );
          }

          // User chat message
          const isSender = msg.isSender;

          return (
            <div
              key={msg.id}
              className={`mb-4 flex items-start gap-3 ${isSender ? "flex-row-reverse" : "flex-row"}`}
            >
              <Link
                to={msg.sender ? `/profile/${msg.sender.username}` : "#"}
                className="shrink-0 focus:outline-none"
              >
                <UserAvatar author={msg.sender} size="sm" />
              </Link>

              <div
                className={`max-w-[85%] sm:max-w-[75%] text-[#171717] ${isSender ? "text-right" : "text-left"}`}
              >
                {/* Message author & timestamp */}
                <div
                  className={`flex items-baseline gap-1.5 text-xs mb-1 ${
                    isSender ? "text-neutral-500 justify-end" : "text-neutral-500"
                  }`}
                >
                  <Link
                    to={msg.sender ? `/profile/${msg.sender.username}` : "#"}
                    className={`font-semibold hover:underline truncate ${
                      "text-[#171717]"
                    }`}
                  >
                    {msg.sender?.displayName || msg.sender?.username || "Learner"}
                  </Link>
                  {msg.sender?.username && (
                    <span className="text-[#A31C1B]">
                      @{msg.sender.username}
                    </span>
                  )}
                  <span>·</span>
                  <span className="text-[11px] opacity-75">
                    {formatRelativeTime(msg.createdAt)}
                  </span>
                  {(currentUser?.role === "ADMIN" || isSender) && (
                    <>
                      <span>·</span>
                      <button
                        type="button"
                        onClick={() => {
                          if (window.confirm("Are you sure you want to delete this message?")) {
                            deleteMutation.mutate(msg.id);
                          }
                        }}
                        className={`text-[11px] cursor-pointer hover:underline ${
                          "text-neutral-400 hover:text-red-600"
                        }`}
                        title="Delete message"
                      >
                        Delete
                      </button>
                    </>
                  )}
                </div>

                {/* Message content */}
                <div className="text-sm leading-relaxed break-words">
                  <MentionRenderer content={msg.content} />
                </div>
              </div>
            </div>
          );
        })}

        <div ref={messagesEndRef} />
      </main>

      {/* Message Composer or Join Prompt (Fixed bottom) */}
      <footer className="shrink-0 border-t border-neutral-200 bg-white p-3 sm:p-4">
        {errorMessage && (
          <p className="mb-2 text-xs text-[#A31C1B] font-medium">{errorMessage}</p>
        )}

        {!isAuthenticated ? (
          <div className="rounded-xl border border-neutral-200 bg-neutral-50/80 p-4 text-center">
            <p className="text-sm text-neutral-600">
              Sign in to join the <span className="font-semibold text-[#171717]">{community.name}</span> community and start chatting.
            </p>
            <div className="mt-3 flex items-center justify-center gap-3">
              <Link
                to="/login"
                className="rounded-lg bg-[#06110C] px-4 py-1.5 text-xs font-semibold text-white shadow-xs hover:bg-[#171717]"
              >
                Log in
              </Link>
              <Link
                to="/register"
                className="rounded-lg border border-neutral-300 bg-white px-4 py-1.5 text-xs font-semibold text-[#171717] hover:bg-neutral-50"
              >
                Sign up
              </Link>
            </div>
          </div>
        ) : !isMember ? (
          <div className="rounded-xl border border-neutral-200 bg-neutral-50/80 p-4 text-center">
            <h2 className="text-sm font-bold text-[#171717]">
              Join {community.name} Community
            </h2>
            <p className="mt-1 text-xs text-neutral-500 max-w-md mx-auto">
              Join this domain group chat to discuss topics, ask questions, and collaborate with fellow learners.
            </p>
            <button
              type="button"
              disabled={joinMutation.isPending}
              onClick={() => joinMutation.mutate()}
              className="mt-3 inline-flex items-center rounded-lg bg-[#06110C] px-5 py-2 text-xs font-semibold text-white shadow-xs hover:bg-[#171717] disabled:opacity-50 transition-colors"
            >
              {joinMutation.isPending ? "Joining..." : "Join Community"}
            </button>
          </div>
        ) : (
          <div className="flex items-end gap-2 rounded-[1.5rem] border border-neutral-300 bg-[#171719] px-3 py-1.5 shadow-[0_8px_24px_rgba(15,15,18,0.12)] focus-within:border-[#a31c1b]">
            <button type="button" onClick={() => document.getElementById("domain-chat-composer")?.focus()} className="h-8 w-8 shrink-0 text-2xl font-light text-neutral-300" aria-label="Focus message composer">+</button>
            <div className="flex-1 min-w-0">
              <MentionTextarea
                id="domain-chat-composer"
                value={messageText}
                onChange={setMessageText}
                placeholder={`Write a message... · Message #${community.name}`}
                maxLength={2000}
                minRows={1}
                onSubmitShortcut={handleSendMessage}
                showCharCount={false}
                submitOnEnter
                textareaClassName="max-h-40 min-h-10 resize-none overflow-y-auto border-0 bg-transparent px-1 py-1 text-white placeholder-neutral-400 outline-none focus:border-0 focus:ring-0"
              />
            </div>
            <button
              type="button"
              disabled={!messageText.trim() || sendMutation.isPending}
              onClick={handleSendMessage}
              aria-label="Send message"
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[#555] text-white transition-colors hover:bg-[#444] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {sendMutation.isPending ? (
                <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
              ) : (
                <svg className="h-4 w-4 rotate-90" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                </svg>
              )}
            </button>
          </div>
        )}
      </footer>

      {/* Member List Drawer / Sidebar */}
      {isMembersDrawerOpen && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/40 backdrop-blur-xs animate-in fade-in">
          <div className="flex h-full w-80 max-w-[85vw] flex-col bg-white p-5 shadow-2xl animate-in slide-in-from-right duration-200">
            <div className="flex items-center justify-between border-b border-neutral-200 pb-3">
              <div>
                <h3 className="font-bold text-[#171717]">Domain Members</h3>
                <p className="text-xs text-neutral-500">{community.memberCount} total</p>
              </div>
              <button
                type="button"
                onClick={() => setIsMembersDrawerOpen(false)}
                className="rounded p-1 text-neutral-400 hover:text-neutral-700"
              >
                ✕
              </button>
            </div>

            <div className="flex-1 overflow-y-auto py-3 space-y-3">
              {membersQuery.isLoading ? (
                <div className="py-6 text-center text-xs text-neutral-400">Loading members...</div>
              ) : members.length === 0 ? (
                <div className="py-6 text-center text-xs text-neutral-400">No members yet.</div>
              ) : (
                members.map((member) => (
                  <Link
                    key={member.id}
                    to={`/profile/${member.username}`}
                    className="flex items-center gap-3 rounded-lg p-2 hover:bg-neutral-50 transition-colors"
                  >
                    <UserAvatar author={member} size="sm" />
                    <div className="min-w-0 flex-1">
                      <div className="text-sm font-medium text-[#171717] truncate">
                        {member.displayName}
                      </div>
                      <div className="text-xs text-neutral-500 truncate">
                        @{member.username}
                      </div>
                    </div>
                  </Link>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
