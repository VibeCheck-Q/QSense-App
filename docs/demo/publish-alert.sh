#!/usr/bin/env bash
# Publishes a sample fault alert to the QSense inbound topic.
# Requires Mosquitto clients (mosquitto_pub).
set -euo pipefail

BROKER="${1:-test.mosquitto.org}"
PORT="${2:-1883}"
NAMESPACE="${3:-qsense-demo}"

PAYLOAD='{"alertId":"a1b2c3","machineNo":"MTR-07","partName":"Blade","partNo":"BLD-330","severity":"high","timestamp":"2026-07-11T10:30:00Z","temperature":78,"humidity":82}'
TOPIC="qsense/${NAMESPACE}/alerts"

echo "Publishing to ${BROKER}:${PORT}  topic=${TOPIC}"
mosquitto_pub -h "$BROKER" -p "$PORT" -t "$TOPIC" -q 1 -m "$PAYLOAD"
