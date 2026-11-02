import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { errorMessage } from "../service/http";
import type { Role } from "../lib/types";
import { initializeKeycloak } from "../config/keycloak";
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
    initializeKeycloak()
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
