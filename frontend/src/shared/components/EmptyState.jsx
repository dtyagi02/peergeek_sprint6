export function EmptyState({ title, description, action }) {
  return (
    <section
      aria-labelledby="empty-state-title"
      className="grid justify-items-center gap-3 rounded-xl border border-dashed border-surface-200 bg-white px-6 py-12 text-center"
    >
      <h1 className="text-xl font-semibold text-ink-900" id="empty-state-title">
        {title}
      </h1>
      {description && <p className="max-w-lg text-ink-700">{description}</p>}
      {action}
    </section>
  );
}
