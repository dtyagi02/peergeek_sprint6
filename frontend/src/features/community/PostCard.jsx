import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { UserAvatar } from "./UserAvatar";
import { MentionRenderer } from "./MentionRenderer";
import { MentionTextarea } from "./MentionTextarea";
import { CommentItem } from "./CommentItem";
import { formatRelativeTime } from "./formatRelativeTime";
import { createComment, deleteComment, deletePost, listComments } from "./communityApi";

export function PostCard({ post, currentUser, isAuthenticated, onDeletePostSuccess }) {
  const queryClient = useQueryClient();
  const [isCommentsOpen, setIsCommentsOpen] = useState(post.commentCount > 0);
  const [newCommentText, setNewCommentText] = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [errorMsg, setErrorMsg] = useState("");

  // Query comments for this post
  const {
    data: comments = [],
    isLoading: isCommentsLoading,
    refetch: refetchComments,
  } = useQuery({
    queryKey: ["community", "posts", post.id, "comments"],
    queryFn: () => listComments(post.id),
    enabled: isCommentsOpen,
    staleTime: 10 * 1000,
  });

  // Create comment mutation
  const commentMutation = useMutation({
    mutationFn: ({ content, parentCommentId }) =>
      createComment(post.id, { content, parentCommentId }),
    onSuccess: () => {
      setNewCommentText("");
      setErrorMsg("");
      queryClient.invalidateQueries({
        queryKey: ["community", "posts", post.id, "comments"],
      });
      queryClient.invalidateQueries({
        queryKey: ["community", "posts"],
      });
    },
    onError: (err) => {
      setErrorMsg(err.message || "Failed to post comment. Please try again.");
    },
  });

  // Delete comment mutation
  const deleteCommentMutation = useMutation({
    mutationFn: (commentId) => deleteComment(commentId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["community", "posts", post.id, "comments"],
      });
      queryClient.invalidateQueries({
        queryKey: ["community", "posts"],
      });
    },
  });

  // Delete post mutation
  const deletePostMutation = useMutation({
    mutationFn: () => deletePost(post.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["community", "posts"] });
      if (onDeletePostSuccess) onDeletePostSuccess(post.id);
    },
  });

  function handleToggleComments() {
    setIsCommentsOpen((prev) => !prev);
  }

  async function handleCreateTopLevelComment(e) {
    if (e) e.preventDefault();
    const trimmed = newCommentText.trim();
    if (!trimmed || commentMutation.isPending) return;

    commentMutation.mutate({ content: trimmed, parentCommentId: null });
  }

  return (
    <article
      aria-label={`Post by ${post.author.displayName || post.author.username}`}
      className="rounded-xl border border-neutral-200/90 bg-white p-5 sm:p-6 shadow-sm hover:shadow-md transition-shadow"
    >
      {/* Post Header */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3 min-w-0">
          <Link
            to={`/profile/${post.author.username}`}
            className="shrink-0 focus:outline-none"
          >
            <UserAvatar author={post.author} size="md" />
          </Link>
          <div className="min-w-0">
            <div className="flex flex-wrap items-baseline gap-1.5">
              <Link
                to={`/profile/${post.author.username}`}
                className="font-semibold text-[#171717] hover:underline truncate"
              >
                {post.author.displayName || post.author.username}
              </Link>
              <Link
                to={`/profile/${post.author.username}`}
                className="text-xs text-[#A31C1B] hover:underline"
              >
                @{post.author.username}
              </Link>
            </div>
            <div className="text-xs text-neutral-400">
              {formatRelativeTime(post.createdAt)}
            </div>
          </div>
        </div>

        {/* Delete button (if authorized) */}
        {post.canDelete && (
          <div>
            {confirmDelete ? (
              <div className="flex items-center gap-2 rounded-lg bg-red-50 px-2.5 py-1 text-xs text-[#A31C1B]">
                <span>Delete post?</span>
                <button
                  type="button"
                  disabled={deletePostMutation.isPending}
                  onClick={() => deletePostMutation.mutate()}
                  className="font-semibold underline hover:text-[#851615]"
                >
                  Yes
                </button>
                <button
                  type="button"
                  disabled={deletePostMutation.isPending}
                  onClick={() => setConfirmDelete(false)}
                  className="text-neutral-600 hover:underline"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => setConfirmDelete(true)}
                className="rounded p-1 text-neutral-400 hover:bg-neutral-100 hover:text-[#A31C1B] transition-colors focus:outline-none"
                title="Delete post"
                aria-label="Delete post"
              >
                <svg
                  className="h-4 w-4"
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

      {/* Post Content */}
      <div className="mt-3.5 text-[15px] text-[#171717] leading-relaxed break-words">
        <MentionRenderer content={post.content} />
      </div>

      {/* Post Actions / Comment bar */}
      <div className="mt-4 flex items-center justify-between border-t border-neutral-100 pt-3">
        <button
          type="button"
          onClick={handleToggleComments}
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-neutral-600 hover:text-[#A31C1B] transition-colors focus:outline-none"
        >
          <svg
            className="h-4 w-4 text-neutral-400 group-hover:text-[#A31C1B]"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="2"
              d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
            />
          </svg>
          <span>
            {post.commentCount === 1
              ? "1 comment"
              : `${post.commentCount} comments`}
          </span>
        </button>

        {isCommentsOpen && (
          <button
            type="button"
            onClick={() => setIsCommentsOpen(false)}
            className="text-xs text-neutral-400 hover:text-neutral-600"
          >
            Hide
          </button>
        )}
      </div>

      {/* Collapsible Comments Section */}
      {isCommentsOpen && (
        <div className="mt-3 pt-3 border-t border-neutral-100 space-y-4">
          {/* Add top-level comment input */}
          {isAuthenticated ? (
            <div className="flex items-start gap-3">
              <UserAvatar author={currentUser} size="sm" />
              <div className="flex-1 min-w-0">
                <MentionTextarea
                  id={`comment-input-${post.id}`}
                  value={newCommentText}
                  onChange={setNewCommentText}
                  placeholder="Write a comment... use @username to mention"
                  maxLength={1000}
                  minRows={2}
                  onSubmitShortcut={handleCreateTopLevelComment}
                />
                {errorMsg && (
                  <p className="mt-1 text-xs text-[#A31C1B]">{errorMsg}</p>
                )}
                <div className="mt-2 flex justify-end">
                  <button
                    type="button"
                    disabled={
                      !newCommentText.trim() || commentMutation.isPending
                    }
                    onClick={handleCreateTopLevelComment}
                    className="inline-flex items-center rounded-md bg-[#06110C] px-3.5 py-1.5 text-xs font-semibold text-white shadow-sm transition-colors hover:bg-[#171717] disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {commentMutation.isPending ? "Posting..." : "Comment"}
                  </button>
                </div>
              </div>
            </div>
          ) : (
            <div className="rounded-lg bg-neutral-50 px-3.5 py-2.5 text-xs text-neutral-500 text-center">
              Please{" "}
              <Link to="/login" className="font-semibold text-[#A31C1B] underline">
                log in
              </Link>{" "}
              to leave a comment.
            </div>
          )}

          {/* Comment list */}
          {isCommentsLoading ? (
            <div className="py-4 text-center text-xs text-neutral-400">
              Loading comments...
            </div>
          ) : comments.length === 0 ? (
            <div className="py-2 text-center text-xs text-neutral-400">
              No comments yet. Start the discussion!
            </div>
          ) : (
            <div className="divide-y divide-neutral-100 pt-1">
              {comments.map((comment) => (
                <CommentItem
                  key={comment.id}
                  comment={comment}
                  postId={post.id}
                  isAuthenticated={isAuthenticated}
                  onAddReply={(pid, data) => commentMutation.mutateAsync(data)}
                  onDeleteComment={(cid) =>
                    deleteCommentMutation.mutateAsync(cid)
                  }
                  isSubmittingReply={commentMutation.isPending}
                />
              ))}
            </div>
          )}
        </div>
      )}
    </article>
  );
}
