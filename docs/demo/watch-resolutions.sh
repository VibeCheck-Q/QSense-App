#!/usr/bin/env bash
# Subscribes to the QSense outbound resolutions topic. Leave running during the demo.
# Requires Mosquitto clients (mosquitto_sub).
set -euo pipefail

BROKER="${1:-test.mosquitto.org}"
PORT="${2:-1883}"
NAMESPACE="${3:-qsense-demo}"

TOPIC="qsense/${NAMESPACE}/resolutions"
echo "Subscribing to ${BROKER}:${PORT}  topic=${TOPIC}  (Ctrl+C to stop)"
mosquitto_sub -h "$BROKER" -p "$PORT" -t "$TOPIC" -q 1 -v
