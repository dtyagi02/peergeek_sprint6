import { useState } from "react";
import { Link } from "react-router-dom";
import { UserAvatar } from "./UserAvatar";
import { MentionTextarea } from "./MentionTextarea";

export function CreatePostCard({ currentUser, isAuthenticated, onPostCreated, isSubmitting }) {
  const [content, setContent] = useState("");
  const [errorMsg, setErrorMsg] = useState("");

  async function handlePostSubmit(e) {
    if (e) e.preventDefault();
    const trimmed = content.trim();
    if (!trimmed || isSubmitting) return;

    try {
      setErrorMsg("");
      await onPostCreated({ content: trimmed });
      setContent("");
    } catch (err) {
      setErrorMsg(err.message || "Failed to create post. Please try again.");
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="rounded-xl border border-neutral-200/80 bg-white p-5 sm:p-6 shadow-sm text-center">
        <h2 className="text-base font-semibold text-[#171717]">
          Join the conversation
        </h2>
        <p className="mt-1 text-sm text-neutral-500">
          Sign in or create an account to share ideas, ask questions, and interact with the community.
        </p>
        <div className="mt-4 flex items-center justify-center gap-3">
          <Link
            to="/login"
            className="rounded-md bg-[#06110C] px-4 py-2 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#171717]"
          >
            Log in
          </Link>
          <Link
            to="/register"
            className="rounded-md border border-neutral-300 bg-white px-4 py-2 text-sm font-semibold text-[#171717] transition-colors hover:bg-neutral-50"
          >
            Sign up
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-xl border border-neutral-200/90 bg-white p-5 sm:p-6 shadow-sm">
      <div className="flex items-start gap-3.5">
        <Link
          to={`/profile/${currentUser?.username}`}
          className="shrink-0 focus:outline-none"
        >
          <UserAvatar author={currentUser} size="md" />
        </Link>
        <div className="min-w-0 flex-1">
          <div className="mb-2 text-xs font-medium text-neutral-500">
            Posting as <span className="font-semibold text-[#171717]">{currentUser?.displayName || currentUser?.username}</span>
          </div>
          <MentionTextarea
            id="community-create-post-textarea"
            value={content}
            onChange={setContent}
            placeholder="What's on your mind? Share something with the community..."
            maxLength={2000}
            minRows={3}
            onSubmitShortcut={handlePostSubmit}
          />
          {errorMsg && (
            <p className="mt-2 text-xs text-[#A31C1B]">{errorMsg}</p>
          )}
          <div className="mt-3 flex items-center justify-between">
            <span className="text-xs text-neutral-400 hidden sm:inline">
              Tip: Type <span className="font-semibold text-[#A31C1B]">@</span> to mention users
            </span>
            <button
              type="button"
              disabled={!content.trim() || isSubmitting}
              onClick={handlePostSubmit}
              className="inline-flex items-center rounded-lg bg-[#A31C1B] px-5 py-2 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-[#851615] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isSubmitting ? "Posting..." : "Post"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
