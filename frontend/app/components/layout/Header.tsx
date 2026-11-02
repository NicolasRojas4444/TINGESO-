import { Link } from "react-router";
import type { Role } from "../../lib/types";
import { homeForRole, roleLabels } from "../../auth/roles";

export function Header({
  role,
  name,
  logout,
}: {
  role: Role;
  name: string;
  logout: () => void;
}) {
  return (
    <header className="topbar">
      <Link className="brand" to={homeForRole(role)}>
        <span className="brand-mark small">IPT</span>SIGA IPT
      </Link>
      <div className="user-menu">
        <span>{name}</span>
        <span className="role-label">{roleLabels[role]}</span>
        <button onClick={logout}>Cerrar sesión</button>
      </div>
    </header>
  );
}
