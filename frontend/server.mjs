import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { resolve, extname, sep } from "node:path";
import { fileURLToPath } from "node:url";

const contentTypes = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
  ".woff2": "font/woff2",
};

export function publicConfig(env = process.env) {
  const config = {
    apiUrl: env.API_URL,
    keycloakUrl: env.KEYCLOAK_PUBLIC_URL,
    keycloakRealm: env.KEYCLOAK_REALM,
    keycloakClientId: env.KEYCLOAK_FRONTEND_CLIENT_ID,
  };
  if (Object.values(config).some((value) => !value))
    throw new Error("Missing public frontend configuration");
  for (const value of [config.apiUrl, config.keycloakUrl]) {
    const url = new URL(value);
    if (
      !["http:", "https:"].includes(url.protocol) ||
      url.username ||
      url.password
    )
      throw new Error("Invalid public URL");
  }
  return config;
}

export function createFrontendServer({
  root = resolve("build/client"),
  config = publicConfig(),
} = {}) {
  const directory = resolve(root);
  return createServer(async (request, response) => {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    if (!["GET", "HEAD"].includes(request.method)) {
      response.writeHead(405, { Allow: "GET, HEAD" });
      response.end();
      return;
    }
    try {
      const pathname = decodeURIComponent(
        new URL(request.url, "http://frontend.local").pathname,
      );
      if (pathname === "/config.json") {
        response.writeHead(200, {
          "Content-Type": "application/json",
          "Cache-Control": "no-store",
        });
        response.end(
          request.method === "HEAD" ? undefined : JSON.stringify(config),
        );
        return;
      }
      let file = resolve(directory, "." + pathname);
      if (file !== directory && !file.startsWith(directory + sep)) {
        response.writeHead(403);
        response.end();
        return;
      }
      if (pathname.split("/").some((part) => part.startsWith("."))) {
        response.writeHead(404);
        response.end();
        return;
      }
      const exists = await stat(file).catch(() => null);
      if (!exists?.isFile()) {
        if (extname(pathname)) {
          response.writeHead(404);
          response.end();
          return;
        }
        file = resolve(directory, "index.html");
      }
      const body = await readFile(file);
      response.writeHead(200, {
        "Content-Type":
          contentTypes[extname(file)] || "application/octet-stream",
        "Cache-Control": file.endsWith("index.html")
          ? "no-cache"
          : "public, max-age=3600",
      });
      response.end(request.method === "HEAD" ? undefined : body);
    } catch (error) {
      response.writeHead(error instanceof URIError ? 400 : 500, {
        "Content-Type": "text/plain; charset=utf-8",
      });
      response.end("No se pudo servir la página.");
    }
  });
}

if (
  process.argv[1] &&
  fileURLToPath(import.meta.url) === resolve(process.argv[1])
) {
  createFrontendServer().listen(
    Number(process.env.PORT || 5173),
    process.env.HOST || "0.0.0.0",
    () => console.log("SIGA IPT frontend ready"),
  );
}
