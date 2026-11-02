import { useState } from "react";
import { Link } from "react-router";
import { useResource } from "~/hooks/useResource";
import type { Program } from "~/lib/types";
import { programService } from "~/service/programService";
import { ProgramForm } from "~/components/forms/ProgramForm";
import {
  ActionButton,
  Badge,
  Notice,
  PageTitle,
  Resource,
  Table,
} from "~/components/ui";

export default function ProgramsView() {
  const resource = useResource((signal) => programService.getAll(signal));
  const [editing, setEditing] = useState<Program | "new" | null>(null);
  const [success, setSuccess] = useState("");
  return (
    <>
      <PageTitle title="Carreras y planes">
        <button className="primary" onClick={() => setEditing("new")}>
          Nueva carrera
        </button>
      </PageTitle>
      <Notice kind="success">{success}</Notice>
      <Resource {...resource} retry={resource.reload}>
        {editing && (
          <ProgramForm
            key={editing === "new" ? "new" : editing.id}
            program={editing === "new" ? undefined : editing}
            cancel={() => setEditing(null)}
            saved={() => {
              setEditing(null);
              setSuccess("Carrera guardada correctamente.");
              resource.reload();
            }}
          />
        )}
        <section className="panel">
          <p>Duración de cada carrera: cuatro semestres académicos.</p>
          <Table
            headers={["Código", "Nombre", "Descripción", "Estado", "Acciones"]}
            empty={!resource.data?.length}
          >
            {resource.data?.map((program) => (
              <tr key={program.id}>
                <td>{program.code}</td>
                <td>{program.name}</td>
                <td>{program.description}</td>
                <td>
                  <Badge value={program.status} />
                </td>
                <td>
                  <div className="actions">
                    <Link to={`/admin/programs/${program.id}`}>Ver planes</Link>
                    <button onClick={() => setEditing(program)}>Editar</button>
                    <ActionButton
                      run={() =>
                        programService.changeStatus(
                          program.id,
                          program.status === "ACTIVE" ? "INACTIVE" : "ACTIVE",
                        )
                      }
                      onSuccess={resource.reload}
                    >
                      {program.status === "ACTIVE" ? "Desactivar" : "Activar"}
                    </ActionButton>
                    <ActionButton
                      danger
                      confirm={`¿Eliminar la carrera ${program.name}? No puede tener estudiantes ni planes asociados.`}
                      run={() => programService.remove(program.id)}
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
      </Resource>
    </>
  );
}
