import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "../auth/authStore";
import { CreatePostCard } from "./CreatePostCard";
import { PostCard } from "./PostCard";
import { createPost, listPosts } from "./communityApi";

export function CommunityPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const authStatus = useAuthStore((s) => s.status);
  const currentUser = useAuthStore((s) => s.user);
  const isAuthenticated = authStatus === "authenticated";

  // Fetch community posts
  const postsQuery = useQuery({
    queryKey: ["community", "posts", { page, pageSize }],
    queryFn: () => listPosts({ page, size: pageSize }),
    staleTime: 10 * 1000,
  });

  // Create post mutation
  const createPostMutation = useMutation({
    mutationFn: ({ content }) => createPost({ content }),
    onSuccess: () => {
      // Refresh feed
      queryClient.invalidateQueries({ queryKey: ["community", "posts"] });
    },
  });

  const postsData = postsQuery.data;
  const posts = postsData?.content || [];
  const totalPages = postsData?.totalPages || 0;
  const totalElements = postsData?.totalElements || 0;

  return (
    <main className="min-h-screen bg-neutral-50/50 py-8 px-4 sm:px-6 lg:px-8">
      <div className="mx-auto max-w-3xl space-y-6">
        {/* Page Heading */}
        <header className="border-b border-neutral-200/80 pb-5">
          <div className="flex items-center gap-2">
            <span className="h-6 w-1.5 rounded-full bg-[#06110C]" />
            <h1 className="text-2xl font-bold tracking-tight text-[#171717] sm:text-3xl">
              Community
            </h1>
          </div>
          <p className="mt-1.5 text-sm text-neutral-600 sm:text-base">
            Share ideas, ask questions, and learn together.
          </p>
        </header>

        {/* Create Post Section */}
        <section aria-label="Create a post">
          <CreatePostCard
            currentUser={currentUser}
            isAuthenticated={isAuthenticated}
            onPostCreated={(data) => createPostMutation.mutateAsync(data)}
            isSubmitting={createPostMutation.isPending}
          />
        </section>

        {/* Discussion Feed */}
        <section aria-label="Discussion Feed" className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wider text-neutral-500">
              Discussions {totalElements > 0 && `(${totalElements})`}
            </h2>
          </div>

          {/* Loading State */}
          {postsQuery.isLoading && (
            <div className="space-y-4">
              {[1, 2, 3].map((i) => (
                <div
                  key={i}
                  className="rounded-xl border border-neutral-200 bg-white p-5 animate-pulse space-y-3"
                >
                  <div className="flex items-center gap-3">
                    <div className="h-9 w-9 rounded-full bg-neutral-200" />
                    <div className="space-y-1.5 flex-1">
                      <div className="h-3.5 w-32 bg-neutral-200 rounded" />
                      <div className="h-2.5 w-20 bg-neutral-100 rounded" />
                    </div>
                  </div>
                  <div className="h-4 w-3/4 bg-neutral-200 rounded" />
                  <div className="h-4 w-1/2 bg-neutral-100 rounded" />
                </div>
              ))}
            </div>
          )}

          {/* Error State */}
          {postsQuery.isError && (
            <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center">
              <p className="text-sm font-medium text-[#A31C1B]">
                Failed to load discussions.
              </p>
              <button
                type="button"
                onClick={() => postsQuery.refetch()}
                className="mt-3 inline-flex items-center rounded-md bg-white px-3 py-1.5 text-xs font-semibold text-[#171717] shadow-sm ring-1 ring-neutral-300 hover:bg-neutral-50"
              >
                Try again
              </button>
            </div>
          )}

          {/* Empty State */}
          {!postsQuery.isLoading && !postsQuery.isError && posts.length === 0 && (
            <div className="rounded-xl border border-dashed border-neutral-300 bg-white p-10 text-center">
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-neutral-100 text-neutral-400">
                <svg
                  className="h-6 w-6"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="1.5"
                    d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
                  />
                </svg>
              </div>
              <h3 className="mt-3 text-base font-semibold text-[#171717]">
                No posts yet
              </h3>
              <p className="mt-1 text-sm text-neutral-500 max-w-sm mx-auto">
                Be the first to ask a question, share what you are learning, or start a discussion with fellow learners!
              </p>
            </div>
          )}

          {/* Post Items */}
          {!postsQuery.isLoading &&
            !postsQuery.isError &&
            posts.length > 0 && (
              <div className="space-y-4">
                {posts.map((post) => (
                  <PostCard
                    key={post.id}
                    post={post}
                    currentUser={currentUser}
                    isAuthenticated={isAuthenticated}
                  />
                ))}

                {/* Pagination Controls */}
                {totalPages > 1 && (
                  <div className="mt-6 flex items-center justify-between border-t border-neutral-200 pt-4">
                    <button
                      type="button"
                      disabled={page === 0 || postsQuery.isFetching}
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      className="rounded-md border border-neutral-300 bg-white px-3.5 py-1.5 text-xs font-semibold text-neutral-700 hover:bg-neutral-50 disabled:opacity-50"
                    >
                      Previous
                    </button>
                    <span className="text-xs text-neutral-500">
                      Page {page + 1} of {totalPages}
                    </span>
                    <button
                      type="button"
                      disabled={page >= totalPages - 1 || postsQuery.isFetching}
                      onClick={() => setPage((p) => p + 1)}
                      className="rounded-md border border-neutral-300 bg-white px-3.5 py-1.5 text-xs font-semibold text-neutral-700 hover:bg-neutral-50 disabled:opacity-50"
                    >
                      Next
                    </button>
                  </div>
                )}
              </div>
            )}
        </section>
      </div>
    </main>
  );
}
