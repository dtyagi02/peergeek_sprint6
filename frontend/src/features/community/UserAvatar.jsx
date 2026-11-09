import { useState } from "react";

function getInitials(name) {
  if (!name) return "?";
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function UserAvatar({ author, size = "md", className = "" }) {
  const [imgError, setImgError] = useState(false);

  const sizeClasses = {
    sm: "h-7 w-7 text-xs",
    md: "h-9 w-9 text-sm",
    lg: "h-11 w-11 text-base",
  };

  const selectedSize = sizeClasses[size] || sizeClasses.md;
  const initials = getInitials(author?.displayName || author?.username);

  if (author?.avatarUrl && !imgError) {
    return (
      <img
        src={author.avatarUrl}
        alt={author.displayName || author.username || "User avatar"}
        onError={() => setImgError(true)}
        className={`inline-block rounded-full object-cover shrink-0 ring-1 ring-neutral-200 ${selectedSize} ${className}`}
      />
    );
  }

  return (
    <div
      aria-label={author?.displayName || author?.username || "User avatar"}
      className={`inline-flex items-center justify-center rounded-full font-semibold shrink-0 bg-[#06110C] text-white ring-1 ring-[#171717]/20 select-none ${selectedSize} ${className}`}
    >
      {initials}
    </div>
  );
}
