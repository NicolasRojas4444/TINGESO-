import { useState } from "react";
import { Link } from "react-router";
import { api, get } from "../lib/api";
import { useResource } from "../lib/hooks";
import type { Teacher } from "../lib/types";
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
  UserFields,
  text,
  userInput,
} from "../components/ui";

export default function Teachers() {
  const resource = useResource((signal) => get<Teacher[]>("/teachers", signal));
  const [editing, setEditing] = useState<Teacher | "new" | null>(null);
  const [query, setQuery] = useState("");
  const [success, setSuccess] = useState("");
  const rows =
    resource.data?.filter((teacher) =>
      `${teacher.user.run} ${teacher.user.fullName} ${teacher.user.email}`
        .toLowerCase()
        .includes(query.toLowerCase()),
    ) || [];
  return (
    <>
      <PageTitle title="Docentes">
        <button
          className="primary"
          onClick={() => {
            setSuccess("");
            setEditing("new");
          }}
        >
          Nuevo docente
        </button>
      </PageTitle>
      <Notice kind="success">{success}</Notice>
      <Resource {...resource} retry={resource.reload}>
        {editing && (
          <FormPanel
            key={editing === "new" ? "new" : editing.id}
            title={editing === "new" ? "Registrar docente" : "Editar docente"}
            cancel={() => setEditing(null)}
            submit={async (form) => {
              const body = {
                user: userInput(form),
                professionalTitle: text(form, "professionalTitle"),
                academicDegree: text(form, "academicDegree"),
              };
              if (editing === "new") await api.post("/teachers", body);
              else await api.put(`/teachers/${editing.id}`, body);
              setEditing(null);
              setSuccess("Docente guardado correctamente.");
              resource.reload();
            }}
          >
            <UserFields
              creating={editing === "new"}
              user={editing === "new" ? undefined : editing.user}
            />
            <TextField
              name="professionalTitle"
              label="Título profesional"
              value={editing === "new" ? "" : editing.professionalTitle}
              maxLength={200}
            />
            <TextField
              name="academicDegree"
              label="Grado académico"
              value={editing === "new" ? "" : editing.academicDegree}
              maxLength={200}
            />
          </FormPanel>
        )}
        <section className="panel">
          <Field label="Buscar por nombre, RUN o correo">
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </Field>
          <Table
            headers={[
              "RUN",
              "Nombre",
              "Correo",
              "Título / grado",
              "Estado",
              "Acciones",
            ]}
            empty={!rows.length}
          >
            {rows.map((teacher) => (
              <tr key={teacher.id}>
                <td>{teacher.user.run}</td>
                <td>{teacher.user.fullName}</td>
                <td>{teacher.user.email}</td>
                <td>
                  {teacher.professionalTitle}
                  <small>{teacher.academicDegree}</small>
                </td>
                <td>
                  <Badge value={teacher.status} />
                </td>
                <td>
                  <div className="actions">
                    <Link to={`/admin/teachers/${teacher.id}`}>
                      Ver detalle
                    </Link>
                    <button onClick={() => setEditing(teacher)}>Editar</button>
                    <ActionButton
                      run={() =>
                        api.put(`/teachers/${teacher.id}/status`, {
                          status:
                            teacher.status === "ACTIVE" ? "INACTIVE" : "ACTIVE",
                        })
                      }
                      onSuccess={resource.reload}
                    >
                      {teacher.status === "ACTIVE" ? "Desactivar" : "Activar"}
                    </ActionButton>
                    <ActionButton
                      danger
                      confirm={`¿Eliminar el perfil de ${teacher.user.fullName}?`}
                      run={() => api.delete(`/teachers/${teacher.id}`)}
                      onSuccess={() => {
                        setEditing(null);
                        resource.reload();
                      }}
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
