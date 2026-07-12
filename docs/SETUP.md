# QSense — Setup & Run

QSense is a Kotlin Multiplatform (Android) app plus a Python PC service (v2 vision). This guide
covers building the app, side-loading the on-device LLM, and running the vision pipeline.

---

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 17+ | For Gradle / Android build |
| Android SDK | `compileSdk` per `libs.versions.toml`, `minSdk 35` | Set `sdk.dir` in `local.properties` |
| Android Studio | latest | Optional but recommended |
| Python | 3.12 | For the PC vision service |
| A device | Snapdragon 8 Elite / 8 Elite Gen 5 for the **LLM**; any Android 15+ for the rest | GenieX runs only on those SoCs |

> **Network note:** some networks silently block raw MQTT on port 1883 (TCP connects but no CONNACK).
> If MQTT shows *disconnected*, switch to the broker's WebSocket listener — set
> `useWebSocket = true`, `port = 8080`, `webSocketPath = "mqtt"` in `MqttConfig`.

---

## 2. Build & install the Android app

```bash
# from the repo root
./gradlew :androidApp:assembleDebug        # build the APK
./gradlew :androidApp:installDebug         # build + install on a connected device
```

Other useful targets:

```bash
./gradlew :shared:compileDebugKotlinAndroid   # fast compile check of shared code
./gradlew :shared:testAndroidHostTest         # unit + integration tests (JVM host, no phone)
```

The app runs without the LLM or a broker — the dashboard simply waits for alerts, and the LLM
surfaces `ModelStatus.Error` if the model/SoC is unavailable.

---

## 3. Side-load the on-device LLM (optional, for GenieX diagnosis)

GenieX loads a GGUF model from the device's external files dir. Push a model bundle to:

```
<app external files>/models/qsense-llm
```

```bash
adb push <your-model-folder>/. \
  /sdcard/Android/data/com.example.qsense/files/models/qsense-llm/
```

Then relaunch the app. Watch it load:

```bash
adb logcat -s QSenseGenieX
# → "load: model READY" on success
```

If the SoC is unsupported or the model is missing, the app falls back to RAG-grounded diagnosis and
keeps working. See `docs/demo/README.md` for the exact model bundle used in the demo.

---

## 4. Run the PC vision service (v2)

The service receives a captured image over MQTT, runs detection, and returns an annotated image +
diagnosis. Run everything **from the repo root** so `server.*` imports resolve.

```bash
py -m pip install -r server/requirements.txt   # paho-mqtt, Pillow, pytest

py -m server.main                              # terminal 1: the service
py -m server.tools.roundtrip                   # terminal 2: publishes a test image, saves annotated_out.jpg
py -m pytest server/tests                       # run the service tests
```

`roundtrip` prints `diagnosis: Detected: 1 blade.` and writes `server/data/annotated_out.jpg`.

- Broker/topics are in `server/config.py` (defaults match the app's `MqttConfig`).
- Slice A ships a **stub detector** (fixed box) — its job is to prove the MQTT round-trip. A trained
  **RTMDet** (blade/disturbance, exported to ONNX) drops in behind the same `Detector` interface for
  Slice B.

> ⚠️ `test.mosquitto.org` is a **public** broker and `qsense/vision/*` are world-readable topics.
> Fine for a demo; use a private broker before sending real images.

---

## 5. Use the vision feature on the phone

1. Start the PC service: `py -m server.main`.
2. Ensure phone + PC can both reach the broker (WebSocket transport if 1883 is blocked — see the
   network note above).
3. In the app, tap **Scan part** on the dashboard → capture → the annotated image + diagnosis returns.

---

## MQTT topics

| Topic | Direction | Payload |
|---|---|---|
| `qsense/machine/monitoring` | broker → app | `FaultAlert` |
| `qsense/machine/resolutions` | app → broker | `Resolution` |
| `qsense/machine/ack` | app → broker | `{"alertId","resolved":0\|1}` |
| `qsense/vision/request` | app → PC | `{requestId, machineNo, partNo, imageB64, timestamp}` |
| `qsense/vision/response` | PC → app | `{requestId, annotatedImageB64, detections[], diagnosis}` |

All MQTT 5, QoS 1. Payloads are kotlinx-serialization JSON, UTF-8.

---

## Where things live

```
shared/commonMain   pure Kotlin: models, interfaces, use cases, RAG, prompt, Compose UI, ViewModels
shared/androidMain   adapters: GenieXTextGenerator, HiveMqttGateway, SystemClock, CameraX
androidApp           app shell: builds AppContainer, MainActivity, fonts, launcher icon
server/              Python vision service (paho-mqtt + Pillow, stub → RTMDet)
docs/                specs, plans, reports, this guide, the hackathon presentation
```
