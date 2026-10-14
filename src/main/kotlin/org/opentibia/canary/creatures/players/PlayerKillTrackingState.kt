package org.opentibia.canary.creatures.players

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.bestiary.IOBestiary
import org.opentibia.canary.systems.bestiary.MonsterBestiaryInfo
import org.opentibia.canary.systems.concoction.ConcoctionType
import org.opentibia.canary.systems.taskhunting.PreyTaskDataState
import org.opentibia.canary.systems.taskhunting.TaskHuntingOption
import org.opentibia.canary.systems.taskhunting.TaskHuntingSlot

private val logger = KotlinLogging.logger {}

/**
 * The Player kill trackers and kill-entry point.
 *
 * Source: `src/creatures/players/player.cpp:6606-6660` (`addHuntingTaskKill`,
 * `addBestiaryKill`, `addBosstiaryKill`) and `player.cpp:6680-6698`
 * (`onKilledMonster`). The Canary globals (`g_ioprey`, `g_iobestiary`,
 * `g_ioBosstiary`, `g_configManager`, `g_kv`, `g_callbacks`) are constructor
 * seams per AGENTS.md 1.1.
 */

/** `Player::addHuntingTaskKill` (player.cpp:6606-6621). */
fun Player.addHuntingTaskKill(
    /** `mType` (player.cpp:6606). */
    mType: MonsterType,
    /** `g_ioprey().getTaskRewardOption(taskSlot)` (player.cpp:6612). */
    getTaskRewardOption: (TaskHuntingSlot) -> TaskHuntingOption? = {
        features.taskHuntingEngine.getTaskRewardOption(it)
    },
) {
    val taskSlot = getTaskHuntingWithCreature(mType.info.raceid) ?: return
    val option = getTaskRewardOption(taskSlot) ?: return

    taskSlot.currentKills += 1
    if ((taskSlot.upgrade && taskSlot.currentKills >= option.secondKills) ||
        (!taskSlot.upgrade && taskSlot.currentKills >= option.firstKills)
    ) {
        taskSlot.state = PreyTaskDataState.Completed
        sendTextMessageTyped(
            MessageClass.MESSAGE_STATUS,
            "You succesfully finished your hunting task. Your reward is ready to be claimed!",
        )
    }
    reloadTaskSlot(taskSlot.id)
}

/** `Player::addBestiaryKill` (player.cpp:6623-6641). */
fun Player.addBestiaryKill(
    /** `mType` (player.cpp:6623). */
    mType: MonsterType,
    /** `g_iobestiary()` kill engine (iobestiary.cpp:383-410). */
    iobestiary: IOBestiary = IOBestiary(),
    /** `g_configManager().getNumber(BESTIARY_KILL_MULTIPLIER)` (configmanager.cpp:228, default 1). */
    bestiaryKillMultiplier: Int = 1,
    /** `g_kv().scoped("eventscheduler")->get("double-bestiary")` (player.cpp:6630-6631). */
    doubleBestiaryEnabled: () -> Boolean = { false },
) {
    if (mType.isBoss()) {
        return
    }

    var kills = bestiaryKillMultiplier
    if (doubleBestiaryEnabled()) {
        kills *= 2
    }

    if (features.concoctions.isConcoctionActive(ConcoctionType.BestiaryBetterment)) {
        kills *= 2
    }

    iobestiary.addBestiaryKill(features.bestiary, mType.toBestiaryInfo(), kills)
}

/** `Player::addBosstiaryKill` (player.cpp:6643-6660). */
fun Player.addBosstiaryKill(
    /** `mType` (player.cpp:6643). */
    mType: MonsterType,
    /** `g_ioBosstiary().addBosstiaryKill(this, mType, kills)` (io_bosstiary.cpp:173-213). */
    ioBosstiaryAddKill: (Player, MonsterType, Int) -> Unit = { _, _, _ -> },
    /** `g_configManager().getNumber(BOSSTIARY_KILL_MULTIPLIER)` (configmanager.cpp:234, default 1). */
    bosstiaryKillMultiplier: Int = 1,
    /** `g_kv().scoped("eventscheduler")->get("double-bosstiary")` (player.cpp:6652-6653). */
    doubleBosstiaryEnabled: () -> Boolean = { false },
    /** `g_ioBosstiary().getBoostedBossId()` (player.cpp:6657). */
    boostedBossId: () -> Int = { 0 },
    /** `g_configManager().getNumber(BOOSTED_BOSS_KILL_BONUS)` (configmanager.cpp:230, default 3). */
    boostedBossKillBonus: Int = 3,
) {
    if (!mType.isBoss()) {
        return
    }

    var kills = bosstiaryKillMultiplier
    if (doubleBosstiaryEnabled()) {
        kills *= 2
    }

    if (boostedBossId() == mType.info.raceid) {
        kills *= boostedBossKillBonus
    }

    ioBosstiaryAddKill(this, mType, kills)
}

/**
 * `Player::onKilledMonster` (player.cpp:6680-6698).
 *
 * `Monster::getMonsterType()` is resolved by the caller because the port keeps
 * the monster-type state in the monster pipeline layer. The `playerOnKill`
 * Lua callback and the three trackers are injectable so the kill entry point
 * can be exercised without the engine composition.
 */
fun Player.onKilledMonster(
    /** `monster` (player.cpp:6680). */
    monster: Monster,
    /** `monster->getMonsterType()` (monster.cpp:6687). */
    mType: MonsterType?,
    /** `g_callbacks().executeCallback(EventCallback_t::playerOnKill, ...)` (player.cpp:6693). */
    executePlayerOnKillCallback: (Player, Monster) -> Unit = { _, _ -> },
    /** `Player::addHuntingTaskKill` (player.cpp:6694). */
    addHuntingTaskKill: ((Player, MonsterType) -> Unit)? = null,
    /** `Player::addBestiaryKill` (player.cpp:6695). */
    addBestiaryKill: ((Player, MonsterType) -> Unit)? = null,
    /** `Player::addBosstiaryKill` (player.cpp:6696). */
    addBosstiaryKill: ((Player, MonsterType) -> Unit)? = null,
): Boolean {
    if (hasFlag(PlayerFlags_t.NotGenerateLoot)) {
        monster.setDropLoot(false)
    }

    if (monster.hasBeenSummoned()) {
        return false
    }

    if (mType == null) {
        logger.error { "[Player::onKilledMonster] Monster type is null." }
        return false
    }

    if (!monster.getSoulPit()) {
        executePlayerOnKillCallback(this, monster)
        (addHuntingTaskKill ?: { p, m -> p.addHuntingTaskKill(m) })(this, mType)
        (addBestiaryKill ?: { p, m -> p.addBestiaryKill(m) })(this, mType)
        (addBosstiaryKill ?: { p, m -> p.addBosstiaryKill(m) })(this, mType)
    }

    return false
}

/** `MonsterType::info` projection onto the bestiary engine carrier (iobestiary.cpp:383-410). */
private fun MonsterType.toBestiaryInfo(): MonsterBestiaryInfo = MonsterBestiaryInfo(
    raceid = info.raceid,
    name = name,
    bestiaryFirstUnlock = info.bestiaryFirstUnlock,
    bestiarySecondUnlock = info.bestiarySecondUnlock,
    bestiaryToUnlock = info.bestiaryToUnlock,
    bestiaryCharmsPoints = info.bestiaryCharmsPoints,
    bestiaryStars = info.bestiaryStars,
    bestiaryOccurrence = info.bestiaryOccurrence,
    bestiaryClass = info.bestiaryClass,
    bestiaryRace = info.bestiaryRace,
    elementMap = info.elementMap.mapKeys { it.key.value },
)
