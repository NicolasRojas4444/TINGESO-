import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const auth = vi.hoisted(() => ({
  ready: true,
  authenticated: false,
  roles: [] as string[],
  error: "",
  login: vi.fn(),
  logout: vi.fn(),
}));
vi.mock("../app/auth/AuthProvider", () => ({ useAuth: () => auth }));
import LoginView from "../app/views/LoginView";

beforeEach(() => {
  auth.ready = true;
  auth.authenticated = false;
  auth.error = "";
  vi.clearAllMocks();
});

describe("SIGA IPT login branding", () => {
  it("shows the application name and keeps the login action", () => {
    const { container } = render(<LoginView />);
    expect(screen.getByRole("heading", { name: "SIGA IPT" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Iniciar sesión" }));
    expect(auth.login).toHaveBeenCalled();
    expect(container.textContent).not.toMatch(/keycloak/i);
  });

  it("uses application branding while connecting", () => {
    auth.ready = false;
    render(<LoginView />);
    expect(screen.getByRole("status")).toHaveTextContent("Conectando con SIGA IPT");
  });

  it("does not expose provider details in connection errors", () => {
    auth.error = "Keycloak connection failed";
    const { container } = render(<LoginView />);
    expect(screen.getByText(/No se pudo conectar con SIGA IPT/)).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/keycloak/i);
  });

  it("directs accounts without roles to the application administrator", () => {
    auth.authenticated = true;
    const { container } = render(<LoginView />);
    expect(screen.getByText(/Contacta al administrador de SIGA IPT/)).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/keycloak/i);
    fireEvent.click(screen.getByRole("button", { name: "Cerrar sesión" }));
    expect(auth.logout).toHaveBeenCalled();
  });
});
