import { Field } from "../ui";

export function AccountFields() {
  return (
    <>
      <p>
        Se creará una cuenta en Keycloak con el rol correspondiente y su perfil
        académico.
      </p>
      <Field label="Nombre de usuario">
        <input
          name="username"
          required
          minLength={3}
          maxLength={80}
          pattern="[A-Za-z0-9._\-]{3,80}"
          autoComplete="off"
        />
      </Field>
      <Field label="Contraseña">
        <input
          name="password"
          type="password"
          required
          minLength={8}
          maxLength={128}
          autoComplete="new-password"
        />
      </Field>
      <Field label="Confirmar contraseña">
        <input
          name="confirmPassword"
          type="password"
          required
          minLength={8}
          maxLength={128}
          autoComplete="new-password"
        />
      </Field>
    </>
  );
}
