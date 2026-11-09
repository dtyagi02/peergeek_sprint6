import { Link } from "react-router-dom";

const MENTION_REGEX = /(@[a-zA-Z0-9_]{3,30}\b)/g;
const IS_MENTION = /^@[a-zA-Z0-9_]{3,30}$/;

export function MentionRenderer({ content = "", className = "" }) {
  if (!content) return null;

  const parts = content.split(MENTION_REGEX);

  return (
    <span className={`whitespace-pre-wrap break-words ${className}`}>
      {parts.map((part, index) => {
        if (IS_MENTION.test(part)) {
          const username = part.slice(1);
          return (
            <Link
              key={index}
              to={`/profile/${username}`}
              className="font-semibold text-[#A31C1B] hover:text-[#821514] hover:underline transition-colors focus:outline-none focus:ring-1 focus:ring-[#A31C1B] rounded-sm"
              onClick={(e) => e.stopPropagation()}
            >
              {part}
            </Link>
          );
        }
        return part;
      })}
    </span>
  );
}
