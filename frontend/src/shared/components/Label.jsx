import * as LabelPrimitive from "@radix-ui/react-label";

export function Label({ className = "", ...props }) {
  return (
    <LabelPrimitive.Root
      className={`text-sm font-medium text-ink-900 ${className}`}
      {...props}
    />
  );
}
