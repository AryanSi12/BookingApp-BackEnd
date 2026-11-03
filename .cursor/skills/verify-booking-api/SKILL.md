---
name: verify-booking-api
description: "Drive the Booking API (demo/, Spring Boot on port 18095) over HTTP and prove sign-in, event browsing, venues, account, and booking behavior. Use when changing controllers, security, Mongo, or Redis in this backend and a real request must prove the result."
---

# Verify the Booking API

The product surface is the HTTP API in `demo/`. There is no browser UI in this repository. WebSocket seat updates exist only as a side effect of booking. Drive the API with `verify.sh`. Do not call the dev server on port 8095, and do not call hosted Stripe, Cloudinary, or Upstash.

Two API processes can run together only when they do not share a port, a Mongo database, or a Redis server. This skill always uses port `18095`, Mongo database `BookingVerify`, and a Redis it starts on port `6380`. The app's custom Redis client ignores a Redis database index, so a second API on the default Redis would overwrite `allEvents` and `allVenues`. Refuse to launch if `18095` or `6380` is already taken. Never kill a process this run did not start.

## Launch

From the repository root:

```bash
chmod +x .cursor/skills/verify-booking-api/scripts/verify.sh
.cursor/skills/verify-booking-api/scripts/verify.sh launch
```

Launch starts MongoDB on `127.0.0.1:27017` only when nothing is already accepting connections, initializes replica set `rs0` when it has no status, starts Redis on `6380`, packages `demo/` with `./mvnw -B -DskipTests package`, drops and reseeds `BookingVerify`, and starts `java -jar demo/target/demo-0.0.1-SNAPSHOT.jar`.

The seeded end user is `verify-user` / `verify-pass-123` (`verify-user@example.com`, role `END_USER`). Signup through `POST /Public/AddUser` is not used because that route uploads an image to Cloudinary.

Ready means all of the following are true:

- The log line `Started SpringApplication` is in `/tmp/verify-booking-api/run/app.log`.
- `GET http://127.0.0.1:18095/Public/health` returns body `OK`.
- `mongosh` reports replica-set state `1`.
- `redis-cli -p 6380 ping` returns `PONG`.
- `BookingVerify.User` contains `verify-user`.

Launch prints `api_port=18095`, `health=OK`, and `evidence_dir=...`. Keep that evidence directory. Cleanup must not delete it.

## Doctor

Run this before any drive, and again whenever a call looks wrong:

```bash
.cursor/skills/verify-booking-api/scripts/verify.sh doctor
```

Doctor is read-only. It exits non-zero unless the recorded pid is alive, that pid's environment is `SERVER_PORT=18095`, `SPRING_DATA_REDIS_PORT=6380`, and `SPRING_DATA_MONGODB_DATABASE=BookingVerify`, health is `OK`, MongoDB is primary, and Redis on `6380` returns `PONG`. Do not drive an instance that fails doctor.

## Drive

The harness is `.cursor/skills/verify-booking-api/scripts/verify.sh`.

```bash
.cursor/skills/verify-booking-api/scripts/verify.sh login
.cursor/skills/verify-booking-api/scripts/verify.sh request GET /User/getAllEvents
.cursor/skills/verify-booking-api/scripts/verify.sh request-public POST /Public/LoginUser -d '{"username":"verify-user","password":"wrong"}'
.cursor/skills/verify-booking-api/scripts/verify.sh redis-exists allEvents
.cursor/skills/verify-booking-api/scripts/verify.sh mongo-count Event
```

`login` prints `login_http` and `jwt_segments` only. It stores the token in the run state and does not print it. `request` sends `Authorization: Bearer` for that token and redacts any `"password"` field in the body. Paths are the controller routes, including the query string.

Read `.cursor/skills/verify-booking-api/features/README.md` and the feature file before driving. A path listed there that you did not exercise is not verified.

## Evidence

Write proof under the `evidence_dir` printed by launch (` .cursor/skills/verify-booking-api/evidence/<run-id>/`).

For every feature, save the command, the HTTP status, and the response body. Login proof is `login_http=200` and `jwt_segments=3`, not the token. A catalog proof saves the HTTP body and a second observation of the same data: `mongo-count` for the collection and `redis-exists` for `allEvents` or `allVenues` after the GET. The HTTP array and the Mongo count must describe the same catalog. A wrong-password or logged-out check saves that response too.

Do not mock MongoDB or Redis. Cloudinary and Stripe are external production boundaries. Creating a user, venue, or event through the API sends the image to Cloudinary. Booking creates a Stripe PaymentIntent. Those calls are real. If the keys are placeholders, record the failure and the missing precondition. Do not insert an event by hand and call that a booking.

## Cleanup

```bash
.cursor/skills/verify-booking-api/scripts/verify.sh cleanup
```

Cleanup stops the API pid and the Redis pid this launch started, drops the `BookingVerify` database, and stops MongoDB only if this launch started it. It leaves `evidence_dir` in place. After cleanup, confirm a file you wrote during the drive is still in that directory.

Do not kill `java`, `mongod`, or `redis-server` by name. The dev API on port `8095` and Redis on `6379` belong to the environment, not to this run.
