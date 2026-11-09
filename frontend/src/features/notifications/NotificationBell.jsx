import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { getUnreadCount } from "./notificationsApi";
import { NotificationDropdown } from "./NotificationDropdown";
import { useAuthStore } from "../auth/authStore";

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);

  const isAuthenticated = useAuthStore(
    (state) => state.status === "authenticated",
  );

  const unreadCountQuery = useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: getUnreadCount,
    enabled: isAuthenticated,
    refetchInterval: 30000,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });

  const unreadCount = unreadCountQuery.data?.unreadCount || 0;

  useEffect(() => {
    function handleDocumentClick(event) {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target)
      ) {
        setOpen(false);
      }
    }

    if (open) {
      document.addEventListener("mousedown", handleDocumentClick);
    }
    return () => {
      document.removeEventListener("mousedown", handleDocumentClick);
    };
  }, [open]);

  if (!isAuthenticated) return null;

  return (
    <div ref={containerRef} className="relative inline-flex items-center">
      <button
        type="button"
        onClick={() => setOpen((prev) => !prev)}
        aria-label={`Notifications${unreadCount > 0 ? `, ${unreadCount} unread` : ""}`}
        aria-expanded={open}
        className="relative rounded-full p-2 text-ink-600 hover:text-brand-600 hover:bg-surface-100 transition focus:outline-hidden focus:ring-2 focus:ring-brand-500"
      >
        <span className="text-lg leading-none" aria-hidden="true">
          🔔
        </span>
        {unreadCount > 0 && (
          <span
            data-testid="unread-badge"
            className="absolute -top-1 -right-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-danger-600 px-1 text-[11px] font-bold text-white shadow-xs"
          >
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>

      <NotificationDropdown open={open} onClose={() => setOpen(false)} />
    </div>
  );
}
