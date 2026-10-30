import { Link, NavLink, Outlet, useLocation } from "react-router";
import { homeForRole, roleLabels, useAuth } from "../lib/auth";
import type { Role } from "../lib/types";
import { Notice } from "../components/ui";

const navigation: Record<Role, [string, string][]> = {
  ADMIN: [
    ["/admin/students", "Estudiantes"],
    ["/admin/teachers", "Docentes"],
    ["/admin/programs", "Carreras y planes"],
    ["/admin/periods", "Períodos y oferta"],
  ],
  TEACHER: [["/teacher/sections", "Mis secciones"]],
  STUDENT: [
    ["/student/profile", "Mi información"],
    ["/student/plan", "Mi plan de estudios"],
    ["/student/registration", "Inscripción académica"],
    ["/student/history", "Historial académico"],
  ],
};
export default function Workspace() {
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
      <header className="topbar">
        <Link className="brand" to={homeForRole(role)}>
          <span className="brand-mark small">IPT</span>SIGA IPT
        </Link>
        <div className="user-menu">
          <span>{auth.name}</span>
          <span className="role-label">{roleLabels[role]}</span>
          <button onClick={auth.logout}>Cerrar sesión</button>
        </div>
      </header>
      <aside className="sidebar">
        <p className="eyebrow">{roleLabels[role]}</p>
        <nav aria-label="Menú principal">
          {navigation[role].map(([path, label]) => (
            <NavLink key={path} to={path}>
              {label}
            </NavLink>
          ))}
        </nav>
        {auth.roles.length > 1 && (
          <div className="role-switch">
            <p>Probar otro rol</p>
            {auth.roles.map((item) => (
              <Link key={item} to={homeForRole(item)}>
                {roleLabels[item]}
              </Link>
            ))}
          </div>
        )}
        <p className="sidebar-note">
          Versión inicial · Datos reales del backend
        </p>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
