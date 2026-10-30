import { Outlet } from "react-router";
import { RequireRole } from "../lib/auth";
export default function Admin() {
  return (
    <RequireRole roles={["ADMIN"]}>
      <Outlet />
    </RequireRole>
  );
}
