package org.opentibia.canary.io.iologindata

import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionAttr
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream

/**
 * Condition codec of the player persistence layer: the `Condition::serialize`
 * family (`src/creatures/combat/condition.cpp:277-301` plus the subclass
 * overrides at :786-825, :1474-1488, :1652-1656, :1712-1720, :1832-1848,
 * :2510-2535, :2682-2686, :2862-2881) driven by the `IOLoginDataSave::savePlayerFirst`
 * loop (src/io/functions/iologindata_save_player.cpp:279-291), and the
 * `Condition::createCondition(PropStream&)` + `Condition::unserialize` decode
 * half the `IOLoginDataLoad::loadPlayerConditions` loop uses
 * (src/io/functions/iologindata_load_player.cpp:257-275).
 *
 * The C++ save loop iterates `player->conditions` (the active list `Creature`
 * holds) and writes only the `Condition::isPersistent()` conditions, each
 * terminated by `CONDITIONATTR_END`. The port keeps the same active-list
 * behavior and additionally serializes `Player::storedConditionList`
 * (player.hpp:1698), the list [IOLoginDataSubloads.loadPlayerConditions] fills
 * before the `Player::onCreatureAppear` replay moves it into the active list
 * (player.cpp:12231-12234), so an offline load -> save round trip preserves the
 * persisted stream instead of clearing it. The two lists are disjoint: the
 * replay moves each stored condition through `addCondition` and clears the
 * stored list.
 *
 * The codec itself lives on the condition classes, exactly like the C++
 * virtual methods; this class only owns the persistence framing and list
 * selection.
 *
 * PARITY GAP: the login load path in the port is
 * [IOLoginDataSubloads.loadPlayerConditions], which duplicates this decode with
 * a partial attribute reader. It should delegate to
 * [decodeConditions] so buffs/absorbs/increases and the SOULPOINTS/CAPACITY
 * stats survive a reload; that file is outside this workstream's scope.
 */
class PlayerConditionPersistence {

    /**
     * `IOLoginDataSave::savePlayerFirst` condition block
     * (iologindata_save_player.cpp:279-291): every persistent condition of the
     * player is serialized and closed with `CONDITIONATTR_END`; non-persistent
     * conditions are skipped by `Condition::isPersistent`
     * (condition.cpp:475-489). The C++ `PropWriteStream` out-parameter becomes
     * the returned blob.
     */
    fun savePlayerConditions(player: Player): ByteArray =
        encodeConditions(player.storedConditionList + player.conditions)

    /**
     * The serialization loop over a standalone condition list, so callers and
     * tests can drive the codec without a live [Player]. The order is the list
     * order, exactly like the C++ range-for over `player->conditions`.
     */
    fun encodeConditions(conditions: List<Condition>): ByteArray {
        val stream = PropWriteStream()
        for (condition in conditions) {
            if (condition.isPersistent()) {
                condition.serialize(stream)
                stream.writeU8(ConditionAttr.CONDITIONATTR_END.value)
            }
        }
        return stream.getStream()
    }

    /**
     * `IOLoginDataLoad::loadPlayerConditions` decode loop
     * (iologindata_load_player.cpp:257-275): each iteration mirrors
     * `Condition::createCondition(PropStream&)` (condition.cpp:414-462)
     * followed by `Condition::unserialize` (condition.cpp:189-197). A failed
     * header ends the scan; a condition whose attribute list is rejected is
     * skipped but the scan continues from the same cursor, exactly like the
     * C++ `while (condition)` loop.
     */
    fun decodeConditions(blob: ByteArray): List<Condition> {
        val stream = PropStream(blob)
        val conditions = mutableListOf<Condition>()

        var condition = Condition.createCondition(stream)
        while (condition != null) {
            if (condition.unserialize(stream)) {
                conditions.add(condition)
            }
            condition = Condition.createCondition(stream)
        }

        return conditions
    }
}
