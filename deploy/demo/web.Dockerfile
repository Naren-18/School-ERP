# Web image: the React app built to static files, served by Caddy, which also
# proxies /api to the backend and (when given a hostname) gets the HTTPS certificate.
# Build context: the repository root.

FROM node:24-alpine AS build
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM caddy:2-alpine
COPY deploy/demo/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/dist /srv
