# Book a seat

Book a seat lets a signed-in user reserve seats for an event. The API locks those seats in Redis and creates a Stripe PaymentIntent before it stores the booking.

## Sub-features

- `book-seat-submit` posts the chosen seat numbers for an event.
- `book-seat-list` reads the signed-in user's bookings after a successful charge.

## How to get to it (user POV)

- `POST /User/bookSeat/{eventId}` with JSON `{"bookedSeats":["A1"]}` and the bearer token.
- `GET /User/fetchBookingByUserId` with the same token.

## Driving it with verify.sh

Preconditions:

- `verify.sh doctor` prints `doctor_ok=1`.
- `BookingVerify` contains an event whose venue has seat `A1`, created through the organizer API. The baseline seed has no event.
- `STRIPE_API_KEY` is a Stripe test key the API process was launched with. The default launch placeholder is not a chargeable key.

- **Check the catalog.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh mongo-count Event`. If the count is `0`, stop. Report this journey as not run because launch does not create an event. The unmet precondition is an organizer venue and event, which require a Cloudinary image upload, plus a real Stripe test key.
- **Do not invent the event.** Inserting an `Event` document by hand, or calling book with a made-up id, is not this journey.
- **Submit, only when both preconditions are already true.** Sign in with `.cursor/skills/verify-booking-api/scripts/verify.sh login`, then run `.cursor/skills/verify-booking-api/scripts/verify.sh request POST /User/bookSeat/<eventId> -d '{"bookedSeats":["A1"]}'`. A successful response is HTTP 200 or 201 and includes the booking. Then run `.cursor/skills/verify-booking-api/scripts/verify.sh request GET /User/fetchBookingByUserId` and require that same booking in the list.
- **Proof.** Save the book response and the following list response. Also save `redis-exists` for the seat lock key only if the request locked a seat. A Stripe error body is a failed journey, not a pass.

## Gotchas

- The baseline evidence for this feature is the skipped journey and the reason above. Do not weaken the steps so an empty catalog counts as a booking.
- Seat prices are charged in INR through Stripe. A placeholder `STRIPE_API_KEY` fails the charge.
- `GET /Organizer/fetchAllBookingsByEventId/{eventId}` requires role `ORGANIZER`. The seeded end user cannot prove that route.
- Booking publishes on `/topic/seats/{eventId}`. An HTTP success does not by itself prove the WebSocket frame.
