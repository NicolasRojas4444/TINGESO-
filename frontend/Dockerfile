# Build static assets natively; only the runtime image needs the target CPU.
FROM --platform=$BUILDPLATFORM node:24-alpine AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM dependencies AS checks
COPY . .
RUN npm run typecheck

FROM checks AS build
RUN npm run build

FROM node:24-alpine AS runtime
ENV NODE_ENV=production HOST=0.0.0.0 PORT=5173
WORKDIR /app
COPY --from=build --chown=node:node /app/build/client ./build/client
COPY --chown=node:node server.mjs ./server.mjs
USER node
EXPOSE 5173
CMD ["node", "server.mjs"]
