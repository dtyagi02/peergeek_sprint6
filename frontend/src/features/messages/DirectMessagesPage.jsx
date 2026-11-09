import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useAuthStore } from "../auth/authStore";
import { UserAvatar } from "../community/UserAvatar";
import { useCommunityStompSubscription } from "../community/stompClient";
import { Spinner } from "../../shared/components/Spinner";
import {
  getConversationMessages,
  getConversations,
  markConversationRead,
  sendDirectMessage,
} from "./directMessagesApi";

const MAX_CONTENT_LENGTH = 2000;

function listFromResponse(response) {
  if (Array.isArray(response)) return response;
  return response?.content || response?.messages || response?.conversations || [];
}

function participantOf(conversation, currentUserId) {
  return conversation?.otherParticipant ||
    conversation?.participant ||
    (Number(conversation?.participantOneId) === Number(currentUserId)
      ? conversation?.participantTwo
      : conversation?.participantOne) ||
    {};
}

function participantName(participant) {
  return participant?.displayName || participant?.username || "Participant unavailable";
}

function messageId(message) {
  return message?.id ?? message?.messageId;
}

function DirectConversationItem({ conversation, currentUserId, onOpen }) {
  const participant = participantOf(conversation, currentUserId);
  return (
    <button
      type="button"
      onClick={() => onOpen(conversation.id || conversation.conversationId)}
      className="flex w-full items-center gap-3 border-b border-neutral-100 px-4 py-4 text-left transition hover:bg-neutral-50"
    >
      <UserAvatar author={participant} size="md" />
      <span className="min-w-0 flex-1">
        <span className="flex items-center justify-between gap-2">
          <strong className="truncate text-sm text-ink-900">{participantName(participant)}</strong>
          <span className="shrink-0 text-[11px] text-ink-400">
            {conversation.updatedAt ? new Date(conversation.updatedAt).toLocaleDateString() : ""}
          </span>
        </span>
        <span className="mt-1 block truncate text-xs text-ink-500">
          {conversation.lastMessage?.content || conversation.latestMessagePreview || "Start a private conversation"}
        </span>
      </span>
      {conversation.unreadCount > 0 && (
        <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-[#a31c1b] px-1.5 text-[10px] font-bold text-white">
          {conversation.unreadCount}
        </span>
      )}
    </button>
  );
}

export function DirectMessagesPage() {
  const navigate = useNavigate();
  const currentUserId = useAuthStore((state) => state.user?.id);
  const [conversations, setConversations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    getConversations()
      .then((response) => {
        if (active) setConversations(listFromResponse(response));
      })
      .catch((err) => {
        if (active) setError(err.message || "Unable to load messages.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, []);

  if (loading) return <Spinner label="Loading messages..." />;
  return (
    <main className="mx-auto w-full max-w-3xl px-4 py-6 sm:px-6">
      <section className="overflow-hidden rounded-2xl border border-neutral-200 bg-white shadow-sm">
        <header className="border-b border-neutral-200 px-5 py-4">
          <h1 className="text-xl font-bold text-ink-900">Messages</h1>
          <p className="mt-1 text-sm text-ink-500">Private conversations with your PeerGeek community.</p>
        </header>
        {error && <p className="p-5 text-sm text-red-700">{error}</p>}
        {!error && conversations.length === 0 && (
          <div className="p-12 text-center">
            <p className="text-sm font-semibold text-ink-800">No conversations yet</p>
            <p className="mt-1 text-xs text-ink-500">Visit a user profile and choose Message to start one.</p>
          </div>
        )}
        {conversations.map((conversation) => (
          <DirectConversationItem
            key={conversation.id || conversation.conversationId}
            conversation={conversation}
            currentUserId={currentUserId}
            onOpen={(id) => navigate(`/messages/${id}`)}
          />
        ))}
      </section>
    </main>
  );
}

export function DirectChatPage() {
  const { conversationId } = useParams();
  const navigate = useNavigate();
  const currentUserId = useAuthStore((state) => state.user?.id);
  const [conversation, setConversation] = useState(null);
  const [messages, setMessages] = useState([]);
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const endRef = useRef(null);
  const inputRef = useRef(null);

  const mergeMessage = useCallback((incoming) => {
    if (!incoming || messageId(incoming) == null) return;
    setMessages((previous) => {
      if (previous.some((message) => String(messageId(message)) === String(messageId(incoming)))) return previous;
      return [...previous, incoming].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
    });
  }, []);

  const loadChat = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const conversationsResponse = await getConversations();
      const currentConversation = listFromResponse(conversationsResponse)
        .find((item) => String(item.id || item.conversationId) === String(conversationId));
      if (!currentConversation) {
        throw new Error("This conversation was not found or you are not a participant.");
      }
      setConversation(currentConversation);

      const response = await getConversationMessages(conversationId, { limit: 50 });
      setMessages(listFromResponse(response));
      await markConversationRead(conversationId);
    } catch (err) {
      setConversation(null);
      setMessages([]);
      setError(err.message || "Unable to load this conversation.");
    } finally {
      setLoading(false);
    }
  }, [conversationId]);

  useEffect(() => {
    Promise.resolve().then(loadChat);
  }, [loadChat]);

  const destination = useMemo(() => `/user/queue/direct-messages/${conversationId}`, [conversationId]);
  useCommunityStompSubscription({
    destination,
    enabled: Boolean(conversationId),
    onMessage: mergeMessage,
    onReconnect: loadChat,
  });

  const send = async (event) => {
    event?.preventDefault();
    const trimmed = content.trim();
    if (!hasValidConversation || !trimmed || sending) return;
    if (trimmed.length > MAX_CONTENT_LENGTH) {
      setError(`Message cannot exceed ${MAX_CONTENT_LENGTH} characters.`);
      return;
    }
    setSending(true);
    setError("");
    try {
      const created = await sendDirectMessage(conversationId, trimmed);
      mergeMessage(created);
      setContent("");
      setTimeout(() => endRef.current?.scrollIntoView({ behavior: "smooth" }), 50);
    } catch (err) {
      setError(err.message || "Unable to send message.");
    } finally {
      setSending(false);
    }
  };

  const hasValidConversation = Boolean(conversation?.id || conversation?.conversationId);
  const participant = participantOf(conversation, currentUserId);
  return (
    <main className="flex h-full min-h-0 flex-col overflow-hidden bg-[#f8f9fb]">
      <section className="mx-auto flex min-h-0 w-full max-w-5xl flex-1 flex-col overflow-hidden bg-white">
        <header className="flex shrink-0 items-center gap-3 border-b border-neutral-200 px-4 py-3">
          <button type="button" onClick={() => navigate("/messages")} className="rounded-full p-2 text-xl text-ink-700 hover:bg-neutral-100" aria-label="Back to messages">←</button>
          <Link
            to={participant.username ? `/profile/${participant.username}` : "#"}
            className="flex min-w-0 items-center gap-3"
            onClick={(event) => {
              if (!participant.username) event.preventDefault();
            }}
          >
            <UserAvatar author={participant} size="md" />
            <span className="min-w-0">
              <strong className="block truncate text-sm text-ink-900">
                {loading ? "Loading conversation..." : participantName(participant)}
              </strong>
              <span className="text-xs text-ink-500">{participant.role || "Participant"}</span>
            </span>
          </Link>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto bg-[#f8f9fb] px-4 py-5 sm:px-8">
          {loading && <Spinner label="Loading conversation..." />}
          {error && !loading && (
            <div className="flex flex-col items-center gap-3 py-8 text-center">
              <p className="text-sm text-red-700">{error}</p>
              <button type="button" onClick={loadChat} className="rounded-md bg-[#a31c1b] px-4 py-2 text-sm font-semibold text-white">
                Retry
              </button>
            </div>
          )}
          {!loading && !error && messages.length === 0 && (
            <p className="py-8 text-center text-sm text-ink-500">No messages yet. Start the conversation.</p>
          )}
          {!loading && !error && messages.map((message) => {
            const own = message.own === true ||
              Number(message.sender?.id ?? message.senderId ?? message.authorId) === Number(currentUserId);
            return (
              <article key={messageId(message)} className={`mb-4 flex ${own ? "justify-end" : "justify-start"}`}>
                <div className={`max-w-[85%] ${own ? "text-right" : "text-left"}`}>
                  <p className={own ? "text-[#8f1818]" : "text-ink-800"}>{message.content}</p>
                  <time className="mt-1 block text-[10px] text-ink-400">{message.createdAt ? new Date(message.createdAt).toLocaleString() : ""}</time>
                </div>
              </article>
            );
          })}
          <div ref={endRef} />
        </div>

        <form onSubmit={send} className="shrink-0 border-t border-neutral-200 bg-white p-3 sm:p-4">
          <div className="flex items-end gap-2 rounded-[1.5rem] border border-neutral-300 bg-[#171719] px-3 py-1.5 shadow-[0_8px_24px_rgba(15,15,18,0.12)] focus-within:border-[#a31c1b]">
            <button type="button" onClick={() => inputRef.current?.focus()} className="h-8 w-8 shrink-0 text-2xl font-light text-neutral-300" aria-label="Focus message composer">+</button>
            <textarea
              ref={inputRef}
              aria-label="Private message composer"
              value={content}
              onChange={(event) => setContent(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  send(event);
                }
              }}
              maxLength={MAX_CONTENT_LENGTH}
              rows={1}
              placeholder="Write a private message..."
              disabled={!hasValidConversation || loading || sending}
              className="max-h-40 min-h-10 flex-1 resize-none overflow-y-auto bg-transparent px-1 py-1 text-sm leading-5 text-white placeholder-neutral-400 outline-none"
            />
            <button type="submit" disabled={!hasValidConversation || !content.trim() || loading || sending} aria-label="Send Message" className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[#a31c1b] text-white disabled:bg-neutral-600">
              ↑
            </button>
          </div>
          <div className="flex justify-between pt-1 text-[11px] text-ink-400">
            <span>{content.length}/{MAX_CONTENT_LENGTH} characters · Shift+Enter for newline</span>
            <span>Enter to send</span>
          </div>
        </form>
      </section>
    </main>
  );
}
