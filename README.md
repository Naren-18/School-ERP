# School ERP

Multi-tenant school management system (MVP). See `CLAUDE.md` for rules and `PLAN.md` for the build plan.

## Run locally

Prerequisites: Docker, Java 21, Node 20+.

```sh
cp .env.example .env && docker compose up -d     # Postgres 16
cd backend && ./mvnw spring-boot:run             # API on :8080 (Flyway migrates on start)
cd frontend && npm install && npm run dev        # UI on http://localhost:5173 (proxies /api → :8080)
```

Health check: `curl http://localhost:8080/actuator/health`

### Dev logins

The `dev` profile seeds two fictional schools on startup (password: `DEV_SEED_PASSWORD`, default `Demo@12345`):

| School code | School | Username |
|---|---|---|
| `ssv` | Sri Saraswathi Vidyalayam (State, Telugu) | `principal` |
| `gps` | Greenfield Public School (CBSE, English) | `principal` |

## Tests

```sh
cd backend && ./mvnw test    # needs Docker running (Testcontainers)
```
