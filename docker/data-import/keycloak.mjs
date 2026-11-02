export class KeycloakAdmin {
  constructor(env) {
    this.base = env.KEYCLOAK_SERVER_URL?.replace(/\/$/, "");
    this.realm = env.KEYCLOAK_REALM;
    this.username = env.KEYCLOAK_ADMIN;
    this.password = env.KEYCLOAK_ADMIN_PASSWORD;
    if (!this.base || !this.realm || !this.username || !this.password)
      throw new Error("Missing Keycloak administration environment variables");
    if (this.realm !== "siga")
      throw new Error(
        "Local accounts may only be prepared in the siga realm, never master",
      );
    this.token = null;
    this.expiresAt = 0;
  }

  async accessToken() {
    if (this.token && Date.now() < this.expiresAt) return this.token;
    const response = await fetch(
      `${this.base}/realms/master/protocol/openid-connect/token`,
      {
        method: "POST",
        signal: AbortSignal.timeout(15000),
        body: new URLSearchParams({
          client_id: "admin-cli",
          grant_type: "password",
          username: this.username,
          password: this.password,
        }),
      },
    );
    if (!response.ok)
      throw new Error(
        `Cannot authenticate the master administrator (HTTP ${response.status}); check the existing account, not only .env`,
      );
    const result = await response.json();
    if (!result.access_token)
      throw new Error("Keycloak returned no administrator token");
    this.token = result.access_token;
    this.expiresAt =
      Date.now() + Math.max(1, Number(result.expires_in || 30) - 10) * 1000;
    return this.token;
  }

  async request(method, path, body) {
    const token = await this.accessToken();
    const response = await fetch(
      `${this.base}/admin/realms/${encodeURIComponent(this.realm)}${path}`,
      {
        method,
        signal: AbortSignal.timeout(15000),
        headers: {
          Authorization: `Bearer ${token}`,
          ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      },
    );
    // Never log upstream response bodies, credentials, passwords or tokens.
    if (!response.ok)
      throw new Error(
        `Keycloak ${method} ${path.split("?")[0]} failed (HTTP ${response.status}); no global password policy was changed`,
      );
    if (response.status === 204 || response.status === 201) return response;
    return response.json();
  }

  async findUser(username) {
    const users = await this.request(
      "GET",
      `/users?username=${encodeURIComponent(username)}&exact=true`,
    );
    if (users.length > 1)
      throw new Error(`Ambiguous Keycloak username: ${username}`);
    return users[0] || null;
  }
}
