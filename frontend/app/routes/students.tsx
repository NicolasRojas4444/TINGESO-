import { useState } from "react";
import { Link } from "react-router";
import { api, get } from "../lib/api";
import { useResource } from "../lib/hooks";
import type { Program, Student } from "../lib/types";
import {
  ActionButton,
  Badge,
  Field,
  FormPanel,
  Notice,
  PageTitle,
  Resource,
  Table,
  UserFields,
  number,
  userInput,
} from "../components/ui";

export default function Students() {
  const resource = useResource(async (signal) => {
    const [students, programs] = await Promise.all([
      get<Student[]>("/students", signal),
      get<Program[]>("/degree-programs", signal),
    ]);
    return { students, programs };
  });
  const [editing, setEditing] = useState<Student | "new" | null>(null);
  const [query, setQuery] = useState("");
  const [success, setSuccess] = useState("");
  const data = resource.data;
  const saved = () => {
    setEditing(null);
    setSuccess("Estudiante guardado correctamente.");
    resource.reload();
  };
  const rows =
    data?.students.filter((student) =>
      `${student.user.run} ${student.user.fullName} ${student.user.email}`
        .toLowerCase()
        .includes(query.toLowerCase()),
    ) || [];
  return (
    <>
      <PageTitle title="Estudiantes">
        <button
          className="primary"
          onClick={() => {
            setSuccess("");
            setEditing("new");
          }}
        >
          Nuevo estudiante
        </button>
      </PageTitle>
      <Notice kind="success">{success}</Notice>
      <Resource {...resource} retry={resource.reload}>
        {data && (
          <>
            {editing && (
              <FormPanel
                key={editing === "new" ? "new" : editing.id}
                title={
                  editing === "new"
                    ? "Registrar estudiante"
                    : "Editar estudiante"
                }
                cancel={() => setEditing(null)}
                submit={async (form) => {
                  const body = {
                    user: userInput(form),
                    degreeProgramId: number(form, "degreeProgramId"),
                  };
                  if (editing === "new") await api.post("/students", body);
                  else
                    await api.put(`/students/${editing.id}`, {
                      user: body.user,
                    });
                  saved();
                }}
              >
                <UserFields
                  creating={editing === "new"}
                  user={editing === "new" ? undefined : editing.user}
                />
                {editing === "new" && (
                  <Field label="Carrera">
                    <select name="degreeProgramId" required defaultValue="">
                      <option value="" disabled>
                        Selecciona una carrera
                      </option>
                      {data.programs
                        .filter((program) => program.status === "ACTIVE")
                        .map((program) => (
                          <option key={program.id} value={program.id}>
                            {program.name}
                          </option>
                        ))}
                    </select>
                  </Field>
                )}
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
                  "Carrera",
                  "Estado",
                  "Acciones",
                ]}
                empty={!rows.length}
              >
                {rows.map((student) => (
                  <tr key={student.id}>
                    <td>{student.user.run}</td>
                    <td>{student.user.fullName}</td>
                    <td>{student.user.email}</td>
                    <td>
                      {data.programs.find(
                        (program) => program.id === student.degreeProgramId,
                      )?.name || student.degreeProgramId}
                    </td>
                    <td>
                      <Badge value={student.academicStatus} />
                    </td>
                    <td>
                      <div className="actions">
                        <Link to={`/admin/students/${student.id}`}>
                          Ver detalle
                        </Link>
                        <button onClick={() => setEditing(student)}>
                          Editar
                        </button>
                        <ActionButton
                          danger
                          confirm={`¿Eliminar el perfil de ${student.user.fullName}? El backend rechazará la operación si tiene actividad protegida.`}
                          run={() => api.delete(`/students/${student.id}`)}
                          onSuccess={() => {
                            setEditing(null);
                            setSuccess(
                              "Perfil eliminado. La identidad se conserva según su política de acceso.",
                            );
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
          </>
        )}
      </Resource>
    </>
  );
}
