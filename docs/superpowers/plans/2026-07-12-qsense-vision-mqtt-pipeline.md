# QSense Vision MQTT Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove an image can be sent from a client to a PC service and an annotated result returned, entirely over the existing MQTT broker — then wire the QSense phone app into that same round-trip.

**Architecture:** A new Python service (`server/`) subscribes to `qsense/vision/request`, decodes the JPEG, runs a stub detector, draws boxes, builds a one-line diagnosis, and publishes the result to `qsense/vision/response`. Milestone 1 validates this with a pure-Python round-trip tool against the real broker (no detection heavy-lifting, no Android). Milestone 2 extends the QSense `MqttGateway` + adds camera capture so the phone is the real client.

**Tech Stack:** Python 3.12 + `paho-mqtt` + `Pillow` (PC); Kotlin Multiplatform + kotlinx-serialization + HiveMQ MQTT client + CameraX + Compose (phone).

## Global Constraints

- Broker: `test.mosquitto.org` port `1883`, MQTT 5, QoS 1 (matches existing `MqttConfig`).
- New topics only, distinct from existing ones: `qsense/vision/request`, `qsense/vision/response`.
- Image is downscaled to max side 640px and JPEG-encoded before base64 — payload target < 200KB.
- `requestId` (UUID string) correlates response to request.
- Response JSON shape is fixed: `{ requestId, annotatedImageB64, detections:[{cls,score,box:[x1,y1,x2,y2]}], diagnosis }`.
- Slice-A detector is a **stub** (fixed centered box, class `blade`, score `1.0`). No torch / mmdet.
- PC service lives in `server/` inside this repo. Python system interpreter (3.12).
- A failed message must never crash the MQTT loop.

## Broker & security caveats (from plan review)

- `test.mosquitto.org` is a **public** broker; `qsense/vision/*` are **world-readable shared
  topics**. Acceptable for this plumbing proof (responses are filtered by `requestId`), but:
  - **Do not send real/proprietary blade images over the public broker.** Use a private broker
    (or HiveMQ Cloud with auth) before Slice B.
  - Optional hardening (deferred): append a random per-session token to the topics
    (`qsense/vision/<token>/request`) so two sessions never collide. Not required for Milestone 1.
- Public-broker latency can spike; the round-trip tool's 15s timeout is a proof threshold, not a
  production SLA.

---

# MILESTONE 1 — PC pipeline + MQTT round-trip proof

Deliverable: a running PC service + a `roundtrip.py` tool that publishes a real JPEG to the live
broker and receives back a valid annotated response. This alone proves "sending and receiving
through MQTT is fine".

---

### Task 1: Project scaffold + config

**Files:**
- Create: `server/config.py`
- Create: `server/requirements.txt`
- Create: `server/__init__.py`
- Test: `server/tests/test_config.py`
- Create: `server/tests/__init__.py`

**Interfaces:**
- Produces: `Config` dataclass with fields `host:str`, `port:int`, `request_topic:str`,
  `response_topic:str`, `keepalive:int`, `qos:int`, and classmethod `Config.default() -> Config`.

- [ ] **Step 1: Write `server/requirements.txt`**

```
paho-mqtt==2.1.0
Pillow==11.0.0
pytest==8.3.3
```

- [ ] **Step 2: Write the failing test**

`server/tests/test_config.py`:
```python
from server.config import Config

def test_default_config_matches_broker_and_topics():
    c = Config.default()
    assert c.host == "test.mosquitto.org"
    assert c.port == 1883
    assert c.request_topic == "qsense/vision/request"
    assert c.response_topic == "qsense/vision/response"
    assert c.qos == 1
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd server && py -m pytest tests/test_config.py -v`
Expected: FAIL — `ModuleNotFoundError: No module named 'server.config'`

- [ ] **Step 4: Write `server/config.py`**

```python
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    host: str
    port: int
    request_topic: str
    response_topic: str
    keepalive: int
    qos: int

    @classmethod
    def default(cls) -> "Config":
        return cls(
            host="test.mosquitto.org",
            port=1883,
            request_topic="qsense/vision/request",
            response_topic="qsense/vision/response",
            keepalive=60,
            qos=1,
        )
```

- [ ] **Step 5: Create empty `server/__init__.py` and `server/tests/__init__.py`**

Both empty files.

- [ ] **Step 6: Install deps and run the test**

Run: `cd server && py -m pip install -r requirements.txt && py -m pytest tests/test_config.py -v`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add server/config.py server/requirements.txt server/__init__.py server/tests/
git commit -m "feat(server): scaffold vision service config"
```

---

### Task 2: Stub detector

**Files:**
- Create: `server/detector.py`
- Test: `server/tests/test_detector.py`

**Interfaces:**
- Produces: `Detection` dataclass `{cls:str, score:float, box:tuple[int,int,int,int]}`;
  `Detector` typing.Protocol with `infer(width:int, height:int) -> list[Detection]`;
  `StubDetector` implementing it (one box covering the centre half of the image).

- [ ] **Step 1: Write the failing test**

`server/tests/test_detector.py`:
```python
from server.detector import StubDetector

def test_stub_detector_returns_one_centered_blade_box():
    dets = StubDetector().infer(width=640, height=480)
    assert len(dets) == 1
    d = dets[0]
    assert d.cls == "blade"
    assert d.score == 1.0
    x1, y1, x2, y2 = d.box
    assert (x1, y1, x2, y2) == (160, 120, 480, 360)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && py -m pytest tests/test_detector.py -v`
Expected: FAIL — module not found

- [ ] **Step 3: Write `server/detector.py`**

```python
from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Detection:
    cls: str
    score: float
    box: tuple[int, int, int, int]  # x1, y1, x2, y2


class Detector(Protocol):
    def infer(self, width: int, height: int) -> list[Detection]:
        ...


class StubDetector:
    """Placeholder for Slice A — returns a fixed box over the centre half of the frame."""

    def infer(self, width: int, height: int) -> list[Detection]:
        x1, y1 = width // 4, height // 4
        x2, y2 = width - x1, height - y1
        return [Detection(cls="blade", score=1.0, box=(x1, y1, x2, y2))]
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd server && py -m pytest tests/test_detector.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/detector.py server/tests/test_detector.py
git commit -m "feat(server): add stub detector"
```

---

### Task 3: Annotate + diagnose

**Files:**
- Create: `server/annotate.py`
- Create: `server/diagnose.py`
- Test: `server/tests/test_annotate.py`
- Test: `server/tests/test_diagnose.py`

**Interfaces:**
- Consumes: `Detection` from Task 2.
- Produces: `annotate(image_bytes:bytes, detections:list[Detection]) -> bytes` (returns JPEG bytes
  with boxes+labels drawn); `diagnose(detections:list[Detection]) -> str`.

- [ ] **Step 1: Write the failing tests**

`server/tests/test_annotate.py`:
```python
import io
from PIL import Image
from server.annotate import annotate
from server.detector import Detection

def _jpeg(w, h):
    buf = io.BytesIO()
    Image.new("RGB", (w, h), (10, 20, 30)).save(buf, format="JPEG")
    return buf.getvalue()

def test_annotate_returns_valid_jpeg_same_size():
    src = _jpeg(640, 480)
    out = annotate(src, [Detection("blade", 1.0, (10, 10, 100, 100))])
    img = Image.open(io.BytesIO(out))
    assert img.format == "JPEG"
    assert img.size == (640, 480)
```

`server/tests/test_diagnose.py`:
```python
from server.diagnose import diagnose
from server.detector import Detection

def test_diagnose_empty():
    assert diagnose([]) == "No objects detected."

def test_diagnose_counts_by_class():
    dets = [Detection("blade", 1.0, (0,0,1,1)), Detection("disturbance", 0.8, (0,0,1,1))]
    assert diagnose(dets) == "Detected: 1 blade, 1 disturbance."
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && py -m pytest tests/test_annotate.py tests/test_diagnose.py -v`
Expected: FAIL — modules not found

- [ ] **Step 3: Write `server/annotate.py`**

```python
import io
from PIL import Image, ImageDraw
from server.detector import Detection


def annotate(image_bytes: bytes, detections: list[Detection]) -> bytes:
    img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    draw = ImageDraw.Draw(img)
    for d in detections:
        x1, y1, x2, y2 = d.box
        draw.rectangle([x1, y1, x2, y2], outline=(255, 92, 74), width=3)
        draw.text((x1 + 4, y1 + 4), f"{d.cls} {d.score:.2f}", fill=(255, 255, 255))
    out = io.BytesIO()
    img.save(out, format="JPEG", quality=85)
    return out.getvalue()
```

- [ ] **Step 4: Write `server/diagnose.py`**

```python
from collections import Counter
from server.detector import Detection


def diagnose(detections: list[Detection]) -> str:
    if not detections:
        return "No objects detected."
    counts = Counter(d.cls for d in detections)
    parts = [f"{n} {cls}" for cls, n in counts.items()]
    return "Detected: " + ", ".join(parts) + "."
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd server && py -m pytest tests/test_annotate.py tests/test_diagnose.py -v`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/annotate.py server/diagnose.py server/tests/test_annotate.py server/tests/test_diagnose.py
git commit -m "feat(server): add annotate + diagnose"
```

---

### Task 4: Request handler (pure, no MQTT)

**Files:**
- Create: `server/handler.py`
- Test: `server/tests/test_handler.py`

**Interfaces:**
- Consumes: `Detector`/`StubDetector` (Task 2), `annotate` (Task 3), `diagnose` (Task 3).
- Produces: `handle_request(payload:bytes, detector:Detector) -> bytes` — parses the request JSON,
  base64-decodes the image, runs the pipeline, and returns response JSON bytes with the fixed shape.
  On any error returns a valid response with empty `detections` and a `diagnosis` error string,
  preserving `requestId` when parseable.

- [ ] **Step 1: Write the failing test**

`server/tests/test_handler.py`:
```python
import base64, io, json
from PIL import Image
from server.handler import handle_request
from server.detector import StubDetector

def _request_bytes(w=640, h=480, request_id="abc"):
    buf = io.BytesIO()
    Image.new("RGB", (w, h), (0, 0, 0)).save(buf, format="JPEG")
    b64 = base64.b64encode(buf.getvalue()).decode()
    return json.dumps({"requestId": request_id, "machineNo": "M1",
                        "partNo": "blade-1", "imageB64": b64,
                        "timestamp": "2026-07-12T00:00:00Z"}).encode()

def test_handle_request_returns_valid_response():
    out = json.loads(handle_request(_request_bytes(), StubDetector()))
    assert out["requestId"] == "abc"
    assert len(out["detections"]) == 1
    assert out["detections"][0]["cls"] == "blade"
    assert out["diagnosis"] == "Detected: 1 blade."
    # annotated image round-trips as a JPEG
    img = Image.open(io.BytesIO(base64.b64decode(out["annotatedImageB64"])))
    assert img.format == "JPEG"

def test_handle_request_bad_payload_does_not_raise():
    out = json.loads(handle_request(b"not json", StubDetector()))
    assert out["detections"] == []
    assert "could not" in out["diagnosis"].lower()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && py -m pytest tests/test_handler.py -v`
Expected: FAIL — module not found

- [ ] **Step 3: Write `server/handler.py`**

```python
import base64
import io
import json
from dataclasses import asdict
from PIL import Image
from server.annotate import annotate
from server.detector import Detector
from server.diagnose import diagnose


def handle_request(payload: bytes, detector: Detector) -> bytes:
    request_id = ""
    try:
        req = json.loads(payload)
        request_id = req.get("requestId", "")
        image_bytes = base64.b64decode(req["imageB64"])
        width, height = Image.open(io.BytesIO(image_bytes)).size
        detections = detector.infer(width, height)
        annotated = annotate(image_bytes, detections)
        response = {
            "requestId": request_id,
            "annotatedImageB64": base64.b64encode(annotated).decode(),
            "detections": [
                {"cls": d.cls, "score": d.score, "box": list(d.box)} for d in detections
            ],
            "diagnosis": diagnose(detections),
        }
    except Exception as exc:  # never let a bad message crash the loop
        response = {
            "requestId": request_id,
            "annotatedImageB64": "",
            "detections": [],
            "diagnosis": f"Could not process image: {exc}",
        }
    return json.dumps(response).encode()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd server && py -m pytest tests/test_handler.py -v`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/handler.py server/tests/test_handler.py
git commit -m "feat(server): add pure request handler"
```

---

### Task 5: MQTT service wiring + entrypoint

**Files:**
- Create: `server/mqtt_service.py`
- Create: `server/main.py`
- Test: `server/tests/test_mqtt_service.py`

**Interfaces:**
- Consumes: `Config` (Task 1), `handle_request` (Task 4), `StubDetector` (Task 2).
- Produces: `VisionService(config:Config, detector:Detector)` with `_on_message(client, userdata, msg)`
  that calls `handle_request` and publishes to `config.response_topic`; and `run()` that connects,
  subscribes, and starts the network loop. `client` is created via a `make_client()` factory so tests
  can inject a fake.

- [ ] **Step 1: Write the failing test (fake client, no network)**

`server/tests/test_mqtt_service.py`:
```python
import json
from types import SimpleNamespace
from server.mqtt_service import VisionService
from server.config import Config
from server.detector import StubDetector
from server.tests.test_handler import _request_bytes

class FakeClient:
    def __init__(self): self.published = []
    def publish(self, topic, payload, qos): self.published.append((topic, payload, qos))

def test_on_message_publishes_response_to_response_topic():
    svc = VisionService(Config.default(), StubDetector())
    svc.client = FakeClient()
    msg = SimpleNamespace(payload=_request_bytes(request_id="xyz"))
    svc._on_message(svc.client, None, msg)
    assert len(svc.client.published) == 1
    topic, payload, qos = svc.client.published[0]
    assert topic == "qsense/vision/response"
    assert qos == 1
    assert json.loads(payload)["requestId"] == "xyz"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && py -m pytest tests/test_mqtt_service.py -v`
Expected: FAIL — module not found

- [ ] **Step 3: Write `server/mqtt_service.py`**

```python
import logging
import paho.mqtt.client as mqtt
from server.config import Config
from server.detector import Detector
from server.handler import handle_request

log = logging.getLogger("qsense.vision")


def make_client() -> mqtt.Client:
    return mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, protocol=mqtt.MQTTv5)


class VisionService:
    def __init__(self, config: Config, detector: Detector):
        self.config = config
        self.detector = detector
        self.client = make_client()

    def _on_connect(self, client, userdata, flags, reason_code, properties):
        log.info("connected rc=%s; subscribing %s", reason_code, self.config.request_topic)
        client.subscribe(self.config.request_topic, qos=self.config.qos)

    def _on_message(self, client, userdata, msg):
        try:
            response = handle_request(msg.payload, self.detector)
            client.publish(self.config.response_topic, response, qos=self.config.qos)
            log.info("responded (%d bytes)", len(response))
        except Exception:  # defensive: the loop must survive any message
            log.exception("failed handling message")

    def run(self) -> None:
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.client.connect(self.config.host, self.config.port, self.config.keepalive)
        self.client.loop_forever()
```

- [ ] **Step 4: Write `server/main.py`**

```python
import logging
from server.config import Config
from server.detector import StubDetector
from server.mqtt_service import VisionService


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    VisionService(Config.default(), StubDetector()).run()


if __name__ == "__main__":
    main()
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd server && py -m pytest tests/test_mqtt_service.py -v`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/mqtt_service.py server/main.py server/tests/test_mqtt_service.py
git commit -m "feat(server): add MQTT service wiring + entrypoint"
```

---

### Task 6: Real-broker round-trip tool + README (THE MQTT PROOF)

**Files:**
- Create: `server/tools/roundtrip.py`
- Create: `server/tools/__init__.py`
- Create: `server/README.md`
- Create: `server/data/sample.jpg` (any small JPEG; generated if absent — see step)

**Interfaces:**
- Consumes: `Config` (Task 1). Standalone script — publishes a sample image to the request topic,
  waits for a response, saves it to `server/data/annotated_out.jpg`, prints the diagnosis.

- [ ] **Step 1: Write `server/tools/roundtrip.py`**

```python
"""Manual MQTT round-trip proof. Run the service (py -m server.main) first, in another terminal."""
import base64, io, json, sys, time, uuid
import paho.mqtt.client as mqtt
from PIL import Image
from server.config import Config


def _sample_jpeg() -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (640, 480), (40, 60, 90)).save(buf, format="JPEG")
    return buf.getvalue()


def main() -> int:
    cfg = Config.default()
    request_id = str(uuid.uuid4())
    got = {}

    def on_connect(c, u, f, rc, p):
        c.subscribe(cfg.response_topic, qos=cfg.qos)
        payload = json.dumps({
            "requestId": request_id, "machineNo": "M1", "partNo": "blade-1",
            "imageB64": base64.b64encode(_sample_jpeg()).decode(),
            "timestamp": "2026-07-12T00:00:00Z",
        }).encode()
        c.publish(cfg.request_topic, payload, qos=cfg.qos)
        print(f"published request {request_id}")

    def on_message(c, u, msg):
        data = json.loads(msg.payload)
        if data.get("requestId") == request_id:
            got.update(data)

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, protocol=mqtt.MQTTv5)
    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(cfg.host, cfg.port, cfg.keepalive)
    client.loop_start()

    deadline = time.time() + 15
    while not got and time.time() < deadline:
        time.sleep(0.2)
    client.loop_stop()

    if not got:
        print("TIMEOUT — no response in 15s")
        return 1
    with open("server/data/annotated_out.jpg", "wb") as f:
        f.write(base64.b64decode(got["annotatedImageB64"]))
    print("diagnosis:", got["diagnosis"])
    print("saved server/data/annotated_out.jpg")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: Create `server/tools/__init__.py` (empty) and `server/data/` (git-keep)**

Create empty `server/tools/__init__.py`. Create `server/data/.gitkeep`.

- [ ] **Step 3: Write `server/README.md`**

````markdown
# QSense Vision Service (PC)

Receives images over MQTT, returns an annotated image + diagnosis. Slice A uses a stub detector.

## Run

```
cd server
py -m pip install -r requirements.txt
py -m server.main            # terminal 1: the service
py -m server.tools.roundtrip # terminal 2: publishes a test image, saves annotated_out.jpg
```

Broker/topics are in `config.py` (defaults match the QSense app's `MqttConfig`).
````

- [ ] **Step 4: Run the full round-trip against the live broker**

Run (two terminals): `cd server && py -m server.main` then `cd server && py -m server.tools.roundtrip`
Expected: `roundtrip` prints `diagnosis: Detected: 1 blade.` and writes `server/data/annotated_out.jpg`. Open it — a box is drawn on the sample image.

- [ ] **Step 5: Run the whole PC test suite**

Run: `cd server && py -m pytest -v`
Expected: all PASS

- [ ] **Step 6: Commit**

```bash
git add server/tools/ server/README.md server/data/.gitkeep
git commit -m "feat(server): add real-broker round-trip proof + README"
```

**✅ Milestone 1 done: MQTT send/receive proven end-to-end on the PC.**

---

# MILESTONE 2 — QSense phone as the real client

Deliverable: QSense captures a photo, publishes it on `qsense/vision/request`, receives the
annotated result on `qsense/vision/response`, and displays it. Same broker, same contract.

---

### Task 7: Vision domain models + config topics

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/qsense/domain/model/Vision.kt`
- Modify: `shared/src/androidMain/kotlin/com/example/qsense/di/AndroidConfig.kt` (MqttConfig)
- Test: `shared/src/commonTest/kotlin/com/example/qsense/domain/model/VisionSerializationTest.kt`

**Interfaces:**
- Produces: `@Serializable VisionRequest(requestId, machineNo, partNo, imageB64, timestamp)`,
  `@Serializable VisionResponse(requestId, annotatedImageB64, detections, diagnosis)`,
  `@Serializable Detection(cls, score, box:List<Int>)`. Adds `visionRequestTopic`,
  `visionResponseTopic` to `MqttConfig`.

- [ ] **Step 1: Write the failing test**

`VisionSerializationTest.kt`:
```kotlin
package com.example.qsense.domain.model

import com.example.qsense.data.serialization.JsonProviders
import kotlin.test.Test
import kotlin.test.assertEquals

class VisionSerializationTest {
    @Test
    fun responseRoundTrips() {
        val json = """{"requestId":"a","annotatedImageB64":"x",
            "detections":[{"cls":"blade","score":1.0,"box":[1,2,3,4]}],
            "diagnosis":"Detected: 1 blade."}"""
        val r = JsonProviders.strict.decodeFromString(VisionResponse.serializer(), json)
        assertEquals("a", r.requestId)
        assertEquals("blade", r.detections.first().cls)
        assertEquals(listOf(1,2,3,4), r.detections.first().box)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --tests "*VisionSerializationTest*"`
Expected: FAIL — unresolved `VisionResponse`

- [ ] **Step 3: Write `Vision.kt`**

```kotlin
package com.example.qsense.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VisionRequest(
    val requestId: String,
    val machineNo: String,
    val partNo: String,
    val imageB64: String,
    val timestamp: String,
)

@Serializable
data class Detection(
    val cls: String,
    val score: Float,
    val box: List<Int>,
)

@Serializable
data class VisionResponse(
    val requestId: String,
    val annotatedImageB64: String,
    val detections: List<Detection>,
    val diagnosis: String,
)
```

- [ ] **Step 4: Add topics to `MqttConfig` (in `AndroidConfig.kt`)**

Add after `ackTopic`:
```kotlin
    val visionRequestTopic: String = "qsense/vision/request",
    val visionResponseTopic: String = "qsense/vision/response",
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest --tests "*VisionSerializationTest*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/qsense/domain/model/Vision.kt shared/src/androidMain/kotlin/com/example/qsense/di/AndroidConfig.kt shared/src/commonTest/kotlin/com/example/qsense/domain/model/VisionSerializationTest.kt
git commit -m "feat(shared): add vision models + mqtt vision topics"
```

---

### Task 8: Extend MqttGateway + HiveMqttGateway

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/qsense/domain/service/MqttGateway.kt`
- Modify: `shared/src/androidMain/kotlin/com/example/qsense/data/mqtt/HiveMqttGateway.kt`
- Modify: `shared/src/commonTest/.../testutil/FakeMqttGateway.kt`
- Test: `shared/src/commonTest/kotlin/com/example/qsense/data/mqtt/VisionGatewayContractTest.kt`

**Interfaces:**
- Consumes: `VisionRequest`, `VisionResponse` (Task 7).
- Produces: on `MqttGateway` — `val visionResponses: Flow<VisionResponse>` and
  `suspend fun publishVisionRequest(request: VisionRequest)`. `HiveMqttGateway` subscribes to
  `visionResponseTopic` on connect and publishes requests PUBACK-bound (mirroring `publishResolution`).

- [ ] **Step 1: Write the failing test against the fake**

`VisionGatewayContractTest.kt`:
```kotlin
package com.example.qsense.data.mqtt

import com.example.qsense.domain.model.VisionRequest
import com.example.qsense.testutil.FakeMqttGateway
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VisionGatewayContractTest {
    @Test
    fun publishRecordsRequest() = runTest {
        val gw = FakeMqttGateway()
        gw.publishVisionRequest(VisionRequest("r1","M1","blade","b64","t"))
        assertEquals("r1", gw.publishedVisionRequests.single().requestId)
    }
}
```

- [ ] **Step 2: Add the two members to the `MqttGateway` interface**

```kotlin
    val visionResponses: Flow<VisionResponse>
    suspend fun publishVisionRequest(request: VisionRequest)
```
(add imports for `VisionRequest`, `VisionResponse`).

- [ ] **Step 3: Extend `FakeMqttGateway`** (make the test compile)

Add a `MutableSharedFlow<VisionResponse>` exposed as `visionResponses`, a
`val publishedVisionRequests = mutableListOf<VisionRequest>()`, and implement
`publishVisionRequest { publishedVisionRequests += it }`.

- [ ] **Step 4: Run to verify it fails then passes on the fake**

Run: `./gradlew :shared:testAndroidHostTest --tests "*VisionGatewayContractTest*"`
Expected: PASS (fake), FAIL to compile until `HiveMqttGateway` also implements the members.

- [ ] **Step 5: Implement in `HiveMqttGateway`**

- Add a `MutableSharedFlow<VisionResponse>(extraBufferCapacity=8)` exposed as `visionResponses`.
- In the connect/subscribe block, also `subscribe(config.visionResponseTopic, QoS 1)`; in the
  message callback, when topic == `visionResponseTopic`, decode with `JsonProviders.strict` and emit.
- Implement `publishVisionRequest` by encoding with `JsonProviders.strict` and publishing to
  `config.visionRequestTopic` QoS 1, awaiting PUBACK exactly like `publishResolution`.

- [ ] **Step 6: Build shared to confirm it compiles**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/.../MqttGateway.kt shared/src/androidMain/.../HiveMqttGateway.kt shared/src/commonTest/.../FakeMqttGateway.kt shared/src/commonTest/.../VisionGatewayContractTest.kt
git commit -m "feat(shared): mqtt gateway vision publish + response flow"
```

---

### Task 9: Image capture abstraction + Android CameraX impl

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/qsense/domain/service/ImageCapturer.kt`
- Create: `shared/src/androidMain/kotlin/com/example/qsense/data/camera/CameraXImageCapturer.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml` (CAMERA permission)
- Modify: `shared/build.gradle.kts` (CameraX deps in androidMain)

**Interfaces:**
- Produces: `interface ImageCapturer { suspend fun capture(): ByteArray }` returning a JPEG
  downscaled so the longest side ≤ 640px. `CameraXImageCapturer` implements it.

- [ ] **Step 1: Define `ImageCapturer` (commonMain)**

```kotlin
package com.example.qsense.domain.service

/** Captures a photo and returns it as a JPEG whose longest side is <= 640px. */
interface ImageCapturer {
    suspend fun capture(): ByteArray
}
```

- [ ] **Step 2: Add CameraX deps to `shared/build.gradle.kts` androidMain**

```kotlin
implementation("androidx.camera:camera-camera2:1.4.1")
implementation("androidx.camera:camera-lifecycle:1.4.1")
implementation("androidx.camera:camera-view:1.4.1")
```

- [ ] **Step 3: Add CAMERA permission to `AndroidManifest.xml`**

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.any" android:required="false" />
```

- [ ] **Step 4: Implement `CameraXImageCapturer`**

Bind `ImageCapture` to the lifecycle; on `capture()`, take a picture to an in-memory buffer,
decode to `Bitmap`, scale so `max(width,height) <= 640` preserving aspect, compress to JPEG q85,
return the bytes. (Camera preview wiring lives in the Compose screen, Task 11.)

- [ ] **Step 5: Build to confirm it compiles**

Run: `./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/.../ImageCapturer.kt shared/src/androidMain/.../CameraXImageCapturer.kt androidApp/src/main/AndroidManifest.xml shared/build.gradle.kts
git commit -m "feat: add image capture abstraction + CameraX impl"
```

---

### Task 10: VisionViewModel

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/qsense/presentation/feature/vision/VisionViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/qsense/di/AppContainer.kt` (expose capturer + gateway)
- Test: `shared/src/commonTest/kotlin/com/example/qsense/presentation/feature/vision/VisionViewModelTest.kt`

**Interfaces:**
- Consumes: `ImageCapturer` (Task 9), `MqttGateway.publishVisionRequest` + `visionResponses` (Task 8),
  `Clock`.
- Produces: `VisionViewModel(container)` with `val state: StateFlow<VisionUiState>` and
  `fun captureAndDiagnose(machineNo, partNo)`. `VisionUiState` = `Idle | Capturing | Waiting |
  Result(VisionResponse) | Error(String)`. Correlates the response by `requestId`; times out after 15s.

- [ ] **Step 1: Write the failing ViewModel test**

`VisionViewModelTest.kt`: with a `FakeImageCapturer` returning fixed bytes and `FakeMqttGateway`,
call `captureAndDiagnose("M1","blade")`, then emit a matching `VisionResponse` on the fake's
`visionResponses`, advance the test dispatcher, and assert `state.value` becomes
`VisionUiState.Result` with that response. (Use `StandardTestDispatcher` + `Dispatchers.setMain`,
following `DashboardViewModelTest`.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :shared:testAndroidHostTest --tests "*VisionViewModelTest*"`
Expected: FAIL — unresolved `VisionViewModel`

- [ ] **Step 3: Implement `VisionViewModel`**

`captureAndDiagnose`: set `Capturing` → `capturer.capture()` → build `VisionRequest` with a
`requestId` (UUID via an injected id source or `clock`-derived), set `Waiting`, launch a collector
on `visionResponses.first { it.requestId == id }` with a 15s `withTimeoutOrNull`, then set
`Result` or `Error("timeout")`. Also `publishVisionRequest(request)`.

- [ ] **Step 4: Expose capturer on `AppContainer`**

Add `val imageCapturer: ImageCapturer` to `AppContainer` and its Android factory (Task 9 impl).

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :shared:testAndroidHostTest --tests "*VisionViewModelTest*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/.../vision/VisionViewModel.kt shared/src/commonMain/.../di/AppContainer.kt shared/src/commonTest/.../VisionViewModelTest.kt
git commit -m "feat(shared): add VisionViewModel"
```

---

### Task 11: Vision screen (Compose) + navigation entry

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/qsense/presentation/feature/vision/VisionScreen.kt`
- Modify: dashboard nav/entry to open the vision screen (a camera FAB/button)

**Interfaces:**
- Consumes: `VisionViewModel` (Task 10), the app theme.
- Produces: a Compose screen: camera preview + capture button → progress while `Waiting` →
  shows the decoded `annotatedImageB64` (base64 → `ImageBitmap`) + `diagnosis`, styled in QSense theme.

- [ ] **Step 1: Implement `VisionScreen`**

Render per `VisionUiState`: `Idle/Capturing` → CameraX `PreviewView` (via `AndroidView`) + a
capture button calling `captureAndDiagnose`; `Waiting` → `CircularProgressIndicator`; `Result` →
decode base64 to bytes → `BitmapFactory.decodeByteArray` → `asImageBitmap()` in an `Image`, plus the
diagnosis text in an 18dp card; `Error` → message + retry. Follow existing dashboard styling.

- [ ] **Step 2: Add a camera entry point from the dashboard**

Add a camera button (QSense-themed) that navigates to `VisionScreen` in the existing Crossfade nav.

- [ ] **Step 3: Build the app**

Run: `./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual on-device check**

Install (`./gradlew :androidApp:installDebug`), run `py -m server.main` on the PC, open the vision
screen on the phone, capture → confirm the annotated image + "Detected: 1 blade." returns and shows.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/.../vision/VisionScreen.kt shared/src/commonMain/.../presentation/
git commit -m "feat(shared): add vision capture + result screen"
```

**✅ Milestone 2 done: the QSense phone is the real MQTT vision client.**

---

## Notes for later slices (not in this plan)

- **Slice B:** replace `StubDetector` with an RTMDet ONNX detector trained on your blade/disturbance
  images (conda Py 3.10 training env on the RTX 4050; export to ONNX; `onnxruntime` on the server).
- **Slice C:** feed detections into QSense's RAG/LLM diagnosis instead of the rule-based summary.
- Update the repo `CLAUDE.md` (vision feature + `server/`) at the end of Milestone 2.
