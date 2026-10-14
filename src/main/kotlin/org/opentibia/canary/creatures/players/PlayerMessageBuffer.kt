package org.opentibia.canary.creatures.players

import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.network.protocol.MessageClass

/**
 * Player chat anti-spam bookkeeping
 * (src/creatures/players/player.cpp:3508-3543).
 *
 * Canary keeps `MessageBufferCount` on `Player` (player.hpp:1804) and the
 * `muteCountMap` as a static member shared by every player (player.hpp:281).
 * The state holder is the port's composition seam; every Canary global is a
 * constructor seam per AGENTS.md 1.1: [maxMessageBuffer] replaces
 * `g_configManager().getNumber(MAX_MESSAGEBUFFER)` (configmanager.cpp:315,
 * default 4), [cannotBeMuted] replaces
 * `hasFlag(PlayerFlags_t::CannotBeMuted)`, [guid] is `Player::guid`,
 * [addCondition] is `Player::addCondition`,
 * [sendTextMessage] is `Player::sendTextMessage` and [muteCountMap] is the
 * shared static map (inject one instance per world to keep the escalation
 * across player relogs, exactly like Canary).
 *
 * `Player::onThink` feeds [addMessageBuffer] every 1500 ms
 * (player.cpp:8619-8627) and `Game::playerSay` calls [removeMessageBuffer]
 * per accepted message (game.cpp:7437).
 */
class PlayerMessageBuffer(
    /** `g_configManager().getNumber(MAX_MESSAGEBUFFER)` (configmanager.cpp:315). */
    private val maxMessageBuffer: () -> Int = { 0 },
    /** `Player::hasFlag(PlayerFlags_t::CannotBeMuted)`. */
    private val cannotBeMuted: () -> Boolean = { false },
    /** `Player::guid` (player.hpp:1795). */
    private val guid: () -> Long = { 0L },
    /** `Player::addCondition(condition)` (player.cpp:3537). */
    private val addCondition: (Condition) -> Unit = {},
    /** `Player::sendTextMessage(MESSAGE_FAILURE, ...)` (player.cpp:3541). */
    private val sendTextMessage: (MessageClass, String) -> Unit = { _, _ -> },
    /** `Player::muteCountMap` (player.hpp:281): muted-count per player guid. */
    val muteCountMap: MutableMap<Long, Int> = mutableMapOf(),
) {

    /**
     * `Player::MessageBufferCount` (player.hpp:1804); private in Canary,
     * projected read-only so the host and transcript tests can observe it.
     */
    var messageBufferCount: Int = 0
        private set

    /** `Player::addMessageBuffer` (player.cpp:3508-3512). */
    fun addMessageBuffer() {
        if (messageBufferCount > 0 && maxMessageBuffer() != 0 && !cannotBeMuted()) {
            --messageBufferCount
        }
    }

    /** `Player::removeMessageBuffer` (player.cpp:3514-3543). */
    fun removeMessageBuffer() {
        if (cannotBeMuted()) {
            return
        }

        val maxBuffer = maxMessageBuffer()
        if (maxBuffer == 0 || messageBufferCount > maxBuffer + 1) {
            return
        }

        ++messageBufferCount
        if (messageBufferCount <= maxBuffer) {
            return
        }

        val muteCount = muteCountMap[guid()] ?: 1
        val muteTime = 5 * muteCount * muteCount
        muteCountMap[guid()] = muteCount + 1

        val condition = Condition.createCondition(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_MUTED,
            ticks = muteTime * 1000,
        )
        if (condition != null) {
            addCondition(condition)
        }

        sendTextMessage(MessageClass.MESSAGE_FAILURE, "You are muted for $muteTime seconds.")
    }
}
