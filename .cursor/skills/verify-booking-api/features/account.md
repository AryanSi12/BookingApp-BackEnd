# Account

Account lets a signed-in user read their profile and clear the login cookie.

## Sub-features

- `account-current` returns the signed-in username and email.
- `account-logout` clears the `jwtToken` cookie.

## How to get to it (user POV)

- `GET /User/getCurrUser` with the bearer token from sign-in.
- `POST /User/Logout` with the same token.

## Driving it with verify.sh

Preconditions:

- `verify.sh doctor` prints `doctor_ok=1`.
- The signed-in user is the launch seed `verify-user`.

- **Sign in.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh login`. Output contains `login_http=200` and `jwt_segments=3`.
- **Read profile.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh request GET /User/getCurrUser`. Output contains `http=200`, `"username":"verify-user"`, `"email":"verify-user@example.com"`, and `"password":"[redacted]"`.
- **Log out.** Run `.cursor/skills/verify-booking-api/scripts/verify.sh request POST /User/Logout`. Output contains `http=200` and the body `Logged out successfully`.
- **Proof.** Save both responses in the launch evidence directory. The profile file must keep the password redacted.

## Gotchas

- Logout expires the `jwtToken` cookie. It does not revoke the bearer token. A later `request GET /User/getCurrUser` can still return 200. Do not treat that as proof that logout failed.
- `GET /User/getUserDetails` is a second profile route. Reading `getCurrUser` does not verify it.
- `PUT /User/updateUserDetails` changes the account. Do not call it during this read.
