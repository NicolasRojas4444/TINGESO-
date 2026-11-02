import { useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { errorMessage } from "../../service/http";
import { Notice } from "./Notice";

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
      {title && <h2>{title}</h2>}
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
