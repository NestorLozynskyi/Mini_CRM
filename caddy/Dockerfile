# Контекст збірки — корінь репозиторію.

# ---- build frontend ----
FROM node:24-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --include=dev
COPY frontend/ ./
RUN npm run build

# ---- caddy: статичний фронтенд + reverse proxy до API ----
FROM caddy:2-alpine
COPY caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=frontend /app/dist /srv
