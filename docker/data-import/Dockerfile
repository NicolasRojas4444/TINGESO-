FROM node:24-alpine
ENV NODE_ENV=development
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY . .
RUN npm test
USER node
ENTRYPOINT ["node", "setup.mjs"]
CMD ["all"]
