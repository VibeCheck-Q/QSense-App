package com.example.qsense.data.llm

import android.content.Context
import android.util.Log
import com.example.qsense.di.GenieXConfig
import com.example.qsense.domain.service.GenerationParams
import com.example.qsense.domain.service.ModelStatus
import com.example.qsense.domain.service.TextGenerator
import com.geniex.sdk.GenieXSdk
import com.geniex.sdk.LlmWrapper
import com.geniex.sdk.ModelManagerWrapper
import com.geniex.sdk.bean.ChatMessage
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
 * [TextGenerator] backed by Qualcomm GenieX (llama.cpp / GGUF runtime). This is the only
 * file that imports the GenieX SDK. The model bundle is side-loaded to
 * getExternalFilesDir(null)/models/<modelName> (see the demo runbook for the adb push).
 *
 * NOTE: GenieX runs only on supported Snapdragon SoCs. Any init/load failure is surfaced as
 * ModelStatus.Error so the rest of the app (MQTT + dashboard) keeps working.
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

    /** Initializes the SDK and loads the side-loaded model. Call once at startup. */
    suspend fun load() = withContext(io) {
        try {
            val modelDir = File(context.getExternalFilesDir(null), "models/${config.modelName}")
            Log.i(TAG, "load: model dir = ${modelDir.absolutePath} (exists=${modelDir.exists()})")
            if (!modelDir.exists()) {
                _status.value = ModelStatus.Error("Model not found at ${modelDir.absolutePath}")
                return@withContext
            }

            val sdk = GenieXSdk.getInstance()
            Log.i(TAG, "load: initializing GenieX SDK…")
            awaitInit(sdk)
            Log.i(TAG, "load: SDK init OK; registering llama_cpp plugin")
            // Register the GGUF runtime plugin (no-op if already registered).
            runCatching { sdk.registerPlugin(GenieXSdk.PLUGIN_ID_LLAMA_CPP) }
                .onFailure { Log.w(TAG, "load: registerPlugin failed (continuing)", it) }

            Log.i(TAG, "load: ModelManagerWrapper.init(${context.filesDir.absolutePath})")
            ModelManagerWrapper.init(context.filesDir.absolutePath).getOrThrow()

            // Register the local model, then resolve its concrete paths.
            Log.i(TAG, "load: pulling local model '${config.modelName}' from ${modelDir.absolutePath}")
            ModelManagerWrapper.pullFlow(
                ModelPullInput(
                    config.modelName, // model_name
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
                        Log.d(TAG, "load: pull progress ${event.files}")
                    ModelManagerWrapper.PullEvent.Completed ->
                        Log.i(TAG, "load: pull completed")
                    is ModelManagerWrapper.PullEvent.Error -> {
                        Log.e(TAG, "load: pull error (${event.code}): ${event.message}")
                        error("Model pull failed (${event.code}): ${event.message}")
                    }
                }
            }

            val paths = ModelManagerWrapper.getPaths(config.modelName)
                ?: error("Model paths unavailable for ${config.modelName}")
            Log.i(TAG, "load: resolved paths name=${paths.model_name} path=${paths.model_path} runtime=${paths.runtime_id}")

            val input = LlmCreateInput(
                paths.model_name,
                paths.model_path,
                paths.tokenizer_path,
                ModelConfig().also { it.nCtx = config.nCtx },
                RuntimeIdValue.LLAMA_CPP.value,
                null, // compute_unit
            )
            Log.i(TAG, "load: building LlmWrapper (nCtx=${config.nCtx})…")
            llm = LlmWrapper.builder()
                .llmCreateInput(input)
                .dispatcher(io)
                .build()
                .getOrThrow()

            Log.i(TAG, "load: model READY")
            _status.value = ModelStatus.Ready
        } catch (t: Throwable) {
            // Throwable, not Exception: an unsupported SoC / missing native lib throws
            // UnsatisfiedLinkError (an Error), which must still surface as ModelStatus.Error.
            Log.e(TAG, "load: failed", t)
            _status.value = ModelStatus.Error(t.message ?: "Model load failed")
        }
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
                    // Grammar is the crash guarantee: it constrains output to safe ASCII JSON so
                    // GenieX never builds a Java string from invalid UTF-8 token bytes.
                    params.outputConstraint?.let { c -> s.grammarString = GenieXGrammars.forConstraint(c) }
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
