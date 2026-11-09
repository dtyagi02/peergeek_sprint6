import { useId } from "react";
import { useController, useFormContext } from "react-hook-form";
import { Input } from "./Input";
import { Label } from "./Label";

export function FormField({
  control: controlProp,
  name,
  label,
  description,
  ...inputProps
}) {
  const context = useFormContext();
  const control = controlProp ?? context?.control;
  const { field, fieldState } = useController({ control, name });
  const generatedId = useId();
  const inputId = inputProps.id ?? generatedId;
  const descriptionId = description ? `${inputId}-description` : undefined;
  const errorId = fieldState.error ? `${inputId}-error` : undefined;

  return (
    <div className="grid gap-2">
      <Label htmlFor={inputId}>{label}</Label>
      <Input
        {...field}
        {...inputProps}
        aria-describedby={
          [descriptionId, errorId].filter(Boolean).join(" ") || undefined
        }
        aria-invalid={fieldState.invalid || undefined}
        id={inputId}
      />
      {description && (
        <p className="text-sm text-ink-700" id={descriptionId}>
          {description}
        </p>
      )}
      {fieldState.error && (
        <p className="text-sm text-danger-600" id={errorId} role="alert">
          {fieldState.error.message}
        </p>
      )}
    </div>
  );
}
