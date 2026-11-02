import { useState } from "react";
import { Link, useParams } from "react-router";
import { useResource } from "~/hooks/useResource";
import type { Plan } from "~/lib/types";
import { programService } from "~/service/programService";
import { studyPlanService } from "~/service/studyPlanService";
import { StudyPlanForm } from "~/components/forms/StudyPlanForm";
import {
  ActionButton,
  Badge,
  PageTitle,
  Resource,
  Table,
} from "~/components/ui";

export default function ProgramDetailView() {
  const id = Number(useParams().id);
  const resource = useResource(
    async (signal) => {
      const [program, plans] = await Promise.all([
        programService.getById(id, signal),
        studyPlanService.getByProgram(id, signal),
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
              <StudyPlanForm
                key={editing === "new" ? "new" : editing.id}
                plan={editing === "new" ? undefined : editing}
                programId={id}
                cancel={() => setEditing(null)}
                saved={() => {
                  setEditing(null);
                  resource.reload();
                }}
              />
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
                            run={() => studyPlanService.setCurrent(plan.id)}
                            onSuccess={resource.reload}
                          >
                            Establecer como vigente
                          </ActionButton>
                        )}
                        <ActionButton
                          danger
                          confirm={`¿Eliminar el plan ${plan.version}? No debe tener estudiantes ni asignaturas.`}
                          run={() => studyPlanService.remove(plan.id)}
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
