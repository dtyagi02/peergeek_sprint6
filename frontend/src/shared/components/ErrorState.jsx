export function ErrorState({ title = "Unable to load content", description }) {
  return (
    <section
      aria-labelledby="error-state-title"
      className="grid gap-2 rounded-xl border border-danger-600/20 bg-danger-50 p-6"
      role="alert"
    >
      <h1 className="font-semibold text-danger-600" id="error-state-title">
        {title}
      </h1>
      {description && <p className="text-ink-700">{description}</p>}
    </section>
  );
}
