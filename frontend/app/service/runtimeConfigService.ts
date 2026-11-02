import type { RuntimeConfig } from "../lib/types";

export async function loadRuntimeConfig(): Promise<RuntimeConfig> {
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
  return config;
}
