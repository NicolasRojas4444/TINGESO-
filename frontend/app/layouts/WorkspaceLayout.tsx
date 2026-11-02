import { Outlet, useLocation } from "react-router";
import { useAuth } from "~/auth/AuthProvider";
import type { Role } from "~/lib/types";
import { Header } from "~/components/layout/Header";
import { Sidebar } from "~/components/layout/Sidebar";
import { Notice } from "~/components/ui";

export default function WorkspaceLayout() {
  const auth = useAuth();
  const location = useLocation();
  if (!auth.ready)
    return (
      <main className="login-page">
        <p role="status">Conectando…</p>
      </main>
    );
  if (auth.error)
    return (
      <main className="login-page">
        <Notice>{auth.error}</Notice>
        <a href="/">Volver al inicio</a>
      </main>
    );
  if (!auth.authenticated)
    return (
      <main className="login-page">
        <section className="panel">
          <h1>Inicia sesión para continuar</h1>
          <button className="primary" onClick={auth.login}>
            Iniciar sesión
          </button>
        </section>
      </main>
    );
  if (!auth.roles.length)
    return (
      <main className="panel">
        <Notice>Tu cuenta no tiene permisos académicos.</Notice>
        <button onClick={auth.logout}>Cerrar sesión</button>
      </main>
    );
  const requested: Role = location.pathname.startsWith("/student")
    ? "STUDENT"
    : location.pathname.startsWith("/teacher")
      ? "TEACHER"
      : location.pathname.startsWith("/admin")
        ? "ADMIN"
        : auth.roles.includes("ADMIN")
          ? "ADMIN"
          : "TEACHER";
  const role = auth.roles.includes(requested) ? requested : auth.roles[0];
  return (
    <div className="app-shell">
      <Header role={role} name={auth.name} logout={auth.logout} />
      <Sidebar role={role} roles={auth.roles} />
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
