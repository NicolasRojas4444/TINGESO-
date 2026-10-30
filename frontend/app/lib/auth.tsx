import Keycloak from "keycloak-js";
import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { configureApi, errorMessage } from "./api";
import type { Role, RuntimeConfig } from "./types";

export const roleLabels: Record<Role, string> = {
  ADMIN: "Administración",
  TEACHER: "Docencia",
  STUDENT: "Estudiante",
};
export function homeForRole(role: Role) {
  return role === "ADMIN"
    ? "/admin/students"
    : role === "TEACHER"
      ? "/teacher/sections"
      : "/student/profile";
}
export interface AuthState {
  ready: boolean;
  authenticated: boolean;
  roles: Role[];
  name: string;
  error?: string;
  login: () => void;
  logout: () => void;
}
const AuthContext = createContext<AuthState | null>(null);
let initialization: Promise<Keycloak> | undefined;
function initialize() {
  initialization ??= (async () => {
    const response = await fetch("/config.json", { cache: "no-store" });
    if (!response.ok)
      throw new Error("No se pudo cargar la configuración del frontend.");
    const config: RuntimeConfig = await response.json();
    if (
      !config.apiUrl ||
      !config.keycloakUrl ||
      !config.keycloakRealm ||
      !config.keycloakClientId
    ) {
      throw new Error("La configuración pública del frontend está incompleta.");
    }
    const keycloak = new Keycloak({
      url: config.keycloakUrl,
      realm: config.keycloakRealm,
      clientId: config.keycloakClientId,
    });
    await keycloak.init({
      onLoad: "check-sso",
      pkceMethod: "S256",
      checkLoginIframe: false,
      responseMode: "query",
    });
    configureApi(config, async () => {
      if (!keycloak.authenticated) return undefined;
      try {
        await keycloak.updateToken(30);
      } catch {
        throw new Error("La sesión expiró. Vuelve a iniciar sesión.");
      }
      return keycloak.token;
    });
    return keycloak;
  })();
  return initialization;
}
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    ready: false,
    authenticated: false,
    roles: [],
    name: "",
    login: () => {},
    logout: () => {},
  });
  useEffect(() => {
    let active = true;
    initialize()
      .then((keycloak) => {
        const update = () => {
          if (!active) return;
          const roles = (["ADMIN", "TEACHER", "STUDENT"] as Role[]).filter(
            (role) => keycloak.hasRealmRole(role),
          );
          setState({
            ready: true,
            authenticated: !!keycloak.authenticated,
            roles,
            name: String(
              keycloak.tokenParsed?.name ||
                keycloak.tokenParsed?.preferred_username ||
                "Usuario",
            ),
            login: () => {
              void keycloak.login({
                redirectUri: window.location.origin + "/",
              });
            },
            logout: () => {
              void keycloak.logout({
                redirectUri: window.location.origin + "/",
              });
            },
          });
        };
        keycloak.onAuthLogout = update;
        keycloak.onAuthRefreshSuccess = update;
        keycloak.onTokenExpired = () => {
          void keycloak.updateToken(30).catch(() => {
            keycloak.clearToken();
          });
        };
        update();
      })
      .catch((error) => {
        if (active)
          setState((previous) => ({
            ...previous,
            ready: true,
            error: errorMessage(error),
          }));
      });
    return () => {
      active = false;
    };
  }, []);
  return <AuthContext.Provider value={state}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error("AuthProvider is required");
  return auth;
}
export function RequireRole({
  roles,
  children,
}: {
  roles: Role[];
  children: ReactNode;
}) {
  const auth = useAuth();
  if (!roles.some((role) => auth.roles.includes(role))) {
    return (
      <section className="panel">
        <h1>Acceso restringido</h1>
        <p>Tu cuenta no tiene el rol requerido para esta vista.</p>
      </section>
    );
  }
  return children;
}
