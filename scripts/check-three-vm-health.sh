#!/usr/bin/env bash

set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/deploy/three-vm.env"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-5}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --timeout)
      TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    -h|--help)
      echo "Usage: $0 [--env-file deploy/three-vm.env] [--timeout 5]"
      exit 0
      ;;
    *)
      ENV_FILE="$1"
      shift
      ;;
  esac
done

if [[ ! -f "${ENV_FILE}" ]]; then
  EXAMPLE_ENV="${ROOT_DIR}/deploy/three-vm.env.example"
  if [[ -f "${EXAMPLE_ENV}" ]]; then
    echo "WARN: env file not found: ${ENV_FILE}. Falling back to example values: ${EXAMPLE_ENV}" >&2
    ENV_FILE="${EXAMPLE_ENV}"
  else
    echo "ERROR: env file not found: ${ENV_FILE}" >&2
    exit 1
  fi
fi

declare -A ENV_VALUES

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "${value}"
}

while IFS= read -r raw_line || [[ -n "${raw_line}" ]]; do
  line="${raw_line%$'\r'}"
  line="$(trim "${line}")"
  [[ -z "${line}" || "${line}" == \#* ]] && continue
  [[ "${line}" != *=* ]] && continue

  key="$(trim "${line%%=*}")"
  value="$(trim "${line#*=}")"

  if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi

  ENV_VALUES["${key}"]="${value}"
done < "${ENV_FILE}"

required_value() {
  local key="$1"
  local value="${ENV_VALUES[${key}]:-}"
  if [[ -z "${value}" ]]; then
    echo "ERROR: missing required env value: ${key}" >&2
    exit 1
  fi
  printf '%s' "${value}"
}

value_or_default() {
  local key="$1"
  local default_value="$2"
  local value="${ENV_VALUES[${key}]:-}"
  if [[ -z "${value}" ]]; then
    printf '%s' "${default_value}"
  else
    printf '%s' "${value}"
  fi
}

FAILURES=0

record_result() {
  local status="$1"
  local name="$2"
  local target="$3"
  local detail="$4"

  if [[ "${status}" == "ok" ]]; then
    printf '[OK]   %s -> %s (%s)\n' "${name}" "${target}" "${detail}"
  else
    printf '[FAIL] %s -> %s (%s)\n' "${name}" "${target}" "${detail}"
    FAILURES=$((FAILURES + 1))
  fi
}

check_http() {
  local name="$1"
  local url="$2"
  local token="${3:-}"
  local code
  local headers=()
  if [[ -n "${token}" ]]; then
    headers=(-H "Authorization: Bearer ${token}")
  fi

  if ! command -v curl >/dev/null 2>&1; then
    record_result "fail" "${name}" "${url}" "curl not installed"
    return
  fi

  code="$(curl -k -sS -L --max-time "${TIMEOUT_SECONDS}" "${headers[@]}" -o /dev/null -w '%{http_code}' "${url}" 2>/dev/null || true)"
  if [[ "${code}" =~ ^[0-9]+$ && "${code}" -ge 200 && "${code}" -lt 400 ]]; then
    record_result "ok" "${name}" "${url}" "HTTP ${code}"
  else
    record_result "fail" "${name}" "${url}" "HTTP ${code:-timeout}"
  fi
}

check_tcp() {
  local name="$1"
  local host="$2"
  local port="$3"
  local target="${host}:${port}"

  if command -v nc >/dev/null 2>&1; then
    if nc -z -w "${TIMEOUT_SECONDS}" "${host}" "${port}" >/dev/null 2>&1; then
      record_result "ok" "${name}" "${target}" "tcp open"
    else
      record_result "fail" "${name}" "${target}" "tcp closed or timeout"
    fi
    return
  fi

  if command -v timeout >/dev/null 2>&1; then
    if timeout "${TIMEOUT_SECONDS}" bash -c "cat < /dev/null > /dev/tcp/${host}/${port}" >/dev/null 2>&1; then
      record_result "ok" "${name}" "${target}" "tcp open"
    else
      record_result "fail" "${name}" "${target}" "tcp closed or timeout"
    fi
    return
  fi

  if bash -c "cat < /dev/null > /dev/tcp/${host}/${port}" >/dev/null 2>&1; then
    record_result "ok" "${name}" "${target}" "tcp open"
  else
    record_result "fail" "${name}" "${target}" "tcp closed"
  fi
}

gateway_token() {
  local base_url="$1"
  local username="$2"
  local output_var="$3"
  local response
  response="$(curl -sS --max-time "${TIMEOUT_SECONDS}" -X POST "${base_url%/}/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${username}\",\"password\":\"123456\"}" 2>/dev/null || true)"
  local token
  token="$(printf '%s' "${response}" | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  if [[ -n "${token}" ]]; then
    record_result "ok" "gateway ${username} login" "${base_url}" "token issued"
    printf -v "${output_var}" '%s' "${token}"
  else
    record_result "fail" "gateway ${username} login" "${base_url}" "token missing"
    printf -v "${output_var}" ''
  fi
}

VM1_HOST="$(required_value VM1_HOST)"
VM2_HOST="$(required_value VM2_HOST)"
VM3_HOST="$(required_value VM3_HOST)"
FRONTEND_PORT="$(value_or_default FRONTEND_PORT 80)"
GATEWAY_PORT="$(value_or_default GATEWAY_PORT 8080)"
MILVUS_PORT="$(value_or_default MILVUS_PORT 19530)"
GATEWAY_BASE_URL="http://${VM1_HOST}:${GATEWAY_PORT}"

echo "Using env file: ${ENV_FILE}"
echo "Checking VM1=${VM1_HOST} VM2=${VM2_HOST} VM3=${VM3_HOST}"

check_http "VM1 frontend" "http://${VM1_HOST}:${FRONTEND_PORT}/"
check_http "VM1 gateway health" "http://${VM1_HOST}:${GATEWAY_PORT}/actuator/health"
STUDENT_TOKEN=""
gateway_token "${GATEWAY_BASE_URL}" "student" STUDENT_TOKEN
check_http "VM1 frontend api proxy" "http://${VM1_HOST}:${FRONTEND_PORT}/api/ai/status" "${STUDENT_TOKEN}"
check_http "VM1 gateway ai route" "http://${VM1_HOST}:${GATEWAY_PORT}/api/ai/status" "${STUDENT_TOKEN}"
check_http "VM1 nacos console" "http://${VM1_HOST}:8848/nacos/"
check_tcp "VM1 nacos grpc" "${VM1_HOST}" 9848

check_http "VM2 auth-service" "http://${VM2_HOST}:8101/actuator/health"
check_http "VM2 user-service" "http://${VM2_HOST}:8102/actuator/health"
check_http "VM2 resume-service" "http://${VM2_HOST}:8103/actuator/health"
check_http "VM2 job-service" "http://${VM2_HOST}:8104/actuator/health"
check_http "VM2 match-service" "http://${VM2_HOST}:8105/actuator/health"
check_http "VM2 delivery-service" "http://${VM2_HOST}:8107/actuator/health"

check_http "VM3 ai-service health" "http://${VM3_HOST}:8106/actuator/health"
check_http "VM3 ai-service status" "http://${VM3_HOST}:8106/api/ai/status"
check_http "VM3 ai RAG vector status" "http://${VM3_HOST}:8106/api/ai/knowledge/vector/status"
check_tcp "VM3 mysql" "${VM3_HOST}" 3306
check_tcp "VM3 redis" "${VM3_HOST}" 6379
check_http "VM3 minio api" "http://${VM3_HOST}:9000/minio/health/ready"
check_tcp "VM3 minio console" "${VM3_HOST}" 9001
check_tcp "VM3 rocketmq namesrv" "${VM3_HOST}" 9876
check_tcp "VM3 rocketmq broker listen" "${VM3_HOST}" 10911
check_tcp "VM3 rocketmq broker vip" "${VM3_HOST}" 10909
check_tcp "VM3 milvus grpc/rest" "${VM3_HOST}" "${MILVUS_PORT}"

if [[ "${FAILURES}" -gt 0 ]]; then
  echo "${FAILURES} health check(s) failed." >&2
  exit 1
fi

echo "All health checks passed."
