import type { ReactNode } from "react";

export function Field({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  );
}
export function TextField({
  name,
  label,
  value,
  type = "text",
  required = true,
  maxLength,
  min,
  max,
  step,
}: {
  name: string;
  label: string;
  value?: string | number;
  type?: string;
  required?: boolean;
  maxLength?: number;
  min?: number;
  max?: number;
  step?: string;
}) {
  return (
    <Field label={label}>
      <input
        name={name}
        type={type}
        defaultValue={value}
        required={required}
        maxLength={maxLength}
        min={min}
        max={max}
        step={step}
      />
    </Field>
  );
}
