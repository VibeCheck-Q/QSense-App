# QSense v2 Vision — Slice A: End-to-End Image Diagnosis Plumbing

**Date:** 2026-07-12
**Status:** Draft — awaiting user review
**Scope:** Slice A only (the tracer bullet). Slices B and C are sketched under "Follow-up slices".

---

## 1. Context & goal

QSense v1 is text-only fault diagnosis on the phone. This adds the **v2 vision loop**: the
operator photographs a machine part (a blade), the image is processed **on a PC**, and an
**annotated image + a short diagnosis** comes back to the phone.

Because processing happens on the PC, **no on-device inference is involved** — the Qualcomm AI
Hub / quantized / Snapdragon path is explicitly *not* used here. The PC runs a normal Python
detector.

**Slice A goal:** prove the whole round-trip works end-to-end — phone captures → sends over MQTT
→ PC detects + annotates + summarizes → phone displays — using a **stock, throwaway detector**.
No custom training, no user data. This de-risks the plumbing before we invest in the tiny-dataset
training (Slice B).

**Definition of done for Slice A:** operator taps a camera button in QSense, captures a photo, and
within a few seconds sees the same photo with bounding boxes drawn and a one-line text diagnosis,
all via the existing `test.mosquitto.org` broker.

---

## 2. Architecture

```
📱 QSense (Android, this repo)                 💻 PC vision service (new: server/, Python)
┌─────────────────────────────┐               ┌───────────────────────────────────────┐
│ CameraX capture             │               │ paho-mqtt subscriber                    │
│  → downscale to ~640px      │  MQTT publish │  → decode JPEG                          │
│  → JPEG encode (~100KB)     │──────────────▶│  → detector.infer() (stock, Slice A)    │
│  → base64 in JSON           │ vision/request│  → draw boxes + labels (Pillow)         │
│                             │               │  → build text diagnosis                 │
│ subscribe vision/response   │◀──────────────│  → JPEG encode annotated + base64        │
│  → decode + show image      │ vision/response│  → publish JSON                         │
│  → show diagnosis (QSense   │               │                                         │
│    theme)                   │               │ RTMDet/ONNX loaded once at startup       │
└─────────────────────────────┘               └───────────────────────────────────────┘
        both connect to test.mosquitto.org:1883 (existing broker, MQTT 5, QoS 1)
```

Key design decision: **downscale to ~640px on the phone before sending.** RTMDet's input is 640px,
so a larger image is wasted bytes. This keeps the MQTT payload at ~50–150KB, comfortably within
the public broker's limits — which is what makes "reuse MQTT" viable.

---

## 3. Message contract (MQTT 5, QoS 1)

New topics, siblings of the existing ones, kept separate so nothing cross-ingests:

- **`qsense/vision/request`** (phone → PC):
  ```json
  { "requestId": "uuid", "machineNo": "M-12", "partNo": "blade-3",
    "imageB64": "<jpeg base64>", "timestamp": "2026-07-12T05:00:00Z" }
  ```
- **`qsense/vision/response`** (PC → phone):
  ```json
  { "requestId": "uuid",
    "annotatedImageB64": "<jpeg base64>",
    "detections": [ { "cls": "blade", "score": 0.91, "box": [x1,y1,x2,y2] } ],
    "diagnosis": "1 disturbance detected on blade." }
  ```

`requestId` correlates response to request (phone ignores responses whose id it isn't waiting on).
Coordinates are in pixels of the returned annotated image.

---

## 4. PC vision service (`server/`, Python 3.12, system interpreter)

Single small package; inference via **ONNX Runtime** (no mmdet on the serving box). Modules:

- `mqtt_client.py` — connect to the broker, subscribe `vision/request`, publish `vision/response`.
  QoS 1, MQTT 5, clean start, auto-reconnect (mirrors the QSense gateway's behaviour).
- `detector.py` — a small interface `Detector.infer(image) -> list[Detection]`. Slice A ships a
  `StockDetector` backed by **torchvision** (`fasterrcnn`/`ssdlite`, COCO-pretrained) — chosen
  because it installs with just `torch`+`torchvision` (no mmdet) and proves the preprocessing /
  box path. Slice B adds `RtmdetOnnxDetector` behind the same interface.
- `annotate.py` — draw boxes + class labels on the image with Pillow; return JPEG bytes.
- `diagnose.py` — Slice A: a rule-based text summary from the detections
  (e.g. counts per class). Slice C: optionally call QSense's RAG/LLM path.
- `service.py` — wire the above: on message → decode → infer → annotate → diagnose → publish.
- `config.py` — broker host/port/topics (defaults match `MqttConfig`), model path.
- `requirements.txt`, `README.md` (run instructions).

Startup: load the detector once, connect, block on the MQTT loop.

---

## 5. QSense phone changes (this repo)

Follow existing clean-arch KMP boundaries:

- **`MqttConfig`** (`di/AndroidConfig.kt`): add `visionRequestTopic = "qsense/vision/request"`,
  `visionResponseTopic = "qsense/vision/response"`.
- **`MqttGateway`** (commonMain interface): add
  `suspend fun publishVisionRequest(request: VisionRequest)` and
  `val visionResponses: Flow<VisionResponse>`.
- **`HiveMqttGateway`** (androidMain): implement — subscribe to the response topic on connect,
  emit parsed `VisionResponse`; publish requests PUBACK-bound like the existing publishers.
- **Domain models** (commonMain): `VisionRequest`, `VisionResponse`, `Detection` +
  kotlinx-serialization (strict for these payloads, consistent with MQTT payload handling).
- **Camera capture** (androidMain): CameraX capture → downscale to 640px → JPEG → base64.
  A thin `ImageCapturer` interface in commonMain with the Android implementation, so the
  ViewModel stays platform-agnostic.
- **Presentation**: a new vision feature (capture button → capturing → waiting → result screen
  showing the annotated image + diagnosis), styled in the QSense theme. New `VisionViewModel`
  (or extend `DashboardViewModel`) holding request/response state, wired through `AppContainer`.
- **Permissions**: `CAMERA` in the Android manifest.

---

## 6. Detector strategy across slices

| Slice | Detector on PC | Trained on | mmdet needed? |
|---|---|---|---|
| **A** (this spec) | torchvision COCO (throwaway) | COCO (stock) | No |
| **B** | RTMDet exported to ONNX | your blade/disturbance data | Only for training (conda Py 3.10) + one ONNX export; **not** on the serving box |
| **C** | same | same | same |

Training env (Slice B): isolated **conda Python 3.10** on the RTX 4050 (6 GB VRAM → RTMDet-s/-tiny,
small batch). The `qai_hub_models/rtmdet` `model.py` is the reference for how weights/config load;
we fine-tune from the COCO checkpoint with `num_classes=2`.

---

## 7. Error handling

- **PC:** malformed/oversized payload → log + publish a response with empty `detections` and a
  `diagnosis` of "could not process image"; a failed inference never crashes the loop.
- **Phone:** response timeout (e.g. 15s) → show a retry-able error; ignore responses with an
  unknown `requestId`; MQTT disconnect surfaces via the existing `connectionState`.
- Image too large after 640px downscale should not happen; if base64 exceeds a sanity cap the
  phone rejects before publishing.

---

## 8. Testing

- **PC:** unit tests for `detector` (stubbed), `annotate` (box drawing on a fixture), `diagnose`
  (detections → text), and a `service` test that feeds a fixture request and asserts a well-formed
  response (detector faked). No broker needed for units; one manual broker round-trip check.
- **Phone:** follow existing test patterns — fake `MqttGateway` + `ImageCapturer`; a ViewModel test
  for capture → publish → receive → display state transitions. Manual on-device camera check.

---

## 9. Out of scope (Slice A) / follow-up slices

- **Slice B — custom training:** annotate your images (COCO format), fine-tune RTMDet-s on the
  RTX 4050, export to ONNX, drop in `RtmdetOnnxDetector`. Needs your dataset (blocking) — honest
  caveat: a few dozen images yields a PoC-quality detector, not production.
- **Slice C — rich diagnosis:** feed detections into QSense's existing RAG/LLM diagnosis instead of
  the rule-based summary.
- Not in any current slice: live/streaming frames, on-device inference, auth on the broker.

---

## 10. Open items / needed from user

1. **Your blade images** in `server/data/raw/` + exact count — for Slice B (not Slice A).
2. Confirm the two defaulted assumptions: diagnosis = rule-based text in A/B (LLM deferred to C);
   PC service lives in `server/` inside this repo.
3. Green light to create the conda training env (scripted) when we reach Slice B.
