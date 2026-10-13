package org.opentibia.canary.utils

import java.util.TreeMap
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * `findOne` result holder replacing the C++ `std::string& result` out
 * parameter: [name] is only set when [value] is [ReturnValue.NOERROR].
 */
data class WildcardMatchResult(val value: ReturnValue, val name: String?)

/**
 * Port of `WildcardTreeNode` (src/utils/wildcardtree.hpp:14-36,
 * wildcardtree.cpp:12-108): the sorted character tree behind CLI `~suffix`
 * player-name resolution. Children stay in `std::map<char, ...>` order so
 * `findOne` always extends along the lowest character.
 *
 * PARITY GAP: `insert("")` relies on `std::string::length() - 1` underflow in
 * C++ (undefined behavior); the port rejects the empty string.
 */
class WildcardTreeNode(private var breakpoint: Boolean) {

    private val children: TreeMap<Char, WildcardTreeNode> = TreeMap()

    /** `WildcardTreeNode::getChild` (wildcardtree.cpp:12-18). */
    fun getChild(ch: Char): WildcardTreeNode? = children[ch]

    /** `WildcardTreeNode::addChild` (wildcardtree.cpp:28-39). */
    fun addChild(ch: Char, breakp: Boolean): WildcardTreeNode {
        val child = children[ch]
        if (child != null) {
            if (breakp && !child.breakpoint) {
                child.breakpoint = true
            }
            return child
        }

        val created = WildcardTreeNode(breakp)
        children[ch] = created
        return created
    }

    /** `WildcardTreeNode::insert` (wildcardtree.cpp:41-50). */
    fun insert(str: String) {
        require(str.isNotEmpty()) { "insert requires a non-empty name" }

        var cur = this
        val length = str.length - 1
        for (pos in 0 until length) {
            cur = cur.addChild(str[pos], false)
        }
        cur.addChild(str[length], true)
    }

    /** `WildcardTreeNode::remove` (wildcardtree.cpp:52-83). */
    fun remove(str: String) {
        var cur = this
        val path = ArrayDeque<WildcardTreeNode>()
        path.addLast(cur)

        var len = str.length
        for (pos in 0 until len) {
            cur = cur.getChild(str[pos]) ?: return
            path.addLast(cur)
        }

        cur.breakpoint = false

        while (true) {
            cur = path.removeLast()
            if (cur.children.isNotEmpty() || cur.breakpoint || path.isEmpty()) {
                break
            }

            val parent = path.last()
            len--
            parent.children.remove(str[len])
        }
    }

    /** `WildcardTreeNode::findOne` (wildcardtree.cpp:85-108). */
    fun findOne(query: String): WildcardMatchResult {
        var cur = this
        for (ch in query) {
            cur = cur.getChild(ch) ?: return WildcardMatchResult(ReturnValue.PLAYERWITHTHISNAMEISNOTONLINE, null)
        }

        val result = StringBuilder(query)
        while (true) {
            val size = cur.children.size
            if (size == 0) {
                return WildcardMatchResult(ReturnValue.NOERROR, result.toString())
            }
            if (size > 1 || cur.breakpoint) {
                return WildcardMatchResult(ReturnValue.NAMEISTOOAMBIGUOUS, null)
            }

            val entry = cur.children.firstEntry()
            result.append(entry.key)
            cur = entry.value
        }
    }
}
