import { useState } from "react";
import { Link } from "react-router";
import { academicPeriodService } from "~/service/academicPeriodService";
import { useResource } from "~/hooks/useResource";
import { periodName } from "~/components/academic";
import { number } from "~/utils/form";
import {
  Badge,
  Field,
  FormPanel,
  PageTitle,
  Resource,
  Table,
  TextField,
} from "~/components/ui";

export default function PeriodsView() {
  const resource = useResource((signal) =>
    academicPeriodService.getAll(signal),
  );
  const [creating, setCreating] = useState(false);
  const rows = [...(resource.data || [])].sort(
    (a, b) => b.year - a.year || b.semester - a.semester,
  );
  return (
    <>
      <PageTitle title="Períodos y oferta">
        <button className="primary" onClick={() => setCreating(true)}>
          Nuevo período
        </button>
      </PageTitle>
      <Resource {...resource} retry={resource.reload}>
        {creating && (
          <FormPanel
            title="Crear período académico"
            cancel={() => setCreating(false)}
            submit={async (form) => {
              await academicPeriodService.create({
                year: number(form, "year"),
                semester: number(form, "semester"),
              });
              setCreating(false);
              resource.reload();
            }}
          >
            <TextField
              name="year"
              label="Año"
              type="number"
              min={1}
              value={new Date().getFullYear()}
            />
            <Field label="Semestre">
              <select name="semester">
                <option value="1">1</option>
                <option value="2">2</option>
              </select>
            </Field>
          </FormPanel>
        )}
        <section className="panel">
          <Table
            headers={["Período", "Estado", "Fecha de cierre", "Acciones"]}
            empty={!rows.length}
          >
            {rows.map((period) => (
              <tr key={period.id}>
                <td>{periodName(period)}</td>
                <td>
                  <Badge value={period.status} />
                </td>
                <td>
                  {period.closedAt
                    ? new Date(period.closedAt).toLocaleString("es-CL")
                    : "—"}
                </td>
                <td>
                  <Link to={`/admin/periods/${period.id}`}>
                    Ver oferta y cierre
                  </Link>
                </td>
              </tr>
            ))}
          </Table>
        </section>
      </Resource>
    </>
  );
}
