package com.example.qsense.data.llm

/**
 * The on-device LLM runtime GenieX should drive.
 *
 * - [QAIRT] runs on the Hexagon NPU (Snapdragon 8 Elite / 8 Elite Gen 5) from a side-loaded QNN
 *   context bundle.
 * - [LLAMA_CPP] runs the GGUF on CPU and works on any arm64 device.
 * - [AUTO] picks QAIRT when the chip supports it and a QNN bundle is present, else LLAMA_CPP.
 */
enum class Backend { AUTO, QAIRT, LLAMA_CPP }

/**
 * Pure backend selector (no Android APIs) so it can be unit-tested off-device. Returns the runtime
 * to load, or `null` when no LLM path is possible — the caller then falls back to the RAG diagnosis.
 *
 * @param requested the configured preference (AUTO, or a forced backend for a demo)
 * @param socModel the SoC model string (e.g. `Build.SOC_MODEL`); matched case-insensitively
 * @param qnnBundleExists whether the side-loaded QNN bundle folder is present
 * @param ggufBundleExists whether the side-loaded GGUF bundle folder is present
 * @param supportedChipsets SoC substrings eligible for QAIRT (e.g. `SM8750`, `SM8850`)
 */
fun selectBackend(
    requested: Backend,
    socModel: String?,
    qnnBundleExists: Boolean,
    ggufBundleExists: Boolean,
    supportedChipsets: Set<String>,
): Backend? {
    val chipSupportsQnn = socModel != null &&
        supportedChipsets.any { chip -> socModel.contains(chip, ignoreCase = true) }
    return when (requested) {
        Backend.QAIRT -> if (qnnBundleExists) Backend.QAIRT else null
        Backend.LLAMA_CPP -> if (ggufBundleExists) Backend.LLAMA_CPP else null
        Backend.AUTO -> when {
            chipSupportsQnn && qnnBundleExists -> Backend.QAIRT
            ggufBundleExists -> Backend.LLAMA_CPP
            else -> null
        }
    }
}
