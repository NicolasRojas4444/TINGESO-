import { Link, NavLink } from "react-router";
import type { Role } from "../../lib/types";
import { homeForRole, roleLabels } from "../../auth/roles";
import { navigation } from "../../config/navigation";

export function Sidebar({ role, roles }: { role: Role; roles: Role[] }) {
  return (
    <aside className="sidebar">
      <p className="eyebrow">{roleLabels[role]}</p>
      <nav aria-label="Menú principal">
        {navigation[role].map(([path, label]) => (
          <NavLink key={path} to={path}>
            {label}
          </NavLink>
        ))}
      </nav>
      {roles.length > 1 && (
        <div className="role-switch">
          <p>Probar otro rol</p>
          {roles.map((item) => (
            <Link key={item} to={homeForRole(item)}>
              {roleLabels[item]}
            </Link>
          ))}
        </div>
      )}
      <p className="sidebar-note">Versión inicial · Datos reales del backend</p>
    </aside>
  );
}
