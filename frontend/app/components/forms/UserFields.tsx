import type { User } from "../../lib/types";
import { TextField } from "../ui";

export function UserFields({
  user,
}: {
  user?: Pick<User, "run" | "fullName" | "email">;
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
    </>
  );
}
