import type { ReactNode } from "react";

export function Notice({
  children,
  kind = "error",
}: {
  children: ReactNode;
  kind?: "error" | "success" | "info";
}) {
  return children ? (
    <div
      role={kind === "error" ? "alert" : "status"}
      className={`notice ${kind}`}
    >
      {children}
    </div>
  ) : null;
}
