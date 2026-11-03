# Browse venues

Browse venues lets a signed-in user read the venue catalog. The first read fills the Redis key `allVenues`.

## Sub-features

- `venues-list` returns the catalog for a signed-in user.
- `venues-cache` stores that catalog under `allVenues`.
- `venues-anonymous` rejects a catalog read with no token.

## How to get to it (user POV)

- `GET /User/getAllVenues` with the bearer token from sign-in.
- `GET /User/getAllVenues` with no `Authorization` header.

## Driving it with verify.sh

Preconditions:

- `verify.sh doctor` prints `doctor_ok=1`.
- Launch left the venue collection empty. If it is not empty, stop and report that the baseline seed changed.

- **Sign in.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh login`. Output contains `login_http=200` and `jwt_segments=3`.
- **List venues.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh request GET /User/getAllVenues`. Output contains `http=200` and a JSON array.
- **Match the database.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh mongo-count Venues`. The collection name is `Venues`. The count matches the array length. On the baseline seed the body is `[]` and the count is `0`.
- **Match the cache.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh redis-exists allVenues`. Output is `1`.
- **Anonymous.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh request-public GET /User/getAllVenues`. Output contains `http=403`.
- **Proof.** Save the list body, the Mongo count, and the Redis EXISTS result in the launch evidence directory.

## Gotchas

- Venue creation is `POST /Organizer/createVenue` and uploads an image to Cloudinary. Do not invent a venue row to make this list non-empty.
- `GET /User/getVenueById/{venueId}` needs an id from a real venue. The empty catalog does not cover it.
- `GET /Organizer/getAllVenuesByUserId` requires role `ORGANIZER`. The seeded user is only `END_USER` and receives HTTP 403.
