import assert from "node:assert/strict";
import { randomBytes, createHash } from "node:crypto";
import { demoAccounts } from "./demo.mjs";

// Exercise the existing browser authorization-code/PKCE flow. No temporary IAM
// client, password grant, global policy change or application data mutation.
const base = process.env.KEYCLOAK_SERVER_URL.replace(/\/$/, "");
const redirect = process.env.DEMO_FRONTEND_URL.replace(/\/$/, "") + "/";
const password = process.env.DEMO_PASSWORD;
if (!password) throw new Error("DEMO_PASSWORD required for verification");

try {
  for (const person of demoAccounts) {
    const verifier = randomBytes(32).toString("base64url");
    const url = new URL(`${base}/realms/siga/protocol/openid-connect/auth`);
    url.search = new URLSearchParams({
      client_id: "siga-frontend", redirect_uri: redirect, response_type: "code", scope: "openid",
      code_challenge_method: "S256", code_challenge: createHash("sha256").update(verifier).digest("base64url"),
    });
    const page = await fetch(url, { redirect: "manual" });
    assert.equal(page.status, 200, "Login form unavailable");
    const html = await page.text();
    const form = html.match(/<form\b[^>]*\bid="kc-form-login"[^>]*\baction="([^"]+)"/);
    assert.ok(form, "Credential form unavailable");
    const action = new URL(form[1].replaceAll("&amp;", "&"));
    const internal = new URL(base);
    action.protocol = internal.protocol; action.host = internal.host;
    const cookies = page.headers.getSetCookie().map(cookie => cookie.split(";")[0]).join("; ");
    const login = await fetch(action, {
      method: "POST", redirect: "manual", headers: { Cookie: cookies },
      body: new URLSearchParams({ username: person.username, password }),
    });
    assert.equal(login.status, 302, `${person.username}: credentials rejected or required actions pending`);
    const result = new URL(login.headers.get("location"));
    assert.ok(result.href.startsWith(redirect), "Unexpected login redirect");
    const code = result.searchParams.get("code");
    assert.ok(code, "No authorization code returned");
    const tokenResponse = await fetch(`${base}/realms/siga/protocol/openid-connect/token`, {
      method: "POST", body: new URLSearchParams({
        grant_type: "authorization_code", client_id: "siga-frontend", redirect_uri: redirect,
        code, code_verifier: verifier,
      }),
    });
    assert.equal(tokenResponse.status, 200, "Token exchange failed");
    const { access_token: token } = await tokenResponse.json();
    const claims = JSON.parse(Buffer.from(token.split(".")[1], "base64url"));
    assert.ok(claims.realm_access.roles.includes(person.role), "Missing academic role");
    const path = person.role === "ADMIN" ? "/api/students" : person.role === "TEACHER" ? "/api/teachers/me" : "/api/students/me";
    const response = await fetch(`http://backend:8080${path}`, { headers: { Authorization: `Bearer ${token}` } });
    assert.equal(response.status, 200, `${person.username}: backend access failed`);
    console.log(`PASS: ${person.username} real browser login (PKCE), ${person.role} role and backend access`);
  }
} catch (error) {
  console.error(error instanceof assert.AssertionError ? error.message : "Demo login verification failed; no credentials were logged");
  process.exitCode = 1;
}
