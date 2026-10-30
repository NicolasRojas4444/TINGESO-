import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createFrontendServer, publicConfig } from "../server.mjs";

const env = {
  API_URL: "http://backend.test/api",
  KEYCLOAK_PUBLIC_URL: "http://identity.test",
  KEYCLOAK_REALM: "siga",
  KEYCLOAK_FRONTEND_CLIENT_ID: "frontend",
  SECRET: "must-not-be-exposed",
};
test("runtime configuration exposes only public settings", () => {
  assert.deepEqual(Object.keys(publicConfig(env)).sort(), [
    "apiUrl",
    "keycloakClientId",
    "keycloakRealm",
    "keycloakUrl",
  ]);
  assert.throws(() => publicConfig({}));
  assert.throws(() =>
    publicConfig({ ...env, API_URL: "http://user:password@backend.test" }),
  );
  assert.throws(() =>
    publicConfig({ ...env, KEYCLOAK_PUBLIC_URL: "file:///secret" }),
  );
});
test("serves SPA deep links, uncached configuration and missing asset errors", async (context) => {
  const directory = await mkdtemp(join(tmpdir(), "siga-frontend-test-"));
  await writeFile(join(directory, "index.html"), "<html>SIGA IPT</html>");
  await writeFile(join(directory, "app.js"), "console.log('test')");
  const server = createFrontendServer({
    root: directory,
    config: publicConfig(env),
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  context.after(async () => {
    await new Promise((resolve) => server.close(resolve));
    await rm(directory, { recursive: true });
  });
  const base = `http://127.0.0.1:${server.address().port}`;
  const page = await fetch(base + "/admin/students/1");
  assert.equal(page.status, 200);
  assert.match(await page.text(), /SIGA IPT/);
  const config = await fetch(base + "/config.json");
  assert.equal(config.headers.get("cache-control"), "no-store");
  assert.equal((await config.json()).apiUrl, env.API_URL);
  assert.equal((await fetch(base + "/missing.js")).status, 404);
  assert.equal((await fetch(base + "/.env")).status, 404);
  assert.equal((await fetch(base, { method: "POST" })).status, 405);
  assert.equal(
    (await fetch(base + "/app.js")).headers.get("content-type"),
    "text/javascript; charset=utf-8",
  );
});
