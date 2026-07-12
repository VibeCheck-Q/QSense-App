package com.example.qsense.data.llm

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.qsense.di.GenieXConfig
import com.example.qsense.domain.service.GenerationParams
import com.example.qsense.domain.service.ModelStatus
import com.example.qsense.domain.service.TextGenerator
import com.geniex.sdk.GenieXSdk
import com.geniex.sdk.LlmWrapper
import com.geniex.sdk.ModelManagerWrapper
import com.geniex.sdk.bean.ChatMessage
import com.geniex.sdk.bean.ComputeUnitValue
import com.geniex.sdk.bean.GenerationConfig
import com.geniex.sdk.bean.HubSource
import com.geniex.sdk.bean.LlmCreateInput
import com.geniex.sdk.bean.LlmStreamResult
import com.geniex.sdk.bean.ModelConfig
import com.geniex.sdk.bean.ModelPullInput
import com.geniex.sdk.bean.RuntimeIdValue
import com.geniex.sdk.bean.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [TextGenerator] backed by Qualcomm GenieX. This is the only file that imports the GenieX SDK.
 * It drives one of two runtimes, chosen at load time by [selectBackend]:
 *
 * - [Backend.QAIRT] — the Hexagon NPU (QNN) path, on Snapdragon 8 Elite / 8 Elite Gen 5.
 * - [Backend.LLAMA_CPP] — the GGUF-on-CPU path, on any arm64 device.
 *
 * Both bundles are side-loaded to getExternalFilesDir(null)/models/<folder> (see the demo runbook
 * for the adb push). On a supported chip with a QNN bundle present, AUTO uses QAIRT and falls back
 * to llama.cpp if it fails; otherwise it uses llama.cpp. Any load failure is surfaced as
 * ModelStatus.Error so the rest of the app (MQTT + dashboard) keeps working (RAG carries the
 * diagnosis).
 */
class GenieXTextGenerator(
    private val context: Context,
    private val config: GenieXConfig,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TextGenerator {

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Loading)
    override val status: StateFlow<ModelStatus> = _status.asStateFlow()

    @Volatile
    private var llm: LlmWrapper? = null

    // The runtime actually loaded; drives the grammar decision in generate(). Set by loadBackend().
    @Volatile
    private var activeBackend: Backend = Backend.LLAMA_CPP

    /** Initializes the SDK and loads the side-loaded model. Call once at startup. */
    suspend fun load() = withContext(io) {
        try {
            val ggufDir = File(context.getExternalFilesDir(null), "models/${config.modelName}")
            val qnnDir = File(context.getExternalFilesDir(null), "models/${config.qnnModelName}")
            val soc = runCatching { Build.SOC_MODEL }.getOrNull()
            Log.i(TAG, "load: soc=$soc pref=${config.backend} gguf=${ggufDir.exists()} qnn=${qnnDir.exists()}")

            val chosen = selectBackend(
                requested = config.backend,
                socModel = soc,
                qnnBundleExists = qnnDir.exists(),
                ggufBundleExists = ggufDir.exists(),
                supportedChipsets = config.qnnChipsets,
            )
            if (chosen == null) {
                _status.value =
                    ModelStatus.Error("No usable model bundle for backend ${config.backend} (soc=$soc)")
                return@withContext
            }

            val sdk = GenieXSdk.getInstance()
            Log.i(TAG, "load: initializing GenieX SDK…")
            awaitInit(sdk)
            Log.i(TAG, "load: SDK init OK; selected backend=$chosen")

            try {
                loadBackend(sdk, chosen)
            } catch (t: Throwable) {
                // AUTO reached QAIRT but it failed at runtime; retry the CPU path if the GGUF exists.
                if (config.backend == Backend.AUTO && chosen == Backend.QAIRT && ggufDir.exists()) {
                    Log.w(TAG, "load: QAIRT failed, retrying LLAMA_CPP", t)
                    loadBackend(sdk, Backend.LLAMA_CPP)
                } else {
                    throw t
                }
            }

            Log.i(TAG, "load: model READY (backend=$activeBackend)")
            _status.value = ModelStatus.Ready
        } catch (t: Throwable) {
            // Throwable, not Exception: an unsupported SoC / missing native lib throws
            // UnsatisfiedLinkError (an Error), which must still surface as ModelStatus.Error.
            Log.e(TAG, "load: failed", t)
            _status.value = ModelStatus.Error(t.message ?: "Model load failed")
        }
    }

    /**
     * Registers the plugin, pulls the side-loaded bundle, and builds the engine for [backend].
     * On success sets [llm] + [activeBackend]; throws on any failure so [load] can fall back.
     */
    private suspend fun loadBackend(sdk: GenieXSdk, backend: Backend) {
        val folder = if (backend == Backend.QAIRT) config.qnnModelName else config.modelName
        val modelDir = File(context.getExternalFilesDir(null), "models/$folder")
        if (!modelDir.exists()) error("Model not found at ${modelDir.absolutePath}")

        val pluginId =
            if (backend == Backend.QAIRT) GenieXSdk.PLUGIN_ID_QAIRT else GenieXSdk.PLUGIN_ID_LLAMA_CPP
        Log.i(TAG, "loadBackend: $backend plugin=$pluginId dir=${modelDir.absolutePath}")
        // Register the runtime plugin (no-op if already registered).
        runCatching { sdk.registerPlugin(pluginId) }
            .onFailure { Log.w(TAG, "loadBackend: registerPlugin failed (continuing)", it) }

        ModelManagerWrapper.init(context.filesDir.absolutePath).getOrThrow()

        // Register the local model, then resolve its concrete paths. chipset/precision stay null:
        // a LOCALFS bundle is self-describing, so we let the SDK read them from the bundle.
        ModelManagerWrapper.pullFlow(
            ModelPullInput(
                folder, // model_name
                null, // precision
                HubSource.LOCALFS, // hub
                modelDir.absolutePath, // local_path
                null, // hf_token
                null, // chipset
                null, // display_name
                null, // model_type
            ),
        ).collect { event ->
            when (event) {
                is ModelManagerWrapper.PullEvent.Progress ->
                    Log.d(TAG, "loadBackend: pull progress ${event.files}")
                ModelManagerWrapper.PullEvent.Completed ->
                    Log.i(TAG, "loadBackend: pull completed")
                is ModelManagerWrapper.PullEvent.Error -> {
                    Log.e(TAG, "loadBackend: pull error (${event.code}): ${event.message}")
                    error("Model pull failed (${event.code}): ${event.message}")
                }
            }
        }

        val paths = ModelManagerWrapper.getPaths(folder)
            ?: error("Model paths unavailable for $folder")
        Log.i(TAG, "loadBackend: paths name=${paths.model_name} path=${paths.model_path} runtime=${paths.runtime_id}")

        val runtimeId =
            if (backend == Backend.QAIRT) RuntimeIdValue.QAIRT.value else RuntimeIdValue.LLAMA_CPP.value
        // HYBRID = NPU + CPU for QAIRT; null = default (CPU) for llama.cpp.
        val computeUnit = if (backend == Backend.QAIRT) ComputeUnitValue.HYBRID.value else null

        val input = LlmCreateInput(
            paths.model_name,
            paths.model_path,
            paths.tokenizer_path,
            ModelConfig().also { it.nCtx = config.nCtx },
            runtimeId,
            computeUnit,
        )
        Log.i(TAG, "loadBackend: building LlmWrapper (backend=$backend, nCtx=${config.nCtx})…")
        llm = LlmWrapper.builder()
            .llmCreateInput(input)
            .dispatcher(io)
            .build()
            .getOrThrow()
        activeBackend = backend
    }

    override suspend fun generate(prompt: String, params: GenerationParams, system: String?): String =
        withContext(io) {
            val engine = llm ?: throw IllegalStateException("Model not ready")
            Log.i(TAG, "generate: start (maxTokens=${params.maxTokens}, temp=${params.temperature})")
            // A blank system prompt is treated as none, so we never send an empty system message.
            val messages = if (system.isNullOrBlank()) {
                arrayOf(ChatMessage("user", prompt))
            } else {
                arrayOf(ChatMessage("system", system), ChatMessage("user", prompt))
            }
            val templated = engine
                .applyChatTemplate(messages, null, false)
                .getOrThrow()

            val genConfig = GenerationConfig().also {
                it.maxTokens = params.maxTokens
                it.samplerConfig = SamplerConfig().also { s ->
                    s.temperature = params.temperature
                    s.topP = params.topP
                    s.topK = params.topK
                    s.minP = params.minP
                    s.repetitionPenalty = params.repetitionPenalty
                    params.seed?.let { seed -> s.seed = seed }
                    // Grammar is the llama.cpp crash guarantee: it constrains output to safe ASCII
                    // JSON so the llama.cpp JNI never builds a Java string from invalid UTF-8 bytes.
                    // GBNF is a llama.cpp feature; the QAIRT path relies on the prompt + parser + RAG
                    // fallback instead, so we only attach it on the llama.cpp backend.
                    if (activeBackend == Backend.LLAMA_CPP) {
                        params.outputConstraint?.let { c -> s.grammarString = GenieXGrammars.forConstraint(c) }
                    }
                }
            }

            val output = StringBuilder()
            try {
                withTimeout(config.generationTimeoutMs) {
                    engine.generateStreamFlow(templated.formattedText, genConfig).collect { result ->
                        when (result) {
                            is LlmStreamResult.Token -> output.append(result.text)
                            is LlmStreamResult.Completed -> Unit
                            is LlmStreamResult.Error -> {
                                Log.e(TAG, "generate: stream error", result.throwable)
                                throw result.throwable
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // Timeout: stop the native stream (NonCancellable so it still runs) and fail
                // into a normal error the ViewModel renders.
                Log.w(TAG, "generate: timed out after ${config.generationTimeoutMs}ms")
                withContext(NonCancellable) { runCatching { engine.stopStream() } }
                throw IllegalStateException("Generation timed out")
            } catch (e: CancellationException) {
                // Operator selected another alert: stop the native stream, then propagate.
                Log.d(TAG, "generate: cancelled; stopping stream")
                withContext(NonCancellable) { runCatching { engine.stopStream() } }
                throw e
            }
            Log.i(TAG, "generate: done (${output.length} chars)")
            output.toString()
        }

    private companion object {
        const val TAG = "QSenseGenieX"
    }

    private suspend fun awaitInit(sdk: GenieXSdk) =
        suspendCancellableCoroutine { cont ->
            sdk.init(
                context,
                object : GenieXSdk.InitCallback {
                    override fun onSuccess() {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onFailure(message: String) {
                        if (cont.isActive) cont.resumeWithException(RuntimeException(message))
                    }
                },
            )
        }
}
