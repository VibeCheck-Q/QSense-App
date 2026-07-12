package com.example.qsense.data.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Unit tests for the pure [selectBackend] runtime chooser (no Android APIs). */
class GenieXBackendTest {

    private val supported = setOf("SM8750", "SM8850")

    @Test
    fun auto_supportedChip_withQnnBundle_picksQairt() {
        assertEquals(
            Backend.QAIRT,
            selectBackend(Backend.AUTO, "SM8750", qnnBundleExists = true, ggufBundleExists = true, supported),
        )
    }

    @Test
    fun auto_chipMatchIsCaseInsensitiveAndSubstring() {
        assertEquals(
            Backend.QAIRT,
            selectBackend(Backend.AUTO, "qualcomm sm8850 blah", qnnBundleExists = true, ggufBundleExists = false, supported),
        )
    }

    @Test
    fun auto_supportedChip_butNoQnnBundle_fallsBackToLlama() {
        assertEquals(
            Backend.LLAMA_CPP,
            selectBackend(Backend.AUTO, "SM8750", qnnBundleExists = false, ggufBundleExists = true, supported),
        )
    }

    @Test
    fun auto_unsupportedChip_picksLlama() {
        assertEquals(
            Backend.LLAMA_CPP,
            selectBackend(Backend.AUTO, "SM7325", qnnBundleExists = true, ggufBundleExists = true, supported),
        )
    }

    @Test
    fun auto_nullSoc_picksLlamaWhenGgufPresent() {
        assertEquals(
            Backend.LLAMA_CPP,
            selectBackend(Backend.AUTO, null, qnnBundleExists = true, ggufBundleExists = true, supported),
        )
    }

    @Test
    fun auto_noBundlesAtAll_returnsNull() {
        assertNull(
            selectBackend(Backend.AUTO, "SM8750", qnnBundleExists = false, ggufBundleExists = false, supported),
        )
    }

    @Test
    fun forcedQairt_usesQairtOnlyWhenBundlePresent() {
        assertEquals(
            Backend.QAIRT,
            selectBackend(Backend.QAIRT, "SM7325", qnnBundleExists = true, ggufBundleExists = true, supported),
        )
        assertNull(
            selectBackend(Backend.QAIRT, "SM8750", qnnBundleExists = false, ggufBundleExists = true, supported),
        )
    }

    @Test
    fun forcedLlama_ignoresChipAndUsesGgufWhenPresent() {
        assertEquals(
            Backend.LLAMA_CPP,
            selectBackend(Backend.LLAMA_CPP, "SM8750", qnnBundleExists = true, ggufBundleExists = true, supported),
        )
        assertNull(
            selectBackend(Backend.LLAMA_CPP, "SM8750", qnnBundleExists = true, ggufBundleExists = false, supported),
        )
    }
}
