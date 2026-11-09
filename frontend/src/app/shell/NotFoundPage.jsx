import { Link } from "react-router-dom";
import { EmptyState } from "../../shared/components/EmptyState";

export function NotFoundPage() {
  return (
    <EmptyState
      action={
        <Link
          className="font-semibold text-brand-700 underline underline-offset-4"
          to="/"
        >
          Return home
        </Link>
      }
      description="The page you requested does not exist or may have moved."
      title="Page not found"
    />
  );
}
