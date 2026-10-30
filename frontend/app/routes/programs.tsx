import { useState } from "react";
import { Link } from "react-router";
import { api, get } from "../lib/api";
import { useResource } from "../lib/hooks";
import type { Program } from "../lib/types";
import {
  ActionButton,
  Badge,
  Field,
  FormPanel,
  Notice,
  PageTitle,
  Resource,
  Table,
  TextField,
  text,
} from "../components/ui";

export default function Programs() {
  const resource = useResource((signal) =>
    get<Program[]>("/degree-programs", signal),
  );
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
          <FormPanel
            key={editing === "new" ? "new" : editing.id}
            title={editing === "new" ? "Crear carrera" : "Editar carrera"}
            cancel={() => setEditing(null)}
            submit={async (form) => {
              const body = {
                code: text(form, "code"),
                name: text(form, "name"),
                description: text(form, "description"),
              };
              if (editing === "new") await api.post("/degree-programs", body);
              else await api.put(`/degree-programs/${editing.id}`, body);
              setEditing(null);
              setSuccess("Carrera guardada correctamente.");
              resource.reload();
            }}
          >
            <TextField
              name="code"
              label="Código"
              value={editing === "new" ? "" : editing.code}
              maxLength={30}
            />
            <TextField
              name="name"
              label="Nombre"
              value={editing === "new" ? "" : editing.name}
              maxLength={200}
            />
            <Field label="Descripción">
              <textarea
                name="description"
                defaultValue={editing === "new" ? "" : editing.description}
                required
                maxLength={2000}
              />
            </Field>
          </FormPanel>
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
                        api.put(`/degree-programs/${program.id}/status`, {
                          status:
                            program.status === "ACTIVE" ? "INACTIVE" : "ACTIVE",
                        })
                      }
                      onSuccess={resource.reload}
                    >
                      {program.status === "ACTIVE" ? "Desactivar" : "Activar"}
                    </ActionButton>
                    <ActionButton
                      danger
                      confirm={`¿Eliminar la carrera ${program.name}? No puede tener estudiantes ni planes asociados.`}
                      run={() => api.delete(`/degree-programs/${program.id}`)}
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
