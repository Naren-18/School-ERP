# Hosted demo on a VPS (no domain needed)

A self-contained copy of the School ERP for sales demos: app, Postgres and Caddy
in one Docker Compose project, separate from anything else on the server. The
address comes from [sslip.io](https://sslip.io): `erp.169-58-40-189.sslip.io`
resolves to `169.58.40.189`, so a real HTTPS certificate works without buying a
domain.

This is the quick version, ahead of Phase 10. It has no R2 storage, backups or
`make deploy` yet.

Run everything below **on the server**, as root (or with `sudo`).

## 1. Check the server

```sh
docker --version && docker compose version
ss -ltnp | grep -E ':(80|443|8090|8443) '
free -h && df -h /
```

- **No Docker?** On Ubuntu or Debian, run `curl -fsSL https://get.docker.com | sh`.
- **Nothing on 80 or 443?** Use **option A** below (Caddy handles HTTPS).
- **nginx, Traefik or Caddy already on 80/443?** Use **option B** (your proxy forwards to
  this demo).
- **8090 or 8443 taken?** Pick other free ports for option B.

## 2. Get the code

```sh
git clone https://github.com/Naren-18/School-ERP.git /opt/school-erp
cd /opt/school-erp/deploy/demo
```

If the repository is private, use a GitHub personal access token with read access as
the password, or add a deploy key.

## 3. Settings

```sh
cp .env.example .env
sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 24)|" .env
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')|" .env
sed -i "s|^APP_ENCRYPTION_KEY=.*|APP_ENCRYPTION_KEY=$(openssl rand -base64 32)|" .env
nano .env
```

In `nano`:
1. Set `DEMO_SEED_PASSWORD`: the password every demo login will use. Choose your own.
2. For **option B**, comment out the three option A lines and uncomment option B.
3. Save with Ctrl+O, Enter, then exit with Ctrl+X.

Keep a copy of `.env` somewhere safe. `APP_ENCRYPTION_KEY` can't be recovered.

## 4. Start it

```sh
docker compose up -d --build
docker compose logs -f app     # wait for "Started SchoolErpApplication", then Ctrl+C
```

- The first build takes a few minutes.
- **Option A:** if a firewall is on, open the ports: `ufw allow 80,443/tcp`.
- **Option B with Caddy already on the server:** add this site block to its Caddyfile
  (usually `/etc/caddy/Caddyfile`). Caddy gets the certificate by itself:

  ```caddyfile
  erp.169-58-40-189.sslip.io {
      reverse_proxy 127.0.0.1:8090
  }
  ```

  Then run `caddy validate --config /etc/caddy/Caddyfile` and `systemctl reload caddy`.
  A reload is graceful, so sites already running keep working.
- **Option B with nginx:**

  ```nginx
  server {
      listen 80;
      server_name erp.169-58-40-189.sslip.io;
      client_max_body_size 5m;
      location / {
          proxy_pass http://127.0.0.1:8090;
          proxy_set_header Host $host;
          proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
          proxy_set_header X-Forwarded-Proto $scheme;
      }
  }
  ```

  Then `nginx -t && systemctl reload nginx` and `certbot --nginx -d erp.169-58-40-189.sslip.io`.

## 5. Create the demo schools (once, about 10 minutes)

Use `tmux` (or `screen`) so a dropped SSH connection doesn't stop it:

```sh
tmux new -s seed
docker compose run --rm app --spring.profiles.active=prod,demo --seed-demo --server.port=0
```

It logs each step and ends with `Demo seed finished. Created: [vjhs, lvps, sems]`.
Running it again changes nothing.

## 6. Open it

**https://erp.169-58-40-189.sslip.io**

- School codes: `vjhs`, `lvps`, `sems`.
- Users: `principal`, `admin`, `accountant`, `teacher1`, `teacher2`.
- Password: your `DEMO_SEED_PASSWORD`.

If the browser shows a certificate warning for the first minute, Caddy (option A) is
still getting the certificate. If it never works, check `docker compose logs web`.

## Everyday commands (in /opt/school-erp/deploy/demo)

| What | Command |
|---|---|
| Logs | `docker compose logs -f app` |
| Update to the latest code | `git pull && docker compose up -d --build` |
| Stop / start | `docker compose stop` / `docker compose start` |
| Fresh demo data (deletes all this demo's data) | `docker compose down -v && docker compose up -d --build`, then step 5 again |
| Health check (for uptime monitors) | `https://erp.169-58-40-189.sslip.io/actuator/health` |

The demo's "today" is the day it was seeded (attendance marked today, overdue fees,
upcoming exams). Refresh the data shortly before an important demo.

Memory limits add up to about 1.9 GB: app 1 GB, Postgres 768 MB, Caddy 128 MB.
