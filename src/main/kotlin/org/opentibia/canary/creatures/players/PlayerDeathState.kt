package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.SkillState
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.systems.bestiary.Charm
import org.opentibia.canary.systems.bestiary.CharmRune
import kotlin.math.ceil
import kotlin.math.floor

/**
 * `Creature::CountBlock_t` (src/creatures/creature.hpp:830-833): the damage-map
 * entry holds the accumulated damage and the timestamp of the last hit.
 */
data class CountBlock(
    /** `CountBlock_t::total` (creature.hpp:831). */
    val total: Int = 0,
    /** `CountBlock_t::ticks` (creature.hpp:832). */
    val ticks: Long = 0,
)

/**
 * Self-contained mirror of `Player::death` (src/creatures/players/player.cpp:
 * 4035-4284) and the death flag it flips (`Player::setDead`/`isDead`,
 * player.cpp:11081-11093), plus the state the death penalty applies to.
 *
 * The class is intentionally *not* `Player.kt` (per task constraints): the host
 * `Player` lives in `creatures/Creature.kt`, which this wave does not own. Canary
 * keeps every member this method touches directly on `Player`
 * (player.hpp:1711, 1722, 1849-1851; creature.hpp:839, 932); the public ones
 * (`level`, `experience`, `magLevel`, health/mana maxima, skull, blessings,
 * `getLostPercent()`) are read through the injected [player], and every private
 * or out-of-scope member is a documented constructor seam:
 *
 *  - `Player::skills[SKILL_LAST + 1]` (player.hpp:1711) -> [skills]
 *  - `Player::manaSpent` (player.hpp:1722) -> the [manaSpent] field here
 *  - `Player::levelPercent` (player.hpp:1849) -> [setLevelPercent]
 *  - `Player::magLevelPercent` (player.hpp:1851) -> [setMagLevelPercent]
 *  - `Player::lastLogout` -> [setLastLogout]
 *  - `Creature::damageMap` (creature.hpp:839) -> [getDamageMap]
 *  - `Creature::skillLoss` (creature.hpp:932) -> [getSkillLoss]/[setSkillLoss]
 *  - `Player::vocation` (player.hpp:1755) -> [getVocation]
 *  - `g_game().getPlayerByID` -> [getPlayerById]
 *  - `g_iobestiary().getBestiaryCharm` -> [getBestiaryCharm]
 *  - `g_monsters().getMonsterType` -> [getMonsterType]
 *  - the event/callback dispatches, protocol sends, mount state
 *    (`Player::isMounted`/`dismount`) and world effects (`despawn`,
 *    `internalTeleport`, `addCreatureHealth`, `addPlayerMana`,
 *    `internalCreatureChangeOutfit`, `removeItemOfType`) -> their seams.
 *
 * PARITY GAPS (documented, host wiring is out of this package):
 *  - the host `Player` must override `death(lastHitCreature)` and delegate here;
 *    `Creature.kt` is out of scope for this wave.
 *  - `Player::trackRemovedCondition` (creature.hpp:867-873) has no port-side
 *    condition-type counters; `Player::removeCondition` already runs the
 *    `endCondition`/`onEndCondition` pair the C++ death loop runs explicitly.
 *  - the C++ `uint64_t`/`uint32_t` accumulators are modelled as [Long]/[Int];
 *    the values stay inside the Long range for gameplay-reachable levels.
 */
class PlayerDeathState(
    /** The host `Player` whose public state the death penalty mutates. */
    private val player: Player,
    /** `OTSYS_TIME()` (`src/utils/time.hpp`); milliseconds. */
    private val now: () -> Long = { System.currentTimeMillis() },
    /** `getTimeNow()` (`src/account/account.cpp`); seconds, for `lastLogout`. */
    private val getTimeNow: () -> Long = { System.currentTimeMillis() / 1000L },
    /** `g_configManager().getBoolean(TOGGLE_MOUNT_IN_PZ)` (configmanager.cpp:373, default false). */
    private val toggleMountInPz: Boolean = false,
    /** `g_configManager().getNumber(ADVENTURERSBLESSING_LEVEL)` (configmanager.cpp:227). */
    private val adventurersBlessingLevel: () -> Int = { 21 },
    /** `Creature::skillLoss` (creature.hpp:932, protected). */
    private val getSkillLoss: () -> Boolean = { true },
    /** `Creature::setSkillLoss` (creature.hpp:644-646). */
    private val setSkillLoss: (Boolean) -> Unit = { player.setSkillLoss(it) },
    /** `Player::skills[SKILL_LAST + 1]` (player.hpp:1711, private). */
    private val skills: List<SkillState> = List(SKILL_LAST + 1) { SkillState() },
    /** `Player::levelPercent` (player.hpp:1849, private `uint8_t`). */
    private val setLevelPercent: (Int) -> Unit = {},
    /** `Player::magLevelPercent` (player.hpp:1851, private `double_t`). */
    private val setMagLevelPercent: (Double) -> Unit = {},
    /** `Player::lastLogout` (player.hpp, private); `lastLogout = getTimeNow()`. */
    private val setLastLogout: (Long) -> Unit = {},
    /** `Creature::damageMap` (creature.hpp:839). */
    private val getDamageMap: () -> Map<Long, CountBlock> = { emptyMap() },
    /** `g_game().getPlayerByID` (game.cpp). */
    private val getPlayerById: (Long) -> Player? = { null },
    /** `Player::vocation` (player.hpp:1755); the unwired fallback is `VOCATION_NONE`. */
    private val getVocation: () -> Vocation = {
        player.vocations?.getVocation(player.vocationId) ?: unwiredVocation
    },
    /** `g_iobestiary().getBestiaryCharm(CHARM_BLESS)` (iobestiary.cpp:231-248). */
    private val getBestiaryCharm: (CharmRune) -> Charm? = { null },
    /** `g_monsters().getMonsterType(name)` (`src/creatures/monsters/monsters.hpp`). */
    private val getMonsterType: (String) -> MonsterType? = { null },
    /** `Player::isMounted` (player.cpp:7987). */
    private val isMounted: () -> Boolean = { player.features.mounts.isMounted() },
    /** `Player::dismount` (player.cpp:8121). */
    private val dismount: () -> Unit = { player.features.mounts.dismount() },
    /** `g_game().internalCreatureChangeOutfit(player, defaultOutfit)` (player.cpp:4038). */
    private val internalCreatureChangeOutfit: (Player, Outfit) -> Unit = { _, _ -> },
    /** `g_game().sendSingleSoundEffect(pos, id, source)` (player.cpp:4042). */
    private val sendSingleSoundEffect: (Position, SoundEffect, Creature) -> Unit = { _, _, _ -> },
    /** `g_events().eventPlayerOnLoseExperience(player, expLoss)` (player.cpp:4118). */
    private val eventPlayerOnLoseExperience: (Player, Long) -> Unit = { _, _ -> },
    /** `g_callbacks().executeCallback(playerOnLoseExperience, player, expLoss)` (player.cpp:4119). */
    private val executePlayerOnLoseExperienceCallback: (Player, Long) -> Unit = { _, _ -> },
    /** `Player::sendStats()` protocol seam. */
    private val sendStats: () -> Unit = {},
    /** `Player::sendReLoginWindow(unfairFightReduction)` (player.cpp:2384-2388). */
    private val sendReLoginWindow: (Int) -> Unit = {},
    /** `Player::removeItemOfType(itemId, amount, subType)` (player.cpp:5292) for the AOL. */
    private val removeItemOfType: (Int, Int, Int) -> Unit = { _, _, _ -> },
    /** `Player::despawn()` (player.cpp:4310-4344). */
    private val despawn: () -> Unit = {},
    /** `g_game().internalTeleport(player, getTemplePosition(), true)` (player.cpp:4270). */
    private val internalTeleport: (Player, Position, Boolean) -> Unit = { _, _, _ -> },
    /** `g_game().addCreatureHealth(player)` (player.cpp:4271). */
    private val addCreatureHealth: (Player) -> Unit = {},
    /** `g_game().addPlayerMana(player)` (player.cpp:4272). */
    private val addPlayerMana: (Player) -> Unit = {},
    /** `Player::getTemplePosition()` (player.cpp:8319-8326). */
    private val getTemplePosition: () -> Position = { Position(0, 0, 0) },
    /** `Player::loginPosition = town->getTemplePosition()` (player.cpp:4040). */
    private val setLoginPosition: (Position) -> Unit = {},
    /** `g_logger().debug(...)` (player.cpp:4117). */
    private val logDebug: (String) -> Unit = {},
) {

    /**
     * `Player::manaSpent` (player.hpp:1722): the magic-level progress
     * accumulator the death penalty drains.
     */
    var manaSpent: Long = 0

    /** `Player::getSpentMana` (player.cpp:7849-7851). */
    fun getSpentMana(): Long = manaSpent

    /**
     * `Player::m_isDead` (player.hpp:1883); `false` by default. Kept apart from
     * `Creature::isDead` (health-driven) exactly like the C++ override.
     */
    private var mIsDead: Boolean = false

    /**
     * `Player::setDead(isDead)` (player.cpp:11081-11088): latches the flag and
     * adds the player to (or removes it from) the game's dead-player registry.
     * `g_game().addDeadPlayer`/`removeDeadPlayer` are seams per AGENTS.md 1.1.
     */
    fun setDead(
        /** The `isDead` argument (player.cpp:11081). */
        isDead: Boolean,
        /** `g_game().addDeadPlayer(this)` (player.cpp:11084). */
        addDeadPlayer: (Player) -> Unit = {},
        /** `g_game().removeDeadPlayer(getName())` (player.cpp:11086). */
        removeDeadPlayer: (String) -> Unit = {},
    ) {
        mIsDead = isDead
        if (isDead) {
            addDeadPlayer(player)
        } else {
            removeDeadPlayer(player.name)
        }
    }

    /** `Player::isDead()` (player.cpp:11091-11093). */
    fun isDead(): Boolean = mIsDead

    /**
     * `Player::death` (player.cpp:4035-4284). The two branches mirror the C++
     * `if (skillLoss)` split exactly: the penalty branch computes the unfair
     * fight reduction, magic-level, experience, skill and level losses, strips
     * the blessings and despawns; the no-loss branch only clears the persistent
     * conditions and respawns at the temple.
     */
    fun death(lastHitCreature: Creature?) {
        if (!toggleMountInPz && isMounted()) {
            dismount()
            internalCreatureChangeOutfit(player, player.getDefaultOutfit())
        }

        setLoginPosition(getTemplePosition())

        sendSingleSoundEffect(
            player.position,
            if (player.getSex() == PlayerSex_t.FEMALE) SoundEffect.HUMAN_FEMALE_DEATH else SoundEffect.HUMAN_MALE_DEATH,
            player,
        )

        if (getSkillLoss()) {
            applySkillLossDeath(lastHitCreature)
            despawn()
        } else {
            applyNoSkillLossDeath()
        }

        if (player.getPlayerVocationEnum() == Vocation.VOCATION_MONK_CIP) {
            player.emptyHarmony()
        }
    }

    /** The `if (skillLoss)` body of `Player::death` (player.cpp:4043-4250). */
    private fun applySkillLossDeath(lastHitCreature: Creature?) {
        val vocation = getVocation()

        var playerDmg = 0
        var othersDmg = 0
        var sumLevels = 0
        val inFightTicks = 5L * 60L * 1000L
        for ((creatureId, damageInfo) in getDamageMap()) {
            if ((now() - damageInfo.ticks) <= inFightTicks) {
                val damageDealer = getPlayerById(creatureId)
                if (damageDealer != null) {
                    playerDmg += damageInfo.total
                    sumLevels += damageDealer.level
                } else {
                    othersDmg += damageInfo.total
                }
            }
        }

        var pvpDeath = false
        if (playerDmg > 0 || othersDmg > 0) {
            pvpDeath = lastHitIsPlayer(lastHitCreature) ||
                playerDmg / (playerDmg + othersDmg.toDouble()) >= 0.05
        }

        var unfairFightReduction = 100
        if (pvpDeath && sumLevels > player.level) {
            val reduce = player.level / sumLevels.toDouble()
            unfairFightReduction = maxOf(20, floor((reduce * 100) + 0.5).toInt())
        }

        // Magic level loss (player.cpp:4078-4113).
        var sumMana = 0L
        for (i in 1..player.magLevel) {
            sumMana += vocation.getReqMana(i)
        }
        sumMana += manaSpent

        var deathLossPercent = player.getLostPercent() * (unfairFightReduction / 100.0)

        // Charm bless bestiary (player.cpp:4084-4096).
        val charmBlessRaceId = player.features.bestiary.parseRacebyCharm(CharmRune.CHARM_BLESS)
        val charm = getBestiaryCharm(CharmRune.CHARM_BLESS)
        val lastHitMonster = lastHitCreature as? Monster
        if (charm != null && lastHitMonster != null && charmBlessRaceId != 0) {
            val monsterType = getMonsterType(lastHitCreature.name)
            if (monsterType != null && monsterType.info.raceid == charmBlessRaceId) {
                val percentReduction = charm.getChance(player.features.bestiary.getCharmTier(CharmRune.CHARM_BLESS)) / 100
                deathLossPercent -= deathLossPercent * percentReduction
            }
        }

        var lostMana = (sumMana * deathLossPercent).toLong()
        var magLevel = player.magLevel
        while (lostMana > manaSpent && magLevel > 0) {
            lostMana -= manaSpent
            manaSpent = vocation.getReqMana(magLevel)
            magLevel--
        }

        manaSpent -= lostMana
        player.magLevel = magLevel

        val nextReqMana = vocation.getReqMana(magLevel + 1)
        if (nextReqMana > vocation.getReqMana(magLevel)) {
            setMagLevelPercent(Player.getPercentLevel(manaSpent, nextReqMana))
        } else {
            setMagLevelPercent(0.0)
        }

        // Level loss (player.cpp:4115-4172).
        val expLoss = ceil(player.experience * deathLossPercent).toLong()
        logDebug("[death] - experience lost $expLoss")

        eventPlayerOnLoseExperience(player, expLoss)
        executePlayerOnLoseExperienceCallback(player, expLoss)

        player.sendTextMessageTyped(MessageClass.MESSAGE_EVENT_ADVANCE, "You are dead.")
        val lostExp = "You lost $expLoss experience."

        // Skill loss (player.cpp:4123-4139).
        for (skillId in SKILL_FIRST..SKILL_LAST) {
            val skill = skills[skillId]
            var sumSkillTries = 0L
            for (level in 11..skill.level) {
                sumSkillTries += vocation.getReqSkillTries(skillId, level)
            }

            sumSkillTries += skill.tries

            var lostSkillTries = (sumSkillTries * deathLossPercent).toLong() and UINT32_MAX
            while (lostSkillTries > skill.tries) {
                lostSkillTries -= skill.tries

                if (skill.level <= 10) {
                    skill.level = 10
                    skill.tries = 0
                    lostSkillTries = 0
                    break
                }

                skill.tries = vocation.getReqSkillTries(skillId, skill.level)
                skill.level--
            }

            skill.tries = maxOf(0L, skill.tries - lostSkillTries)
            skill.percent = Player.getPercentLevel(skill.tries, vocation.getReqSkillTries(skillId, skill.level))
        }

        player.sendTextMessageTyped(MessageClass.MESSAGE_EVENT_ADVANCE, lostExp)

        if (expLoss != 0L) {
            val oldLevel = player.level

            if (vocation.getId() == Vocation.VOCATION_NONE || player.level > 7) {
                player.experience -= expLoss
            }

            var level = player.level
            while (level > 1 && player.experience < Player.getExpForLevel(level)) {
                --level
                player.maxHealth = maxOf(0, player.maxHealth - vocation.getHPGain())
                player.maxMana = maxOf(0, player.maxMana - vocation.getManaGain())
                player.capacity = maxOf(0, (player.capacity - vocation.getCapGain()).toInt()).toDouble()
            }
            player.level = level

            if (oldLevel != level) {
                player.sendTextMessageTyped(
                    MessageClass.MESSAGE_EVENT_ADVANCE,
                    "You were downgraded from Level $oldLevel to Level $level.",
                )
            }

            val currLevelExp = Player.getExpForLevel(level)
            val nextLevelExp = Player.getExpForLevel(level + 1)
            if (nextLevelExp > currLevelExp) {
                setLevelPercent(
                    Player.getPercentLevel(player.experience - currLevelExp, nextLevelExp - currLevelExp).toInt(),
                )
            } else {
                setLevelPercent(0)
            }
        }

        player.sendTextMessageTyped(
            MessageClass.MESSAGE_EVENT_ADVANCE,
            if (pvpDeath) "You died during PvP." else "You died during PvE.",
        )

        // Blessings (player.cpp:4180-4221).
        val willNotLoseBless = player.level < adventurersBlessingLevel() &&
            player.getVocationId() > Vocation.VOCATION_NONE
        val bless = player.getBlessingsName()

        if (willNotLoseBless) {
            player.sendTextMessageTyped(
                MessageClass.MESSAGE_EVENT_ADVANCE,
                "You still have adventurer's blessings for being level lower than ${adventurersBlessingLevel()}!",
            )
        } else {
            player.sendTextMessageTyped(
                MessageClass.MESSAGE_EVENT_ADVANCE,
                if (bless.isEmpty()) {
                    "You weren't protected with any blessings."
                } else {
                    "You were blessed with $bless"
                },
            )

            val playerSkull = player.getSkull()
            val hasSkull = playerSkull == Skulls_t.SKULL_RED || playerSkull == Skulls_t.SKULL_BLACK
            if (!hasSkull && pvpDeath && player.hasBlessing(1)) {
                player.removeBlessing(1, 1) // Remove TOF only
            } else {
                for (i in 2..MAX_BLESSING) {
                    player.removeBlessing(i, 1)
                }

                val playerAmulet = player.getInventoryItem(Slots_t.CONST_SLOT_NECKLACE)
                val usingAol = playerAmulet != null && playerAmulet.id == ITEM_AMULETOFLOSS
                if (usingAol) {
                    removeItemOfType(ITEM_AMULETOFLOSS, 1, -1)
                }
            }
        }

        sendStats()
        player.sendSkills()
        sendReLoginWindow(unfairFightReduction)
        player.sendBlessStatus()
        setLastLogout(getTimeNow())
        if (player.getSkull() == Skulls_t.SKULL_BLACK) {
            player.health = 40
            player.mana = 0
        } else {
            player.health = player.maxHealth
            player.mana = player.maxMana
        }

        for (condition in player.conditions.toList()) {
            if (condition.isPersistent() && condition.isRemovableOnDeath()) {
                player.removeCondition(condition)
            }
        }
    }

    /** The `else` body of `Player::death` (player.cpp:4253-4279). */
    private fun applyNoSkillLossDeath() {
        setSkillLoss(true)

        for (condition in player.conditions.toList()) {
            if (condition.isPersistent()) {
                player.removeCondition(condition)
            }
        }

        player.health = player.maxHealth
        internalTeleport(player, getTemplePosition(), true)
        addCreatureHealth(player)
        addPlayerMana(player)
        player.onThink(EVENT_CREATURE_THINK_INTERVAL)
        player.onIdleStatus()
        sendStats()
    }

    companion object {
        /** `SKILL_FIRST = SKILL_FIST` (creatures_definitions.hpp:466). */
        private const val SKILL_FIRST = 0

        /** `SKILL_LAST = SKILL_MANA_LEECH_AMOUNT` (creatures_definitions.hpp:486). */
        private const val SKILL_LAST = 12

        /** `maxBlessing` (player.cpp:4208). */
        private const val MAX_BLESSING = 8

        /** `std::numeric_limits<uint32_t>::max()` for the `uint32_t` cast. */
        private const val UINT32_MAX = 0xFFFF_FFFFL

        /** `ItemId_t::ITEM_AMULETOFLOSS` (utils_definitions.hpp:638). */
        const val ITEM_AMULETOFLOSS = 3057

        /** `EVENT_CREATURE_THINK_INTERVAL` (creature.hpp:47). */
        const val EVENT_CREATURE_THINK_INTERVAL = 1000

        /** Unwired `Player::vocation` fallback (player.hpp:1755). */
        private val unwiredVocation = Vocation(Vocation.VOCATION_NONE)
    }
}
