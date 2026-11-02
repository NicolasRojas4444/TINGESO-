import { useState } from "react";
import { Link } from "react-router";
import { useResource } from "~/hooks/useResource";
import type { Teacher } from "~/lib/types";
import { teacherService } from "~/service/teacherService";
import { TeacherForm } from "~/components/forms/TeacherForm";
import {
  ActionButton,
  Badge,
  Field,
  Notice,
  PageTitle,
  Resource,
  Table,
} from "~/components/ui";

export default function TeachersView() {
  const resource = useResource((signal) => teacherService.getAll(signal));
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
          <TeacherForm
            key={editing === "new" ? "new" : editing.id}
            teacher={editing === "new" ? undefined : editing}
            cancel={() => setEditing(null)}
            saved={() => {
              setEditing(null);
              setSuccess("Docente guardado correctamente.");
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
                        teacherService.changeStatus(
                          teacher.id,
                          teacher.status === "ACTIVE" ? "INACTIVE" : "ACTIVE",
                        )
                      }
                      onSuccess={resource.reload}
                    >
                      {teacher.status === "ACTIVE" ? "Desactivar" : "Activar"}
                    </ActionButton>
                    <ActionButton
                      danger
                      confirm={`¿Eliminar el perfil de ${teacher.user.fullName}?`}
                      run={() => teacherService.remove(teacher.id)}
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
