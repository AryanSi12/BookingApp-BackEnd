# Sign in

Sign in lets an existing end user exchange a username and password for a session token, and it rejects a wrong password.

## Sub-features

- `sign-in-ok` returns a JWT for `verify-user`.
- `sign-in-wrong-password` rejects a bad password without issuing a token.

## How to get to it (user POV)

- `POST /Public/LoginUser` with JSON `{"username":"verify-user","password":"verify-pass-123"}`.
- `POST /Public/LoginUser` with the same username and password `wrong`.

## Driving it with verify.sh

Preconditions:

- `verify.sh doctor` prints `doctor_ok=1` and `api_port=18095`.
- `BookingVerify` contains `verify-user` from launch. Do not create the user through `POST /Public/AddUser`.

- **Sign in.** Submit the seeded password. Run `.cursor/skills/verify-booking-api/scripts/verify.sh login`. Output contains `login_http=200` and `jwt_segments=3`.
- **Wrong password.** Submit a bad password on the public route. Run `.cursor/skills/verify-booking-api/scripts/verify.sh request-public POST /Public/LoginUser -d '{"username":"verify-user","password":"wrong"}'`. Output contains `http=400` and the body `Incorrect Username or password`.
- **Proof.** Save both outputs in the launch evidence directory as `sign-in-ok.txt` and `sign-in-wrong.txt`. The success file must not contain the token.

## Gotchas

- The login body is the raw JWT, not a JSON object. Recording that body publishes a live credential.
- A wrong password is HTTP 400, not 401.
- `POST /Public/AddUser` and `POST /Public/AddOrganizer` require a multipart image and upload it to Cloudinary. They are not a substitute for the seeded user.
