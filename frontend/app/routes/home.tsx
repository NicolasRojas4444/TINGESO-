import type { Route } from "./+types/home";
import { Navigate } from "react-router";
import { homeForRole, useAuth } from "../lib/auth";
import { Notice } from "../components/ui";

export function meta({}: Route.MetaArgs) {
  return [
    { title: "SIGA IPT · Gestión académica" },
    {
      name: "description",
      content: "Sistema Integrado de Gestión Académica del IPT",
    },
  ];
}

export default function Home() {
  const auth = useAuth();
  if (auth.ready && auth.authenticated && auth.roles.length)
    return <Navigate to={homeForRole(auth.roles[0])} replace />;
  return (
    <main className="login-page">
      <section className="login-card">
        <div className="brand-mark">IPT</div>
        <p className="eyebrow">GESTIÓN ACADÉMICA</p>
        <h1>SIGA IPT</h1>
        <p>
          Administra la oferta académica, inscripciones y resultados del
          instituto.
        </p>
        {!auth.ready ? (
          <p role="status">Conectando con Keycloak…</p>
        ) : auth.error ? (
          <>
            <Notice>{auth.error}</Notice>
            <button onClick={() => window.location.reload()}>
              Reintentar conexión
            </button>
          </>
        ) : auth.authenticated ? (
          <>
            <Notice>
              Tu cuenta no tiene un rol académico asignado. Solicita a ADMIN que
              la revise en Keycloak.
            </Notice>
            <button onClick={auth.logout}>Cerrar sesión</button>
          </>
        ) : (
          <button className="primary" onClick={auth.login}>
            Iniciar sesión con Keycloak
          </button>
        )}
        <small>Las credenciales se ingresan únicamente en Keycloak.</small>
      </section>
    </main>
  );
}
