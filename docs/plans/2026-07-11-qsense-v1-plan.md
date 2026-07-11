# QSense v1 - Implementation Plan (rev 3, Codex-approved)

## Goal

Operator-facing Android app slice: an MQTT fault alert (machine no + part) arrives -> a
local on-device LLM (Qualcomm GenieX, AI Hub bundle) generates 3-5 likely causes each with
a suggested fix -> a dashboard displays them -> the operator picks the actual cause + fix,
adds notes, and taps **Resolve** -> the app publishes an acknowledgement back over MQTT.
Ships with automated integration tests, a manual demo harness, and a demo runbook.

**Scope guard (v1):** text-only. No camera, no vision, no "locate part" - those are v2.
Every changed line traces to the flow above.

## v1 non-goals (explicit, to bound scope)

Deferred deliberately - not needed for the hackathon demo:
- No persistence across process death / rotation. Alerts are in-memory and transient; a
  restart starts clean. (Rotation is handled only by keeping state in the ViewModel.)
- No MQTT lifecycle management. The connection is opened at startup and lives for the process
  lifetime; there is no foreground-scoped connect/disconnect. Fine for the demo; proper
  lifecycle handling (disconnect when backgrounded) is v2.
- No model download-manager / checksum / license-gating flow. Model is side-loaded (below).
- No deep accessibility pass beyond content descriptions on the primary controls.
- No auth/TLS to the broker (insecure demo default; see MQTT security).

## Target hardware (pinned)

The team's Snapdragon phone is the single supported device. GenieX is a Qualcomm developer
preview that runs only on supported Snapdragon SoCs - not the emulator, not arbitrary phones.
The app must degrade gracefully (an `Unsupported`/`ModelError` state) rather than crash if
GenieX init fails, so the rest of the demo (MQTT + dashboard + resolve) still works.

## Model delivery decision (pinned)

- A single pinned Qualcomm AI Hub instruct LLM bundle (exact id/quantization to be filled in
  from the AI Hub model page during Stage 2; recorded in `CLAUDE.md` once chosen).
- Delivered by **side-load to a deterministic, app-derived path**:
  `context.getExternalFilesDir(null)/models/<bundle>` (resolved at runtime, surfaced in the UI
  and logs). Not bundled in the APK (bundles are GB-scale; APK/asset packaging is impractical
  for the demo). No download logic.
- On startup the app checks that path: present -> load (async, status surfaced); absent ->
  `ModelError("model not found")` state showing the exact expected path so the operator can
  `adb push` it (the runbook lists the precise command).
- Inference policy: one generation at a time; a new alert selection cancels the in-flight
  generation (stale-result guard); bounded `maxTokens`; fixed low temperature; a generation
  timeout that surfaces `ModelError` rather than hanging.

## Architecture - Approach A (clean-arch KMP)

Mirror the existing clean-arch layering, corrected for the platform boundary.

- `shared/commonMain` - domain models, service **interfaces**, use cases, prompt builder,
  Compose dashboard, ViewModel, and a plain `AppContainer` **class** (not the global object).
- `shared/androidMain` - the two Android-only adapters (GenieX, HiveMQ) behind those
  interfaces, plus an Android factory that builds the container from a `Context`.
- `androidApp` - builds the container once at the application layer and passes a ViewModel
  factory into `App()`.

DI correction (Codex finding #1): a `commonMain` object cannot take `android.content.Context`.
So:
- `AppContainer` becomes a normal class in `commonMain` whose constructor takes
  `TextGenerator`, `MqttGateway`, `AlertStore`, `Clock`, and a `CoroutineDispatcher`; it
  builds the use cases.
- `androidMain` provides `fun createAndroidAppContainer(context, config): AppContainer` that
  constructs `GenieXTextGenerator` + `HiveMqttGateway` and returns the common container.
- The container is created once in a custom `Application` (or `MainActivity`, guarded to be
  idempotent) and exposed to Compose via a `ViewModelProvider.Factory` / `viewModel { }`
  factory lambda - **not** via singleton default constructor args (removes the hidden-global
  pattern in `HomeViewModel`).

All GenieX 0.3.1 API surface is confined to `GenieXTextGenerator`; nothing else imports the SDK.

## MQTT contract (proposed) + semantics

Broker: Mosquitto. Host/port configurable via a `MqttConfig`. Demo default is an explicitly
**insecure** public broker; do not send real factory data through it.

Topic namespacing (Codex finding #3 - fixed public-broker topics collide): all topics are
prefixed with a per-run namespace, `qsense/{namespace}/...`, where `namespace` is a config
value (e.g. a short team token). Default documented in the demo runbook.
- Inbound alerts: `qsense/{ns}/alerts`
- Outbound resolutions: `qsense/{ns}/resolutions`

MQTT semantics (pinned):
- MQTT 5 (HiveMQ default), **QoS 1** for both subscribe and publish.
- Unique client id per launch (`qsense-<random>`); **clean start** session. HiveMQ's automatic
  reconnect is enabled for transport recovery, but because a clean-start session discards
  subscriptions, the gateway registers a `connected` listener that **explicitly re-issues the
  subscribe** on every successful connect/reconnect (not relying on any "built-in" resubscribe).
- Payloads are encoded/decoded as **UTF-8** on both directions.
- Alerts are **not** treated as retained; duplicate alerts are de-duplicated by `alertId` in
  `AlertStore`.
- Publish path is suspending with a timeout; the alert is marked resolved **only after** the
  broker returns PUBACK. On failure the UI shows a retryable `publish-error`.
- Clean disconnect on container shutdown; all flows are cancellation-safe.

Inbound `FaultAlert` JSON (strict decode; required ids; bounded strings; `ignoreUnknownKeys`):
```json
{
  "alertId": "a1b2c3",
  "machineNo": "M-101",
  "partName": "Hydraulic Pump",
  "partNo": "HP-2045",
  "severity": "high",
  "timestamp": "2026-07-11T10:30:00Z"
}
```

Outbound `Resolution` JSON:
```json
{
  "alertId": "a1b2c3",
  "machineNo": "M-101",
  "partNo": "HP-2045",
  "chosenCause": "Seal degradation causing pressure loss",
  "appliedFix": "Replaced hydraulic seal kit",
  "notes": "Confirmed leak at inlet flange",
  "resolvedAt": "2026-07-11T10:45:00Z"
}
```
`timestamp`/`resolvedAt` are validated ISO-8601 strings (no `kotlinx-datetime` dependency for
v1; `resolvedAt` comes from the injected `Clock` for deterministic tests).

## Files

**commonMain (new)**
- `domain/model/FaultAlert.kt` - inbound alert (`@Serializable`)
- `domain/model/PossibleCause.kt` - `cause: String, fix: String`
- `domain/model/Diagnosis.kt` - `alertId`, `List<PossibleCause>` only (no status; per finding #2)
- `domain/model/Resolution.kt` - outbound payload (`@Serializable`)
- `domain/service/TextGenerator.kt` - `interface`: `suspend fun generate(prompt, params): String`;
  `val status: StateFlow<ModelStatus>` (Loading/Ready/Error) - a service (not domain-model) flow
- `domain/service/MqttGateway.kt` - `interface`: `val alerts: Flow<FaultAlert>`,
  `val connectionState: StateFlow<ConnectionState>`, `suspend fun publishResolution(Resolution)`,
  `connect()/disconnect()`
- `domain/service/Clock.kt` - `interface { fun nowIso(): String }` (injected; deterministic in tests)
- `domain/repository/AlertStore.kt` - owns the transient alert list + resolved status
  (`StateFlow<List<AlertItem>>`, `add`, `markResolved`, dedupe by `alertId`)
- `domain/prompt/DiagnosisPrompt.kt` - builds system + user prompt; pins the JSON output schema;
  sanitizes/bounds MQTT-provided machine/part text (prompt-injection boundary)
- `domain/parse/DiagnosisParser.kt` - tolerant LLM-output -> `List<PossibleCause>` behind a
  small interface (separate from the strict MQTT serializer; handles fenced/partial output)
- `domain/usecase/GenerateDiagnosisUseCase.kt` - prompt -> `generate` -> parse -> `Diagnosis`
- `domain/usecase/ObserveAlertsUseCase.kt`
- `domain/usecase/PublishResolutionUseCase.kt`
- `di/AppContainer.kt` - **class** (constructor-injected); builds use cases
- `presentation/feature/dashboard/DashboardUiState.kt` - holds loading/error/selection/empty/
  disconnected/model-error/publish-error UI states
- `presentation/feature/dashboard/DashboardViewModel.kt` - injected use cases + dispatcher
- `presentation/feature/dashboard/DashboardScreen.kt`

**commonMain (edit / remove)**
- Remove Greeting: `HomeScreen/HomeViewModel/HomeUiState`, `GetGreetingUseCase`,
  `GreetingRepository(+Impl)`, and their tests; delete the old `AppContainer` object.
- `presentation/App.kt` + `navigation/Routes.kt` - route to Dashboard; App takes a VM factory.

**androidMain (new)**
- `data/llm/GenieXTextGenerator.kt` - implements `TextGenerator` via `geniex-android:0.3.1`
- `data/mqtt/HiveMqttGateway.kt` - implements `MqttGateway` via HiveMQ; bridges the async
  Java client into suspend/`callbackFlow`; async API only (no network-on-main)
- `di/AndroidAppContainer.kt` - `createAndroidAppContainer(context, config): AppContainer`

**androidApp (edit)**
- `MainActivity.kt` (or a new `QSenseApplication`) - build the container once (idempotent),
  provide the VM factory to `App()`
- `AndroidManifest.xml` - add `<uses-permission android:name="android.permission.INTERNET"/>`
- `androidApp/build.gradle.kts` - Netty packaging excludes / keep rules per HiveMQ Android docs

## Dependencies to add (version catalog)
- `kotlin-plugin-serialization` (id `org.jetbrains.kotlin.plugin.serialization`, version.ref
  `kotlin` = 2.4.0) - alias in root `build.gradle.kts` with `apply false`, applied in
  `shared/build.gradle.kts`
- `kotlinx-serialization-json` **1.11.0** runtime (commonMain) - runtime version is independent
  of the Kotlin/compiler-plugin version
- `kotlinx-coroutines-core` (commonMain); coroutines are already transitively present via
  lifecycle, but pin explicitly
- `kotlinx-coroutines-test` (commonTest)
- `com.qualcomm.qti:geniex-android:0.3.1` (androidMain)
- `com.hivemq:hivemq-mqtt-client:1.3.15` (androidMain, latest on Maven Central) - Java async
  client; preferred over the stale Paho Android Service for a new app

## Runtime flow
1. App start -> build container (idempotent) -> MQTT connect + subscribe `qsense/{ns}/alerts`;
   kick off async model load (status surfaced in UI).
2. `FaultAlert` arrives -> `AlertStore.add` (dedupe by `alertId`) -> dashboard list updates.
3. Operator taps an alert -> `GenerateDiagnosisUseCase` (cancels any in-flight generation) ->
   GenieX generates -> parser -> 3-5 causes+fixes shown; loading/error states handled.
4. Operator selects the real cause + fix, types notes -> **Resolve** (disabled until a cause is
   chosen and a generation exists) -> `PublishResolutionUseCase` publishes to
   `qsense/{ns}/resolutions`; on ack -> `AlertStore.markResolved`; on failure -> retryable error.

## Testing

**Unit / component (commonTest, run via the configured `withHostTest` JVM host tests):**
- Test Main dispatcher installed (`kotlinx-coroutines-test`); dispatcher injected into VM.
- `FakeTextGenerator`, `FakeMqttGateway`, `FakeAlertStore`, `FixedClock` - controllable hot
  flows, publish success/failure, connection transitions, model failure, generation delay/cancel.
- `DiagnosisParserTest` - valid JSON, fenced ```json blocks, trailing prose, malformed -> fallback.
- MQTT payload **round-trip** contract tests for `FaultAlert` and `Resolution` (encode+decode).
- `GenerateDiagnosisUseCaseTest`, `PublishResolutionUseCaseTest`.
- `DashboardViewModelTest` - alert-in -> generating -> ready -> resolve -> published; plus
  repeated alerts, stale generation on re-selection, disconnect/reconnect, publish failure/retry,
  malformed alert, VM clear/cancellation.

**Automated integration test (the "E2E" deliverable, JVM):** wires the *real* use cases +
`AppContainer` + parser + serializer + `AlertStore` + ViewModel together, driving a fake MQTT
gateway end to end (alert in -> diagnosis -> resolve -> resolution captured). This is the true
automated E2E; the shell scripts below are a separate *manual* harness.

**On-device (documented, run by Shaan on the phone; not CI):** a GenieX smoke test and a
HiveMQ-against-real-Mosquitto check - fakes can't validate SDK packaging, native libs, or the
broker.

## Manual demo harness + runbook
- `docs/demo/` with **both** PowerShell and bash variants (repo is on Windows):
  - `publish-alert.ps1` / `.sh` - `mosquitto_pub` a sample `FaultAlert` to `qsense/{ns}/alerts`.
  - `watch-resolutions.ps1` / `.sh` - `mosquitto_sub` on `qsense/{ns}/resolutions`.
- Runbook, in order:
  1. Start/point to the Mosquitto broker; note host/port + namespace.
  2. Push the model to the exact device path, e.g.
     `adb push <bundle> /sdcard/Android/data/com.example.qsense/files/models/<bundle>`
     (this maps to `getExternalFilesDir(null)/models/<bundle>`).
  3. Run the app on the phone (model loads; MQTT connects/subscribes).
  4. Publish a sample alert; show generation; resolve; observe the resolution on the sub terminal.

## Risks / assumptions
- **GenieX 0.3.1 API** not memorized - confirm exact init/load-bundle/generate calls against the
  official docs in Stage 2; contained behind `TextGenerator`; graceful `ModelError` fallback.
- **HiveMQ async->coroutine bridge** must avoid blocking calls; verify Netty packaging excludes
  and keep rules from the HiveMQ Android setup docs.
- **Model side-load** path/permissions on the device; app shows a clear missing-model state.
- **LLM JSON reliability** - tolerant parser + one reprompt/fallback if unparseable.
- **Public broker** - namespaced topics + insecure-demo-only warning; no real data.
