import { Outlet } from "react-router";
import { RequireRole } from "../auth/RequireRole";
export default function StudentLayout() {
  return (
    <RequireRole roles={["STUDENT"]}>
      <Outlet />
    </RequireRole>
  );
}
