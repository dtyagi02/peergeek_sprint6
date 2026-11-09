import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getNotifications,
  markAllAsRead,
  markAsRead,
} from "./notificationsApi";
import { Spinner } from "../../shared/components/Spinner";
import { Button } from "../../shared/components/Button";

const TYPE_ICONS = {
  SUBMISSION_GRADED: "📝",
  COURSE_PUBLISHED: "🚀",
  COURSE_UNPUBLISHED: "⏸️",
  REPORT_CREATED: "🚩",
  REVIEW_CREATED: "⭐",
  ASSIGNMENT_DEADLINE_APPROACHING: "⏰",
};

export function NotificationsPage() {
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const queryClient = useQueryClient();

  const notificationsQuery = useQuery({
    queryKey: ["notifications", "list", { page, unreadOnly }],
    queryFn: () => getNotifications({ page, size: 15, unreadOnly }),
    staleTime: 10 * 1000,
  });

  const markReadMutation = useMutation({
    mutationFn: (id) => markAsRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  const markAllMutation = useMutation({
    mutationFn: markAllAsRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  const data = notificationsQuery.data;
  const notifications = data?.content || [];
  const totalPages = data?.totalPages || 0;
  const totalElements = data?.totalElements || 0;

  function handleFilterChange(unread) {
    setUnreadOnly(unread);
    setPage(0);
  }

  return (
    <main className="mx-auto max-w-4xl px-4 py-8 sm:px-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-surface-200 pb-5 mb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-ink-900">
            Notifications
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Stay up to date with course publications, assignments, grades, and
            community feedback.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Button
            type="button"
            variant="secondary"
            onClick={() => markAllMutation.mutate()}
            disabled={markAllMutation.isPending || notifications.length === 0}
          >
            Mark all as read
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-surface-200 mb-6">
        <button
          type="button"
          onClick={() => handleFilterChange(false)}
          className={`px-4 py-2 text-sm font-semibold border-b-2 transition -mb-px ${
            !unreadOnly
              ? "border-brand-600 text-brand-600"
              : "border-transparent text-slate-500 hover:text-ink-900"
          }`}
        >
          All
        </button>
        <button
          type="button"
          onClick={() => handleFilterChange(true)}
          className={`px-4 py-2 text-sm font-semibold border-b-2 transition -mb-px ${
            unreadOnly
              ? "border-brand-600 text-brand-600"
              : "border-transparent text-slate-500 hover:text-ink-900"
          }`}
        >
          Unread only
        </button>
      </div>

      {/* Content */}
      {notificationsQuery.isLoading ? (
        <div className="flex min-h-[300px] items-center justify-center">
          <Spinner label="Loading notifications..." />
        </div>
      ) : notificationsQuery.isError ? (
        <div className="rounded-xl border border-danger-200 bg-danger-50 p-6 text-center">
          <p className="text-sm font-semibold text-danger-700">
            Failed to load notifications.
          </p>
          <div className="mt-3">
            <Button
              type="button"
              variant="secondary"
              onClick={() => notificationsQuery.refetch()}
            >
              Retry
            </Button>
          </div>
        </div>
      ) : notifications.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-surface-300 p-12 text-center bg-surface-50/50">
          <span className="text-3xl block mb-2">📭</span>
          <h3 className="text-base font-bold text-ink-900">
            {unreadOnly ? "No unread notifications" : "No notifications yet"}
          </h3>
          <p className="text-sm text-slate-500 mt-1 max-w-sm mx-auto">
            {unreadOnly
              ? "You have marked all notifications as read."
              : "When events occur on your enrolled or authored courses, notifications will appear here."}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {notifications.map((item) => (
            <div
              key={item.id}
              className={`rounded-xl border p-4 sm:p-5 transition flex flex-col sm:flex-row sm:items-center justify-between gap-4 ${
                !item.isRead
                  ? "border-brand-200 bg-brand-50/30 shadow-xs"
                  : "border-surface-200 bg-white"
              }`}
            >
              <div className="flex items-start gap-3.5">
                <span className="text-2xl shrink-0 mt-0.5">
                  {TYPE_ICONS[item.type] || "🔔"}
                </span>
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-sm font-bold text-ink-900">
                      {item.title}
                    </h3>
                    {!item.isRead && (
                      <span className="inline-flex items-center rounded-full bg-brand-100 px-2 py-0.5 text-[10px] font-bold text-brand-700">
                        New
                      </span>
                    )}
                  </div>
                  <p className="text-sm text-slate-600 mt-1">{item.message}</p>
                  <span className="text-xs text-slate-400 mt-2 block">
                    {new Date(item.createdAt).toLocaleString(undefined, {
                      dateStyle: "medium",
                      timeStyle: "short",
                    })}
                  </span>
                </div>
              </div>

              <div className="flex items-center gap-2 shrink-0 self-end sm:self-center">
                {item.actionUrl && (
                  <Link
                    to={item.actionUrl}
                    onClick={() => {
                      if (!item.isRead) markReadMutation.mutate(item.id);
                    }}
                    className="inline-flex items-center justify-center rounded-lg border border-surface-300 bg-white px-3 py-1.5 text-xs font-semibold text-ink-800 hover:bg-surface-50 transition"
                  >
                    View details →
                  </Link>
                )}
                {!item.isRead && (
                  <Button
                    type="button"
                    variant="secondary"
                    className="text-xs py-1.5 px-3"
                    onClick={() => markReadMutation.mutate(item.id)}
                    disabled={markReadMutation.isPending}
                  >
                    Mark as read
                  </Button>
                )}
              </div>
            </div>
          ))}

          {/* Pagination controls */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-surface-200 pt-4 mt-6">
              <span className="text-xs text-slate-500">
                Page {page + 1} of {totalPages} ({totalElements} notifications)
              </span>
              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  variant="secondary"
                  disabled={page <= 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="text-xs"
                >
                  Previous
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                  className="text-xs"
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </main>
  );
}
