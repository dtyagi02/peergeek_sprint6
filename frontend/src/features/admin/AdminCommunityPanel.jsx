import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { ErrorState } from "../../shared/components/ErrorState";
import { formatRelativeTime } from "../community/formatRelativeTime";
import { UserAvatar } from "../community/UserAvatar";
import { useCommunityStompSubscription } from "../community/stompClient";
import {
  getAdminCommunityMessages,
  adminDeleteCommunityMessage,
  adminBlockCommunityUser,
  adminUnblockCommunityUser,
  getAdminBlockedCommunityUsers,
  listDomainCommunities,
  getDomainMessages,
  deleteDomainMessage,
} from "../community/communityApi";

export function AdminCommunityPanel() {
  const queryClient = useQueryClient();

  const [subView, setSubView] = useState("messages"); // 'messages' | 'domains' | 'blocked'
  const [includeDeleted, setIncludeDeleted] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  // Real-time STOMP subscription to /topic/admin.community
  const handleAdminRealtimeEvent = (event) => {
    queryClient.invalidateQueries({ queryKey: ["admin", "community"] });
    if (event?.payload?.domainSlug || event?.domainSlug) {
      const slug = event.payload?.domainSlug || event.domainSlug;
      queryClient.invalidateQueries({ queryKey: ["admin", "domain-messages", slug] });
    }
  };

  const { isConnected: isRealtimeConnected } = useCommunityStompSubscription({
    destination: "/topic/admin.community",
    onMessage: handleAdminRealtimeEvent,
  });

  // Modals
  const [selectedMessageForDelete, setSelectedMessageForDelete] = useState(null);
  const [deleteReason, setDeleteReason] = useState("");

  const [selectedDomainMessageForDelete, setSelectedDomainMessageForDelete] = useState(null);

  const [selectedUserForBlock, setSelectedUserForBlock] = useState(null);
  const [blockReason, setBlockReason] = useState("");

  const [selectedUserForUnblock, setSelectedUserForUnblock] = useState(null);

  // Domain communities monitoring state
  const [selectedDomainSlug, setSelectedDomainSlug] = useState("");

  const { data: domainCommunities = [], isLoading: domainsLoading } = useQuery({
    queryKey: ["admin", "domain-communities"],
    queryFn: listDomainCommunities,
    enabled: subView === "domains",
  });

  const activeDomainSlug =
    selectedDomainSlug ||
    domainCommunities[0]?.category?.slug ||
    domainCommunities[0]?.categorySlug ||
    domainCommunities[0]?.slug ||
    "";

  const {
    data: domainMessages = [],
    isLoading: domainMessagesLoading,
    error: domainMessagesError,
    refetch: refetchDomainMessages,
  } = useQuery({
    queryKey: ["admin", "domain-messages", activeDomainSlug],
    queryFn: () => getDomainMessages(activeDomainSlug, { limit: 100 }),
    enabled: subView === "domains" && Boolean(activeDomainSlug),
  });

  const deleteDomainMutation = useMutation({
    mutationFn: ({ slug, messageId }) => deleteDomainMessage(slug, messageId),
    onSuccess: () => {
      setSelectedDomainMessageForDelete(null);
      queryClient.invalidateQueries({ queryKey: ["admin", "domain-messages", activeDomainSlug] });
    },
    onError: (err) => {
      alert(err?.message || "Failed to delete domain message");
    },
  });

  // Queries
  const {
    data: messagesData,
    isLoading: messagesLoading,
    error: messagesError,
    refetch: refetchMessages,
  } = useQuery({
    queryKey: ["admin", "community", "messages", { page, pageSize, includeDeleted, query: appliedQuery }],
    queryFn: () =>
      getAdminCommunityMessages({
        page,
        size: pageSize,
        includeDeleted,
        query: appliedQuery || undefined,
      }),
  });

  const {
    data: blockedUsersData = [],
    isLoading: blockedLoading,
    error: blockedError,
    refetch: refetchBlocked,
  } = useQuery({
    queryKey: ["admin", "community", "blocked-users"],
    queryFn: getAdminBlockedCommunityUsers,
  });

  const blockedUsers = Array.isArray(blockedUsersData)
    ? blockedUsersData
    : (blockedUsersData?.content || []);

  // Mutations
  const deleteMutation = useMutation({
    mutationFn: ({ messageId, reason }) =>
      adminDeleteCommunityMessage(messageId, { reason }),
    onSuccess: () => {
      setSelectedMessageForDelete(null);
      setDeleteReason("");
      queryClient.invalidateQueries({ queryKey: ["admin", "community"] });
      queryClient.invalidateQueries({ queryKey: ["community", "global"] });
    },
    onError: (err) => {
      alert(err?.message || "Failed to delete message");
    },
  });

  const blockMutation = useMutation({
    mutationFn: ({ userId, reason }) =>
      adminBlockCommunityUser(userId, { reason }),
    onSuccess: () => {
      setSelectedUserForBlock(null);
      setBlockReason("");
      queryClient.invalidateQueries({ queryKey: ["admin", "community"] });
      queryClient.invalidateQueries({ queryKey: ["community", "global"] });
    },
    onError: (err) => {
      alert(err?.message || "Failed to block user");
    },
  });

  const unblockMutation = useMutation({
    mutationFn: ({ userId }) =>
      adminUnblockCommunityUser(userId, {}),
    onSuccess: () => {
      setSelectedUserForUnblock(null);
      queryClient.invalidateQueries({ queryKey: ["admin", "community"] });
      queryClient.invalidateQueries({ queryKey: ["community", "global"] });
    },
    onError: (err) => {
      alert(err?.message || "Failed to unblock user");
    },
  });

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setPage(0);
    setAppliedQuery(searchQuery.trim());
  };

  const messages = messagesData?.content || [];
  const totalElements = messagesData?.totalElements || 0;
  const totalPages = messagesData?.totalPages || 0;

  return (
    <section aria-labelledby="community-moderation-heading" className="space-y-6">
      {/* Moderation Controls Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap items-center gap-3">
          <div>
            <h2 id="community-moderation-heading" className="text-lg font-bold text-ink-900">
              Community Moderation & Real-Time Monitoring
            </h2>
            <p className="mt-0.5 text-xs text-ink-500">
              Audit global & domain communities, delete messages in real time, and manage user participation.
            </p>
          </div>
          {isRealtimeConnected ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-700 border border-emerald-200">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
              Live STOMP
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-surface-100 px-2.5 py-0.5 text-[11px] font-medium text-ink-500 border border-surface-200">
              <span className="h-1.5 w-1.5 rounded-full bg-surface-400" />
              Connecting STOMP...
            </span>
          )}
        </div>

        {/* View Switcher Buttons */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setSubView("messages")}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
              subView === "messages"
                ? "bg-brand-600 text-white"
                : "bg-surface-100 text-ink-700 hover:bg-surface-200"
            }`}
          >
            Global Messages ({totalElements})
          </button>
          <button
            type="button"
            onClick={() => setSubView("domains")}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
              subView === "domains"
                ? "bg-brand-600 text-white"
                : "bg-surface-100 text-ink-700 hover:bg-surface-200"
            }`}
          >
            Domain Communities
          </button>
          <button
            type="button"
            onClick={() => setSubView("blocked")}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
              subView === "blocked"
                ? "bg-brand-600 text-white"
                : "bg-surface-100 text-ink-700 hover:bg-surface-200"
            }`}
          >
            Blocked Users ({blockedUsers.length})
          </button>
        </div>
      </div>

      {/* SUBVIEW 1: MESSAGES FEED */}
      {subView === "messages" && (
        <div className="space-y-4">
          {/* Filters & Search */}
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between rounded-xl border border-surface-200 bg-white p-4">
            <form onSubmit={handleSearchSubmit} className="flex flex-1 max-w-md items-center gap-2">
              <input
                type="text"
                placeholder="Search message text..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full rounded-lg border border-surface-300 px-3 py-1.5 text-xs text-ink-900 focus:border-brand-500"
              />
              <Button type="submit" variant="secondary" size="sm">
                Search
              </Button>
              {appliedQuery && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchQuery("");
                    setAppliedQuery("");
                    setPage(0);
                  }}
                  className="text-xs text-ink-500 hover:text-ink-800"
                >
                  Clear
                </button>
              )}
            </form>

            <div className="flex items-center gap-3 text-xs">
              <label className="flex items-center gap-1.5 font-medium text-ink-700 cursor-pointer">
                <input
                  type="checkbox"
                  checked={includeDeleted}
                  onChange={(e) => {
                    setIncludeDeleted(e.target.checked);
                    setPage(0);
                  }}
                  className="h-4 w-4 rounded border-surface-300 text-brand-600 focus:ring-brand-500"
                />
                Show Deleted Messages
              </label>

              <Button
                variant="secondary"
                size="sm"
                onClick={() => refetchMessages()}
              >
                ↻ Refresh
              </Button>
            </div>
          </div>

          {/* Loading & Errors */}
          {messagesLoading && (
            <div className="py-12">
              <Spinner label="Loading community messages for audit..." />
            </div>
          )}

          {messagesError && (
            <ErrorState
              title="Failed to load community messages"
              description={messagesError?.message}
            />
          )}

          {/* Messages Table / List */}
          {!messagesLoading && !messagesError && (
            <div className="rounded-xl border border-surface-200 bg-white shadow-xs overflow-hidden">
              {messages.length === 0 ? (
                <div className="py-12 text-center text-xs text-ink-500">
                  No community messages match your filters.
                </div>
              ) : (
                <div className="divide-y divide-surface-200">
                  {messages.map((msg) => (
                    <div
                      key={msg.id}
                      className={`p-4 transition-colors ${
                        msg.deleted ? "bg-red-50/30" : "hover:bg-surface-50"
                      }`}
                    >
                      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                        <div className="flex items-start gap-3">
                          <UserAvatar
                            author={{ displayName: msg.authorDisplayName, username: msg.authorUsername }}
                            size="md"
                          />
                          <div>
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-sm font-bold text-ink-900">
                                {msg.authorDisplayName}
                              </span>
                              <span className="text-xs text-ink-400">
                                ({msg.authorEmail})
                              </span>
                              <span className="rounded-full bg-surface-100 px-2 py-0.5 text-[10px] font-semibold text-ink-700">
                                {msg.authorRole}
                              </span>
                              <time className="text-xs text-ink-400">
                                {formatRelativeTime(msg.createdAt)}
                              </time>

                              {msg.deleted && (
                                <span className="rounded-md bg-red-100 px-2 py-0.5 text-[10px] font-bold text-red-800 border border-red-200">
                                  DELETED
                                </span>
                              )}
                            </div>

                            <p className="mt-1.5 text-sm text-ink-800 whitespace-pre-wrap break-words">
                              {msg.content}
                            </p>

                            {/* Deletion audit info */}
                            {msg.deleted && (
                              <div className="mt-2 rounded-lg bg-red-50 p-2 text-xs text-red-800 border border-red-100">
                                <strong>Deleted by:</strong> {msg.deletedByName || "Admin"} ·{" "}
                                <strong>Reason:</strong> {msg.deleteReason || "No reason specified"} ·{" "}
                                <time>{new Date(msg.deletedAt).toLocaleString()}</time>
                              </div>
                            )}
                          </div>
                        </div>

                        {/* Admin Action Buttons */}
                        <div className="flex items-center gap-2 self-end sm:self-start shrink-0">
                          {!msg.deleted && (
                            <Button
                              variant="danger"
                              size="sm"
                              onClick={() => {
                                setSelectedMessageForDelete(msg);
                                setDeleteReason("");
                              }}
                            >
                              Delete
                            </Button>
                          )}

                          {msg.authorRole !== "ADMIN" && (
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() => {
                                setSelectedUserForBlock({
                                  id: msg.authorId,
                                  displayName: msg.authorDisplayName,
                                  email: msg.authorEmail,
                                });
                                setBlockReason("");
                              }}
                            >
                              Block User
                            </Button>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Pagination */}
              {totalPages > 1 && (
                <div className="flex items-center justify-between border-t border-surface-200 px-4 py-3 text-xs">
                  <span className="text-ink-500">
                    Page {page + 1} of {totalPages} ({totalElements} total)
                  </span>
                  <div className="flex gap-2">
                    <Button
                      variant="secondary"
                      size="sm"
                      disabled={page <= 0}
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                    >
                      Previous
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      disabled={page >= totalPages - 1}
                      onClick={() => setPage((p) => p + 1)}
                    >
                      Next
                    </Button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* SUBVIEW: DOMAIN COMMUNITIES */}
      {subView === "domains" && (
        <div className="space-y-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between rounded-xl border border-surface-200 bg-white p-4">
            <div>
              <p className="text-xs font-bold text-ink-800">Domain Communities Monitoring</p>
              <p className="text-xs text-ink-500">
                Select a domain to inspect messages and moderate discussions in real time.
              </p>
            </div>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => refetchDomainMessages()}
              disabled={!activeDomainSlug}
            >
              ↻ Refresh Domain Feed
            </Button>
          </div>

          {/* Domain Category Selector */}
          {domainsLoading ? (
            <div className="py-6">
              <Spinner label="Loading domain communities..." />
            </div>
          ) : (
            <div className="flex flex-wrap gap-2">
              {domainCommunities.map((d) => {
                const slug = d.category?.slug || d.categorySlug || d.slug;
                const name = d.category?.name || d.name || slug;
                const isSelected = slug === activeDomainSlug;
                return (
                  <button
                    key={d.id || slug}
                    type="button"
                    onClick={() => setSelectedDomainSlug(slug)}
                    className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
                      isSelected
                        ? "bg-brand-600 text-white shadow-xs"
                        : "bg-white border border-surface-300 text-ink-700 hover:bg-surface-50"
                    }`}
                  >
                    {name} ({d.memberCount ?? 0} members)
                  </button>
                );
              })}
            </div>
          )}

          {/* Domain Messages Feed */}
          {domainMessagesLoading && (
            <div className="py-12">
              <Spinner label="Loading domain messages..." />
            </div>
          )}

          {domainMessagesError && (
            <ErrorState
              title="Failed to load domain messages"
              description={domainMessagesError?.message}
            />
          )}

          {!domainMessagesLoading && !domainMessagesError && activeDomainSlug && (
            <div className="rounded-xl border border-surface-200 bg-white shadow-xs overflow-hidden">
              {domainMessages.length === 0 ? (
                <div className="py-12 text-center text-xs text-ink-500">
                  No messages found in this domain community.
                </div>
              ) : (
                <div className="divide-y divide-surface-200">
                  {domainMessages.map((msg) => {
                    const isSystem = msg.messageType?.startsWith("SYSTEM_");
                    return (
                      <div
                        key={msg.id}
                        className={`p-4 transition-colors ${
                          isSystem ? "bg-surface-50/50" : "hover:bg-surface-50"
                        }`}
                      >
                        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                          <div className="flex items-start gap-3">
                            {!isSystem && (
                              <UserAvatar
                                author={{
                                  displayName: msg.sender?.displayName,
                                  username: msg.sender?.username,
                                }}
                                size="md"
                              />
                            )}
                            <div>
                              <div className="flex flex-wrap items-center gap-2">
                                <span className="text-sm font-bold text-ink-900">
                                  {isSystem ? "System" : msg.sender?.displayName || "Anonymous"}
                                </span>
                                {!isSystem && msg.sender?.username && (
                                  <span className="text-xs text-ink-400">
                                    @{msg.sender.username}
                                  </span>
                                )}
                                <time className="text-xs text-ink-400">
                                  {formatRelativeTime(msg.createdAt)}
                                </time>
                              </div>

                              <p className={`mt-1.5 text-sm whitespace-pre-wrap break-words ${
                                isSystem ? "italic text-ink-500" : "text-ink-800"
                              }`}>
                                {msg.content}
                              </p>
                            </div>
                          </div>

                          {/* Admin Actions */}
                          {!isSystem && (
                            <div className="flex items-center gap-2 self-end sm:self-start shrink-0">
                              <Button
                                variant="danger"
                                size="sm"
                                onClick={() => setSelectedDomainMessageForDelete(msg)}
                              >
                                Delete
                              </Button>

                              {msg.sender?.id && (
                                <Button
                                  variant="secondary"
                                  size="sm"
                                  onClick={() => {
                                    setSelectedUserForBlock({
                                      id: msg.sender.id,
                                      displayName: msg.sender.displayName || msg.sender.username,
                                      email: msg.sender.username,
                                    });
                                    setBlockReason("");
                                  }}
                                >
                                  Block User
                                </Button>
                              )}
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* SUBVIEW 2: BLOCKED USERS */}
      {subView === "blocked" && (
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <p className="text-xs text-ink-600">
              Users restricted from posting in the global community. These users can still access course learning materials.
            </p>
            <Button variant="secondary" size="sm" onClick={() => refetchBlocked()}>
              ↻ Refresh
            </Button>
          </div>

          {blockedLoading && (
            <div className="py-8">
              <Spinner label="Loading blocked users..." />
            </div>
          )}

          {blockedError && (
            <ErrorState
              title="Failed to load blocked users"
              description={blockedError?.message}
            />
          )}

          {!blockedLoading && !blockedError && (
            <div className="rounded-xl border border-surface-200 bg-white shadow-xs overflow-hidden">
              {blockedUsers.length === 0 ? (
                <div className="py-12 text-center text-xs text-ink-500">
                  No users are currently blocked from the global community.
                </div>
              ) : (
                <table className="min-w-full divide-y divide-surface-200 text-left text-xs">
                  <thead className="bg-surface-50 text-ink-600 font-semibold uppercase tracking-wider">
                    <tr>
                      <th className="px-4 py-3">User</th>
                      <th className="px-4 py-3">Role</th>
                      <th className="px-4 py-3">Blocked By</th>
                      <th className="px-4 py-3">Reason</th>
                      <th className="px-4 py-3">Date</th>
                      <th className="px-4 py-3 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-surface-200">
                    {blockedUsers.map((b) => (
                      <tr key={b.id} className="hover:bg-surface-50">
                        <td className="px-4 py-3">
                          <div className="font-semibold text-ink-900">{b.userDisplayName || b.displayName || b.username}</div>
                          <div className="text-ink-500">{b.userEmail || b.email || b.username}</div>
                        </td>
                        <td className="px-4 py-3">
                          <span className="rounded-full bg-surface-100 px-2 py-0.5 font-semibold text-ink-700">
                            {b.userRole || b.role || "USER"}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-ink-700">
                          {b.blockedByName || b.blockedByUsername || "Admin"}
                        </td>
                        <td className="px-4 py-3 text-ink-700 max-w-xs truncate" title={b.reason}>
                          {b.reason || "No reason specified"}
                        </td>
                        <td className="px-4 py-3 text-ink-500">
                          {(b.blockedAt || b.createdAt) ? new Date(b.blockedAt || b.createdAt).toLocaleDateString() : "—"}
                        </td>
                        <td className="px-4 py-3 text-right">
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => setSelectedUserForUnblock(b)}
                          >
                            Unblock
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {selectedMessageForDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-ink-900">Admin Delete Community Message</h3>
            <p className="text-xs text-ink-600">
              This message will be removed from the community feed. An audit record will be logged with the specified reason.
            </p>

            <div className="rounded-lg bg-surface-50 p-3 border border-surface-200 text-xs text-ink-700 italic max-h-24 overflow-y-auto">
              "{selectedMessageForDelete.content}"
            </div>

            <div className="space-y-1">
              <label htmlFor="admin-delete-reason" className="text-xs font-semibold text-ink-800">
                Reason for Removal
              </label>
              <input
                id="admin-delete-reason"
                type="text"
                value={deleteReason}
                onChange={(e) => setDeleteReason(e.target.value)}
                placeholder="e.g. Inappropriate language, spam, or harassment"
                className="block w-full rounded-lg border border-surface-300 p-2 text-xs text-ink-900 focus:border-brand-500"
              />
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="secondary"
                onClick={() => setSelectedMessageForDelete(null)}
                disabled={deleteMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="danger"
                onClick={() =>
                  deleteMutation.mutate({
                    messageId: selectedMessageForDelete.id,
                    reason: deleteReason.trim() || undefined,
                  })
                }
                disabled={deleteMutation.isPending}
              >
                {deleteMutation.isPending ? "Deleting..." : "Confirm Removal"}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Block User Modal */}
      {selectedUserForBlock && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-ink-900">Block User from Community</h3>
            <p className="text-xs text-ink-600">
              Block <strong>{selectedUserForBlock.displayName}</strong> ({selectedUserForBlock.email}) from posting in the global community. The user's course access and account login remain unaffected.
            </p>

            <div className="space-y-1">
              <label htmlFor="admin-block-reason" className="text-xs font-semibold text-ink-800">
                Reason for Block
              </label>
              <input
                id="admin-block-reason"
                type="text"
                value={blockReason}
                onChange={(e) => setBlockReason(e.target.value)}
                placeholder="e.g. Violating community rules, spamming"
                className="block w-full rounded-lg border border-surface-300 p-2 text-xs text-ink-900 focus:border-brand-500"
              />
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="secondary"
                onClick={() => setSelectedUserForBlock(null)}
                disabled={blockMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="danger"
                onClick={() =>
                  blockMutation.mutate({
                    userId: selectedUserForBlock.id,
                    reason: blockReason.trim() || undefined,
                  })
                }
                disabled={blockMutation.isPending}
              >
                {blockMutation.isPending ? "Blocking..." : "Block from Community"}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Unblock Confirmation Modal */}
      {selectedUserForUnblock && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-ink-900">Unblock Community User</h3>
            <p className="text-xs text-ink-600">
              Restore posting privileges in the global community for <strong>{selectedUserForUnblock.userDisplayName || selectedUserForUnblock.displayName || selectedUserForUnblock.username}</strong>?
            </p>

            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="secondary"
                onClick={() => setSelectedUserForUnblock(null)}
                disabled={unblockMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="primary"
                onClick={() =>
                  unblockMutation.mutate({
                    userId: selectedUserForUnblock.userId || selectedUserForUnblock.id,
                  })
                }
                disabled={unblockMutation.isPending}
              >
                {unblockMutation.isPending ? "Unblocking..." : "Confirm Unblock"}
              </Button>
            </div>
          </div>
        </div>
      )}
      {/* Domain Message Delete Confirmation Modal */}
      {selectedDomainMessageForDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-ink-900">Delete Domain Community Message</h3>
            <p className="text-xs text-ink-600">
              Remove this message from the <strong>{activeDomainSlug}</strong> community feed? The message will be deleted in real time for all connected domain participants and an audit entry will be recorded.
            </p>

            <div className="rounded-lg bg-surface-50 p-3 border border-surface-200 text-xs text-ink-700 italic max-h-24 overflow-y-auto">
              "{selectedDomainMessageForDelete.content}"
            </div>

            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="secondary"
                onClick={() => setSelectedDomainMessageForDelete(null)}
                disabled={deleteDomainMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="danger"
                onClick={() =>
                  deleteDomainMutation.mutate({
                    slug: activeDomainSlug,
                    messageId: selectedDomainMessageForDelete.id,
                  })
                }
                disabled={deleteDomainMutation.isPending}
              >
                {deleteDomainMutation.isPending ? "Deleting..." : "Confirm Removal"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
