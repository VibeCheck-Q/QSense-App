# QSense — QAIRT / NPU backend (dual-path) plan

**Date:** 2026-07-12
**Decisions (locked by user):** (1) same model — Qwen3-0.6B; (2) **dual-path** — QAIRT/NPU
on Snapdragon 8 Elite (SM8750) / 8 Elite Gen 5 (SM8850), llama.cpp/GGUF everywhere else;
(3) **side-loading** (`HubSource.LOCALFS`). On-device phone testing is done **last, manually**.

## Goal

Add the Qualcomm **QAIRT (QNN / Hexagon NPU)** runtime as a first-class backend behind the
existing `TextGenerator` interface, selected automatically at load time, with the current
llama.cpp/GGUF path as the fallback rung and RAG as the final rung. No change to the domain,
use cases, ViewModel, MQTT, or UI.

## Phase 0 findings (verified against `geniex-android-0.3.1` — DONE)

The NPU path is a supported, self-contained swap in the SDK we already ship:

- `GenieXSdk.PLUGIN_ID_QAIRT` exists alongside `PLUGIN_ID_LLAMA_CPP`.
- `RuntimeIdValue.QAIRT`, `ComputeUnitValue.HYBRID` exist.
- AAR bundles **all** native libs: `libgeniex_plugin_qairt.so`, `libQnnHtp*.so`,
  `libQnnSystem.so`, and the Hexagon skel libs **`libQnnHtpV79Skel.so` (SM8750)** +
  **`libQnnHtpV81Skel.so` (SM8850)** — the exact two supported chips. **No separate QNN SDK
  download, no manual skel-lib push.**
- `androidApp/build.gradle.kts` packaging already forces `.so` extraction and its comment
  explicitly names the qairt plugin → **no build/native/Gradle changes required.**
- `LlmCreateInput(model_name, model_path, tokenizer_path, config, runtime_id, compute_unit)`
  is identical for both backends — only `runtime_id` + `compute_unit` differ.
- `SamplerConfig.grammarString` is a plain field (won't break compile on QAIRT); whether the
  QAIRT sampler *honors* GBNF is the one thing to confirm on-device (Phase 5).
- `ModelPullInput` already carries `precision` + `chipset` (currently null) for the QAIRT pull.

## What changes (files)

All GenieX-specific; the SDK stays confined to `androidMain/data/llm/`.

1. **`di/AndroidConfig.kt` — `GenieXConfig`**: add
   - `qnnModelName: String = "qsense-llm-qnn"` (QNN bundle folder; GGUF stays `modelName = "qsense-llm"`),
   - `backend: Backend = Backend.AUTO` (AUTO | QAIRT | LLAMA_CPP — an escape hatch for the demo),
   - `qnnChipsets: Set<String> = setOf("SM8750", "SM8850")` (SoCs eligible for QAIRT).
2. **New `data/llm/GenieXBackend.kt`**: `enum class Backend { AUTO, QAIRT, LLAMA_CPP }` +
   a **pure** selector `fun selectBackend(requested, socModel, qnnBundleExists, ggufBundleExists,
   supportedChipsets): Backend?` (returns null → no LLM path → RAG). Pure so it is unit-testable
   off-device.
3. **`data/llm/GenieXTextGenerator.kt`**: use the selector in `load()`; branch plugin id,
   pull folder (+ chipset/precision for QAIRT), and `LlmCreateInput` runtime/compute-unit on the
   chosen backend; store `activeBackend`; in `generate()` attach `grammarString` **only** for
   `LLAMA_CPP` (QAIRT relies on prompt + `JsonDiagnosisParser` + RAG fallback). Update the
   class KDoc (no longer "llama.cpp only").
4. **`docs/demo/README.md`**: document side-loading the QNN bundle to
   `getExternalFilesDir(null)/models/qsense-llm-qnn` (adb push), alongside the existing GGUF.
5. **`CLAUDE.md`**: update the GenieX section to describe the dual backend + selection ladder.

## Backend selection ladder (in `load()`)

1. Read SoC via `Build.SOC_MODEL` (minSdk 35 → always available; `Build.SOC_MANUFACTURER`
   should be `QTI`). **Match case-insensitively / by substring** against `qnnChipsets` — the
   reported string casing/format is not guaranteed, so exact `==` is too brittle.
2. `selectBackend(config.backend, soc, qnnDirExists, ggufDirExists, config.qnnChipsets)`:
   - `AUTO` → QAIRT if `soc` matches supported **and** QNN bundle present; else LLAMA_CPP if
     GGUF present; else `null`.
   - `QAIRT`/`LLAMA_CPP` (forced) → that backend if its bundle exists; else `null`.
3. `null` → `ModelStatus.Error(...)` → app falls back to RAG (unchanged behavior).
4. Otherwise register the matching plugin and load. **If AUTO chose QAIRT and the QAIRT
   build/init throws at runtime, retry once with LLAMA_CPP** (if the GGUF bundle exists) before
   surfacing `ModelStatus.Error`. Any remaining failure → `ModelStatus.Error` → RAG (the dual
   path never hard-fails the app).

## Per-backend `LlmCreateInput`

| Field | LLAMA_CPP (today) | QAIRT (new) |
|---|---|---|
| plugin | `PLUGIN_ID_LLAMA_CPP` | `PLUGIN_ID_QAIRT` |
| pull folder | `modelName` (GGUF) | `qnnModelName` (QNN context bundle) |
| pull `chipset`/`precision` | null | **try null first** (a LOCALFS bundle is self-describing); only set if the pull fails |
| `runtime_id` | `RuntimeIdValue.LLAMA_CPP.value` | `RuntimeIdValue.QAIRT.value` |
| `compute_unit` | null (CPU) | `ComputeUnitValue.HYBRID.value` (NPU+CPU) |
| grammar in `generate()` | GBNF ASCII guard | **omit** (prompt + parser + RAG) |

## Stage 3 — Testing

- **Unit (JVM host, `commonTest`/androidHostTest):** test the **pure `selectBackend`** across the
  matrix (supported/unsupported SoC × QNN present/absent × GGUF present/absent × forced modes).
  This is the only new logic that is testable off-device.
- GenieX SDK init/inference and grammar behavior on QAIRT are **manual, on-device, last** (per
  the decision and the repo's existing testing note). No host test can exercise the native path.

## Risks & assumptions

- **The QNN model asset is a manual, off-device prerequisite (Action Required).** Qwen3-0.6B must
  be compiled to a QNN context bundle for Hexagon v79/v81 via Qualcomm AI Hub. **The bundle's
  internal layout matters**: the side-loaded folder must contain the files `ModelManager.pullFlow`
  (LOCALFS) + `getPaths` expect — the QNN context binary (`model_path`), a `tokenizer`
  (`tokenizer_path`), and any `genie-config` the QAIRT plugin needs. Confirming that exact layout
  is a Phase 1 deliverable. If the bundle is absent, AUTO simply uses llama.cpp everywhere —
  **no regression**, the app still runs.
- **QNN models have a compiled fixed sequence length.** `ModelConfig.nCtx = 4096` may be ignored
  or clamped on the QAIRT path (it is a llama.cpp-oriented knob); harmless, but don't rely on it.
- **GBNF on QAIRT unverified.** We omit grammar on QAIRT by design; if a UTF-8/detokenizer crash
  appears on-device, `grammarPath`/`grammarString` or a stricter prompt is the follow-up. Parser +
  RAG fallback already guard output either way.
- **Qwen3-0.6B is still small.** NPU makes it faster, not smarter; RAG may still carry many
  diagnoses. Upsizing the model is a separate future decision (explicitly out of scope here).
- **No Gradle/native/UI/domain changes** — scope is the four files above + two docs.
