# QSense — MQTT Integration Contract

Everything an external service (monitoring producer, PC vision service, dashboard) needs to
interoperate with the QSense app over MQTT.

## Connection

| | Value |
|---|---|
| Broker host | `test.mosquitto.org` (demo — swap for a private/authenticated broker in prod) |
| Port (TCP) | `1883` |
| Port (WebSocket) | `8080`, path `/mqtt` (use if the network blocks/reset raw 1883) |
| Protocol | **MQTT 5** |
| QoS | **1** (at-least-once) everywhere |
| Clean start | `true` · Retain | `false` |
| Encoding | UTF-8 **JSON** on every topic |

Identity rule: an alert is keyed by **`machineNo` + `partNo`** (one logical fault site). The
monitoring feed re-sends the same machine/part with a fresh `alertId` on each reading.

## Topics

| Topic | Publisher → Subscriber | Payload |
|---|---|---|
| `qsense/machine/monitoring` | monitoring platform → app | `FaultAlert` |
| `qsense/machine/resolutions` | app → consumers | `Resolution` |
| `qsense/machine/ack` | app → consumers | `ResolvedAck` |
| `qsense/vision/request` | app → PC vision service | `VisionRequest` |
| `qsense/vision/response` | PC vision service → app | `VisionResponse` |

---

## Payloads (all fields)

### `FaultAlert` — inbound alert (`qsense/machine/monitoring`)

| Field | Type | Req? | Notes |
|---|---|---|---|
| `alertId` | string | yes | non-blank; ≤200 chars |
| `machineNo` | string | yes | non-blank; ≤200 |
| `partName` | string | yes | human-readable, e.g. `"Spindle Bearing"`; ≤200 |
| `partNo` | string | yes | e.g. `"SB-204"`; ≤200 |
| `severity` | number **or** string | yes | flexible: numeric anomaly score (`48.896`) **or** label (`"high"`) — both decode to string. ≥40 CRITICAL / ≥10 WARNING / else OK |
| `timestamp` | string | yes | free-form (ISO instant or local date-time w/ microseconds); non-blank |
| `temperature` | number | no | °C; nullable/omittable |
| `humidity` | number | no | %; nullable/omittable |

```json
{
  "alertId": "live-001",
  "machineNo": "M-07",
  "partName": "Spindle Bearing",
  "partNo": "SB-204",
  "severity": 42.5,
  "timestamp": "2026-07-12T10:15:00Z",
  "temperature": 78.5,
  "humidity": 40.0
}
```

### `Resolution` — outbound on resolve (`qsense/machine/resolutions`)

| Field | Type | Notes |
|---|---|---|
| `alertId` | string | the resolved alert |
| `machineNo` | string | |
| `partNo` | string | |
| `chosenCause` | string | the cause the operator picked |
| `appliedFix` | string | the fix applied |
| `notes` | string | operator free-text (may be empty) |
| `resolvedAt` | string | ISO timestamp |

```json
{
  "alertId": "live-001",
  "machineNo": "M-07",
  "partNo": "SB-204",
  "chosenCause": "Lubrication breakdown",
  "appliedFix": "Re-lubricate or replace the bearing",
  "notes": "swapped bearing, re-greased",
  "resolvedAt": "2026-07-12T10:20:00Z"
}
```

### `ResolvedAck` — lifecycle ack (`qsense/machine/ack`)

| Field | Type | Notes |
|---|---|---|
| `alertId` | string | |
| `resolved` | number | **`0`** when the alert first arrives (best-effort), **`1`** when resolved (PUBACK-bound) |

```json
{ "alertId": "live-001", "resolved": 0 }
```

### `VisionRequest` — detection request (`qsense/vision/request`)

| Field | Type | Notes |
|---|---|---|
| `requestId` | string | correlation id; the response echoes it |
| `machineNo` | string | operator label (from the alert or typed on the scan screen) |
| `partNo` | string | operator label / part name |
| `imageB64` | string | **base64 JPEG**, downscaled so the longest side ≤ 640px, quality ~85 (~100 KB) |
| `timestamp` | string | ISO capture time |

```json
{
  "requestId": "req-8f3a1c",
  "machineNo": "M-12",
  "partNo": "Cutting Blade",
  "imageB64": "/9j/4AAQSkZJRgABAQ...",
  "timestamp": "2026-07-12T10:40:00Z"
}
```

### `VisionResponse` — detection result (`qsense/vision/response`)

| Field | Type | Notes |
|---|---|---|
| `requestId` | string | must match the request's `requestId` (app ignores non-matching) |
| `annotatedImageB64` | string | base64 JPEG with boxes drawn |
| `detections` | array of `Detection` | may be empty |
| `diagnosis` | string | short text summary |

**`Detection`**

| Field | Type | Notes |
|---|---|---|
| `cls` | string | class label, e.g. `"blade"`, `"disturbance"` |
| `score` | number | confidence 0–1 |
| `box` | array[4] of int | `[x1, y1, x2, y2]` in pixels of the annotated image |

```json
{
  "requestId": "req-8f3a1c",
  "annotatedImageB64": "/9j/4AAQSkZJRg...",
  "detections": [
    { "cls": "blade", "score": 0.91, "box": [160, 120, 480, 360] },
    { "cls": "disturbance", "score": 0.74, "box": [300, 210, 360, 260] }
  ],
  "diagnosis": "Detected: 1 blade, 1 disturbance."
}
```

---

## Behavior notes for integrators

- **QoS 1 = at-least-once** — de-duplicate by `alertId` / `requestId` if you must be exactly-once.
- **No offline queue on the app side** (clean start): a message published while the app is
  disconnected/backgrounded is missed. Publish while a subscriber is connected.
- **Distinct topics** keep the app from re-ingesting its own `ack`/`resolution` output.
- **Vision correlation:** always echo the request's `requestId` in the response; the app drops
  responses whose id it isn't waiting on, and times out after 15s.
- **Vision images** are already downscaled to 640px on the phone — send/return JPEG, not raw.
- ⚠️ On the public broker these topics are **world-readable**; use a private/authenticated broker
  before real data.

_Source of truth: `shared/src/commonMain/.../domain/model/` (FaultAlert, Resolution, ResolvedAck,
Vision.kt) and `MqttConfig` in `shared/src/androidMain/.../di/AndroidConfig.kt`._
