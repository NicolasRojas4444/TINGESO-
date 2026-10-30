import axios from "axios";
import type { RuntimeConfig } from "./types";

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
export function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "No se pudo completar la operación.";
}
export function normalizeError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (!axios.isAxiosError<Problem>(error))
    return new ApiError(errorMessage(error));
  const status = error.response?.status;
  const problem = error.response?.data;
  const defaults: Record<number, string> = {
    401: "La sesión no es válida o ha expirado. Vuelve a iniciar sesión.",
    403: "No tienes permiso para esta operación o tu cuenta está suspendida.",
    404: "No se encontró el recurso solicitado.",
    409: "La operación entra en conflicto con el estado actual de los datos.",
    503: "El proveedor de identidad no está disponible. Inténtalo nuevamente.",
  };
  const detail = status === 401 ? defaults[401] : problem?.detail;
  return new ApiError(
    detail ||
      (status && defaults[status]) ||
      "No se pudo contactar al backend. Comprueba la conexión.",
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
