export function validateForm(schema, values, setError) {
  const result = schema.safeParse(values);
  if (result.success) return result.data;

  for (const issue of result.error.issues) {
    const field = issue.path[0];
    if (typeof field === "string") {
      setError(field, { type: "validate", message: issue.message });
    }
  }
  return null;
}

export function applyApiFieldErrors(apiError, setError) {
  for (const fieldError of apiError.fieldErrors ?? []) {
    setError(fieldError.field, {
      type: "server",
      message: fieldError.message,
    });
  }
}

export function safeReturnPath(path) {
  return typeof path === "string" &&
    path.startsWith("/") &&
    !path.startsWith("//")
    ? path
    : "/";
}
