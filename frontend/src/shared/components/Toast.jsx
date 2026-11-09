import * as ToastPrimitive from "@radix-ui/react-toast";
import { useCallback, useMemo, useState } from "react";
import { ToastContext } from "./toastContext";

export function Toast({ title, description, open = true, onOpenChange }) {
  return (
    <ToastPrimitive.Root
      className="grid gap-1 rounded-lg border border-surface-200 bg-white p-4 shadow-lg"
      onOpenChange={onOpenChange}
      open={open}
    >
      <ToastPrimitive.Title className="font-semibold text-ink-900">
        {title}
      </ToastPrimitive.Title>
      {description && (
        <ToastPrimitive.Description className="text-sm text-ink-700">
          {description}
        </ToastPrimitive.Description>
      )}
      <ToastPrimitive.Close
        aria-label="Dismiss notification"
        className="sr-only"
      >
        Dismiss
      </ToastPrimitive.Close>
    </ToastPrimitive.Root>
  );
}

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const notify = useCallback((toast) => {
    const id =
      globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;
    setToasts((current) => [...current, { id, ...toast }]);
    return id;
  }, []);
  const dismiss = useCallback(
    (id) => setToasts((current) => current.filter((toast) => toast.id !== id)),
    [],
  );
  const value = useMemo(() => ({ notify, dismiss }), [notify, dismiss]);

  return (
    <ToastContext.Provider value={value}>
      <ToastPrimitive.Provider swipeDirection="right">
        {children}
        <ToastPrimitive.Viewport
          aria-label="Notifications"
          className="fixed bottom-4 right-4 z-[60] grid w-[min(24rem,calc(100vw-2rem))] gap-3"
        >
          {toasts.map((toast) => (
            <Toast
              description={toast.description}
              key={toast.id}
              onOpenChange={(open) => {
                if (!open) dismiss(toast.id);
              }}
              title={toast.title}
            />
          ))}
        </ToastPrimitive.Viewport>
      </ToastPrimitive.Provider>
    </ToastContext.Provider>
  );
}
