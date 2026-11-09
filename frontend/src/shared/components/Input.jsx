import { forwardRef } from "react";

export const Input = forwardRef(function Input(
  { className = "", ...props },
  ref,
) {
  return (
    <input
      className={`block min-h-10 w-full rounded-md border border-surface-200 bg-white px-3 py-2 text-ink-900 placeholder:text-slate-400 disabled:cursor-not-allowed disabled:bg-surface-100 ${className}`}
      ref={ref}
      {...props}
    />
  );
});
