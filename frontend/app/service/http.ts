import axios from "axios";
import type { RuntimeConfig } from "../lib/types";

export interface Problem {
  detail?: string;
  code?: string;
  sectionIds?: number[];
  status?: number;
}
export class ApiError extends Error {
  constructor(
    message: string,
    public status?: number,
    public problem?: Problem,
  ) {
    super(message);
  }
}
const genericError = "No se pudo completar la operación. Inténtalo nuevamente.";
const hasTechnicalNames = (message: string) =>
  /keycloak|back[\s-]?end|front[\s-]?end/i.test(message);
export function errorMessage(error: unknown): string {
  return error instanceof Error && !hasTechnicalNames(error.message)
    ? error.message
    : genericError;
}
export function normalizeError(error: unknown): ApiError {
  if (error instanceof ApiError)
    return hasTechnicalNames(error.message)
      ? new ApiError(errorMessage(error), error.status, error.problem)
      : error;
  if (!axios.isAxiosError<Problem>(error))
    return new ApiError(errorMessage(error));
  const status = error.response?.status;
  const problem = error.response?.data;
  const defaults: Record<number, string> = {
    400: "Revisa los datos ingresados e inténtalo nuevamente.",
    401: "La sesión no es válida o ha expirado. Vuelve a iniciar sesión.",
    403: "No tienes permiso para esta operación o tu cuenta está suspendida.",
    404: "No se encontró el recurso solicitado.",
    409: "La operación entra en conflicto con el estado actual de los datos.",
    500: genericError,
    503: "El servicio no está disponible. Inténtalo nuevamente.",
  };
  const detail = status && [401, 500, 503].includes(status)
    ? defaults[status]
    : problem?.detail;
  return new ApiError(
    (typeof detail === "string" && !hasTechnicalNames(detail) && detail) ||
      (status && defaults[status]) ||
      (status ? genericError : "No se pudo conectar con SIGA IPT. Comprueba la conexión."),
    status,
    problem,
  );
}
export const api = axios.create({ timeout: 20000 });
export function configureApi(
  config: RuntimeConfig,
  token: () => Promise<string | undefined>,
) {
  api.defaults.baseURL = config.apiUrl.replace(/\/$/, "");
  api.interceptors.request.use(async (request) => {
    const value = await token();
    if (!value) throw new ApiError("Inicia sesión para continuar.", 401);
    request.headers.set("Authorization", `Bearer ${value}`);
    return request;
  });
}
api.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(normalizeError(error)),
);
export async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  return (await api.get<T>(path, { signal })).data;
}
