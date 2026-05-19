#!/bin/bash
echo "============================================"
echo " Cinepolis QA Runner Agent"
echo "============================================"

export BACKEND_URL="${BACKEND_URL:-https://qautomation-production.up.railway.app}"
export RUNNER_TOKEN="${RUNNER_TOKEN:-runner-local-token}"
export POLL_INTERVAL_MS="${POLL_INTERVAL_MS:-5000}"
export WORK_DIR="${WORK_DIR:-..}"
export TEST_COMMAND="${TEST_COMMAND:-./gradlew test}"

echo "Backend:  $BACKEND_URL"
echo "Work Dir: $WORK_DIR"
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
mvn package -q
java -jar target/cinepolis-runner.jar
