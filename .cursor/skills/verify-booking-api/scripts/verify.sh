#!/usr/bin/env bash
# Drive an isolated Booking API instance. Never kills processes this run did not start.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
STATE="${VERIFY_BOOKING_STATE:-/tmp/verify-booking-api/run}"
API_PORT=18095
REDIS_PORT=6380
MONGO_PORT=27017
MONGO_DB=BookingVerify
BASE_URL="http://127.0.0.1:${API_PORT}"
VERIFY_USER="verify-user"
VERIFY_PASSWORD="verify-pass-123"

die() { echo "verify-booking-api: $*" >&2; exit 1; }

port_listening() {
  local port="$1"
  python3 - "$port" <<'PY'
import socket, sys
sock = socket.socket()
sock.settimeout(0.3)
try:
    code = sock.connect_ex(("127.0.0.1", int(sys.argv[1])))
finally:
    sock.close()
sys.exit(0 if code == 0 else 1)
PY
}

ensure_mongo() {
  mkdir -p "$STATE"
  if mongosh --quiet --port "$MONGO_PORT" --eval 'db.runCommand({ ping: 1 }).ok' 2>/dev/null | grep -q 1; then
    echo 0 >"$STATE/mongo_started"
  else
    mkdir -p "$HOME/mongo/data" "$HOME/mongo/log"
    mongod --replSet rs0 --bind_ip 127.0.0.1 --port "$MONGO_PORT" \
      --dbpath "$HOME/mongo/data" --logpath "$HOME/mongo/log/mongod.log" \
      --fork --pidfilepath "$STATE/mongod.pid"
    echo 1 >"$STATE/mongo_started"
  fi
  local ready=0
  for _ in $(seq 1 30); do
    if mongosh --quiet --port "$MONGO_PORT" --eval 'db.runCommand({ ping: 1 }).ok' 2>/dev/null | grep -q 1; then
      ready=1
      break
    fi
    sleep 1
  done
  [[ "$ready" == 1 ]] || die "MongoDB did not accept connections on port ${MONGO_PORT}"
  mongosh --quiet --port "$MONGO_PORT" --eval 'try { if (!rs.status().ok) { throw new Error("rs"); } } catch (e) { rs.initiate({_id:"rs0", members:[{_id:0, host:"127.0.0.1:27017"}]}); }' >/dev/null
  local primary=0 state
  for _ in $(seq 1 40); do
    state="$(mongosh --quiet --port "$MONGO_PORT" --eval 'try { print(rs.status().myState) } catch (e) { print(0) }' | tr -d '[:space:]')"
    if [[ "$state" == 1 ]]; then
      primary=1
      break
    fi
    sleep 1
  done
  [[ "$primary" == 1 ]] || die "MongoDB replica set has no primary"
}

seed_user() {
  local crypto commons hash
  crypto="$(find "$HOME/.m2/repository/org/springframework/security/spring-security-crypto" -name 'spring-security-crypto-*.jar' | sort | tail -1)"
  commons="$(find "$HOME/.m2/repository/commons-logging/commons-logging" -name 'commons-logging-*.jar' | sort | tail -1)"
  [[ -n "$crypto" && -n "$commons" ]] || die "Spring Security crypto jars are missing. Run launch so Maven can download them."
  javac -cp "${crypto}:${commons}" -d "$STATE" "$ROOT/.cursor/skills/verify-booking-api/scripts/Hash.java"
  hash="$(java -cp "${STATE}:${crypto}:${commons}" Hash "$VERIFY_PASSWORD")"
  VERIFY_PASSWORD_HASH="$hash" STATE_DIR="$STATE" python3 - <<'PY'
import os
from pathlib import Path
hash_value = os.environ["VERIFY_PASSWORD_HASH"]
path = Path(os.environ["STATE_DIR"]) / "seed.js"
script = """db.User.drop();
db.Event.drop();
db.User.insertOne({
  username: 'verify-user',
  email: 'verify-user@example.com',
  password: %r,
  roles: ['END_USER'],
  userImage: '',
  createdAt: new Date(),
  updatedAt: new Date(),
  _class: 'com.example.demo.Models.User'
});
""" % hash_value
path.write_text(script)
PY
  mongosh --quiet --port "$MONGO_PORT" "$MONGO_DB" "$STATE/seed.js" >/dev/null
}

cmd_launch() {
  if [[ -f "$STATE/api.pid" ]] && kill -0 "$(cat "$STATE/api.pid")" 2>/dev/null; then
    die "A verification API is already running (pid $(cat "$STATE/api.pid")). Run cleanup before launching another."
  fi
  if port_listening "$API_PORT"; then
    die "Port ${API_PORT} is already in use. Refusing to drive or replace that process."
  fi
  if port_listening "$REDIS_PORT"; then
    die "Port ${REDIS_PORT} is already in use. Refusing to share another Redis with this run."
  fi
  mkdir -p "$STATE/redis"
  local run_id evidence
  run_id="$(date -u +%Y%m%dT%H%M%SZ)"
  evidence="$ROOT/.cursor/skills/verify-booking-api/evidence/${run_id}"
  mkdir -p "$evidence"
  echo "$run_id" >"$STATE/run_id"
  echo "$evidence" >"$STATE/evidence_dir"
  ensure_mongo
  redis-server --daemonize yes --bind 127.0.0.1 --port "$REDIS_PORT" --protected-mode yes \
    --dir "$STATE/redis" --dbfilename dump.rdb --save "" --appendonly no \
    --pidfile "$STATE/redis.pid"
  echo 1 >"$STATE/redis_started"
  redis-cli -p "$REDIS_PORT" ping | grep -q PONG || die "Dedicated Redis on port ${REDIS_PORT} did not answer PONG"
  (cd "$ROOT/demo" && chmod +x mvnw && ./mvnw -B -DskipTests package)
  seed_user
  (
    cd "$ROOT/demo"
    export MONGODB_URI="mongodb://127.0.0.1:${MONGO_PORT}/${MONGO_DB}?replicaSet=rs0"
    export SPRING_DATA_MONGODB_DATABASE="$MONGO_DB"
    export APP_SECRET_KEY="local-dev-jwt-secret-key-32bytes"
    export STRIPE_API_KEY="${STRIPE_API_KEY:-sk_test_local_placeholder}"
    export CLOUDINARY_CLOUD_NAME="${CLOUDINARY_CLOUD_NAME:-local}"
    export CLOUDINARY_API_KEY="${CLOUDINARY_API_KEY:-local}"
    export CLOUDINARY_API_SECRET="${CLOUDINARY_API_SECRET:-local}"
    export SPRING_DATA_REDIS_HOST=127.0.0.1
    export SPRING_DATA_REDIS_PORT="$REDIS_PORT"
    export SPRING_DATA_REDIS_USERNAME=default
    export SPRING_DATA_REDIS_PASSWORD=
    export SPRING_DATA_REDIS_SSL_ENABLED=false
    export SERVER_PORT="$API_PORT"
    setsid java -jar target/demo-0.0.1-SNAPSHOT.jar >"$STATE/app.log" 2>&1 < /dev/null &
    echo $! >"$STATE/api.pid"
  )
  local pid ready=0
  pid="$(cat "$STATE/api.pid")"
  for _ in $(seq 1 90); do
    if curl -sf "$BASE_URL/Public/health" 2>/dev/null | grep -q '^OK$'; then
      ready=1
      break
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      tail -n 80 "$STATE/app.log" >&2 || true
      die "API process ${pid} exited before health was OK"
    fi
    sleep 1
  done
  [[ "$ready" == 1 ]] || die "API on port ${API_PORT} did not return health OK within 90s. See ${STATE}/app.log"
  {
    echo "run_id=${run_id}"
    echo "api_port=${API_PORT}"
    echo "api_pid=${pid}"
    echo "redis_port=${REDIS_PORT}"
    echo "mongo_db=${MONGO_DB}"
    echo "health=OK"
  } | tee "$evidence/launch.txt"
  echo "evidence_dir=${evidence}"
}

require_api() {
  [[ -f "$STATE/api.pid" ]] || die "No verification API state. Run launch first."
  local pid
  pid="$(cat "$STATE/api.pid")"
  kill -0 "$pid" 2>/dev/null || die "Verification API pid ${pid} is not running."
  echo "$pid"
}

cmd_doctor() {
  local pid envdump
  pid="$(require_api)"
  envdump="$(tr '\0' '\n' <"/proc/${pid}/environ")"
  grep -qx "SERVER_PORT=${API_PORT}" <<<"$envdump" || die "pid ${pid} is not the verification API on port ${API_PORT}"
  grep -qx "SPRING_DATA_REDIS_PORT=${REDIS_PORT}" <<<"$envdump" || die "pid ${pid} is not using the dedicated Redis port ${REDIS_PORT}"
  grep -qx "SPRING_DATA_MONGODB_DATABASE=${MONGO_DB}" <<<"$envdump" || die "pid ${pid} is not using database ${MONGO_DB}"
  local health
  health="$(curl -sf "$BASE_URL/Public/health")"
  [[ "$health" == "OK" ]] || die "Health body was not OK"
  local mongo_state redis_pong
  mongo_state="$(mongosh --quiet --port "$MONGO_PORT" --eval 'print(rs.status().myState)' | tr -d '[:space:]')"
  [[ "$mongo_state" == 1 ]] || die "MongoDB is not primary (state ${mongo_state})"
  redis_pong="$(redis-cli -p "$REDIS_PORT" ping | tr -d '[:space:]')"
  [[ "$redis_pong" == "PONG" ]] || die "Redis on port ${REDIS_PORT} did not return PONG"
  echo "doctor_ok=1"
  echo "api_pid=${pid}"
  echo "api_port=${API_PORT}"
  echo "base_url=${BASE_URL}"
  echo "mongo_db=${MONGO_DB}"
  echo "mongo_primary=1"
  echo "redis_port=${REDIS_PORT}"
  echo "health=OK"
  java -version 2>&1 | head -n 1
}

cmd_login() {
  require_api >/dev/null
  local code segments
  code="$(curl -sS -o "$STATE/token" -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${VERIFY_USER}\",\"password\":\"${VERIFY_PASSWORD}\"}" \
    "${BASE_URL}/Public/LoginUser")"
  segments="$(awk -F. 'NF{print NF}' "$STATE/token" | head -n 1)"
  echo "login_http=${code}"
  echo "jwt_segments=${segments}"
  [[ "$code" == 200 && "$segments" == 3 ]] || die "Login did not return a JWT"
}

cmd_request() {
  local method="$1" path="$2"
  shift 2
  require_api >/dev/null
  [[ -s "$STATE/token" ]] || die "No token. Run login first."
  local code body
  body="$(mktemp)"
  code="$(curl -sS -o "$body" -w '%{http_code}' \
    -H "Authorization: Bearer $(cat "$STATE/token")" \
    -H 'Content-Type: application/json' \
    -X "$method" "$@" \
    "${BASE_URL}${path}")"
  echo "http=${code}"
  # Redact password hashes if a user document is returned.
  python3 - "$body" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8", errors="replace").read()
text = re.sub(r'"password"\s*:\s*"[^"]*"', '"password":"[redacted]"', text)
sys.stdout.write(text)
if text and not text.endswith("\n"):
    sys.stdout.write("\n")
PY
  rm -f "$body"
}

cmd_request_public() {
  local method="$1" path="$2"
  shift 2
  require_api >/dev/null
  local body code
  body="$(mktemp)"
  code="$(curl -sS -o "$body" -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    -X "$method" "$@" \
    "${BASE_URL}${path}")"
  echo "http=${code}"
  cat "$body"
  [[ -s "$body" ]] && [[ "$(tail -c 1 "$body")" != $'\n' ]] && echo
  rm -f "$body"
}

cmd_redis_exists() {
  local key="$1"
  require_api >/dev/null
  redis-cli -p "$REDIS_PORT" EXISTS "$key"
}

cmd_mongo_count() {
  local collection="$1"
  require_api >/dev/null
  mongosh --quiet --port "$MONGO_PORT" "$MONGO_DB" --eval "print(db.getCollection('${collection}').countDocuments({}))"
}

cmd_cleanup() {
  local evidence=""
  if [[ -f "$STATE/evidence_dir" ]]; then
    evidence="$(cat "$STATE/evidence_dir")"
  fi
  if [[ -f "$STATE/api.pid" ]]; then
    local pid
    pid="$(cat "$STATE/api.pid")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      for _ in $(seq 1 20); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 0.5
      done
      if kill -0 "$pid" 2>/dev/null; then
        kill -9 "$pid" 2>/dev/null || true
      fi
    fi
  fi
  if [[ -f "$STATE/redis_started" && "$(cat "$STATE/redis_started")" == 1 && -f "$STATE/redis.pid" ]]; then
    local rpid
    rpid="$(cat "$STATE/redis.pid")"
    if [[ -n "$rpid" ]] && kill -0 "$rpid" 2>/dev/null; then
      kill "$rpid" 2>/dev/null || true
    fi
  fi
  if mongosh --quiet --port "$MONGO_PORT" --eval 'db.runCommand({ ping: 1 }).ok' 2>/dev/null | grep -q 1; then
    mongosh --quiet --port "$MONGO_PORT" "$MONGO_DB" --eval 'db.dropDatabase()' >/dev/null || true
  fi
  if [[ -f "$STATE/mongo_started" && "$(cat "$STATE/mongo_started")" == 1 && -f "$STATE/mongod.pid" ]]; then
    local mpid
    mpid="$(cat "$STATE/mongod.pid")"
    if [[ -n "$mpid" ]] && kill -0 "$mpid" 2>/dev/null; then
      kill "$mpid" 2>/dev/null || true
    fi
  fi
  rm -rf "$STATE"
  if [[ -n "$evidence" ]]; then
    [[ -d "$evidence" ]] || die "Evidence directory disappeared: ${evidence}"
    echo "evidence_kept=${evidence}"
  else
    echo "evidence_kept=none"
  fi
}

usage() {
  cat <<EOF
Usage: verify.sh <launch|doctor|login|request|request-public|redis-exists|mongo-count|cleanup>
  launch
  doctor
  login
  request <METHOD> <path> [curl-args...]
  request-public <METHOD> <path> [curl-args...]
  redis-exists <key>
  mongo-count <collection>
  cleanup
EOF
}

main() {
  local cmd="${1:-}"
  shift || true
  case "$cmd" in
    launch) cmd_launch ;;
    doctor) cmd_doctor ;;
    login) cmd_login ;;
    request) [[ $# -ge 2 ]] || die "request needs METHOD and path"; cmd_request "$@" ;;
    request-public) [[ $# -ge 2 ]] || die "request-public needs METHOD and path"; cmd_request_public "$@" ;;
    redis-exists) [[ $# -eq 1 ]] || die "redis-exists needs a key"; cmd_redis_exists "$1" ;;
    mongo-count) [[ $# -eq 1 ]] || die "mongo-count needs a collection"; cmd_mongo_count "$1" ;;
    cleanup) cmd_cleanup ;;
    *) usage; exit 1 ;;
  esac
}

main "$@"
