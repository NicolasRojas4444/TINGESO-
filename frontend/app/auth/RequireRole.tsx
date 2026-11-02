import type { ReactNode } from "react";
import type { Role } from "../lib/types";
import { useAuth } from "./AuthProvider";

export function RequireRole({
  roles,
  children,
}: {
  roles: Role[];
  children: ReactNode;
}) {
  const auth = useAuth();
  if (!roles.some((role) => auth.roles.includes(role))) {
    return (
      <section className="panel">
        <h1>Acceso restringido</h1>
        <p>Tu cuenta no tiene el rol requerido para esta vista.</p>
      </section>
    );
  }
  return children;
}
