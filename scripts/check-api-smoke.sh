#!/usr/bin/env bash

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-8}"
CORE_ONLY=false

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
    --core-only)
      CORE_ONLY=true
      shift
      ;;
    -h|--help)
      echo "Usage: $0 [--base-url http://localhost:8080] [--timeout 8] [--core-only]"
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
STUDENT_TOKEN=""
COMPANY_TOKEN=""
ADMIN_TOKEN=""

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
  local token="${5:-}"
  local url="${BASE_URL}${path}"
  local response
  local headers=(-H 'Content-Type: application/json')
  if [[ -n "${token}" ]]; then
    headers+=(-H "Authorization: Bearer ${token}")
  fi

  if [[ -n "${body}" ]]; then
    response="$(curl -sS --max-time "${TIMEOUT_SECONDS}" -X "${method}" "${url}" \
      "${headers[@]}" \
      -d "${body}" 2>/dev/null || true)"
  else
    response="$(curl -sS --max-time "${TIMEOUT_SECONDS}" -X "${method}" "${url}" \
      "${headers[@]}" 2>/dev/null || true)"
  fi

  LAST_RESPONSE="${response}"
  if [[ "${response}" == *'"code":0'* ]]; then
    record_result "ok" "${name}" "code=0"
  else
    record_result "fail" "${name}" "${response:-no response}"
  fi
}

extract_token() {
  printf '%s' "${LAST_RESPONSE}" | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
}

echo "Running API smoke checks against ${BASE_URL}"

api_json "student auth login" "POST" "/api/auth/login" '{"username":"student","password":"123456"}'
STUDENT_TOKEN="$(extract_token)"
api_json "company auth login" "POST" "/api/auth/login" '{"username":"company","password":"123456"}'
COMPANY_TOKEN="$(extract_token)"
api_json "admin auth login" "POST" "/api/auth/login" '{"username":"admin","password":"123456"}'
ADMIN_TOKEN="$(extract_token)"

api_json "auth me" "GET" "/api/auth/me" "" "${STUDENT_TOKEN}"
api_json "student profile" "GET" "/api/students/profile" "" "${STUDENT_TOKEN}"
api_json "admin dashboard" "GET" "/api/admin/dashboard" "" "${ADMIN_TOKEN}"

if [[ "${CORE_ONLY}" != "true" ]]; then
  api_json "job list" "GET" "/api/jobs" "" "${STUDENT_TOKEN}"
  api_json "ai status" "GET" "/api/ai/status" "" "${STUDENT_TOKEN}"
  api_json "company delivery list" "GET" "/api/deliveries/company?companyId=C001" "" "${COMPANY_TOKEN}"
  api_json "create delivery" "POST" "/api/deliveries" '{"studentId":"S001","resumeId":"R001","jobId":"J001"}' "${STUDENT_TOKEN}"
  api_json "delivery events" "GET" "/api/deliveries/events" "" "${STUDENT_TOKEN}"
fi

if [[ "${FAILURES}" -gt 0 ]]; then
  echo "${FAILURES} API smoke check(s) failed." >&2
  exit 1
fi

echo "All API smoke checks passed."
