import { StrictMode } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  init: vi.fn().mockResolvedValue(true),
  login: vi.fn(),
  logout: vi.fn(),
}));
vi.mock("keycloak-js", () => ({
  default: class {
    authenticated = true;
    tokenParsed = { name: "Test Teacher" };
    init = mocks.init;
    login = mocks.login;
    logout = mocks.logout;
    hasRealmRole(role: string) {
      return role === "TEACHER";
    }
  },
}));
import {
  AuthProvider,
  homeForRole,
  RequireRole,
  useAuth,
} from "../app/lib/auth";

function Probe() {
  const auth = useAuth();
  return (
    <>
      <span>{auth.ready ? auth.name : "loading"}</span>
      <button onClick={auth.login}>Login</button>
      <button onClick={auth.logout}>Logout</button>
      <RequireRole roles={["ADMIN"]}>
        <span>Administrative data</span>
      </RequireRole>
      <RequireRole roles={["TEACHER"]}>
        <span>Teacher data</span>
      </RequireRole>
    </>
  );
}
describe("Keycloak session and roles", () => {
  it("initializes once under StrictMode and blocks unauthorized content", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          apiUrl: "http://api.test/api",
          keycloakUrl: "http://iam.test",
          keycloakRealm: "siga",
          keycloakClientId: "frontend",
        }),
      }),
    );
    render(
      <StrictMode>
        <AuthProvider>
          <Probe />
        </AuthProvider>
      </StrictMode>,
    );
    await waitFor(() =>
      expect(screen.getByText("Test Teacher")).toBeInTheDocument(),
    );
    expect(mocks.init).toHaveBeenCalledTimes(1);
    expect(mocks.init).toHaveBeenCalledWith(
      expect.objectContaining({ pkceMethod: "S256", checkLoginIframe: false }),
    );
    expect(screen.queryByText("Administrative data")).not.toBeInTheDocument();
    expect(screen.getByText("Teacher data")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Login"));
    fireEvent.click(screen.getByText("Logout"));
    expect(mocks.login).toHaveBeenCalledWith({
      redirectUri: window.location.origin + "/",
    });
    expect(mocks.logout).toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
  it("maps each role to its initial view", () => {
    expect(homeForRole("ADMIN")).toBe("/admin/students");
    expect(homeForRole("TEACHER")).toBe("/teacher/sections");
    expect(homeForRole("STUDENT")).toBe("/student/profile");
  });
});
