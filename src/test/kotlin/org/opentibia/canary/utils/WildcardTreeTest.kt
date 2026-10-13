package org.opentibia.canary.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for `WildcardTreeNode` (`src/utils/wildcardtree.cpp:12-108`):
 * the CLI `~suffix` player-name resolution tree.
 */
class WildcardTreeTest {

    @Test
    fun `findOne extends the query along a unique path`() {
        val tree = WildcardTreeNode(false)
        tree.insert("guild")

        val result = tree.findOne("gui")

        assertThat(result.value).isEqualTo(ReturnValue.NOERROR)
        assertThat(result.name).isEqualTo("guild")
    }

    @Test
    fun `findOne returns the exact name when the query is complete`() {
        val tree = WildcardTreeNode(false)
        tree.insert("guild")

        val result = tree.findOne("guild")

        assertThat(result.value).isEqualTo(ReturnValue.NOERROR)
        assertThat(result.name).isEqualTo("guild")
    }

    @Test
    fun `findOne reports an unknown path as not online`() {
        val tree = WildcardTreeNode(false)
        tree.insert("guild")

        val result = tree.findOne("xyz")

        assertThat(result.value).isEqualTo(ReturnValue.PLAYERWITHTHISNAMEISNOTONLINE)
        assertThat(result.name).isNull()
    }

    @Test
    fun `findOne reports a branching path as ambiguous`() {
        val tree = WildcardTreeNode(false)
        tree.insert("guild")
        tree.insert("guest")

        val result = tree.findOne("gu")

        assertThat(result.value).isEqualTo(ReturnValue.NAMEISTOOAMBIGUOUS)
        assertThat(result.name).isNull()
    }

    @Test
    fun `findOne treats a breakpoint node as ambiguous`() {
        val tree = WildcardTreeNode(false)
        tree.insert("guild")
        tree.insert("gui")

        val result = tree.findOne("gui")

        assertThat(result.value).isEqualTo(ReturnValue.NAMEISTOOAMBIGUOUS)
        assertThat(result.name).isNull()
    }

    @Test
    fun `inserting a short prefix makes its exact query ambiguous`() {
        val tree = WildcardTreeNode(false)
        tree.insert("abc")
        tree.insert("ab")

        assertThat(tree.findOne("ab").value).isEqualTo(ReturnValue.NAMEISTOOAMBIGUOUS)
        assertThat(tree.findOne("abc").name).isEqualTo("abc")
    }

    @Test
    fun `removing the extension frees the shorter prefix`() {
        val tree = WildcardTreeNode(false)
        tree.insert("abc")
        tree.insert("ab")

        tree.remove("abc")

        assertThat(tree.findOne("ab").value).isEqualTo(ReturnValue.NOERROR)
        assertThat(tree.findOne("ab").name).isEqualTo("ab")
    }

    @Test
    fun `remove prunes the path so the name is no longer found`() {
        val tree = WildcardTreeNode(false)
        tree.insert("guild")

        tree.remove("guild")

        assertThat(tree.findOne("guild").value).isEqualTo(ReturnValue.PLAYERWITHTHISNAMEISNOTONLINE)
        assertThat(tree.findOne("gui").value).isEqualTo(ReturnValue.PLAYERWITHTHISNAMEISNOTONLINE)
    }

    @Test
    fun `remove keeps the shared prefix of another name`() {
        val tree = WildcardTreeNode(false)
        tree.insert("guild")
        tree.insert("guest")

        tree.remove("guild")

        assertThat(tree.findOne("gue").name).isEqualTo("guest")
        assertThat(tree.findOne("guild").value).isEqualTo(ReturnValue.PLAYERWITHTHISNAMEISNOTONLINE)
    }

    @Test
    fun `remove ignores unknown or partial paths`() {
        val tree = WildcardTreeNode(false)
        tree.insert("guild")

        tree.remove("nope")
        tree.remove("gui")
        tree.remove("")

        assertThat(tree.findOne("gui").name).isEqualTo("guild")
    }

    @Test
    fun `insert rejects the empty string that underflows the C++ length`() {
        val tree = WildcardTreeNode(false)

        val failure = runCatching { tree.insert("") }

        assertThat(failure.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }
}
