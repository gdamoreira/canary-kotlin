package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.combat.CombatOrigin
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.bestiary.Charm
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.wheel.PlayerWheel
import org.opentibia.canary.systems.wheel.WheelStage

/**
 * Transcript tests for the `Game::` combat state-change pipeline:
 *
 *  - `Game::applyHealthChange` (src/game/game.cpp:8327)
 *  - `Game::combatChangeHealth` (src/game/game.cpp:8344)
 *  - `Game::combatChangeMana` (src/game/game.cpp:9063)
 *  - `Game::applyLifeLeech` / `applyManaLeech` / `calculateLeechAmount`
 *    (src/game/game.cpp:8998-9061)
 *  - `Game::applyPvPDamage` / `pvpLevelDifferenceDamageMultiplier`
 *    (src/game/game.cpp:8232-8260)
 *  - `Game::updatePlayerPartyHuntAnalyzer` (src/game/game.cpp:8773)
 *  - `Game::handleHazardSystemAttack` (src/game/game.cpp:8193)
 *  - `Game::addMagicEffect` (src/game/game.cpp:7802)
 *  - the `PlayerWheel::getGiftOfLife*` accessors
 *    (src/creatures/players/components/wheel/player_wheel.cpp:3945-3996)
 *
 * The protocol/event/host interactions (`sendTextMessage`, magic-effect
 * senders, creature events, bestiary charms, hazard parsing) are injected
 * seams, following the established `Game` facade pattern.
 */
class GameCombatPipelineTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.player(id: Long, name: String, health: Int = 150, maxHealth: Int = 150): Player =
        Player(id = id, name = name, maxHealth = maxHealth, health = health, position = center).also { addPlayer(it) }

    private fun Game.placedPlayer(id: Long, name: String, health: Int = 150, maxHealth: Int = 150): Player {
        map.getOrCreateTile(center).ground = Item(id = 100)
        return Player(id = id, name = name, maxHealth = maxHealth, health = health, position = center).also {
            addPlayer(it)
            internalPlaceCreature(it, center)
        }
    }

    private fun monster(id: Long, health: Int, maxHealth: Int = 100): Monster =
        Monster(id = id, name = "Target$id", maxHealth = maxHealth, health = health, position = Position(1001, 1000, 7))

    private data class SentMessage(val player: Player, val type: MessageClass, val text: String)

    // ----- calculateLeechAmount -----

    @Test
    fun `calculateLeechAmount applies the skill fraction on a single target`() {
        val game = newGame()

        assertThat(game.calculateLeechAmount(100, 10000, 1)).isEqualTo(100)
        assertThat(game.calculateLeechAmount(100, 5000, 1)).isEqualTo(50)
        assertThat(game.calculateLeechAmount(200, 2500, 1)).isEqualTo(50)
    }

    @Test
    fun `calculateLeechAmount scales down with additional affected targets`() {
        val game = newGame()

        assertThat(game.calculateLeechAmount(100, 10000, 2)).isEqualTo(55)
        assertThat(game.calculateLeechAmount(100, 10000, 10)).isEqualTo(19)
    }

    @Test
    fun `calculateLeechAmount clamps to zero and to the real damage`() {
        val game = newGame()

        assertThat(game.calculateLeechAmount(100, 0, 1)).isZero()
        assertThat(game.calculateLeechAmount(100, 30000, 1)).isEqualTo(100)
    }

    // ----- applyHealthChange / Gift of Life -----

    @Test
    fun `applyHealthChange returns the target health for non-player targets`() {
        val game = newGame()
        val target = monster(2, health = 60)

        assertThat(game.applyHealthChange(CombatDamage(primaryValue = 90), target)).isEqualTo(60)
    }

    @Test
    fun `applyHealthChange triggers the gift inside the overkill threshold and heals`() {
        val game = newGame()
        val target = game.player(1, "Hero", health = 20)
        target.wheel().setStage(WheelStage.GIFT_OF_LIFE, 3)

        val damage = CombatDamage(primaryValue = 25)
        assertThat(game.applyHealthChange(damage, target)).isEqualTo(65)
    }

    @Test
    fun `applyHealthChange skips the gift above the threshold`() {
        val game = newGame()
        val target = game.player(1, "Hero", health = 20)
        target.wheel().setStage(WheelStage.GIFT_OF_LIFE, 3)

        val damage = CombatDamage(primaryValue = 100)
        assertThat(game.applyHealthChange(damage, target)).isEqualTo(20)
    }

    @Test
    fun `applyHealthChange skips the gift while the cooldown is armed`() {
        val game = newGame()
        val target = game.player(1, "Hero", health = 20)
        target.wheel().setStage(WheelStage.GIFT_OF_LIFE, 3)
        target.wheel().setGiftOfCooldown(100, true)

        val damage = CombatDamage(primaryValue = 25)
        assertThat(game.applyHealthChange(damage, target)).isEqualTo(20)
    }

    @Test
    fun `applyHealthChange skips the gift without the stage`() {
        val game = newGame()
        val target = game.player(1, "Hero", health = 20)

        val damage = CombatDamage(primaryValue = 25)
        assertThat(game.applyHealthChange(damage, target)).isEqualTo(20)
    }

    @Test
    fun `checkGiftOfLife heals by the stage fraction and arms the total cooldown`() {
        val game = newGame()
        val target = game.player(1, "Hero", health = 20)
        target.wheel().setStage(WheelStage.GIFT_OF_LIFE, 3)

        game.checkGiftOfLife(target)

        assertThat(target.health).isEqualTo(65)
        assertThat(target.wheel().getGiftOfCooldown()).isEqualTo(36000)
    }

    // ----- combatChangeHealth: healing -----

    @Test
    fun `combatChangeHealth heals a living target and caps at max health`() {
        val game = newGame()
        val target = game.player(1, "Hero", health = 100)

        val damage = CombatDamage(primaryValue = 40, primaryType = CombatType.HEALING)
        assertThat(game.combatChangeHealth(null, target, damage)).isTrue()
        assertThat(target.health).isEqualTo(140)

        val capped = CombatDamage(primaryValue = 40, primaryType = CombatType.HEALING)
        game.combatChangeHealth(null, target, capped)
        assertThat(target.health).isEqualTo(150)
    }

    @Test
    fun `combatChangeHealth refuses to heal a dead target`() {
        val game = newGame()
        val target = game.player(1, "Hero", health = 0)

        val damage = CombatDamage(primaryValue = 40, primaryType = CombatType.HEALING)
        assertThat(game.combatChangeHealth(null, target, damage)).isFalse()
        assertThat(target.health).isZero()
    }

    @Test
    fun `combatChangeHealth sends the heal message through the text seam`() {
        val game = newGame()
        val target = game.placedPlayer(1, "Hero", health = 100)
        val messages = mutableListOf<SentMessage>()
        game.sendTextMessage = { player, type, text -> messages.add(SentMessage(player, type, text)) }

        val damage = CombatDamage(primaryValue = 40, primaryType = CombatType.HEALING)
        game.combatChangeHealth(null, target, damage)

        val message = messages.single()
        assertThat(message.player).isSameAs(target)
        assertThat(message.type).isEqualTo(MessageClass.MESSAGE_HEALED)
        assertThat(message.text).isEqualTo("You were healed for 40 hitpoints.")
    }

    // ----- combatChangeHealth: damage -----

    @Test
    fun `combatChangeHealth drains the target health`() {
        val game = newGame()
        val target = monster(2, health = 100)

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.PHYSICALDAMAGE)
        assertThat(game.combatChangeHealth(null, target, damage)).isTrue()
        assertThat(target.health).isEqualTo(70)
    }

    @Test
    fun `combatChangeHealth never drains below zero`() {
        val game = newGame()
        val target = monster(2, health = 20)

        val damage = CombatDamage(primaryValue = -50, primaryType = CombatType.PHYSICALDAMAGE)
        assertThat(game.combatChangeHealth(null, target, damage)).isTrue()
        assertThat(target.health).isZero()
    }

    @Test
    fun `combatChangeHealth applies the poff effect to a non-attackable target`() {
        val game = newGame()
        game.placedPlayer(1, "Observer")
        val target = monster(2, health = 100)
        game.isAttackable = { false }
        val effects = mutableListOf<Pair<Position, Int>>()
        game.sendMagicEffect = { _, position, effect -> effects.add(position to effect) }

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.PHYSICALDAMAGE)
        assertThat(game.combatChangeHealth(null, target, damage)).isTrue()
        assertThat(target.health).isEqualTo(100)
        assertThat(effects).containsExactly(target.position to Game.CONST_ME_POFF)
    }

    @Test
    fun `combatChangeHealth returns false for a black-skulled attacker against an unmarked target`() {
        val game = newGame()
        val attacker = game.player(1, "Killer")
        val target = game.player(2, "Victim")
        attacker.setSkull(Skulls_t.SKULL_BLACK)
        game.getSkullClient = { _, _ -> Skulls_t.SKULL_NONE }

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.PHYSICALDAMAGE)
        assertThat(game.combatChangeHealth(attacker, target, damage)).isFalse()
        assertThat(target.health).isEqualTo(150)
    }

    @Test
    fun `combatChangeHealth skips the skull gate for a marked target`() {
        val game = newGame()
        val attacker = game.player(1, "Killer")
        val target = game.player(2, "Victim")
        attacker.setSkull(Skulls_t.SKULL_BLACK)
        game.getSkullClient = { _, _ -> Skulls_t.SKULL_YELLOW }

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.PHYSICALDAMAGE)
        assertThat(game.combatChangeHealth(attacker, target, damage)).isTrue()
        assertThat(target.health).isEqualTo(120)
    }

    @Test
    fun `combatChangeHealth re-enters after the creature health-change events consume the origin`() {
        val game = newGame()
        val target = monster(2, health = 100)
        var executed = 0
        game.executeCreatureHealthChangeEvents = { _, _, _ ->
            executed++
            true
        }
        game.sendMagicEffect = { _, _, _ -> }

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.PHYSICALDAMAGE, origin = CombatOrigin.SPELL)
        game.combatChangeHealth(null, target, damage)

        assertThat(executed).isEqualTo(1)
        assertThat(damage.origin).isEqualTo(CombatOrigin.NONE)
        assertThat(target.health).isEqualTo(70)
    }

    @Test
    fun `combatChangeHealth applies the damage-dealt buff percentage`() {
        val game = newGame()
        val attacker = monster(1, health = 100)
        val target = monster(2, health = 100)
        attacker.setBuff(Buff.BUFF_DAMAGEDEALT, -50)

        val damage = CombatDamage(primaryValue = -100, primaryType = CombatType.PHYSICALDAMAGE, origin = CombatOrigin.SPELL)
        game.combatChangeHealth(attacker, target, damage)

        assertThat(target.health).isEqualTo(50)
    }

    @Test
    fun `combatChangeHealth applies the damage-received buff percentage`() {
        val game = newGame()
        val target = monster(2, health = 100)
        target.setBuff(Buff.BUFF_DAMAGERECEIVED, -50)

        val damage = CombatDamage(primaryValue = -100, primaryType = CombatType.PHYSICALDAMAGE, origin = CombatOrigin.SPELL)
        game.combatChangeHealth(null, target, damage)

        assertThat(target.health).isEqualTo(50)
    }

    @Test
    fun `combatChangeHealth applies the custom PvP multipliers between players`() {
        val game = newGame()
        val attacker = game.player(1, "Attacker")
        val target = game.player(2, "Target", health = 1000, maxHealth = 1000)
        game.getVocation = { player ->
            Vocation(player.getVocationId()).apply {
                pvpDamageDealtMultiplier = if (player.id == 1L) 1.5f else 1.0f
                pvpDamageReceivedMultiplier = if (player.id == 2L) 2.0f else 1.0f
            }
        }

        val damage = CombatDamage(primaryValue = -100, primaryType = CombatType.PHYSICALDAMAGE)
        game.combatChangeHealth(attacker, target, damage)

        assertThat(target.health).isEqualTo(700)
    }

    // ----- combatChangeMana -----

    @Test
    fun `combatChangeMana restores mana on a positive change`() {
        val game = newGame()
        val target = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 200,
            mana = 50,
            position = center,
        )
        game.addPlayer(target)

        val damage = CombatDamage(primaryValue = 30)
        assertThat(game.combatChangeMana(null, target, damage)).isTrue()
        assertThat(target.mana).isEqualTo(80)
    }

    @Test
    fun `combatChangeMana drains mana on a manadrain change`() {
        val game = newGame()
        val target = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 200,
            mana = 50,
            position = center,
        )
        game.addPlayer(target)

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.MANADRAIN)
        assertThat(game.combatChangeMana(null, target, damage)).isTrue()
        assertThat(target.mana).isEqualTo(20)
    }

    @Test
    fun `combatChangeMana never drains below zero`() {
        val game = newGame()
        val target = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 200,
            mana = 10,
            position = center,
        )
        game.addPlayer(target)

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.MANADRAIN)
        game.combatChangeMana(null, target, damage)
        assertThat(target.mana).isZero()
    }

    @Test
    fun `combatChangeMana credits the attacker on a manadrain change`() {
        // game.cpp:9226 `target->drainMana(attacker, manaLoss)` ->
        // Creature::drainMana (creature.cpp:901-907) -> addDamagePoints
        // (creature.cpp:1195-1211).
        val game = newGame()
        val attacker = game.player(9, "Mage")
        val target = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 200,
            mana = 50,
            position = center,
        )
        game.addPlayer(target)

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.MANADRAIN)
        assertThat(game.combatChangeMana(attacker, target, damage)).isTrue()
        assertThat(target.mana).isEqualTo(20)
        assertThat(target.getDamageMap()[9L]?.total).isEqualTo(30)
        assertThat(target.lastHitCreatureId).isEqualTo(9L)
    }

    @Test
    fun `combatChangeMana leaves a casterless drain unattributed`() {
        val game = newGame()
        val target = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 200,
            mana = 50,
            position = center,
        )
        game.addPlayer(target)

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.MANADRAIN)
        assertThat(game.combatChangeMana(null, target, damage)).isTrue()
        assertThat(target.mana).isEqualTo(20)
        assertThat(target.getDamageMap()).isEmpty()
        assertThat(target.lastHitCreatureId).isZero()
    }

    @Test
    fun `combatChangeMana clamps the credited loss to the available mana`() {
        // game.cpp:9171 `manaLoss = min(target->getMana(), -manaChange)`.
        val game = newGame()
        val attacker = game.player(9, "Mage")
        val target = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 200,
            mana = 10,
            position = center,
        )
        game.addPlayer(target)

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.MANADRAIN)
        assertThat(game.combatChangeMana(attacker, target, damage)).isTrue()
        assertThat(target.mana).isZero()
        assertThat(target.getDamageMap()[9L]?.total).isEqualTo(10)
    }

    @Test
    fun `combatChangeMana routes the drain through the player drainMana override`() {
        // Player::drainMana (player.cpp:3549-3552) over Creature::drainMana.
        // The virtual `Player::changeMana` (player.cpp:6765-6772) refreshes the
        // stats once and `Player::drainMana`'s tail refreshes them again,
        // exactly like the C++ flow.
        val game = newGame()
        val attacker = game.player(9, "Mage")
        val target = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 200,
            mana = 50,
            position = center,
        )
        game.addPlayer(target)
        var stats = 0
        target.sendStats = { stats++ }

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.MANADRAIN)
        game.combatChangeMana(attacker, target, damage)

        assertThat(stats).isEqualTo(2)
    }

    @Test
    fun `combatChangeMana refuses to drain an unattackable target`() {
        // game.cpp:9162-9168: `!target->isAttackable()` -> POFF, return false.
        val game = newGame()
        val attacker = game.player(9, "Mage")
        val target = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 200,
            mana = 50,
            position = center,
        )
        game.addPlayer(target)
        game.isAttackable = { false }

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.MANADRAIN)
        assertThat(game.combatChangeMana(attacker, target, damage)).isFalse()
        assertThat(target.mana).isEqualTo(50)
        assertThat(target.getDamageMap()).isEmpty()
    }

    @Test
    fun `combatChangeMana blocks the drain of a manadrain-immune target`() {
        // game.cpp:9177-9181: `target->blockHit(attacker, COMBAT_MANADRAIN,
        // manaLoss)` -> `BLOCK_IMMUNITY` -> POFF, return false.
        val game = newGame()
        val attacker = game.player(9, "Mage")
        val target = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 200,
            mana = 50,
            position = center,
        )
        game.addPlayer(target)
        game.combatIsImmune = { _, combatType -> combatType == CombatType.MANADRAIN }

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.MANADRAIN)
        assertThat(game.combatChangeMana(attacker, target, damage)).isFalse()
        assertThat(target.mana).isEqualTo(50)
        assertThat(target.getDamageMap()).isEmpty()
    }

    @Test
    fun `combatChangeMana inverts a manadrain when the void inversion charm procs`() {
        // game.cpp:9078-9094: `parseRacebyCharm(CHARM_VOIDINVERSION)` matching
        // the attacker monster's raceid plus a passing
        // `charm->chance[tier] > normal_random(0, 100)` roll inverts the
        // COMBAT_MANADRAIN components, turning the drain into a restore.
        val game = newGame()
        val attacker = monster(9, health = 100)
        val target = game.player(1, "Hero", health = 150)
        target.mana = 50
        target.maxMana = 200
        target.features.bestiary.setCharmTier(CharmRune.CHARM_VOIDINVERSION, 0)
        target.features.bestiary.parseRacebyCharm(CharmRune.CHARM_VOIDINVERSION, set = true, newRaceid = 42)
        game.getMonsterTypeByName = { name -> MonsterType(name).apply { info.raceid = 42 } }
        game.getBestiaryCharm = { rune ->
            if (rune == CharmRune.CHARM_VOIDINVERSION) {
                Charm(id = CharmRune.CHARM_VOIDINVERSION, chance = listOf(100.0))
            } else {
                null
            }
        }
        game.normalRandom = { min, _ -> min }

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.MANADRAIN)
        assertThat(game.combatChangeMana(attacker, target, damage)).isTrue()

        assertThat(damage.primaryValue).isEqualTo(30)
        assertThat(target.mana).isEqualTo(80)
        assertThat(target.getDamageMap()).isEmpty()
        assertThat(target.lastHitCreatureId).isZero()
    }

    @Test
    fun `combatChangeMana skips the inversion when the charm roll misses`() {
        // game.cpp:9089: `charm->chance[charmTier] > normal_random(0, 100)`
        // must pass for the inversion; a failing roll keeps the drain.
        val game = newGame()
        val attacker = monster(9, health = 100)
        val target = game.player(1, "Hero", health = 150)
        target.mana = 50
        target.maxMana = 200
        target.features.bestiary.setCharmTier(CharmRune.CHARM_VOIDINVERSION, 0)
        target.features.bestiary.parseRacebyCharm(CharmRune.CHARM_VOIDINVERSION, set = true, newRaceid = 42)
        game.getMonsterTypeByName = { name -> MonsterType(name).apply { info.raceid = 42 } }
        game.getBestiaryCharm = { rune ->
            if (rune == CharmRune.CHARM_VOIDINVERSION) {
                Charm(id = CharmRune.CHARM_VOIDINVERSION, chance = listOf(100.0))
            } else {
                null
            }
        }
        game.normalRandom = { _, max -> max }

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.MANADRAIN)
        assertThat(game.combatChangeMana(attacker, target, damage)).isTrue()

        assertThat(damage.primaryValue).isEqualTo(-30)
        assertThat(target.mana).isEqualTo(20)
        assertThat(target.getDamageMap()[9L]?.total).isEqualTo(30)
    }

    @Test
    fun `combatChangeMana skips the inversion when the charm raceid does not match`() {
        // game.cpp:9082-9084: `playerCharmRaceid == mType->info.raceid`.
        val game = newGame()
        val attacker = monster(9, health = 100)
        val target = game.player(1, "Hero", health = 150)
        target.mana = 50
        target.maxMana = 200
        target.features.bestiary.setCharmTier(CharmRune.CHARM_VOIDINVERSION, 0)
        target.features.bestiary.parseRacebyCharm(CharmRune.CHARM_VOIDINVERSION, set = true, newRaceid = 42)
        game.getMonsterTypeByName = { name -> MonsterType(name).apply { info.raceid = 7 } }
        game.getBestiaryCharm = { rune ->
            if (rune == CharmRune.CHARM_VOIDINVERSION) {
                Charm(id = CharmRune.CHARM_VOIDINVERSION, chance = listOf(100.0))
            } else {
                null
            }
        }
        game.normalRandom = { min, _ -> min }

        val damage = CombatDamage(primaryValue = -30, primaryType = CombatType.MANADRAIN)
        assertThat(game.combatChangeMana(attacker, target, damage)).isTrue()

        assertThat(damage.primaryValue).isEqualTo(-30)
        assertThat(target.mana).isEqualTo(20)
    }

    @Test
    fun `combatChangeMana re-enters after the creature mana-change events consume the origin`() {
        // game.cpp:9185-9194: the CREATURE_EVENT_MANACHANGE run resets the
        // origin and recurses.
        val game = newGame()
        val attacker = game.player(9, "Mage")
        val target = Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 200,
            mana = 50,
            position = center,
        )
        game.addPlayer(target)
        var executed = 0
        game.executeCreatureManaChangeEvents = { _, _, _ ->
            executed++
            true
        }

        val damage = CombatDamage(
            primaryValue = -30,
            primaryType = CombatType.MANADRAIN,
            origin = CombatOrigin.SPELL,
        )
        assertThat(game.combatChangeMana(attacker, target, damage)).isTrue()

        assertThat(executed).isEqualTo(1)
        assertThat(damage.origin).isEqualTo(CombatOrigin.NONE)
        assertThat(target.mana).isEqualTo(20)
        assertThat(target.getDamageMap()[9L]?.total).isEqualTo(30)
    }

    // ----- leech -----

    @Test
    fun `applyLifeLeech heals the attacker by the calculated fraction`() {
        val game = newGame()
        val attacker = game.player(1, "Knight", health = 40)
        val target = monster(2, health = 100)

        game.applyLifeLeech(attacker, target, target, CombatDamage(lifeLeech = 10000), 100)

        assertThat(attacker.health).isEqualTo(140)
    }

    @Test
    fun `applyLifeLeech skips a zero skill`() {
        val game = newGame()
        val attacker = game.player(1, "Knight", health = 40)
        val target = monster(2, health = 100)

        game.applyLifeLeech(attacker, target, target, CombatDamage(), 100)

        assertThat(attacker.health).isEqualTo(40)
    }

    @Test
    fun `applyManaLeech restores the attacker mana by the calculated fraction`() {
        val game = newGame()
        val attacker = Player(
            id = 1,
            name = "Sorcerer",
            maxHealth = 150,
            health = 150,
            maxMana = 200,
            mana = 10,
            position = center,
        )
        game.addPlayer(attacker)
        val target = monster(2, health = 100)

        game.applyManaLeech(attacker, target, target, CombatDamage(manaLeech = 10000), 100)

        assertThat(attacker.mana).isEqualTo(110)
    }

    // ----- party hunt analyzer -----

    @Test
    fun `updatePlayerPartyHuntAnalyzer forwards both damage components`() {
        val game = newGame()
        val player = game.player(1, "Druid")
        Party(player)
        val forwarded = mutableListOf<Pair<Player, Int>>()
        game.addPlayerDamage = { target, amount -> forwarded.add(target to amount) }

        game.updatePlayerPartyHuntAnalyzer(CombatDamage(primaryValue = 30, secondaryValue = 20), player)

        assertThat(forwarded).containsExactly(player to 30, player to 20)
    }

    @Test
    fun `updatePlayerPartyHuntAnalyzer ignores a player without a party`() {
        val game = newGame()
        val player = game.player(1, "Druid")
        var forwarded = false
        game.addPlayerDamage = { _, _ -> forwarded = true }

        game.updatePlayerPartyHuntAnalyzer(CombatDamage(primaryValue = 30), player)

        assertThat(forwarded).isFalse()
    }

    @Test
    fun `updatePlayerPartyHuntAnalyzer ignores an absent player`() {
        val game = newGame()
        var forwarded = false
        game.addPlayerDamage = { _, _ -> forwarded = true }

        game.updatePlayerPartyHuntAnalyzer(CombatDamage(primaryValue = 30), null)

        assertThat(forwarded).isFalse()
    }

    // ----- magic effects -----

    @Test
    fun `addMagicEffect fans the effect out to the spectator list`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val sent = mutableListOf<Triple<Player, Position, Int>>()
        game.sendMagicEffect = { target, position, effect -> sent.add(Triple(target, position, effect)) }

        game.addMagicEffect(listOf(player), center, Game.CONST_ME_POFF)

        assertThat(sent).containsExactly(Triple(player, center, Game.CONST_ME_POFF))
    }

    @Test
    fun `handleHazardSystemAttack forwards only hazardous monster hits`() {
        val game = newGame()
        val player = game.player(1, "Hero")
        val target = monster(2, health = 100)
        val parsed = mutableListOf<Boolean>()
        game.monsterHasHazard = { true }
        game.parseHazardSystemAttack = { _, _, _, isPlayerAttacker -> parsed.add(isPlayerAttacker) }

        game.handleHazardSystemAttack(CombatDamage(primaryValue = -10), player, target, true)
        game.handleHazardSystemAttack(CombatDamage(primaryValue = 0), player, target, false)
        game.monsterHasHazard = { false }
        game.handleHazardSystemAttack(CombatDamage(primaryValue = -10), player, target, false)

        assertThat(parsed).containsExactly(true)
    }

    // ----- PlayerWheel Gift of Life accessors -----

    @Test
    fun `getGiftOfLifeValue maps the wheel stages`() {
        val wheel = PlayerWheel(vocationCipId = 0, level = 1)

        wheel.setStage(WheelStage.GIFT_OF_LIFE, 1)
        assertThat(wheel.getGiftOfLifeValue()).isEqualTo(20)
        wheel.setStage(WheelStage.GIFT_OF_LIFE, 2)
        assertThat(wheel.getGiftOfLifeValue()).isEqualTo(25)
        wheel.setStage(WheelStage.GIFT_OF_LIFE, 3)
        assertThat(wheel.getGiftOfLifeValue()).isEqualTo(30)
        wheel.setStage(WheelStage.GIFT_OF_LIFE, 0)
        assertThat(wheel.getGiftOfLifeValue()).isZero()
    }

    @Test
    fun `getGiftOfLifeTotalCooldown maps the wheel stages`() {
        val wheel = PlayerWheel(vocationCipId = 0, level = 1)

        wheel.setStage(WheelStage.GIFT_OF_LIFE, 1)
        assertThat(wheel.getGiftOfLifeTotalCooldown()).isEqualTo(108000)
        wheel.setStage(WheelStage.GIFT_OF_LIFE, 2)
        assertThat(wheel.getGiftOfLifeTotalCooldown()).isEqualTo(72000)
        wheel.setStage(WheelStage.GIFT_OF_LIFE, 3)
        assertThat(wheel.getGiftOfLifeTotalCooldown()).isEqualTo(36000)
        wheel.setStage(WheelStage.GIFT_OF_LIFE, 0)
        assertThat(wheel.getGiftOfLifeTotalCooldown()).isZero()
    }

    @Test
    fun `gift cooldown stores the value and decreases in place`() {
        val wheel = PlayerWheel(vocationCipId = 0, level = 1)

        assertThat(wheel.getGiftOfCooldown()).isZero()
        wheel.setGiftOfCooldown(100, true)
        assertThat(wheel.getGiftOfCooldown()).isEqualTo(100)
        wheel.decreaseGiftOfCooldown(40)
        assertThat(wheel.getGiftOfCooldown()).isEqualTo(60)
    }
}
