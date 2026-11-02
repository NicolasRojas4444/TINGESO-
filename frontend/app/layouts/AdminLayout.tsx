import { Outlet } from "react-router";
import { RequireRole } from "../auth/RequireRole";
export default function AdminLayout() {
  return (
    <RequireRole roles={["ADMIN"]}>
      <Outlet />
    </RequireRole>
  );
}
