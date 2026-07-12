# QSense

**On-device predictive maintenance.** A fault alert arrives over MQTT, relevant failure modes are
retrieved from an on-device knowledge base, an on-device LLM turns them into ranked causes & fixes,
the operator picks one and resolves it — all on the phone, no cloud round-trip. **v2** adds
camera-based part inspection: the phone sends a photo over MQTT to a PC service that runs object
detection and returns an annotated image + diagnosis.

Kotlin Multiplatform + Compose (Android target) · Qualcomm GenieX on-device LLM · HiveMQ MQTT 5 ·
RAG grounding + fallback · CameraX + a Python vision service.

---

## What it does — the flow

### Diagnosis flow (v1)

```
Detect            Retrieve           Diagnose            Review              Resolve
MQTT alert   →    RAG lookup    →    GenieX LLM     →    Dashboard      →    Ack over MQTT
machine+part      known failure      ~5 ranked           operator picks      resolution + ack
temp/humidity     modes for part     causes + fixes      the real cause      published
```

1. **Detect** — a `FaultAlert` (machine no + part, optional temperature/humidity) arrives on
   `qsense/machine/monitoring`.
2. **Retrieve** — `InMemoryKnowledgeBase` looks up known failure modes for that part (deterministic
   keyword match — no embeddings).
3. **Diagnose** — the retrieved issues + sensor context are built into a grounded prompt; the
   on-device GenieX LLM ranks and rewrites them into ~5 causes + fixes. If the model fails or emits
   unparseable text, the retrieved knowledge **is** the diagnosis (RAG fallback).
4. **Review** — the dashboard shows the causes/fixes; the operator selects the real one and adds
   notes.
5. **Resolve** — publishes a `Resolution` (+ a minimal ack) over MQTT; the alert is marked resolved
   only after both publishes are acknowledged.

Everything left of the broker runs **on the phone**.

### Vision flow (v2)

```
Phone: capture        MQTT               PC service          MQTT               Phone: show
CameraX → 640px   →   vision/request →   detect (RTMDet) →   vision/response →  annotated image
JPEG → base64         QoS 1              draw boxes          + diagnosis        + diagnosis
```

Detection runs on the **PC** (plain PyTorch/ONNX), not on-device — so there's no quantization/AI-Hub
track. The 640px downscale keeps the image small enough (~100 KB) to ride ordinary MQTT.

---

## Architecture — clean-architecture KMP

The domain is pure Kotlin behind interfaces; platform SDKs are adapters. The domain never imports an
Android SDK, so use cases and ViewModels are unit-testable on the JVM with fakes, and each vendor SDK
can be swapped without touching logic.

| Layer | Responsibility |
|---|---|
| `shared/commonMain` _(pure Kotlin)_ | Domain models; service **interfaces** (`TextGenerator`, `MqttGateway`, `Clock`); use cases; RAG (`KnowledgeBase` + `InMemoryKnowledgeBase`); prompt builder + tolerant `JsonDiagnosisParser`; Compose UI + ViewModels + theme |
| `shared/androidMain` _(SDK adapters)_ | `GenieXTextGenerator` (GenieX), `HiveMqttGateway` (HiveMQ), `SystemClock`, CameraX capture + Compose `expect/actual` for camera & image |
| `androidApp` _(thin shell)_ | Builds the `AppContainer` once (manual DI), hosts the activity, loads fonts, launcher icon |
| `server/` _(Python)_ | v2 vision service: `paho-mqtt` + `Pillow` (→ RTMDet/ONNX); pure `handler()` unit-testable without a broker |

**DI:** no framework — `AppContainer` is a constructor-injected class built from platform services
and handed to ViewModels via `viewModel { … }`.

---

## Tech stack & SDKs — and why each

| Component | Version | Role & why we use it |
|---|---|---|
| **Kotlin Multiplatform** | — | Splits pure logic from platform code → JVM-testable, iOS-ready. Android is the only shipping target today. |
| **Compose Multiplatform** | — | Declarative UI beside the shared code; one design system everywhere. |
| **Qualcomm GenieX** | 0.3.1 | **On-device LLM** via llama.cpp/GGUF (QAIRT/Genie). The only runtime that loads our GGUF model; runs on Snapdragon 8 Elite. |
| **HiveMQ MQTT client** | 1.3.15 | MQTT 5 transport, QoS 1. Async PUBACK futures, automatic reconnect, and a WebSocket transport fallback for networks that block/reset raw 1883. |
| **kotlinx-serialization** | 1.11.0 | JSON for MQTT + LLM payloads. Multiplatform, compile-time, no reflection. `strict` for MQTT, `lenient` for model output. |
| **kotlinx-coroutines** | — | Flows model the MQTT/LLM event streams; deterministic ViewModel tests via an injected test dispatcher. |
| **CameraX** | 1.4.1 | v2 vision capture. Lifecycle-aware; `imageProxy.toBitmap()` → downscale to 640px. |
| **paho-mqtt · Pillow** | 2.1 · 11.0 | PC vision service — reference Python MQTT client + lightweight image annotation. |

### On-device LLM + RAG (why it's shaped this way)

- The LLM is asked to **select-and-adapt** over retrieved failure modes, not invent freely — a small
  model handles that far more reliably.
- Output is constrained by an **ASCII-only GBNF grammar** (the crash guarantee: GenieX aborts on
  invalid UTF-8); JSON structure comes from the prompt + a tolerant parser, not the grammar.
- The side-loaded model (Qwen3-0.6B) is too small for dependable structured JSON, so in practice the
  **RAG fallback usually carries the diagnosis** — the app stays grounded regardless. A 1B-class
  instruct model would let the LLM path dominate. See
  `docs/reports/2026-07-12-on-device-llm-iteration.md`.

---

## Build & run

```bash
./gradlew :androidApp:assembleDebug     # build APK
./gradlew :androidApp:installDebug      # install on a connected device
./gradlew :shared:testAndroidHostTest   # unit + integration tests (JVM host, no phone)
```

PC vision service (from repo root):

```bash
py -m pip install -r server/requirements.txt
py -m server.main                       # the service
py -m server.tools.roundtrip            # MQTT round-trip proof
```

Full setup (LLM side-load, network notes, on-device vision) → **[`docs/SETUP.md`](docs/SETUP.md)**.

---

## Docs

- **[`docs/SETUP.md`](docs/SETUP.md)** — build, install, model side-load, run the vision service.
- **[`docs/MQTT-CONTRACT.md`](docs/MQTT-CONTRACT.md)** — every topic + payload field (integration spec).
- `docs/reports/2026-07-12-on-device-llm-iteration.md` — on-device LLM findings & why a small model.
- `docs/superpowers/` — design specs + implementation plan for the vision pipeline.
- `server/README.md` — PC vision service details.

## Repo layout

```
shared/commonMain   pure Kotlin: models, interfaces, use cases, RAG, prompt, Compose UI, ViewModels
shared/androidMain   adapters: GenieXTextGenerator, HiveMqttGateway, SystemClock, CameraX
androidApp           app shell: builds AppContainer, MainActivity, fonts, launcher icon
server/              Python vision service (paho-mqtt + Pillow, stub → RTMDet)
docs/                setup, MQTT contract, reports, specs/plans
```

## Testing

Tests live in `shared/src/commonTest` (run via the Android host-test target) with fakes in
`testutil/` (`FakeTextGenerator`, `FakeMqttGateway`, `FixedClock`). `EndToEndFlowTest` exercises the
full wired flow with only the two platform services faked. On-device GenieX inference and a
real-broker check are validated manually on the phone.
