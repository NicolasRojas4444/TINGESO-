import type { ReactNode } from "react";
import { Notice } from "./Notice";

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
  return children;
}
