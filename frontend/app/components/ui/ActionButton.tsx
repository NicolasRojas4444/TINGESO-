import { useState } from "react";
import type { ReactNode } from "react";
import { ApiError, errorMessage } from "../../service/http";
import { Notice } from "./Notice";

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
      const message =
        failure instanceof ApiError &&
        failure.problem?.code === "FINAL_GRADES_MISSING"
          ? `Faltan notas finales en las secciones: ${failure.problem.sectionIds?.join(", ") || "sin identificar"}. Completa sus calificaciones antes de cerrar.`
          : errorMessage(failure);
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
