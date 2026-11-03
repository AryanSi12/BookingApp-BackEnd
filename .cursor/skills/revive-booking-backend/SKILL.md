---
name: revive-booking-backend
description: Bring the BookingApp Spring Boot backend in demo/ back up after Redis auth, CORS, or startup failure. Use when port 8095 will not serve /Public/health, Upstash Redis rejects the connection, the Vite or Vercel frontend is blocked by CORS, or the user asks to get the backend running again.
---

# Revive the booking backend

The API is the Spring Boot 3.3.11 process in `demo/`. It listens on port `8095`. Ready means `GET http://127.0.0.1:8095/Public/health` returns body `OK`.

These are the repairs that made that process start and accept the frontend again. Apply the matching repair. Do not redesign Redis, auth, or CORS while doing it.

## Redis will not connect

`RedisConfig` reads only these keys:

- `spring.data.redis.host`
- `spring.data.redis.port`
- `spring.data.redis.username` (default `default`)
- `spring.data.redis.password`
- `spring.data.redis.ssl.enabled` (default `true`, which turns on Lettuce SSL)

`spring.redis.*` is ignored. A host or password written under the old prefix leaves the factory on the previous Upstash instance.

The live instance is `fancy-lamb-246550.upstash.io`, port `6379`, username `default`, SSL on. The password is the value already in `demo/src/main/resources/application.properties`. Do not copy that password into this skill, a commit message, or a log. If Upstash rotates the token, replace `spring.data.redis.password` in that file and leave the username and SSL flag as they are. Do not put the host or password back into `RedisConfig` as Java literals.

Do not add a `<version>` on `spring-boot-starter-data-redis`. The Boot 3.3.11 parent supplies it. Pinning `2.7.18` is what broke startup. Jedis stays on the classpath and is unused. The connection factory is Lettuce.

A local Redis used by `.cursor/skills/verify-booking-api` is a different server: port `6380`, SSL off, database `BookingVerify`. Do not point the dev process on `8095` at that Redis.

## The browser is blocked before the API

CORS is configured twice, and both lists must stay identical:

- `SecurityConfig.corsConfigurationSource`
- `WebConfig.addCorsMappings`

`SecurityConfig` must keep `.cors(Customizer.withDefaults())` and `OPTIONS /**` as `permitAll`. `WebConfig` alone does not cover secured routes. Both lists use exact origins and `allowCredentials(true)`. Do not replace them with `*`.

Origins that are already allowed:

- `http://localhost:5173`
- `http://127.0.0.1:5173`
- `https://booking-app-frontend-kappa.vercel.app`
- `https://booking-app-frontend-git-cursor-92ba6e-aryans-projects-bbec6789.vercel.app`

`localhost` and `127.0.0.1` are different origins. Add a new frontend origin to both lists in the same change. A missing preview host looks like a dead API in the browser and a silent preflight failure.

A request that carries a bad or expired `jwtToken` cookie gets `401` from `JwtFilter` before `/Public/**` is permitted, including `POST /Public/LoginUser`. That is not a CORS failure and not a down process. Clear the cookie or call the route with no `Authorization` header and no `jwtToken` cookie.

## Start

From `demo/`, the process needs these environment variables or it fails during context startup:

- `MONGODB_URI`
- `APP_SECRET_KEY`
- `STRIPE_API_KEY`
- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`

MongoDB must be a replica set. `Config` installs a `MongoTransactionManager`, and booking and event writes are `@Transactional`. A standalone `mongod` starts the process and then fails those writes. Local Mongo on `127.0.0.1:27017` is replica set `rs0`. The database name is `BookingEntries`.

`demo/dockerfile` exposes `8080`. The app does not read `PORT` and does not bind `8080`. Leave `server.port=8095` in place while fixing Redis or CORS.

Run from `demo/`:

```bash
bash ./mvnw -B spring-boot:run
```

Wait until the log contains `Started SpringApplication`, then:

```bash
curl -sS -D - http://127.0.0.1:8095/Public/health
```

The body is `OK`. If the log shows `RedisConnectionFailureException` or `WRONGPASS` before that line, fix `spring.data.redis.*` and start again. Do not switch the port to match the dockerfile.

To prove sign-in and catalogs against an isolated Mongo database and Redis, use `.cursor/skills/verify-booking-api`. That harness uses port `18095`. It is not a substitute for the health check on `8095`.
