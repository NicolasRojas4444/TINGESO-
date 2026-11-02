import type { User } from "../../lib/types";
import { TextField } from "../ui";

export function UserFields({
  user,
  creating = false,
}: {
  user?: Pick<User, "run" | "fullName" | "email" | "keycloakSubject">;
  creating?: boolean;
}) {
  return (
    <>
      <TextField name="run" label="RUN" value={user?.run} maxLength={12} />
      <TextField
        name="fullName"
        label="Nombre completo"
        value={user?.fullName}
        maxLength={200}
      />
      <TextField
        name="email"
        label="Correo electrónico"
        type="email"
        value={user?.email}
        maxLength={254}
      />
      {creating && (
        <TextField
          name="keycloakSubject"
          label="ID de cuenta Keycloak (existente, habilitada y con el rol requerido)"
          maxLength={255}
        />
      )}
    </>
  );
}
