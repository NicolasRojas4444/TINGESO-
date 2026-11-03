import { useState } from "react";
import { Link } from "react-router";
import { useResource } from "~/hooks/useResource";
import type { Student } from "~/lib/types";
import { programService } from "~/service/programService";
import { studentService } from "~/service/studentService";
import { StudentForm } from "~/components/forms/StudentForm";
import {
  ActionButton,
  Badge,
  Field,
  Notice,
  PageTitle,
  Resource,
  Table,
} from "~/components/ui";

export default function StudentsView() {
  const resource = useResource(async (signal) => {
    const [students, programs] = await Promise.all([
      studentService.getAll(signal),
      programService.getAll(signal),
    ]);
    return { students, programs };
  });
  const [editing, setEditing] = useState<Student | "new" | null>(null);
  const [query, setQuery] = useState("");
  const [success, setSuccess] = useState("");
  const data = resource.data;
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
              <StudentForm
                key={editing === "new" ? "new" : editing.id}
                student={editing === "new" ? undefined : editing}
                programs={data.programs}
                cancel={() => setEditing(null)}
                saved={() => {
                  setEditing(null);
                  setSuccess("Estudiante guardado correctamente.");
                  resource.reload();
                }}
              />
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
                          confirm={`¿Eliminar el perfil de ${student.user.fullName}? No se puede eliminar si tiene inscripciones o historial académico.`}
                          run={() => studentService.remove(student.id)}
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
