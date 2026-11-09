import { Link, useParams } from "react-router-dom";

export function CourseEditorAnalyticsHeaderLink(props) {
  const params = useParams();
  const courseId = props.courseId || params.courseId;
  if (!courseId) return null;

  return (
    <Link
      to={`/instructor/courses/${courseId}/analytics`}
      className="inline-flex items-center gap-1.5 rounded-lg border border-surface-300 bg-white px-3 py-1.5 text-xs font-semibold text-ink-700 shadow-sm transition hover:bg-surface-50 hover:text-ink-900"
      aria-label="View Course Analytics"
    >
      <svg
        className="h-3.5 w-3.5 text-brand-600"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        aria-hidden="true"
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth={2}
          d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
        />
      </svg>
      <span>Analytics</span>
    </Link>
  );
}
