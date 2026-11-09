import { forwardRef } from "react";

const variants = {
  primary: "bg-brand-600 text-white hover:bg-brand-700",
  secondary:
    "border border-surface-200 bg-white text-ink-900 hover:bg-surface-100",
  danger: "bg-danger-600 text-white hover:bg-red-700",
};

export const Button = forwardRef(function Button(
  { className = "", variant = "primary", type = "button", ...props },
  ref,
) {
  return (
    <button
      className={`inline-flex min-h-10 items-center justify-center rounded-md px-4 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant] ?? variants.primary} ${className}`}
      ref={ref}
      type={type}
      {...props}
    />
  );
});
