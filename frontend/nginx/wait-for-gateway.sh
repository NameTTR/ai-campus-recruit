#!/bin/sh
set -eu

if [ "${GATEWAY_WAIT_ENABLED:-true}" = "false" ]; then
    exit 0
fi

upstream="${GATEWAY_UPSTREAM:-http://gateway-service:8080}"
health_url="${upstream%/}/actuator/health"
attempts="${GATEWAY_WAIT_ATTEMPTS:-60}"
interval="${GATEWAY_WAIT_INTERVAL_SECONDS:-1}"

attempt=0
while [ "$attempt" -lt "$attempts" ]; do
    if wget -q -O - --timeout=2 "$health_url" 2>/dev/null | grep -q '"status":"UP"'; then
        exit 0
    fi
    attempt=$((attempt + 1))
    sleep "$interval"
done

echo "nginx: gateway health check did not pass after ${attempts} attempts; starting anyway" >&2
