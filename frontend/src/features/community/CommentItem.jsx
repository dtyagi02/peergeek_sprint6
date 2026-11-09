import { useState } from "react";
import { Link } from "react-router-dom";
import { UserAvatar } from "./UserAvatar";
import { MentionRenderer } from "./MentionRenderer";
import { MentionTextarea } from "./MentionTextarea";
import { formatRelativeTime } from "./formatRelativeTime";

export function CommentItem({
  comment,
  postId,
  isAuthenticated,
  onAddReply,
  onDeleteComment,
  isSubmittingReply,
}) {
  const [isReplying, setIsReplying] = useState(false);
  const [replyText, setReplyText] = useState("");
  const [isDeleting, setIsDeleting] = useState(false);
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);

  function handleStartReply(authorUsername) {
    if (!isAuthenticated) return;
    const initialText = authorUsername ? `@${authorUsername} ` : "";
    setReplyText(initialText);
    setIsReplying(true);
  }

  async function handleSubmitReply() {
    const trimmed = replyText.trim();
    if (!trimmed || isSubmittingReply) return;

    try {
      await onAddReply(postId, {
        content: trimmed,
        parentCommentId: comment.id,
      });
      setReplyText("");
      setIsReplying(false);
    } catch {
      // Handled in parent
    }
  }

  async function handleDelete(commentId) {
    setIsDeleting(true);
    try {
      await onDeleteComment(commentId);
      setConfirmDeleteId(null);
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="group relative pt-3 pb-2 transition-colors border-t border-neutral-100 first:border-t-0">
      {/* Top-level comment */}
      <div className="flex items-start gap-3">
        <Link
          to={`/profile/${comment.author.username}`}
          className="shrink-0 focus:outline-none"
        >
          <UserAvatar author={comment.author} size="sm" />
        </Link>

        <div className="min-w-0 flex-1">
          {/* Author line */}
          <div className="flex flex-wrap items-baseline gap-1.5 text-xs">
            <Link
              to={`/profile/${comment.author.username}`}
              className="font-semibold text-[#171717] hover:underline"
            >
              {comment.author.displayName || comment.author.username}
            </Link>
            <span className="text-neutral-400">·</span>
            <Link
              to={`/profile/${comment.author.username}`}
              className="text-neutral-500 hover:text-[#A31C1B]"
            >
              @{comment.author.username}
            </Link>
            <span className="text-neutral-400">·</span>
            <span className="text-neutral-400">
              {formatRelativeTime(comment.createdAt)}
            </span>

            {/* Delete button */}
            {comment.canDelete && (
              <div className="ml-auto">
                {confirmDeleteId === comment.id ? (
                  <span className="inline-flex items-center gap-1.5 text-xs">
                    <span className="text-neutral-500">Delete comment?</span>
                    <button
                      type="button"
                      disabled={isDeleting}
                      onClick={() => handleDelete(comment.id)}
                      className="font-medium text-[#A31C1B] hover:underline disabled:opacity-50"
                    >
                      Yes
                    </button>
                    <button
                      type="button"
                      disabled={isDeleting}
                      onClick={() => setConfirmDeleteId(null)}
                      className="text-neutral-500 hover:underline"
                    >
                      Cancel
                    </button>
                  </span>
                ) : (
                  <button
                    type="button"
                    onClick={() => setConfirmDeleteId(comment.id)}
                    className="text-neutral-400 hover:text-[#A31C1B] transition-colors focus:outline-none"
                    title="Delete comment"
                    aria-label="Delete comment"
                  >
                    <svg
                      className="h-3.5 w-3.5"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth="2"
                        d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                      />
                    </svg>
                  </button>
                )}
              </div>
            )}
          </div>

          {/* Comment text */}
          <div className="mt-1 text-sm text-neutral-800 leading-relaxed">
            <MentionRenderer content={comment.content} />
          </div>

          {/* Reply button (subtle Instagram-style) */}
          {isAuthenticated && (
            <div className="mt-1.5 flex items-center gap-3">
              <button
                type="button"
                onClick={() => handleStartReply(comment.author.username)}
                className="text-xs font-semibold text-neutral-500 hover:text-[#A31C1B] transition-colors focus:outline-none"
              >
                Reply
              </button>
            </div>
          )}

          {/* Nested Replies (1 level) */}
          {comment.replies && comment.replies.length > 0 && (
            <div className="mt-3.5 space-y-3 border-l-2 border-neutral-100 pl-3.5 sm:pl-5">
              {comment.replies.map((reply) => (
                <div key={reply.id} className="flex items-start gap-2.5">
                  <Link
                    to={`/profile/${reply.author.username}`}
                    className="shrink-0 focus:outline-none"
                  >
                    <UserAvatar author={reply.author} size="sm" />
                  </Link>

                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-baseline gap-1.5 text-xs">
                      <Link
                        to={`/profile/${reply.author.username}`}
                        className="font-semibold text-[#171717] hover:underline"
                      >
                        {reply.author.displayName || reply.author.username}
                      </Link>
                      <span className="text-neutral-400">·</span>
                      <Link
                        to={`/profile/${reply.author.username}`}
                        className="text-neutral-500 hover:text-[#A31C1B]"
                      >
                        @{reply.author.username}
                      </Link>
                      <span className="text-neutral-400">·</span>
                      <span className="text-neutral-400">
                        {formatRelativeTime(reply.createdAt)}
                      </span>

                      {/* Reply delete button */}
                      {reply.canDelete && (
                        <div className="ml-auto">
                          {confirmDeleteId === reply.id ? (
                            <span className="inline-flex items-center gap-1.5 text-xs">
                              <span className="text-neutral-500">Delete?</span>
                              <button
                                type="button"
                                disabled={isDeleting}
                                onClick={() => handleDelete(reply.id)}
                                className="font-medium text-[#A31C1B] hover:underline disabled:opacity-50"
                              >
                                Yes
                              </button>
                              <button
                                type="button"
                                disabled={isDeleting}
                                onClick={() => setConfirmDeleteId(null)}
                                className="text-neutral-500 hover:underline"
                              >
                                Cancel
                              </button>
                            </span>
                          ) : (
                            <button
                              type="button"
                              onClick={() => setConfirmDeleteId(reply.id)}
                              className="text-neutral-400 hover:text-[#A31C1B] transition-colors focus:outline-none"
                              title="Delete reply"
                              aria-label="Delete reply"
                            >
                              <svg
                                className="h-3.5 w-3.5"
                                fill="none"
                                viewBox="0 0 24 24"
                                stroke="currentColor"
                              >
                                <path
                                  strokeLinecap="round"
                                  strokeLinejoin="round"
                                  strokeWidth="2"
                                  d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                                />
                              </svg>
                            </button>
                          )}
                        </div>
                      )}
                    </div>

                    <div className="mt-1 text-xs sm:text-sm text-neutral-800 leading-relaxed">
                      <MentionRenderer content={reply.content} />
                    </div>

                    {/* Reply to this reply (associates with top-level comment and mentions the user) */}
                    {isAuthenticated && (
                      <div className="mt-1 flex items-center gap-3">
                        <button
                          type="button"
                          onClick={() => handleStartReply(reply.author.username)}
                          className="text-xs font-semibold text-neutral-500 hover:text-[#A31C1B] transition-colors focus:outline-none"
                        >
                          Reply
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Inline Reply Input Box */}
          {isReplying && (
            <div className="mt-3 rounded-lg border border-neutral-200 bg-neutral-50/70 p-3 animate-in fade-in">
              <div className="mb-1.5 flex items-center justify-between text-xs text-neutral-500">
                <span>Replying to comment</span>
                <button
                  type="button"
                  onClick={() => setIsReplying(false)}
                  className="hover:text-neutral-800"
                >
                  Cancel
                </button>
              </div>

              <MentionTextarea
                id={`reply-input-${comment.id}`}
                value={replyText}
                onChange={setReplyText}
                placeholder="Write a reply... use @username to mention"
                maxLength={1000}
                minRows={2}
                autoFocus={true}
                onSubmitShortcut={handleSubmitReply}
              />

              <div className="mt-2 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsReplying(false)}
                  className="rounded px-2.5 py-1 text-xs font-medium text-neutral-600 hover:bg-neutral-200/60 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  aria-label="Submit reply"
                  disabled={!replyText.trim() || isSubmittingReply}
                  onClick={handleSubmitReply}
                  className="inline-flex items-center rounded-md bg-[#A31C1B] px-3 py-1 text-xs font-semibold text-white transition-colors hover:bg-[#851615] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {isSubmittingReply ? "Replying..." : "Reply"}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
