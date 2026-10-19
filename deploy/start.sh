#!/usr/bin/env bash
# Staging launcher: Java and Python bridge share ONE container, but never the database.
set -euo pipefail
for key in MYSQL_HOST MYSQL_DATABASE MYSQL_USERNAME MYSQL_PASSWORD KALAAZU_PREVIEW_KEY; do
  if [[ -z "${!key:-}" ]]; then
    echo "ERROR: required environment variable $key is missing" >&2
    exit 1
  fi
done
if [[ "${KALAAZU_PREVIEW_KEY}" == "LabOnly12345!" || ${#KALAAZU_PREVIEW_KEY} -lt 20 ]]; then
  echo 'ERROR: preview gate key must be an independently generated secret (at least 20 characters)' >&2
  exit 1
fi
export BRIDGE_PORT="${PORT:-10000}"
export BRIDGE_BIND=0.0.0.0
export BRIDGE_COOKIE_SECURE=true
export KALAAZU_HTTP=http://127.0.0.1:8081
export KALAAZU_GAME_HOST=127.0.0.1
export KALAAZU_GAME_PORT=8080
if [[ -z "${BRIDGE_PUBLIC_ORIGIN:-}" && -n "${RENDER_EXTERNAL_HOSTNAME:-}" ]]; then
  export BRIDGE_PUBLIC_ORIGIN="https://${RENDER_EXTERNAL_HOSTNAME}"
fi
if [[ "${BRIDGE_PUBLIC_ORIGIN:-}" != https://* ]]; then
  echo 'ERROR: set BRIDGE_PUBLIC_ORIGIN=https://your-render-hostname.onrender.com' >&2
  exit 1
fi
# Prevent logging of account/session packets via Kalaazu's debug settings.
export SPRING_APPLICATION_JSON='{"app":{"game":{"packets":{"printIn":false,"printOut":false}}}}'
if [[ "${SPRING_JPA_HIBERNATE_DDL_AUTO:-validate}" != validate ]]; then
  echo 'ERROR: persistent lab must use schema validation, not automatic schema updates' >&2
  exit 1
fi
export SPRING_JPA_HIBERNATE_DDL_AUTO=validate

/opt/kalaazu/bin/main &
java_pid=$!
bridge_pid=""
cleanup() {
  [[ -z "$bridge_pid" ]] || kill "$bridge_pid" 2>/dev/null || true
  kill "$java_pid" 2>/dev/null || true
}
trap cleanup EXIT TERM INT

# Do not publish the login UI until Java HTTP and game ports respond.
ready=0
for attempt in $(seq 1 150); do
  if ! kill -0 "$java_pid" 2>/dev/null; then
    echo 'ERROR: Java exited before becoming ready' >&2
    exit 1
  fi
  if /opt/bridge-venv/bin/python - <<'PY'
import socket
for port in (8080, 8081):
    with socket.create_connection(('127.0.0.1', port), timeout=1):
        pass
PY
  then
    ready=1
    break
  fi
  sleep 2
done
if [[ "$ready" != 1 ]]; then
  echo 'ERROR: Kalaazu did not become ready' >&2
  exit 1
fi

echo 'Kalaazu ready; starting protected HTML5 bridge'
/opt/bridge-venv/bin/python deploy/secured_gateway.py &
bridge_pid=$!
set +e
wait -n "$java_pid" "$bridge_pid"
exit_code=$?
set -e
exit "$exit_code"
