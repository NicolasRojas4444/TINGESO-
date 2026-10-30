import { useState } from "react";
import { Link, useParams } from "react-router";
import { api, get } from "../lib/api";
import { useResource } from "../lib/hooks";
import type { Plan, Program } from "../lib/types";
import {
  ActionButton,
  Badge,
  Field,
  FormPanel,
  PageTitle,
  Resource,
  Table,
  TextField,
  text,
} from "../components/ui";

export default function ProgramDetail() {
  const { id } = useParams();
  const resource = useResource(
    async (signal) => {
      const [program, plans] = await Promise.all([
        get<Program>(`/degree-programs/${id}`, signal),
        get<Plan[]>(`/study-plans/by-degree-program/${id}`, signal),
      ]);
      return { program, plans };
    },
    [id],
  );
  const [editing, setEditing] = useState<Plan | "new" | null>(null);
  const data = resource.data;
  return (
    <>
      <Link to="/admin/programs">← Carreras</Link>
      <PageTitle title={data?.program.name || "Planes de estudio"}>
        <button className="primary" onClick={() => setEditing("new")}>
          Nuevo plan
        </button>
      </PageTitle>
      <Resource {...resource} retry={resource.reload}>
        {data && (
          <>
            {editing && (
              <FormPanel
                key={editing === "new" ? "new" : editing.id}
                title={
                  editing === "new"
                    ? "Crear plan de estudios"
                    : "Editar versión"
                }
                cancel={() => setEditing(null)}
                submit={async (form) => {
                  if (editing === "new") {
                    if (
                      text(form, "status") === "CURRENT" &&
                      !window.confirm(
                        "¿Establecer el nuevo plan como vigente? El plan vigente anterior pasará a no vigente; los estudiantes conservarán su plan.",
                      )
                    )
                      return;
                    await api.post("/study-plans", {
                      degreeProgramId: Number(id),
                      version: text(form, "version"),
                      status: text(form, "status"),
                    });
                  } else
                    await api.put(`/study-plans/${editing.id}`, {
                      version: text(form, "version"),
                    });
                  setEditing(null);
                  resource.reload();
                }}
              >
                <TextField
                  name="version"
                  label="Versión / identificador"
                  maxLength={30}
                  value={editing === "new" ? "" : editing.version}
                />
                {editing === "new" && (
                  <Field label="Estado inicial">
                    <select name="status">
                      <option value="NON_CURRENT">No vigente</option>
                      <option value="CURRENT">Vigente</option>
                    </select>
                  </Field>
                )}
              </FormPanel>
            )}
            <section className="panel">
              <p>
                Los cambios de vigencia no trasladan a los estudiantes de planes
                anteriores.
              </p>
              <Table
                headers={["Versión", "Estado", "Acciones"]}
                empty={!data.plans.length}
              >
                {data.plans.map((plan) => (
                  <tr key={plan.id}>
                    <td>{plan.version}</td>
                    <td>
                      <Badge value={plan.status} />
                    </td>
                    <td>
                      <div className="actions">
                        <Link to={`/admin/plans/${plan.id}`}>
                          Ver asignaturas
                        </Link>
                        <button onClick={() => setEditing(plan)}>Editar</button>
                        {plan.status !== "CURRENT" && (
                          <ActionButton
                            confirm="¿Establecer este plan como vigente y dejar el anterior como no vigente?"
                            run={() =>
                              api.put(`/study-plans/${plan.id}/current`)
                            }
                            onSuccess={resource.reload}
                          >
                            Establecer como vigente
                          </ActionButton>
                        )}
                        <ActionButton
                          danger
                          confirm={`¿Eliminar el plan ${plan.version}? No debe tener estudiantes ni asignaturas.`}
                          run={() => api.delete(`/study-plans/${plan.id}`)}
                          onSuccess={resource.reload}
                        >
                          Eliminar
                        </ActionButton>
                      </div>
                    </td>
                  </tr>
                ))}
              </Table>
            </section>
          </>
        )}
      </Resource>
    </>
  );
}
