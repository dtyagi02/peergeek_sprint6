import { Link } from "react-router-dom";

export function DomainCommunityEntryButton({
  domainSlug,
  domainName,
  label = "Open Community",
  variant = "button",
  className = "",
}) {
  if (!domainSlug) return null;

  const targetUrl = `/domains/${domainSlug}/community`;

  if (variant === "card") {
    return (
      <Link
        to={targetUrl}
        aria-label={`Join ${domainName || "domain"} community chat`}
        className={`group flex items-center justify-between rounded-xl border border-neutral-200 bg-white p-4 shadow-xs transition-all hover:border-[#06110C] hover:shadow-md ${className}`}
      >
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-[#06110C] text-white transition-colors group-hover:bg-[#A31C1B]">
            <svg
              className="h-5 w-5"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2"
                d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
              />
            </svg>
          </div>
          <div>
            <h4 className="text-sm font-bold text-[#171717] group-hover:text-[#A31C1B] transition-colors">
              {domainName} Community
            </h4>
            <p className="text-xs text-neutral-500">
              Connect & chat with peers in this domain
            </p>
          </div>
        </div>
        <span className="text-xs font-semibold text-[#06110C] group-hover:text-[#A31C1B] group-hover:translate-x-0.5 transition-all">
          Join Chat →
        </span>
      </Link>
    );
  }

  return (
    <Link
      to={targetUrl}
      aria-label={`Open ${domainName || "domain"} community chat`}
      className={`inline-flex items-center gap-1.5 rounded-lg border border-neutral-300 bg-white px-3 py-1.5 text-xs font-semibold text-[#171717] shadow-xs transition-all hover:border-[#06110C] hover:bg-neutral-50 hover:text-[#A31C1B] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#06110C] ${className}`}
    >
      <svg
        className="h-3.5 w-3.5 text-[#06110C] group-hover:text-[#A31C1B]"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="2"
          d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
        />
      </svg>
      <span>{label}</span>
    </Link>
  );
}
