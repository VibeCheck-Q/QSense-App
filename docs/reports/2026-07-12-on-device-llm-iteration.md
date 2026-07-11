# On-device LLM iteration report — GenieX, and would tflite fix it?

_Date: 2026-07-12 · Scope: QSense v1 diagnosis generation_

## Goal

When a fault alert arrives, produce ~5 clear, grounded likely-causes + fixes **on device**
(no cloud), shaped as `{"causes":[{"cause":"…","fix":"…"}]}`, rendered on the dashboard.

## The pipeline as built

`GenerateDiagnosisUseCase` (commonMain):

1. **Retrieve** known failure modes for the alert's part from `InMemoryKnowledgeBase` (keyword RAG,
   no embeddings).
2. **Build** the prompt: a fixed master `SYSTEM` prompt (persona + a strict JSON output contract,
   with a worked example) + a per-alert user message grounded in the retrieved knowledge + sensor
   readings (`DiagnosisPrompt`).
3. **Generate** with the on-device model (`GenieXTextGenerator`, llama.cpp/GGUF runtime).
4. **Parse** tolerantly (`JsonDiagnosisParser`: strips markdown fences, isolates the `{…}` span,
   lenient decode, caps to 5).
5. **Fallback**: if generation throws or the text can't be parsed into ≥1 cause, return the
   retrieved RAG entries directly as the diagnosis, so the operator always gets grounded content.

## What we tried in GenieX, and how each failed

### 1. Force JSON with a structured GBNF grammar → hard generation failure
GenieX/llama.cpp accepts a GBNF grammar to constrain sampling. The natural move was a grammar that
forces the exact `{"causes":[{"cause":…,"fix":…}]}` shape. In this GenieX build a heavily
structured grammar **masks nearly every candidate token**, the sampler empties mid-stream, and
generation aborts (`ErrorCode -200101`) after the first few tokens — a "stream error", not JSON.
**Result:** structured grammar is unusable here.

### 2. Invalid-UTF-8 JNI abort → the ASCII-only grammar guarantee
GenieX builds Java strings from raw token bytes via JNI `NewStringUTF`, which **aborts the process**
on invalid multi-byte UTF-8 (a multi-byte token split across a stream chunk is enough). The fix that
stuck: a *deliberately unstructured* grammar that restricts output to single-byte printable ASCII
(`root ::= char+`, `char ::= [\t\n\r\x20-\x7E]`) — see `GenieXGrammars`. This is the crash
guarantee: ASCII-only bytes never trip the abort. Structure is therefore **not** enforced by the
grammar; it is elicited by the prompt and recovered by the tolerant parser.

### 3. The model itself is too small for reliable structured text
The side-loaded model is **Qwen3-0.6B**. Even with the master prompt's strict contract and a worked
example, a 0.6B model frequently **emits a stray token or two and stops**, or produces text that
doesn't parse into causes. It cannot reliably hold the "emit only this JSON object" instruction
across a full generation. **This is the core "model text issue."**

### 4. Hardware restriction
GenieX runs only on Snapdragon 8 Elite (`SM8750`) / 8 Elite Gen 5 (`SM8850`). On any other SoC the
SDK init fails (surfaced as `ModelStatus.Error`), so the LLM path is unavailable entirely.

## Root cause

Two independent problems, only one of which is about the runtime:

- **Capacity (model):** 0.6B is below the threshold for dependable structured-JSON emission. No
  prompt or parser tolerance fully compensates.
- **Runtime ergonomics (GenieX):** the UTF-8 JNI abort + the structured-grammar failure force the
  ASCII-only-grammar workaround, which removes the one mechanism that could have guaranteed shape.

Net effect today: the LLM path *usually* yields nothing parseable, and **the RAG fallback carries
the diagnosis** in practice. The app still works and stays grounded — but the on-device LLM is not
doing the work it was meant to.

## Would switching to tflite / LiteRT solve the text issue?

**Short answer: not on its own.** The text issue is primarily *model capacity*, and the runtime is
secondary. Switching runtime while keeping a ~0.6B model would give ~the same weak structured
output. Specifics:

- **tflite/LiteRT (MediaPipe LLM Inference) cannot load our GGUF.** It needs a `.task`/`.litertlm`
  model, and MediaPipe's converter officially supports only a short list (Gemma, Phi-2, Falcon,
  StableLM) — **Qwen3 is not on it**. So "run the same Qwen3-0.6B on tflite" is not a drop-in; it's
  a conversion that may be unsupported.
- **What tflite *would* genuinely improve** (if paired with a supported model): MediaPipe does
  tokenization/detokenization inside the runtime and returns proper strings, so the **UTF-8 JNI
  abort disappears** and the **ASCII-only-grammar workaround is no longer needed**. It also runs on
  non-Snapdragon devices (CPU/GPU).
- **But the reliability win comes from the model, not the label "tflite."** Moving to a
  **1B-class instruction-tuned model** (e.g. Gemma-3 1B-IT int4, ~550MB) is what actually makes
  `{"causes":[…]}` emission dependable. That is a *model upgrade* that happens to require a
  different runtime, not a runtime fix per se.

## Recommendation

Ranked by effort→payoff:

1. **Cheapest lever — bigger/better GGUF, keep GenieX:** side-load a 1B-class instruct GGUF instead
   of Qwen3-0.6B. Reuses the existing llama.cpp path unchanged; likely the single biggest quality
   jump for the least code. Still Snapdragon-only.
2. **Runtime + model change — MediaPipe LLM Inference + Gemma-3 1B:** true tflite path; drops the
   ASCII-grammar workaround, runs on any modern Android device, and a supported 1B instruct model
   emits JSON far more reliably. Larger change (new dependency, new adapter behind `TextGenerator`,
   new side-loaded model).
3. **Either way, keep the RAG fallback.** Even a 1B on-device model is not cloud-grade; the fallback
   is what guarantees the operator always sees grounded causes/fixes.

**Bottom line:** don't switch to tflite *expecting the runtime alone to fix the text*. Fix the
**model** (1B-class instruct). tflite is worth it if you also want to drop the Snapdragon
restriction and the UTF-8/grammar workarounds — in which case tflite + Gemma-3 1B is the coherent
package.
