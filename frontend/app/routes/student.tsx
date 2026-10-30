import { Outlet } from "react-router";
import { RequireRole } from "../lib/auth";
export default function StudentArea() {
  return (
    <RequireRole roles={["STUDENT"]}>
      <Outlet />
    </RequireRole>
  );
}
