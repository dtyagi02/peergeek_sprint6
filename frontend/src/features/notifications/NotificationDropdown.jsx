import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import {
  getNotifications,
  markAllAsRead,
  markAsRead,
} from "./notificationsApi";

const TYPE_ICONS = {
  SUBMISSION_GRADED: "📝",
  COURSE_PUBLISHED: "🚀",
  COURSE_UNPUBLISHED: "⏸️",
  REPORT_CREATED: "🚩",
  REVIEW_CREATED: "⭐",
  ASSIGNMENT_DEADLINE_APPROACHING: "⏰",
};

export function NotificationDropdown({ open, onClose }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const notificationsQuery = useQuery({
    queryKey: ["notifications", "recent"],
    queryFn: () => getNotifications({ page: 0, size: 6 }),
    enabled: open,
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

  if (!open) return null;

  const notifications = notificationsQuery.data?.content || [];
  const hasUnread = notifications.some((n) => !n.isRead);

  function handleItemClick(notification) {
    if (!notification.isRead) {
      markReadMutation.mutate(notification.id);
    }
    onClose();
    if (notification.actionUrl) {
      navigate(notification.actionUrl);
    }
  }

  return (
    <div
      role="dialog"
      aria-label="Notifications popover"
      className="absolute right-0 top-full mt-2 w-80 sm:w-96 rounded-xl border border-surface-200 bg-white shadow-xl z-50 overflow-hidden"
    >
      <div className="flex items-center justify-between border-b border-surface-100 px-4 py-3 bg-surface-50">
        <h3 className="text-sm font-bold text-ink-900">Notifications</h3>
        {hasUnread && (
          <button
            type="button"
            onClick={() => markAllMutation.mutate()}
            disabled={markAllMutation.isPending}
            className="text-xs font-semibold text-brand-600 hover:text-brand-700 disabled:opacity-50 transition"
          >
            Mark all as read
          </button>
        )}
      </div>

      <div className="max-h-96 overflow-y-auto divide-y divide-surface-100">
        {notificationsQuery.isLoading ? (
          <div className="p-6 text-center text-xs text-slate-500">
            Loading notifications...
          </div>
        ) : notifications.length === 0 ? (
          <div className="p-8 text-center text-xs text-slate-500 space-y-1">
            <span className="text-2xl block mb-2">🔔</span>
            <p className="font-semibold text-ink-900">No notifications yet</p>
            <p>You&apos;re all caught up!</p>
          </div>
        ) : (
          notifications.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => handleItemClick(item)}
              className={`w-full text-left p-3.5 transition flex items-start gap-3 hover:bg-surface-50 ${
                !item.isRead ? "bg-brand-50/40" : "bg-white"
              }`}
            >
              <span className="text-xl shrink-0 mt-0.5">
                {TYPE_ICONS[item.type] || "🔔"}
              </span>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between gap-1">
                  <h4
                    className={`text-xs truncate ${
                      !item.isRead
                        ? "font-bold text-ink-900"
                        : "font-medium text-slate-700"
                    }`}
                  >
                    {item.title}
                  </h4>
                  {!item.isRead && (
                    <span
                      aria-label="Unread"
                      className="h-2 w-2 rounded-full bg-brand-600 shrink-0"
                    />
                  )}
                </div>
                <p className="text-xs text-slate-500 line-clamp-2 mt-0.5">
                  {item.message}
                </p>
                <span className="text-[10px] text-slate-400 mt-1 block">
                  {new Date(item.createdAt).toLocaleDateString(undefined, {
                    month: "short",
                    day: "numeric",
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </span>
              </div>
            </button>
          ))
        )}
      </div>

      <div className="border-t border-surface-100 p-2 text-center bg-surface-50">
        <Link
          to="/notifications"
          onClick={onClose}
          className="text-xs font-semibold text-brand-600 hover:text-brand-700 block py-1"
        >
          View all notifications →
        </Link>
      </div>
    </div>
  );
}
