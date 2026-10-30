import { useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { ApiError, errorMessage } from "../lib/api";

export function Notice({
  children,
  kind = "error",
}: {
  children: ReactNode;
  kind?: "error" | "success" | "info";
}) {
  return children ? (
    <div
      role={kind === "error" ? "alert" : "status"}
      className={`notice ${kind}`}
    >
      {children}
    </div>
  ) : null;
}
export function Resource({
  loading,
  error,
  retry,
  children,
}: {
  loading: boolean;
  error: string;
  retry: () => void;
  children: ReactNode;
}) {
  if (loading)
    return (
      <p role="status" className="panel">
        Cargando datos…
      </p>
    );
  if (error)
    return (
      <div className="panel">
        <Notice>{error}</Notice>
        <button onClick={retry}>Reintentar</button>
      </div>
    );
  return <>{children}</>;
}
export function PageTitle({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-title">
      <h1>{title}</h1>
      <div className="actions">{children}</div>
    </div>
  );
}
export function Badge({ value }: { value: string }) {
  const labels: Record<string, string> = {
    ACTIVE: "Activo",
    INACTIVE: "Inactivo",
    REGULAR: "Regular",
    POSTPONED: "Postergación",
    TEMPORARY_WITHDRAWAL: "Retiro temporal",
    GRADUATED: "Egresado",
    ELIMINATED: "Eliminado",
    CURRENT: "Vigente",
    NON_CURRENT: "No vigente",
    OPEN: "Abierto",
    CLOSED: "Cerrado",
    PASSED: "Aprobado",
    FAILED: "Reprobado",
    CANCELLED: "Anulada",
  };
  return (
    <span
      className={`badge ${["ACTIVE", "REGULAR", "CURRENT", "OPEN", "PASSED"].includes(value) ? "positive" : "neutral"}`}
    >
      {labels[value] || value}
    </span>
  );
}
export function Table({
  headers,
  children,
  empty,
}: {
  headers: string[];
  children: ReactNode;
  empty?: boolean;
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {headers.map((header) => (
              <th key={header}>{header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {empty ? (
            <tr>
              <td colSpan={headers.length} className="empty">
                No hay registros para mostrar.
              </td>
            </tr>
          ) : (
            children
          )}
        </tbody>
      </table>
    </div>
  );
}
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
export function FormPanel({
  title,
  submit,
  children,
  cancel,
  label = "Guardar",
}: {
  title: string;
  submit: (data: FormData) => Promise<void>;
  children: ReactNode;
  cancel?: () => void;
  label?: string;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      await submit(data);
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="panel">
      <h2>{title}</h2>
      <form onSubmit={save}>
        <fieldset disabled={busy}>
          <div className="form-grid">{children}</div>
          <Notice>{error}</Notice>
          <div className="actions">
            <button type="submit" className="primary">
              {busy ? "Guardando…" : label}
            </button>
            {cancel && (
              <button type="button" onClick={cancel}>
                Cancelar
              </button>
            )}
          </div>
        </fieldset>
      </form>
    </section>
  );
}
export function ActionButton({
  run,
  children,
  confirm,
  danger = false,
  onSuccess,
  disabled = false,
}: {
  run: () => Promise<unknown>;
  children: ReactNode;
  confirm?: string;
  danger?: boolean;
  onSuccess?: () => void;
  disabled?: boolean;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function execute() {
    if (busy || (confirm && !window.confirm(confirm))) return;
    setBusy(true);
    setError("");
    try {
      await run();
      onSuccess?.();
    } catch (failure) {
      let message = errorMessage(failure);
      if (
        failure instanceof ApiError &&
        failure.problem?.code === "FINAL_GRADES_MISSING"
      ) {
        message = `Faltan notas finales en las secciones: ${failure.problem.sectionIds?.join(", ") || "sin identificar"}. Completa sus calificaciones antes de cerrar.`;
      }
      setError(message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="inline-action">
      <button
        type="button"
        disabled={busy || disabled}
        className={danger ? "danger" : ""}
        onClick={execute}
      >
        {busy ? "Procesando…" : children}
      </button>
      <Notice>{error}</Notice>
    </div>
  );
}
export const text = (data: FormData, name: string) =>
  String(data.get(name) || "").trim();
export const number = (data: FormData, name: string) => Number(data.get(name));
export function UserFields({
  user,
  creating = false,
}: {
  user?: {
    run: string;
    fullName: string;
    email: string;
    keycloakSubject: string;
  };
  creating?: boolean;
}) {
  return (
    <>
      <TextField name="run" label="RUN" value={user?.run} maxLength={12} />
      <TextField
        name="fullName"
        label="Nombre completo"
        value={user?.fullName}
        maxLength={200}
      />
      <TextField
        name="email"
        label="Correo electrónico"
        type="email"
        value={user?.email}
        maxLength={254}
      />
      {creating && (
        <TextField
          name="keycloakSubject"
          label="ID de cuenta Keycloak (existente, habilitada y con el rol requerido)"
          maxLength={255}
        />
      )}
    </>
  );
}
export function userInput(data: FormData) {
  return {
    run: text(data, "run"),
    fullName: text(data, "fullName"),
    email: text(data, "email"),
    keycloakSubject: text(data, "keycloakSubject") || undefined,
  };
}
