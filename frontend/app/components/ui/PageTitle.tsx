import type { ReactNode } from "react";
export function PageTitle({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-title">
      <h1>{title}</h1>
      <div className="actions">{children}</div>
    </div>
  );
}
