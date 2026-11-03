# Browse events

Browse events lets a signed-in user read the event catalog and search it by title. An anonymous caller is refused.

## Sub-features

- `events-list` returns the catalog for a signed-in user.
- `events-cache` stores that catalog in Redis under `allEvents`.
- `events-search` returns title matches, including an empty match list.
- `events-anonymous` rejects a catalog read with no token.

## How to get to it (user POV)

- `GET /User/getAllEvents` with the bearer token from sign-in.
- `GET /User/getEventsByQuery?query=zzzz-no-match` with the same token.
- `GET /User/getAllEvents` with no `Authorization` header.

## Driving it with verify.sh

Preconditions:

- `verify.sh doctor` prints `doctor_ok=1`.
- Launch left the `Event` collection empty. If `mongo-count Event` is not `0`, stop and report the unexpected seed instead of deleting rows by hand to force a pass.

- **Sign in.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh login`. Output contains `login_http=200` and `jwt_segments=3`.
- **List events.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh request GET /User/getAllEvents`. Output contains `http=200` and a JSON array.
- **Match the database.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh mongo-count Event`. The count is the number of objects in the HTTP array. On the baseline seed both are empty: body `[]` and count `0`.
- **Match the cache.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh redis-exists allEvents`. Output is `1`.
- **Read again.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh request GET /User/getAllEvents` a second time. The body matches the first response.
- **Search miss.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh request GET '/User/getEventsByQuery?query=zzzz-no-match'`. Output contains `http=200` and body `[]`.
- **Anonymous.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh request-public GET /User/getAllEvents`. Output contains `http=403`.
- **Proof.** Save each command's output under the launch evidence directory. Include the list body, the Mongo count, and the Redis EXISTS result.

## Gotchas

- `GET /User/getEventById/{eventId}` and `GET /Organizer/getAllEventsByUserId` are different routes. An empty catalog does not verify them.
- The Redis value is a cache of the list, not the source of truth. A present key with a Mongo count that disagrees with the HTTP body is a failure.
- Search uses query parameter `query`, not `q`.
- Creating an event is `POST /Organizer/createEvent/{venueId}` as an organizer with a multipart image. That upload goes to Cloudinary and is not part of this read.
