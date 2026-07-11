# QSense — Project Guide

Kotlin Multiplatform + Compose app (Android target only). v1 operator flow: an MQTT fault
alert (machine no + part, plus optional temperature/humidity) arrives → relevant known failure
modes are retrieved from a small on-device knowledge base (RAG grounding) → an on-device
Qualcomm GenieX LLM generates ~5 clear likely causes + fixes, grounded in that knowledge → a
dashboard shows them → the operator picks the real cause/fix, adds notes, and taps **Resolve**,
which publishes an acknowledgement back over MQTT.

v1 is **text-only** (no camera/vision/"locate part" — those are v2). See
`docs/plans/2026-07-11-qsense-v1-plan.md`.

The UI follows the QSense Factory design system (v2 light/minimal): teal primary, navy logo, the
four pipeline-stage accents (coral/amber/slate/sage) on status dots + alert accent bars, 18dp
cards, and Clash Display + Satoshi fonts.

## Architecture (clean-arch KMP)

- `shared/commonMain` — pure Kotlin: domain models, service **interfaces**
  (`TextGenerator`, `MqttGateway`, `Clock`), `AlertStore`, `KnowledgeBase` + `InMemoryKnowledgeBase`
  (RAG grounding), use cases, prompt builder (`DiagnosisPrompt` — master `SYSTEM` prompt +
  knowledge/sensor-grounded user message), tolerant LLM parser, Compose dashboard +
  `DashboardViewModel`, theme (`AppTheme`, `QSenseColors`, `buildQSenseTypography`, sensor colors),
  the `QSenseLogo` (the round badge — navy disc + white ticks/meter box/ECG waveform + coral dot —
  drawn in Compose Canvas), and `AppContainer` (constructor-injected class).
- `shared/androidMain` — the Android-only adapters behind those interfaces:
  `GenieXTextGenerator` (GenieX SDK), `HiveMqttGateway` (HiveMQ), `SystemClock`, plus
  `createAndroidAppContainer(context, scope, config)`.
- `androidApp` — `QSenseApplication` builds the container once; `MainActivity` passes it +
  `qsenseTypography()` to `App(container, typography)`. Fonts live in `androidApp/res/font/`
  (Clash Display + Satoshi OTFs); `qsenseTypography()` builds the families and calls
  `buildQSenseTypography` in shared. androidApp declares `compose.material3` directly (it references
  `Typography`; it is not exposed transitively from `shared`). Adaptive launcher icon is the QSense
  round badge (navy disc via the mask + white ticks/meter box/ECG waveform + coral dot) in
  `res/drawable*/ic_launcher_*`; `minSdk 35` so only the `mipmap-anydpi-v26` adaptive XML renders
  (the legacy PNG mipmaps are unused).

DI: no global singletons. `AppContainer` is constructed from platform services and handed to
the ViewModel via `viewModel { DashboardViewModel(container) }`.

## Key integration facts

- **On-device runtime is GenieX only — no LiteRT/tflite.** The single on-device LLM path is
  GenieX (Qualcomm QAIRT/Genie, `llama.cpp`/GGUF); LiteRT/MediaPipe is intentionally not used
  (it can't load the GGUF model). Non-Snapdragon devices are covered by the RAG fallback, not a
  second runtime.
- **GenieX** (`com.qualcomm.qti:geniex-android:0.3.1`, resolves from mavenCentral). Package
  `com.geniex.sdk`. Runs only on Snapdragon 8 Elite (`SM8750`) / 8 Elite Gen 5 (`SM8850`).
  Ships arm64-v8a native libs (no NDK). All SDK use is confined to `GenieXTextGenerator`;
  failures surface as `ModelStatus.Error` so the rest of the app still runs.
  - Model is **side-loaded** (not in the APK) to
    `getExternalFilesDir(null)/models/qsense-llm` — see `docs/demo/README.md`.
  - Flow: `GenieXSdk.getInstance().init(ctx, cb)` → `registerPlugin(PLUGIN_ID_LLAMA_CPP)` →
    `ModelManagerWrapper.init/pullFlow(LOCALFS)/getPaths` → `LlmWrapper.builder()…build()` →
    `applyChatTemplate` (a `system` master prompt + the `user` message) → `generateStreamFlow`
    (accumulate `LlmStreamResult.Token`).
  - **Output constraint**: diagnosis generation always sends a GBNF grammar (`GenieXGrammars`) that
    restricts output to single-byte printable ASCII (`root ::= char+`, `char ::= [\t\n\r\x20-\x7E]`).
    This is the crash guarantee — ASCII-only bytes never trip the GenieX JNI `NewStringUTF` abort on
    invalid UTF-8. It deliberately does **not** force the JSON structure (a heavily structured
    grammar masks nearly every candidate token and empties the sampler mid-stream → `stream error`);
    structure comes from the `SYSTEM` prompt + the tolerant `JsonDiagnosisParser`.
- **RAG grounding + fallback**: `InMemoryKnowledgeBase` (commonMain, pure keyword lookup on part
  name — no embeddings) returns known symptom→cause→fix entries; `GenerateDiagnosisUseCase` injects
  them into the prompt. If the on-device model fails (GenieX stream error) or returns unparseable
  text, the use case **falls back to those retrieved entries** as the diagnosis, so the operator
  always gets grounded causes/fixes. NOTE: the side-loaded Qwen3-0.6B is too small to reliably emit
  structured JSON on device (often emits a stray token then stops), so in practice the RAG fallback
  usually carries the diagnosis — a larger instruct model would let the LLM path dominate.
- **Alerts**: keyed by `machineNo + partNo`. The monitoring feed re-sends the same machine/part with
  a fresh `alertId`, so `InMemoryAlertStore.add` refreshes that row in place (stable id + resolved
  flag) instead of duplicating. `seed(...)` adds demo fixtures flagged `seeded`; the first real
  `add` purges them so live data never mixes with demo data. Severity → criticality
  (`classifySeverity`: numeric ≥40 CRITICAL / ≥10 WARNING / else OK, or high/medium/low labels)
  drives the alert accent bar + badge.
- **MQTT** (`com.hivemq:hivemq-mqtt-client:1.3.15`). MQTT 5, QoS 1. Inbound alerts on
  `qsense/machine/monitoring`. The app also emits a minimal `ResolvedAck`
  (`{"alertId","resolved":<bool>}`) on `qsense/machine/ack`: `resolved:false` when an alert arrives
  (best-effort, in `IngestAlertsUseCase` — a failed ack never drops the alert) and `resolved:true`
  on resolve. On resolve it publishes **two** messages — the rich `Resolution` on
  `qsense/machine/resolutions` then the `true` ack — both PUBACK-bound and required, so the alert is
  marked locally resolved only after **both** succeed and a failure in either leaves it unresolved
  (`PublishResolutionUseCase`). Topics are distinct so the app never re-ingests its own acks; all
  configurable in `MqttConfig`. Clean start + automatic reconnect + explicit resubscribe on every
  connect. Requires the `INTERNET` permission + Netty packaging excludes in
  `androidApp/build.gradle.kts`.
- **Serialization**: kotlinx-serialization (runtime 1.11.0). `JsonProviders.strict` for MQTT
  payloads, `JsonProviders.lenient` for LLM output. UTF-8 both directions. `FaultAlert.severity`
  uses `FlexibleStringSerializer` (accepts a JSON number or string); `timestamp` is free-form
  (ISO instant or local date-time with microseconds), validated as non-blank only.

## Commands

- Build shared (Android): `./gradlew :shared:assembleAndroidMain`
- Build app: `./gradlew :androidApp:compileDebugKotlin` / `:androidApp:assembleDebug`
- Install on device: `./gradlew :androidApp:installDebug`
- Unit + integration tests (JVM host, no phone): `./gradlew :shared:testAndroidHostTest`
- Demo scripts + runbook: `docs/demo/`

## Testing notes

Tests live in `shared/src/commonTest` (run via the android host-test target). Fakes in
`testutil/` (`FakeTextGenerator`, `FakeMqttGateway`, `FixedClock`). The ViewModel/E2E tests use
`kotlinx-coroutines-test` with an injected `StandardTestDispatcher` + `Dispatchers.setMain`.
`EndToEndFlowTest` exercises the full wired flow with only the two platform services faked.
On-device GenieX inference and a real-broker check must be validated manually on the phone.
