import Keycloak from "keycloak-js";
import { configureApi } from "../service/http";
import { loadRuntimeConfig } from "../service/runtimeConfigService";

// One adapter initialization also protects React StrictMode from duplicate login flows.
let initialization: Promise<Keycloak> | undefined;
export function initializeKeycloak() {
  initialization ??= (async () => {
    const config = await loadRuntimeConfig();
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
