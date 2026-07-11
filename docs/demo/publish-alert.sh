#!/usr/bin/env bash
# Publishes a sample fault alert to the QSense inbound topic.
# Requires Mosquitto clients (mosquitto_pub).
set -euo pipefail

BROKER="${1:-test.mosquitto.org}"
PORT="${2:-1883}"
TOPIC="${3:-qsense/machine/monitoring}"

PAYLOAD='{"alertId":"M-01","machineNo":"M-01","partName":"Fan Motor","partNo":"PN-001","severity":48.896,"timestamp":"2026-07-11T17:57:05.435079"}'

echo "Publishing to ${BROKER}:${PORT}  topic=${TOPIC}"
mosquitto_pub -h "$BROKER" -p "$PORT" -t "$TOPIC" -q 1 -m "$PAYLOAD"
