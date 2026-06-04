#!/usr/bin/env bash

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-8}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --timeout)
      TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    -h|--help)
      echo "Usage: $0 [--base-url http://localhost:8080] [--timeout 8]"
      exit 0
      ;;
    *)
      BASE_URL="$1"
      shift
      ;;
  esac
done

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required." >&2
  exit 1
fi

BASE_URL="${BASE_URL%/}"
FAILURES=0
LAST_RESPONSE=""

record_result() {
  local status="$1"
  local name="$2"
  local detail="$3"

  if [[ "${status}" == "ok" ]]; then
    printf '[OK]   %s (%s)\n' "${name}" "${detail}"
  else
    printf '[FAIL] %s (%s)\n' "${name}" "${detail}"
    FAILURES=$((FAILURES + 1))
  fi
}

api_json() {
  local name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local url="${BASE_URL}${path}"
  local response

  if [[ -n "${body}" ]]; then
    response="$(curl -sS --max-time "${TIMEOUT_SECONDS}" -X "${method}" "${url}" \
      -H 'Content-Type: application/json' \
      -d "${body}" 2>/dev/null || true)"
  else
    response="$(curl -sS --max-time "${TIMEOUT_SECONDS}" -X "${method}" "${url}" \
      -H 'Content-Type: application/json' 2>/dev/null || true)"
  fi

  LAST_RESPONSE="${response}"
  if [[ "${response}" == *'"code":0'* ]]; then
    record_result "ok" "${name}" "code=0"
  else
    record_result "fail" "${name}" "${response:-no response}"
  fi
}

echo "Running API smoke checks against ${BASE_URL}"

api_json "auth login" "POST" "/api/auth/login" '{"username":"student","password":"123456"}'
api_json "student profile" "GET" "/api/students/profile"
api_json "job list" "GET" "/api/jobs"
api_json "ai status" "GET" "/api/ai/status"
api_json "create delivery" "POST" "/api/deliveries" '{"studentId":"S001","resumeId":"R001","jobId":"J001"}'
api_json "delivery events" "GET" "/api/deliveries/events"
api_json "admin dashboard" "GET" "/api/admin/dashboard"

if [[ "${FAILURES}" -gt 0 ]]; then
  echo "${FAILURES} API smoke check(s) failed." >&2
  exit 1
fi

echo "All API smoke checks passed."
