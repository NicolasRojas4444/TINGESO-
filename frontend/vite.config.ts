import { reactRouter } from "@react-router/dev/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig, loadEnv } from "vite";

export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, "..", ""), ...process.env };
  return {
    plugins: [
      tailwindcss(),
      reactRouter(),
      {
        name: "public-runtime-config",
        configureServer(server) {
          server.middlewares.use("/config.json", (_request, response) => {
            response.setHeader("Content-Type", "application/json");
            response.setHeader("Cache-Control", "no-store");
            response.end(
              JSON.stringify({
                apiUrl:
                  env.API_URL ||
                  `${env.BACKEND_PUBLIC_URL || `http://localhost:${env.BACKEND_PORT || "8080"}`}/api`,
                keycloakUrl: env.KEYCLOAK_PUBLIC_URL,
                keycloakRealm: "siga",
                keycloakClientId: "siga-frontend",
              }),
            );
          });
        },
      },
    ],
    resolve: {
      tsconfigPaths: true,
    },
  };
});
