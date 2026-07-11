# QSense — MQTT Integration Contract

What an external publisher (Arduino, Python hub, `mosquitto_pub`, or a bridge) must send to
drive the QSense Android app, and what the app publishes back.

> QSense v1 is **text-only**: it reacts to a *fault alert* (machine + faulty part), asks the
> on-device GenieX LLM for likely causes/fixes, and publishes an acknowledgement when the
> operator resolves it. There is **no telemetry/vibration/audio stream** in v1 — unlike the
> Arduino `telemetry`/`anomaly`/`cmd` contract, QSense has **one inbound** message (alert) and a
> few small outbound ones: a minimal ack (`resolved:false`) when an alert arrives, and — on
> resolve — the rich resolution plus a minimal ack (`resolved:true`).

## Connection

| Setting | Value | Where to change |
|---|---|---|
| Broker host | `test.mosquitto.org` | `MqttConfig.host` in `shared/src/androidMain/.../di/AndroidConfig.kt` |
| Port | `1883` (plain TCP) | `MqttConfig.port` |
| Protocol | **MQTT 5**, QoS **1**, clean start, auto-reconnect | `HiveMqttGateway` |
| Client id | `qsense-<8 hex>` (random) | `HiveMqttGateway` |
| Inbound topic | `qsense/machine/monitoring` | `MqttConfig.alertsTopic` |
| Outbound topic | `qsense/machine/resolutions` | `MqttConfig.resolutionsTopic` |
| Outbound ack topic | `qsense/machine/ack` | `MqttConfig.ackTopic` |

- inbound alerts (the app subscribes) → `qsense/machine/monitoring`
- outbound resolutions (the app publishes) → `qsense/machine/resolutions`
- outbound minimal acks (the app publishes) → `qsense/machine/ack`

Unlike the Arduino contract, the **machine id is a field in the payload** (`machineNo`), not part
of the topic. The two topics are distinct so the app never re-ingests its own resolutions.

---

## 1. Fault Alert (inbound — you publish this)

Publish here to make an alert appear in the app and trigger on-device LLM diagnosis.

**Topic:** `qsense/machine/monitoring`
**Payload (JSON, UTF-8):**
```json
{
  "alertId": "e2d69c69-f6a6-4850-b76b-7912fc491e61",
  "machineNo": "M-01",
  "partName": "Fan Motor",
  "partNo": "PN-001",
  "severity": 48.896,
  "timestamp": "2026-07-11T17:57:05.435079"
}
```

**Field rules** (the string fields are **required**; the app silently drops an alert that fails
validation):

| Field | Rule | Notes |
|---|---|---|
| `alertId` | non-blank, ≤200 chars | dedupe key — reuse the same id to update, use a new id per fault |
| `machineNo` | non-blank, ≤200 chars | shown on the dashboard |
| `partName` | ≤200 chars | human-readable part name, fed into the LLM prompt + used to retrieve reference knowledge |
| `partNo` | non-blank, ≤200 chars | part number, fed into the LLM prompt |
| `severity` | number **or** string, ≤200 chars | accepts a numeric anomaly score (e.g. `48.896`) or a label (`high`/`medium`/`low`); decoded to a string |
| `timestamp` | non-blank, ≤200 chars | free-form; ISO instant (`…Z`) or local date-time with microseconds (`2026-07-11T17:57:05.435079`) |
| `temperature` | **optional** number (°C) | shown color-coded on the dashboard + fed into the prompt |
| `humidity` | **optional** number (%) | shown color-coded on the dashboard + fed into the prompt |

---

## 2. Resolution (outbound — the app publishes this)

When the operator picks the real cause/fix, adds notes, and taps **Resolve**, the app publishes
here (QoS 1). The alert is only marked resolved locally **after the broker PUBACK**.

**Topic:** `qsense/machine/resolutions`
**Payload (JSON, UTF-8):**
```json
{
  "alertId": "e2d69c69-f6a6-4850-b76b-7912fc491e61",
  "machineNo": "M-01",
  "partNo": "PN-001",
  "chosenCause": "Seal worn beyond tolerance",
  "appliedFix": "Replaced shaft seal and refilled hydraulic fluid",
  "notes": "Also observed minor weeping at the fitting",
  "resolvedAt": "2026-07-11T10:45:00Z"
}
```

| Field | Meaning |
|---|---|
| `alertId` | echoes the alert being resolved |
| `machineNo`, `partNo` | echoed from the alert |
| `chosenCause` | the cause the operator selected (one of the LLM's suggestions) |
| `appliedFix` | the fix the operator selected |
| `notes` | free-text operator notes (may be empty) |
| `resolvedAt` | ISO-8601 instant the operator resolved it |

Subscribe to `qsense/machine/resolutions` on your hub/Arduino to "close the loop" (e.g. clear
the fault, reset a baseline).

---

## 3. Resolved Ack (outbound — the app also publishes this)

A minimal status signal on `qsense/machine/ack` for subscribers that only need "is this alert
done", without parsing the full resolution. `resolved` is a JSON **boolean**:

- **When an alert arrives** the app publishes `resolved: false` (best-effort — a failed ack never
  drops the alert).
- **When the operator resolves it** the app publishes `resolved: true` (QoS 1, PUBACK-bound, sent
  alongside the rich resolution; both must succeed or the alert stays unresolved).

**Topic:** `qsense/machine/ack`
**Payload (JSON, UTF-8):**
```json
{
  "alertId": "e2d69c69-f6a6-4850-b76b-7912fc491e61",
  "resolved": true
}
```

| Field | Meaning |
|---|---|
| `alertId` | echoes the alert |
| `resolved` | `false` on arrival, `true` once resolved |

---

## Quick test with mosquitto

```bash
# Publish a fault alert (Linux/macOS)
mosquitto_pub -h test.mosquitto.org -p 1883 -q 1 \
  -t qsense/machine/monitoring \
  -m '{"alertId":"e2d69c69-f6a6-4850-b76b-7912fc491e61","machineNo":"M-01","partName":"Fan Motor","partNo":"PN-001","severity":48.896,"timestamp":"2026-07-11T17:57:05.435079"}'

# Watch resolutions + acks come back
mosquitto_sub -h test.mosquitto.org -p 1883 -q 1 -t qsense/machine/resolutions -t qsense/machine/ack -v
```

Ready-made scripts: `docs/demo/publish-alert.{sh,ps1}` and `docs/demo/watch-resolutions.{sh,ps1}`.

---

## Bridging an Arduino / EdgeImpulse anomaly to QSense

The Arduino `anomaly` message (`vibrationRms`, `audioLevel`, `severity: warning|critical`) does
**not** match QSense's schema. To feed a real Arduino into QSense, a small bridge on your hub must
map an anomaly → a QSense **Fault Alert**:

| Arduino `anomaly` | QSense `alert` |
|---|---|
| `machineId` | `machineNo` |
| (static per machine) | `partName`, `partNo` — the monitored part |
| `severity` (`warning`/`critical`) | `severity` (free-form; pass through or map `critical`→`high`) |
| `ts` (unix seconds) | `timestamp` (convert to ISO-8601) |
| `type` / `message` / `value` | fold into `partName`/notes as useful |

QSense has no telemetry concept, so the 1 Hz `telemetry` stream has no equivalent here — it would
be dropped. (Live sensor charts would be a separate v2 feature.)
