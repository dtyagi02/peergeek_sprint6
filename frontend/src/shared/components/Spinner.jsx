export function Spinner({ label = "Loading", className = "" }) {
  return (
    <span
      aria-label={label}
      className={`inline-block size-5 animate-spin rounded-full border-2 border-current border-r-transparent ${className}`}
      role="status"
    />
  );
}
