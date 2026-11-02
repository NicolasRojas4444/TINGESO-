const labels: Record<string, string> = {
  ACTIVE: "Activo",
  INACTIVE: "Inactivo",
  REGULAR: "Regular",
  POSTPONED: "Postergación",
  TEMPORARY_WITHDRAWAL: "Retiro temporal",
  GRADUATED: "Egresado",
  ELIMINATED: "Eliminado",
  CURRENT: "Vigente",
  NON_CURRENT: "No vigente",
  OPEN: "Abierto",
  CLOSED: "Cerrado",
  PASSED: "Aprobado",
  FAILED: "Reprobado",
  CANCELLED: "Anulada",
};
export function Badge({ value }: { value: string }) {
  const positive = ["ACTIVE", "REGULAR", "CURRENT", "OPEN", "PASSED"].includes(
    value,
  );
  return (
    <span className={`badge ${positive ? "positive" : "neutral"}`}>
      {labels[value] || value}
    </span>
  );
}
