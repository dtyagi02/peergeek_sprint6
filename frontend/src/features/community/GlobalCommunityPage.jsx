import { useEffect, useMemo, useRef, useState, useCallback } from "react";
import { Link } from "react-router-dom";
import { useAuthStore } from "../auth/authStore";
import { UserAvatar } from "./UserAvatar";
import { formatRelativeTime } from "./formatRelativeTime";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import {
  getGlobalCommunityMessages,
  sendGlobalCommunityMessage,
  createGlobalCommunityMessage,
  deleteGlobalCommunityMessage,
  getCommunityBlockStatus,
  getGlobalCommunityStatus,
  adminBlockCommunityUser,
} from "./communityApi";
import { useCommunityStompSubscription } from "./stompClient";
import { ReferralModal } from "../referral/ReferralModal";

const MAX_CONTENT_LENGTH = 2000;

export function GlobalCommunityPage() {
  const currentUser = useAuthStore((s) => s.user);
  const isAdmin = currentUser?.role === "ADMIN";

  const [messages, setMessages] = useState([]);
  const [initialLoading, setInitialLoading] = useState(true);
  const [initialError, setInitialError] = useState(null);

  const [hasMoreOlder, setHasMoreOlder] = useState(true);
  const [loadingOlder, setLoadingOlder] = useState(false);

  const [content, setContent] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState(null);

  const [blockStatus, setBlockStatus] = useState({ blocked: false, reason: null });
  const [referralOpen, setReferralOpen] = useState(false);

  // Modals state
  const [deleteModalMsg, setDeleteModalMsg] = useState(null);
  const [deleteReason, setDeleteReason] = useState("");
  const [deleting, setDeleting] = useState(false);

  const [blockModalUser, setBlockModalUser] = useState(null);
  const [blockReason, setBlockReason] = useState("");
  const [blocking, setBlocking] = useState(false);

  const messagesEndRef = useRef(null);
  const isRecoveringRef = useRef(false);
  const messagesContainerRef = useRef(null);
  const composerInputRef = useRef(null);
  const [hasUnreadMessages, setHasUnreadMessages] = useState(false);
  const today = useMemo(() => new Date(), []);
  const yesterday = useMemo(() => new Date(today.getTime() - 86400000), [today]);

  const scrollToBottom = useCallback((smooth = true) => {
    if (messagesEndRef.current?.scrollIntoView) {
      messagesEndRef.current.scrollIntoView({ behavior: smooth ? "smooth" : "auto" });
    }
    setHasUnreadMessages(false);
  }, []);

  const isNearBottom = useCallback(() => {
    const element = messagesContainerRef.current;
    return !element || element.scrollHeight - element.scrollTop - element.clientHeight < 160;
  }, []);

  // Keep latest message id in ref for polling
  const maxMessageIdRef = useRef(null);
  useEffect(() => {
    if (messages.length > 0) {
      const maxId = Math.max(...messages.map((m) => m.id));
      maxMessageIdRef.current = maxId;
    } else {
      maxMessageIdRef.current = null;
    }
  }, [messages]);

  // Check user block status
  const checkBlockStatus = useCallback(async () => {
    try {
      const fetchStatus = getCommunityBlockStatus || getGlobalCommunityStatus;
      const status = await fetchStatus();
      setBlockStatus(status || { blocked: false, reason: null });
    } catch {
      // Non-blocking fallback
    }
  }, []);

  useEffect(() => {
    Promise.resolve().then(checkBlockStatus);
  }, [checkBlockStatus]);

  // Initial load
  const loadInitialMessages = useCallback(async () => {
    setInitialLoading(true);
    setInitialError(null);
    try {
      const res = await getGlobalCommunityMessages({ limit: 50 });
      const initial = Array.isArray(res) ? res : (res?.messages || []);
      setMessages(initial);
      if (typeof res?.hasMore === "boolean") {
        setHasMoreOlder(res.hasMore);
      } else if (initial.length < 50) {
        setHasMoreOlder(false);
      } else {
        setHasMoreOlder(true);
      }
      setTimeout(() => scrollToBottom(false), 100);
    } catch (err) {
      setInitialError(err?.message || "Failed to load community messages.");
    } finally {
      setInitialLoading(false);
    }
  }, [scrollToBottom]);

  useEffect(() => {
    Promise.resolve().then(loadInitialMessages);
  }, [loadInitialMessages]);

  // Load older messages (backwards pagination)
  const handleLoadOlder = async () => {
    if (messages.length === 0 || loadingOlder) return;
    const minId = Math.min(...messages.map((m) => m.id));
    setLoadingOlder(true);
    try {
      const res = await getGlobalCommunityMessages({ beforeId: minId, limit: 30 });
      const older = Array.isArray(res) ? res : (res?.messages || []);
      if (!older || older.length === 0) {
        setHasMoreOlder(false);
      } else {
        if (typeof res?.hasMore === "boolean") {
          setHasMoreOlder(res.hasMore);
        } else if (older.length < 30) {
          setHasMoreOlder(false);
        }
        setMessages((prev) => {
          const map = new Map();
          older.forEach((m) => map.set(m.id, m));
          prev.forEach((m) => map.set(m.id, m));
          return Array.from(map.values()).sort((a, b) => a.id - b.id);
        });
      }
    } catch {
      // Silent error for older messages
    } finally {
      setLoadingOlder(false);
    }
  };

  // Missed-messages recovery function for reconnection and tab visibility
  const recoverMissedMessages = useCallback(async () => {
    const afterId = maxMessageIdRef.current;
    if (!afterId || isRecoveringRef.current) return;
    isRecoveringRef.current = true;
    try {
      const res = await getGlobalCommunityMessages({ afterId, limit: 50 });
      const newer = Array.isArray(res) ? res : (res?.messages || []);
      if (newer && newer.length > 0) {
        setMessages((prev) => {
          const map = new Map(prev.map((m) => [m.id, m]));
          newer.forEach((m) => map.set(m.id, m));
          return Array.from(map.values()).sort((a, b) => a.id - b.id);
        });
      }
    } catch {
      // Best-effort recovery
    } finally {
      isRecoveringRef.current = false;
    }
  }, []);

  // WebSockets & STOMP subscription for instant real-time message delivery
  const { status: stompStatus, retry: retryStomp } = useCommunityStompSubscription({
    destination: "/topic/community/global",
    onMessage: (event) => {
      if (!event) return;
      const type = event.type || "MESSAGE_CREATED";
      const payload = event.payload || event;

      if (type === "MESSAGE_CREATED") {
        const newMsg = payload;
        if (!newMsg || !newMsg.id) return;
        setMessages((prev) => {
          if (prev.some((m) => m.id === newMsg.id)) {
            return prev; // Deduplication
          }
          return [...prev, newMsg].sort((a, b) => a.id - b.id);
        });

        if (isNearBottom()) {
          scrollToBottom(true);
        } else {
          setHasUnreadMessages(true);
        }
      } else if (type === "MESSAGE_DELETED") {
        const deletedId = payload?.messageId;
        if (deletedId) {
          setMessages((prev) => prev.filter((m) => m.id !== deletedId));
        }
      } else if (type === "USER_BLOCKED") {
        if (payload?.userId === currentUser?.id) {
          setBlockStatus({ blocked: true, reason: payload?.reason });
        }
      } else if (type === "USER_UNBLOCKED") {
        if (payload?.userId === currentUser?.id) {
          setBlockStatus({ blocked: false, reason: null });
        }
      }
    },
    onReconnect: () => {
      recoverMissedMessages();
    },
    enabled: Boolean(currentUser),
  });

  // Re-fetch any missed messages when browser tab becomes visible
  useEffect(() => {
    function handleVisibilityChange() {
      if (!document.hidden) {
        recoverMissedMessages();
      }
    }
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [recoverMissedMessages]);

  // Post message
  const handleSendMessage = async (e) => {
    if (e) e.preventDefault();
    const trimmed = content.trim();
    if (!trimmed || sending || blockStatus.blocked) return;

    if (trimmed.length > MAX_CONTENT_LENGTH) {
      setSendError(`Message cannot exceed ${MAX_CONTENT_LENGTH} characters.`);
      return;
    }

    setSending(true);
    setSendError(null);
    try {
      const postMessage = sendGlobalCommunityMessage || createGlobalCommunityMessage;
      const created = await postMessage({ content: trimmed });
      setContent("");
      setMessages((prev) => {
        const map = new Map(prev.map((m) => [m.id, m]));
        map.set(created.id, created);
        return Array.from(map.values()).sort((a, b) => a.id - b.id);
      });
      setTimeout(() => scrollToBottom(true), 50);
    } catch (err) {
      setSendError(err?.message || "Failed to post message. Please try again.");
      checkBlockStatus();
    } finally {
      setSending(false);
    }
  };

  // Delete message confirmation
  const handleConfirmDelete = async () => {
    if (!deleteModalMsg || deleting) return;
    setDeleting(true);
    try {
      await deleteGlobalCommunityMessage(deleteModalMsg.id, {
        reason: deleteReason.trim() || undefined,
      });
      setMessages((prev) => prev.filter((m) => m.id !== deleteModalMsg.id));
      setDeleteModalMsg(null);
      setDeleteReason("");
    } catch (err) {
      alert(err?.message || "Failed to delete message.");
    } finally {
      setDeleting(false);
    }
  };

  // Block user confirmation
  const handleConfirmBlock = async () => {
    if (!blockModalUser || blocking) return;
    setBlocking(true);
    try {
      await adminBlockCommunityUser(blockModalUser.authorId, {
        reason: blockReason.trim() || undefined,
      });
      alert(`User ${blockModalUser.authorDisplayName} has been blocked from the community.`);
      setBlockModalUser(null);
      setBlockReason("");
    } catch (err) {
      alert(err?.message || "Failed to block user.");
    } finally {
      setBlocking(false);
    }
  };

  return (
    <main className="flex h-full min-h-0 flex-col overflow-hidden bg-[#f8f9fb]">
      <div className="flex min-h-0 w-full flex-1 flex-col">
        <section
          aria-label="Community Messages Feed"
          className="relative flex min-h-0 flex-1 flex-col overflow-hidden bg-white"
        >
        {/* Header Banner */}
        <header className="shrink-0 border-b border-neutral-200/80 bg-white px-4 py-3 sm:px-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="min-w-0">
              <div className="flex items-center gap-2.5">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-[#a31c1b] text-lg text-white shadow-sm">
                  🌐
                </span>
                <h1 className="truncate text-xl font-extrabold tracking-tight text-ink-900 sm:text-2xl">
                  PeerGeek Community
                </h1>
              </div>
              <p className="mt-1 text-xs text-ink-600 sm:text-sm">
                A shared space for students and instructors to learn, share ideas, and connect.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-2 self-start sm:self-auto sm:justify-end">
              <button
                className="rounded-lg bg-[#A31C1B] px-3 py-2 text-sm font-semibold text-white hover:bg-[#881716]"
                onClick={() => setReferralOpen(true)}
                type="button"
              >
                Refer &amp; Earn
              </button>
              {/* Dynamic WebSocket Connection Status Indicator */}
              {stompStatus === "connected" && (
                <div
                  className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 border border-emerald-200"
                  title="Real-time WebSocket connection active"
                >
                  <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
                  <span>Connected</span>
                </div>
              )}

              {stompStatus === "connecting" && (
                <div
                  className="inline-flex items-center gap-1.5 rounded-full bg-sky-50 px-3 py-1 text-xs font-semibold text-sky-700 border border-sky-200"
                  title="Connecting to real-time chat..."
                >
                  <span className="h-2 w-2 rounded-full bg-sky-500 animate-pulse" />
                  <span>Connecting...</span>
                </div>
              )}

              {stompStatus === "reconnecting" && (
                <div
                  className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-700 border border-amber-200"
                  title="Attempting automatic reconnection..."
                >
                  <span className="h-2 w-2 rounded-full bg-amber-500 animate-pulse" />
                  <span>Reconnecting...</span>
                </div>
              )}

              {stompStatus === "disconnected" && (
                <div
                  className="inline-flex items-center gap-2 rounded-full bg-rose-50 px-2.5 py-1 text-xs font-semibold text-rose-700 border border-rose-200"
                  title="Disconnected from real-time chat"
                >
                  <div className="flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full bg-rose-500" />
                    <span>Disconnected</span>
                  </div>
                  <button
                    type="button"
                    onClick={retryStomp}
                    className="rounded bg-rose-100 px-1.5 py-0.5 text-[11px] font-bold text-rose-800 hover:bg-rose-200 transition-colors cursor-pointer"
                    title="Retry connection"
                    aria-label="Retry WebSocket connection"
                  >
                    Retry
                  </button>
                </div>
              )}

              {/* Admin Moderation Dashboard Link */}
              {isAdmin && (
                <Link
                  to="/admin?tab=community"
                  className="rounded-lg bg-purple-50 px-3 py-1.5 text-xs font-semibold text-purple-700 border border-purple-200 hover:bg-purple-100 transition-colors"
                >
                  🛡️ Admin Moderation
                </Link>
              )}
            </div>
          </div>
        </header>

          {/* Messages Scroll Area */}
          <div
            ref={messagesContainerRef}
            className="min-h-0 flex-1 space-y-2 overflow-y-auto bg-[#f8f9fb] px-3 py-4 sm:px-6"
            onScroll={() => {
              if (isNearBottom()) setHasUnreadMessages(false);
            }}
          >
            {/* Load Older Messages Button */}
            {hasMoreOlder && messages.length > 0 && (
              <div className="flex justify-center pb-3">
                <button
                  type="button"
                  onClick={handleLoadOlder}
                  disabled={loadingOlder}
                  className="rounded-full border border-neutral-200 bg-white px-4 py-1.5 text-xs font-semibold text-ink-600 shadow-sm transition-colors hover:bg-neutral-50 disabled:opacity-50"
                >
                  {loadingOlder ? "Loading older messages..." : "↑ Load older messages"}
                </button>
              </div>
            )}

            {/* Loading Skeleton */}
            {initialLoading && (
              <div className="flex flex-col items-center justify-center py-20 space-y-3">
                <Spinner label="Loading PeerGeek community messages..." size="lg" />
                <p className="text-xs text-ink-500">Connecting to global community feed...</p>
              </div>
            )}

            {/* Initial Error State */}
            {initialError && !initialLoading && (
              <div className="rounded-xl border border-red-200 bg-red-50 p-8 text-center my-8 space-y-3">
                <p className="text-base font-bold text-red-900">Unable to load messages</p>
                <p className="text-sm text-red-700">{initialError}</p>
                <Button variant="secondary" onClick={loadInitialMessages}>
                  Retry Loading
                </Button>
              </div>
            )}

            {/* Empty State */}
            {!initialLoading && !initialError && messages.length === 0 && (
              <div className="flex flex-col items-center justify-center py-24 text-center space-y-3">
                <span className="text-4xl">💬</span>
                <h3 className="text-base font-bold text-ink-900">No community messages yet</h3>
                <p className="text-xs text-ink-500 max-w-sm">
                  Be the first to say hello or ask a question! All students and instructors will see your post here.
                </p>
              </div>
            )}

            {/* Messages List */}
            {!initialLoading &&
              !initialError &&
              messages.map((msg, index) => {
                const isOwn = currentUser?.id != null && Number(msg.authorId) === Number(currentUser.id);
                const canDelete = msg.canDelete ?? (isOwn || isAdmin);
                const authorRole = msg.authorRole;
                const previous = messages[index - 1];
                const sameAuthor = previous && Number(previous.authorId) === Number(msg.authorId);
                const messageDate = new Date(msg.createdAt);
                const previousDate = previous ? new Date(previous.createdAt) : null;
                const dateKey = messageDate.toLocaleDateString();
                const previousDateKey = previousDate?.toLocaleDateString();
                const showDateSeparator = dateKey !== previousDateKey;
                const dateLabel = messageDate.toDateString() === today.toDateString()
                  ? "Today"
                  : messageDate.toDateString() === yesterday.toDateString()
                    ? "Yesterday"
                    : messageDate.toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" });

                return (
                  <div key={msg.id}>
                    {showDateSeparator && (
                      <div className="my-4 flex items-center gap-3 text-[11px] font-semibold uppercase tracking-wider text-ink-400">
                        <span className="h-px flex-1 bg-neutral-200" />
                        <span>{dateLabel}</span>
                        <span className="h-px flex-1 bg-neutral-200" />
                      </div>
                    )}
                    <article
                      data-testid={`community-message-${msg.id}`}
                      className={`group flex w-full items-end gap-2 ${isOwn ? "justify-end" : "justify-start"} ${sameAuthor ? "mt-1" : "mt-3"}`}
                    >
                      {!isOwn && (
                        <UserAvatar
                          author={{ displayName: msg.authorDisplayName, username: msg.authorUsername, avatarUrl: msg.authorAvatarUrl }}
                          size="sm"
                          className={sameAuthor ? "invisible" : ""}
                        />
                      )}
                      <div className={`flex max-w-[86%] flex-col sm:max-w-[70%] ${isOwn ? "items-end" : "items-start"}`}>
                        {!sameAuthor && (
                          <div className={`mb-1 flex items-center gap-2 px-1 ${isOwn ? "justify-end" : ""}`}>
                            <span className="text-xs font-bold text-ink-800">
                              {msg.authorDisplayName || msg.authorUsername || "PeerGeek User"}
                            </span>
                            <span className="text-[10px] font-semibold uppercase tracking-wider text-ink-400">
                              {authorRole === "ADMIN" ? "ADMIN" : (authorRole === "INSTRUCTOR" || msg.authorMode === "INSTRUCTOR") ? "INSTRUCTOR" : "STUDENT"}
                            </span>
                          </div>
                        )}
                        <div className={`max-w-full ${isOwn ? "text-right" : "text-left"}`}>
                          <p className={`whitespace-pre-wrap break-words text-sm leading-6 ${
                            isOwn ? "text-[#8f1818]" : "text-ink-800"
                          }`}>{msg.content}</p>
                          <div className={`mt-1 flex items-center gap-2 text-[10px] text-ink-400 ${isOwn ? "justify-end" : ""}`}>
                            <time dateTime={msg.createdAt} title={messageDate.toLocaleString()}>
                              {formatRelativeTime(msg.createdAt)}
                            </time>
                            {(isAdmin && !isOwn && msg.authorRole !== "ADMIN") && (
                              <button
                                type="button"
                                onClick={() => {
                                  setBlockModalUser(msg);
                                  setBlockReason("");
                                }}
                                className="font-semibold text-amber-600 hover:text-amber-800"
                                title="Block user from community"
                              >
                                Block
                              </button>
                            )}
                            {canDelete && (
                              <button
                                type="button"
                                onClick={() => {
                                  setDeleteModalMsg(msg);
                                  setDeleteReason("");
                                }}
                                className="font-semibold text-red-600 hover:text-red-800"
                                title={isAdmin && !isOwn ? "Admin delete message" : "Delete your message"}
                                aria-label={`Delete message from ${msg.authorDisplayName}`}
                              >
                                Delete
                              </button>
                            )}
                          </div>
                        </div>
                      </div>
                    </article>
                  </div>
                );
              })}

            <div ref={messagesEndRef} />
          </div>

          {hasUnreadMessages && (
            <button
              type="button"
              onClick={() => scrollToBottom(true)}
              className="absolute bottom-28 left-1/2 z-10 -translate-x-1/2 rounded-full bg-[#a31c1b] px-3 py-1.5 text-xs font-semibold text-white shadow-md"
            >
              New messages ↓
            </button>
          )}

          {/* Composer Footer */}
          <div className="shrink-0 border-t border-neutral-200 bg-white p-3 sm:p-4">
            {blockStatus.blocked && (
              <div
                data-testid="community-blocked-banner"
                className="mb-3 rounded-xl border border-red-200 bg-red-50 p-3 text-center space-y-1"
              >
                <div className="flex items-center justify-center gap-2 text-sm font-bold text-red-900">
                  <span>🚫</span>
                  <span>You are blocked from posting in the community</span>
                </div>
                {blockStatus.reason && (
                  <p className="text-xs text-red-700">
                    Reason: {blockStatus.reason}
                  </p>
                )}
                <p className="text-[11px] text-ink-500">
                  You can still browse discussions and access your courses normally.
                </p>
              </div>
            )}

            <form onSubmit={handleSendMessage} className="space-y-2">
              {sendError && (
                <div className="rounded-lg bg-red-50 p-2 text-xs font-medium text-red-800 border border-red-200">
                  {sendError}
                </div>
              )}

              <div className="flex items-end gap-2 rounded-[1.5rem] border border-neutral-300 bg-[#171719] px-3 py-1.5 shadow-[0_8px_24px_rgba(15,15,18,0.12)] transition focus-within:border-[#a31c1b] focus-within:ring-2 focus-within:ring-red-100">
                <button
                  type="button"
                  aria-label="Focus message composer"
                  onClick={() => composerInputRef.current?.focus()}
                  disabled={sending || blockStatus.blocked}
                  className="mb-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-2xl font-light text-neutral-300 transition hover:bg-white/10 hover:text-white disabled:opacity-40"
                >
                  +
                </button>
                <textarea
                  id="community-message-composer"
                  aria-label="Community message composer"
                  ref={composerInputRef}
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      e.preventDefault();
                      handleSendMessage();
                    }
                  }}
                  placeholder={
                    blockStatus.blocked
                      ? "Posting is disabled while you are blocked from the community"
                      : "Share your thoughts, ask a question, or discuss learning topics... (Enter to send)"
                  }
                  maxLength={MAX_CONTENT_LENGTH}
                  rows={3}
                  disabled={sending || blockStatus.blocked}
                  className="max-h-40 min-h-10 flex-1 resize-none overflow-y-auto bg-transparent px-1 py-1 text-sm leading-5 text-white placeholder-neutral-400 outline-none"
                />
                <button
                  type="submit"
                  aria-label={blockStatus.blocked ? "Blocked" : sending ? "Sending message" : "Send Message"}
                  disabled={blockStatus.blocked || !content.trim() || sending}
                  className="mb-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[#a31c1b] text-white transition hover:bg-[#871615] disabled:bg-neutral-600 disabled:text-neutral-400"
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true" className="h-4 w-4 fill-none stroke-current stroke-2">
                    <path d="m5 12 14-7-3 14-4-6-7-1Z" strokeLinecap="round" strokeLinejoin="round" />
                    <path d="m12 13 7-8" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </button>
              </div>

              <div className="flex items-center justify-between pt-1">
                <span className="text-[11px] text-ink-400">
                  {content.length}/{MAX_CONTENT_LENGTH} characters · Shift+Enter for newline
                </span>
                <span className="text-[11px] text-ink-400">
                  Enter to send
                </span>
              </div>
            </form>
          </div>
        </section>
      </div>

      {/* Delete Confirmation Modal */}
      {deleteModalMsg && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-ink-900">
              {isAdmin && !deleteModalMsg.isOwnMessage ? "Admin Delete Message" : "Delete Community Message"}
            </h3>
            <p className="text-sm text-ink-600">
              Are you sure you want to remove this message? This action is permanent and will be recorded for moderation auditing.
            </p>

            <div className="rounded-lg bg-surface-50 p-3 border border-surface-200 text-xs text-ink-700 italic max-h-24 overflow-y-auto">
              "{deleteModalMsg.content}"
            </div>

            {isAdmin && !deleteModalMsg.isOwnMessage && (
              <div className="space-y-1">
                <label htmlFor="delete-reason-input" className="text-xs font-semibold text-ink-800">
                  Moderation Reason (Optional)
                </label>
                <input
                  id="delete-reason-input"
                  type="text"
                  value={deleteReason}
                  onChange={(e) => setDeleteReason(e.target.value)}
                  placeholder="e.g. Inappropriate language, spam, or harassment"
                  className="block w-full rounded-lg border border-surface-300 p-2 text-xs text-ink-900 focus:border-brand-500"
                />
              </div>
            )}

            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="secondary"
                onClick={() => setDeleteModalMsg(null)}
                disabled={deleting}
              >
                Cancel
              </Button>
              <Button
                variant="danger"
                onClick={handleConfirmDelete}
                disabled={deleting}
              >
                {deleting ? "Deleting..." : "Confirm Delete"}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Admin Block User Modal */}
      {blockModalUser && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-ink-900">
              Block User from Community
            </h3>
            <p className="text-sm text-ink-600">
              Block <strong>{blockModalUser.authorDisplayName}</strong> from posting in the global community. The user will still be able to log in and access their courses.
            </p>

            <div className="space-y-1">
              <label htmlFor="block-reason-input" className="text-xs font-semibold text-ink-800">
                Reason for Community Block
              </label>
              <input
                id="block-reason-input"
                type="text"
                value={blockReason}
                onChange={(e) => setBlockReason(e.target.value)}
                placeholder="e.g. Repeated violation of community code of conduct"
                className="block w-full rounded-lg border border-surface-300 p-2 text-xs text-ink-900 focus:border-brand-500"
              />
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="secondary"
                onClick={() => setBlockModalUser(null)}
                disabled={blocking}
              >
                Cancel
              </Button>
              <Button
                variant="danger"
                onClick={handleConfirmBlock}
                disabled={blocking}
              >
                {blocking ? "Blocking..." : "Block User"}
              </Button>
            </div>
          </div>
        </div>
      )}
      <ReferralModal open={referralOpen} onClose={() => setReferralOpen(false)} />
    </main>
  );
}
