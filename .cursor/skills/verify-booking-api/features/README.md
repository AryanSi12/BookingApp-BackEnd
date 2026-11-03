# Booking API verification map

This directory is the maintained source for verifying the user-facing HTTP behavior of the Booking API. Read the index before driving the app, then use the matching feature file as the recipe.

## Baseline preconditions

- Launch with `.cursor/skills/verify-booking-api/scripts/verify.sh launch` so the API is at `http://127.0.0.1:18095`, MongoDB database `BookingVerify` is primary, and Redis is on port `6380`.
- The launch seed is end user `verify-user` / `verify-pass-123` (`verify-user@example.com`). The event and venue catalogs are empty.
- Run `.cursor/skills/verify-booking-api/scripts/verify.sh doctor` and require `doctor_ok=1`, port `18095`, database `BookingVerify`, and Redis port `6380`.
- Never drive port `8095`, Redis `6379`, or database `BookingEntries`.

## Driving conventions

- Start every recipe from the baseline state unless its preconditions say otherwise.
- Treat every `verify.sh` command as literal. Keep paths and JSON unchanged.
- `login` output must show `login_http=200` and `jwt_segments=3`. Do not record the token.
- Save command output under the `evidence_dir` from launch. Cleanup does not delete that directory.

## Proof and skip reporting

- Capture the request and the resulting HTTP body, not only the status code.
- Catalog proof includes the HTTP body plus `mongo-count` and `redis-exists` for that catalog.
- Record the feature id and the route used with every artifact.
- Report an unreachable path with the attempted command and the unmet precondition.
- Do not report a skipped route as verified through a different path.

## Feature entry contract

Each feature file starts with an H1 title and one paragraph describing the user-visible behavior. It then uses exactly four H2 sections in this order.

1. `Sub-features` lists short IDs with one line for each behavior.
2. `How to get to it (user POV)` lists every user entry point.
3. `Driving it with verify.sh` starts with `Preconditions:` and uses labeled bullets that pair each user action with an exact command and observable result.
4. `Gotchas` lists traps that can waste or invalidate a verification run.

## Features

- [Sign in](./sign-in.md) covers password login and rejection of a wrong password.
- [Browse events](./browse-events.md) covers the signed-in catalog, search, cache, and anonymous rejection.
- [Browse venues](./browse-venues.md) covers the signed-in venue list and its cache.
- [Account](./account.md) covers the current user and logout.
- [Book a seat](./book-seat.md) covers the seat-booking request. It stays failed until an event exists and Stripe accepts the charge.
