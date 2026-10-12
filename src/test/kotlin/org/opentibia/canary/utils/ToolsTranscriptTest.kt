package org.opentibia.canary.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ToolsTranscriptTest {

    @Test
    fun `subtracts elapsed time without crossing zero matching Canary numeric_functions_test`() {
        assertThat(Tools.subtractElapsedTime(1000, 250)).isEqualTo(750L)
        assertThat(Tools.subtractElapsedTime(1000, 0)).isEqualTo(1000L)
        assertThat(Tools.subtractElapsedTime(1000, 1000)).isEqualTo(0L)
        assertThat(Tools.subtractElapsedTime(1000, 1001)).isEqualTo(0L)
        assertThat(Tools.subtractElapsedTime(1, Long.MAX_VALUE)).isEqualTo(0L)
        assertThat(Tools.subtractElapsedTime(0, 1)).isEqualTo(0L)
        assertThat(Tools.subtractElapsedTime(-1, 1)).isEqualTo(0L)
    }

    @Test
    fun `replaces strings matching Canary string_functions_test vectors`() {
        assertThat(Tools.replaceString("", "", "")).isEqualTo("")
        assertThat(Tools.replaceString("subject", "", "x")).isEqualTo("subject")
        assertThat(Tools.replaceString("all together", " ", "_")).isEqualTo("all_together")
        assertThat(Tools.replaceString("beautiful", "u", "")).isEqualTo("beatifl")
        assertThat(Tools.replaceString("empty_empty_empty_", "empty_", "")).isEqualTo("")
        assertThat(Tools.replaceString("I am someone", "someone", "Lucas")).isEqualTo("I am Lucas")
    }

    @Test
    fun `transforms hashes matching Canary canonical vectors`() {
        assertThat(Tools.transformToSHA1("")).isEqualTo("da39a3ee5e6b4b0d3255bfef95601890afd80709")
        assertThat(Tools.transformToSHA1("abc")).isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d")

        assertThat(Tools.transformToSHA256("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        assertThat(Tools.transformToSHA256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
}
