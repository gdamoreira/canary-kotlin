package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.combat.BlockType
import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.players.components.PlayerAsyncTask
import org.opentibia.canary.database.Database
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.SqlDialect
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.protocol.cyclopedia.CyclopediaCharacterWriter
import org.opentibia.canary.network.protocol.cyclopedia.CyclopediaWindowWriter
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.systems.bestiary.Charm
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.bestiary.CharmType
import org.opentibia.canary.systems.bestiary.PlayerCharmsByMonster
import java.io.File
import java.util.Calendar

/**
 * Transcript tests for the final `Game` members:
 *
 *  - `Game::loadBoostedCreature` / `setBoostedName` / `getBoostedMonsterName`
 *    (game.cpp:785-860, game.hpp:463-471)
 *  - `Game::combatBlockHit` (game.cpp:7874-8070) and the
 *    `InternalGame::sendBlockEffect` fanout (game.cpp:413-453)
 *  - `Game::playerCyclopediaCharacterInfo` (game.cpp:9823-9888)
 *  - `Game::loadMainMap` / `loadCustomMaps` / `loadMap`
 *    (game.cpp:1058-1113) and the `Map::loadMap`/`loadMapCustom`/
 *    `loadHouseInfo` boundaries
 *  - `Game::loadAppearanceProtobuf` (game.cpp:1657-1708)
 *
 * The io-owned loaders (OTBM/XML, `appearances.dat` parsing, database reads)
 * are injected seams, following the established `Game` facade pattern.
 */
class GameLastSevenTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.ground(position: Position = center) {
        map.getOrCreateTile(position).ground = Item(id = 100)
    }

    private fun Game.placedPlayer(
        id: Long = 1,
        name: String = "Hero",
        position: Position = center,
    ): Player {
        ground(position)
        return Player(id = id, name = name, maxHealth = 150, health = 150, position = position).also {
            addPlayer(it)
            internalPlaceCreature(it, position)
        }
    }

    private fun monster(id: Long = 2, position: Position = center): Monster =
        Monster(id = id, name = "Rat$id", maxHealth = 100, health = 100, position = position)

    private data class SentMessage(val player: Player, val type: MessageClass, val text: String)

    private data class SentEffect(val player: Player, val position: Position, val effect: Int)

    private data class BlockHitCall(
        val attacker: Creature?,
        val target: Creature,
        val combatType: CombatType,
        val damage: Int,
        val checkDefense: Boolean,
        val checkArmor: Boolean,
        val field: Boolean,
    )

    // ----- loadBoostedCreature -----

    @Test
    fun `loadBoostedCreature keeps the stored creature when the date matches`() {
        val game = newGame()
        game.getTimeNow = { 1_700_000_000L }
        val today = Calendar.getInstance().also { it.timeInMillis = 1_700_000_000L * 1000 }.get(Calendar.DAY_OF_MONTH)
        game.storeBoostedCreature = {
            BoostedCreatureRecord(today, "demon", 2, 35, 0, 0, 0, 0, 0, 0)
        }
        val updates = mutableListOf<String>()
        game.updateBoostedCreature = { updates.add(it); true }

        game.loadBoostedCreature()

        assertThat(game.getBoostedMonsterName()).isEqualTo("demon")
        assertThat(updates).isEmpty()
    }

    @Test
    fun `loadBoostedCreature resets the name and skips the update without a stored row`() {
        val game = newGame()
        game.setBoostedName("old")
        val updates = mutableListOf<String>()
        game.updateBoostedCreature = { updates.add(it); true }

        game.loadBoostedCreature()

        assertThat(game.getBoostedMonsterName()).isEqualTo("old")
        assertThat(updates).isEmpty()
    }

    @Test
    fun `loadBoostedCreature picks another race and persists its outfit`() {
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("boosted_${System.nanoTime()}")
        try {
            val game = Game(GameMap(), GameDispatcher(), database = Database(dataSource))
            game.getTimeNow = { 1_700_000_000L }
            val today = Calendar.getInstance().also { it.timeInMillis = 1_700_000_000L * 1000 }.get(Calendar.DAY_OF_MONTH)
            game.storeBoostedCreature = {
                BoostedCreatureRecord(today - 1, "rat", 1, 21, 1, 2, 3, 4, 5, 6)
            }
            game.addBestiaryList(1, "rat")
            game.addBestiaryList(2, "demon")
            game.normalRandom = { min, _ -> min }
            val monsterType = MonsterType("demon").also {
                it.info.outfit.lookType = 35
                it.info.outfit.lookHead = 1
                it.info.outfit.lookBody = 2
                it.info.outfit.lookLegs = 3
                it.info.outfit.lookFeet = 4
                it.info.outfit.lookAddons = 5
                it.info.outfit.lookMount = 6
            }
            game.getMonsterTypeByName = { monsterType }
            val updates = mutableListOf<String>()
            game.updateBoostedCreature = { updates.add(it); true }

            game.loadBoostedCreature()

            assertThat(game.getBoostedMonsterName()).isEqualTo("demon")
            assertThat(updates).singleElement().isEqualTo(
                "UPDATE `boosted_creature` SET `date` = '$today',`boostname` = 'demon'," +
                    "`looktype` = 35,`lookfeet` = 4,`looklegs` = 3,`lookhead` = 1,`lookbody` = 2," +
                    "`lookaddons` = 5,`lookmount` = 6,`raceid` = '2'",
            )
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `loadBoostedCreature writes the portable form on PostgreSQL`() {
        val game = Game(GameMap(), GameDispatcher(), sqlDialect = { SqlDialect.POSTGRESQL })
        game.getTimeNow = { 1_700_000_000L }
        val today = Calendar.getInstance().also { it.timeInMillis = 1_700_000_000L * 1000 }.get(Calendar.DAY_OF_MONTH)
        game.storeBoostedCreature = {
            BoostedCreatureRecord(today - 1, "rat", 1, 21, 1, 2, 3, 4, 5, 6)
        }
        game.addBestiaryList(1, "rat")
        game.addBestiaryList(2, "demon")
        game.normalRandom = { min, _ -> min }
        val monsterType = MonsterType("demon").also {
            it.info.outfit.lookType = 35
            it.info.outfit.lookHead = 1
            it.info.outfit.lookBody = 2
            it.info.outfit.lookLegs = 3
            it.info.outfit.lookFeet = 4
            it.info.outfit.lookAddons = 5
            it.info.outfit.lookMount = 6
        }
        game.getMonsterTypeByName = { monsterType }
        val updates = mutableListOf<String>()
        game.updateBoostedCreature = { updates.add(it); true }

        game.loadBoostedCreature()

        assertThat(updates).singleElement().isEqualTo(
            "UPDATE boosted_creature SET \"date\" = '$today',boostname = 'demon'," +
                "looktype = 35,lookfeet = 4,looklegs = 3,lookhead = 1,lookbody = 2," +
                "lookaddons = 5,lookmount = 6,raceid = '2'",
        )
    }

    @Test
    fun `loadBoostedCreature persists the portable statement on H2`() {
        val dataSource = DatabaseConnectionFactory.createInMemoryDataSource("boosted_portable_${System.nanoTime()}")
        try {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "CREATE TABLE boosted_creature (\"date\" INT, boostname VARCHAR(255), looktype INT, " +
                            "lookfeet INT, looklegs INT, lookhead INT, lookbody INT, lookaddons INT, " +
                            "lookmount INT, raceid INT)",
                    )
                    statement.execute(
                        "INSERT INTO boosted_creature VALUES (1, 'rat', 21, 1, 2, 3, 4, 5, 6, 1)",
                    )
                }
            }
            val game = Game(
                GameMap(),
                GameDispatcher(),
                database = Database(dataSource),
                sqlDialect = { SqlDialect.H2 },
            )
            game.getTimeNow = { 1_700_000_000L }
            val today = Calendar.getInstance().also { it.timeInMillis = 1_700_000_000L * 1000 }.get(Calendar.DAY_OF_MONTH)
            game.storeBoostedCreature = {
                BoostedCreatureRecord(today - 1, "rat", 1, 21, 1, 2, 3, 4, 5, 6)
            }
            game.addBestiaryList(1, "rat")
            game.addBestiaryList(2, "demon")
            game.normalRandom = { min, _ -> min }
            val monsterType = MonsterType("demon").also {
                it.info.outfit.lookType = 35
                it.info.outfit.lookHead = 1
                it.info.outfit.lookBody = 2
                it.info.outfit.lookLegs = 3
                it.info.outfit.lookFeet = 4
                it.info.outfit.lookAddons = 5
                it.info.outfit.lookMount = 6
            }
            game.getMonsterTypeByName = { monsterType }

            game.loadBoostedCreature()

            assertThat(game.getBoostedMonsterName()).isEqualTo("demon")
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT boostname, looktype, raceid FROM boosted_creature").use { result ->
                        assertThat(result.next()).isTrue()
                        assertThat(result.getString("boostname")).isEqualTo("demon")
                        assertThat(result.getInt("looktype")).isEqualTo(35)
                        assertThat(result.getInt("raceid")).isEqualTo(2)
                    }
                }
            }
        } finally {
            dataSource.close()
        }
    }

    @Test
    fun `loadBoostedCreature ignores an empty selection`() {
        val game = newGame()
        game.getTimeNow = { 1_700_000_000L }
        val today = Calendar.getInstance().also { it.timeInMillis = 1_700_000_000L * 1000 }.get(Calendar.DAY_OF_MONTH)
        game.storeBoostedCreature = {
            BoostedCreatureRecord(today - 1, "rat", 1, 21, 1, 2, 3, 4, 5, 6)
        }
        game.addBestiaryList(1, "rat")
        val updates = mutableListOf<String>()
        game.updateBoostedCreature = { updates.add(it); true }

        game.loadBoostedCreature()

        assertThat(game.getBoostedMonsterName()).isEmpty()
        assertThat(updates).isEmpty()
    }

    @Test
    fun `loadBoostedCreature ignores an unknown monster type`() {
        val game = newGame()
        game.getTimeNow = { 1_700_000_000L }
        val today = Calendar.getInstance().also { it.timeInMillis = 1_700_000_000L * 1000 }.get(Calendar.DAY_OF_MONTH)
        game.storeBoostedCreature = {
            BoostedCreatureRecord(today - 1, "rat", 1, 21, 1, 2, 3, 4, 5, 6)
        }
        game.addBestiaryList(2, "demon")
        game.normalRandom = { min, _ -> min }
        game.getMonsterTypeByName = { null }
        val updates = mutableListOf<String>()
        game.updateBoostedCreature = { updates.add(it); true }

        game.loadBoostedCreature()

        assertThat(game.getBoostedMonsterName()).isEmpty()
        assertThat(updates).isEmpty()
    }

    // ----- combatBlockHit -----

    @Test
    fun `combatBlockHit returns true when both damage types are none`() {
        val game = newGame()

        assertThat(game.combatBlockHit(CombatDamage(), null, monster(), false, false, false)).isTrue()
    }

    @Test
    fun `combatBlockHit returns true for a ghost-mode player target`() {
        val game = newGame()
        val target = game.placedPlayer().also { it.ghostMode = true }
        val damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -10)

        assertThat(game.combatBlockHit(damage, null, target, true, true, false)).isTrue()
        assertThat(damage.primaryValue).isEqualTo(-10)
    }

    @Test
    fun `combatBlockHit returns false for a positive hit or agony damage`() {
        val game = newGame()
        val target = game.placedPlayer()

        assertThat(
            game.combatBlockHit(
                CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = 10),
                null,
                target,
                true,
                true,
                false,
            ),
        ).isFalse()

        assertThat(
            game.combatBlockHit(
                CombatDamage(primaryType = CombatType.AGONYDAMAGE, primaryValue = -10),
                null,
                target,
                true,
                true,
                false,
            ),
        ).isFalse()
    }

    @Test
    fun `combatBlockHit dodge roll sends the dodge effect message and sound`() {
        val game = newGame()
        val target = game.placedPlayer()
        game.getDodgeChance = { _ -> 100 }
        game.uniformRandom = { _, _ -> 0 }
        val messages = mutableListOf<SentMessage>()
        game.sendTextMessage = { player, type, text -> messages.add(SentMessage(player, type, text)) }
        val effects = mutableListOf<SentEffect>()
        game.sendMagicEffectWithSource = { player, position, effect, _ -> effects.add(SentEffect(player, position, effect)) }
        val sounds = mutableListOf<SoundEffect>()
        target.sendSingleSoundEffect = { _, sound, _ -> sounds.add(sound) }

        assertThat(
            game.combatBlockHit(
                CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -10),
                null,
                target,
                true,
                true,
                false,
            ),
        ).isTrue()

        assertThat(messages).singleElement()
            .satisfies({
                assertThat(it.player).isSameAs(target)
                assertThat(it.type).isEqualTo(MessageClass.MESSAGE_ATTENTION)
                assertThat(it.text).isEqualTo("You dodged an attack.")
            })
        assertThat(effects).singleElement().satisfies({
            assertThat(it.player).isSameAs(target)
            assertThat(it.position).isEqualTo(center)
            assertThat(it.effect).isEqualTo(Game.CONST_ME_DODGE)
        })
        assertThat(sounds).containsExactly(SoundEffect.NO_DAMAGE)
    }

    @Test
    fun `combatBlockHit honours the hazard dodge flag without rolling`() {
        val game = newGame()
        val target = game.placedPlayer()
        var rolled = false
        game.uniformRandom = { _, _ -> rolled = true; 0 }
        val damage = CombatDamage(
            primaryType = CombatType.PHYSICALDAMAGE,
            primaryValue = -10,
            hazardDodge = true,
        )

        assertThat(game.combatBlockHit(damage, null, target, true, true, false)).isTrue()
        assertThat(rolled).isFalse()
    }

    @Test
    fun `combatBlockHit blocks both components with the exact call arguments`() {
        val game = newGame()
        val attacker = game.placedPlayer()
        val target = monster()
        val calls = mutableListOf<BlockHitCall>()
        game.combatTargetBlockHit = { a, t, combatType, damage, checkDefense, checkArmor, field ->
            calls.add(BlockHitCall(a, t, combatType, damage, checkDefense, checkArmor, field))
            0 to BlockType.IMMUNITY
        }
        val effects = mutableListOf<SentEffect>()
        game.sendMagicEffectWithSource = { player, position, effect, _ -> effects.add(SentEffect(player, position, effect)) }
        val damage = CombatDamage(
            primaryType = CombatType.FIREDAMAGE,
            primaryValue = -60,
            secondaryType = CombatType.EARTHDAMAGE,
            secondaryValue = -40,
        )

        assertThat(game.combatBlockHit(damage, attacker, target, true, true, true)).isTrue()

        assertThat(calls).containsExactly(
            BlockHitCall(attacker, target, CombatType.FIREDAMAGE, 60, true, true, true),
            BlockHitCall(attacker, target, CombatType.EARTHDAMAGE, 40, false, false, true),
        )
        assertThat(damage.primaryValue).isZero()
        assertThat(damage.secondaryValue).isZero()
        assertThat(effects.map { it.effect }).containsExactly(CONST_ME_BLOCKHIT, CONST_ME_GREEN_RINGS)
    }

    @Test
    fun `combatBlockHit applies the dealt and received buff multipliers`() {
        val game = newGame()
        val attacker = game.placedPlayer()
        val target = monster()
        attacker.setBuff(Buff.BUFF_DAMAGEDEALT, 100)
        target.setBuff(Buff.BUFF_DAMAGERECEIVED, -50)
        val calls = mutableListOf<BlockHitCall>()
        game.combatTargetBlockHit = { a, t, combatType, damage, checkDefense, checkArmor, field ->
            calls.add(BlockHitCall(a, t, combatType, damage, checkDefense, checkArmor, field))
            0 to BlockType.IMMUNITY
        }

        game.combatBlockHit(
            CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -60),
            attacker,
            target,
            true,
            true,
            false,
        )

        assertThat(calls.single().damage).isEqualTo(60)
    }

    @Test
    fun `combatBlockHit adds the absorb and increase exStrings`() {
        val game = newGame()
        val attacker = game.placedPlayer()
        val target = game.placedPlayer(id = 2, name = "Victim")
        game.getAbsorbPercent = { _, _ -> 5 }
        game.getIncreasePercent = { _, _ -> 10 }
        game.combatTargetBlockHit = { _, _, _, _, _, _, _ -> 0 to BlockType.IMMUNITY }
        val damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -60)

        game.combatBlockHit(damage, attacker, target, true, true, false)

        assertThat(damage.exString).isEqualTo("active elemental resiliance, active elemental amplification")
    }

    @Test
    fun `combatBlockHit heals a monster target by its healing combat value`() {
        val game = newGame()
        val attacker = game.placedPlayer()
        val target = monster().also { it.health = 50 }
        game.getHealingCombatValue = { _, _ -> 30 }
        game.combatTargetBlockHit = { _, _, _, _, _, _, _ -> 0 to BlockType.IMMUNITY }

        game.combatBlockHit(
            CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -60),
            attacker,
            target,
            true,
            true,
            false,
        )

        assertThat(target.health).isEqualTo(68)
    }

    @Test
    fun `combatBlockHit reflects the remaining primary damage to the attacker`() {
        val game = newGame()
        val attacker = game.placedPlayer()
        val target = monster()
        game.getReflectPercent = { _, combatType, _ -> if (combatType == CombatType.FIREDAMAGE) 50.0 else 0.0 }
        game.combatTargetBlockHit = { _, _, _, _, _, _, _ -> 20 to BlockType.ARMOR }
        val reflected = mutableListOf<Pair<Creature, CombatDamage>>()
        game.combatDoCombatHealth = { caster, reflectTarget, damage, params ->
            reflected.add(caster to damage)
            assertThat(params.combatType).isEqualTo(CombatType.FIREDAMAGE)
            assertThat(params.aggressive).isTrue()
            assertThat(reflectTarget).isSameAs(attacker)
        }

        game.combatBlockHit(
            CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -80),
            attacker,
            target,
            true,
            true,
            false,
        )

        val damage = reflected.single().second
        // `std::max(-ceil(150 * 0.01), 0 + ceil(-20 * 50 / 100.))` = max(-2, -10)
        // (game.cpp:7963-7966): the 1%-max-health cap clamps the reflection.
        assertThat(damage.primaryValue).isEqualTo(-2)
        assertThat(damage.primaryType).isEqualTo(CombatType.FIREDAMAGE)
        assertThat(damage.extension).isTrue()
        assertThat(damage.exString).isEqualTo(" (damage reflection)")
    }

    @Test
    fun `combatBlockHit skips the reflection for extension damage`() {
        val game = newGame()
        val attacker = game.placedPlayer()
        val target = monster()
        game.getReflectPercent = { _, _, _ -> 50.0 }
        game.combatTargetBlockHit = { _, _, _, _, _, _, _ -> 20 to BlockType.ARMOR }
        var reflected = false
        game.combatDoCombatHealth = { _, _, _, _ -> reflected = true }

        game.combatBlockHit(
            CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -80, extension = true),
            attacker,
            target,
            true,
            true,
            false,
        )

        assertThat(reflected).isFalse()
    }

    @Test
    fun `combatBlockHit rolls the parry charm against a player target`() {
        val game = newGame()
        val attacker = monster(id = 2)
        val target = game.placedPlayer()
        val charm = Charm(id = CharmRune.CHARM_PARRY, type = CharmType.CHARM_DEFENSIVE, chance = listOf(100.0))
        game.getMonsterTypeByName = { MonsterType(attacker.name) }
        game.getCharmFromTarget = { _, _ -> PlayerCharmsByMonster(major = CharmRune.CHARM_PARRY) }
        game.getBestiaryCharm = { charm }
        game.getCharmTier = { _, _ -> 0 }
        game.normalRandom = { _, _ -> 5000 }
        game.combatTargetBlockHit = { _, _, _, _, _, _, _ -> 5 to BlockType.ARMOR }
        val procs = mutableListOf<Pair<Player, Int>>()
        game.parseCharmCombat = { parsed, player, _, realDamage ->
            assertThat(parsed).isSameAs(charm)
            procs.add(player to realDamage)
        }

        game.combatBlockHit(
            CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -30),
            attacker,
            target,
            true,
            true,
            false,
        )

        assertThat(procs).containsExactly(target to -5)
    }

    @Test
    fun `combatBlockHit applies the healing-received buff to a healing hit`() {
        val game = newGame()
        val target = monster()
        target.setBuff(Buff.BUFF_HEALINGRECEIVED, 50)
        game.combatTargetBlockHit = { _, _, _, _, _, _, _ -> 5 to BlockType.ARMOR }
        val damage = CombatDamage(primaryType = CombatType.HEALING, primaryValue = -20)

        game.combatBlockHit(damage, null, target, true, true, false)

        assertThat(damage.primaryValue).isEqualTo(-7)
    }

    @Test
    fun `combatBlockHit skips the mantra absorb when the condition flag is set`() {
        val game = newGame()
        val target = monster()
        var mantra = 0
        game.combatApplyMantraAbsorb = { _, _, value -> mantra++; value }
        game.combatTargetBlockHit = { _, _, _, _, _, _, _ -> 10 to BlockType.ARMOR }

        game.combatBlockHit(
            CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -20),
            null,
            target,
            true,
            true,
            false,
            condition = true,
        )

        assertThat(mantra).isZero()
    }

    @Test
    fun `combatBlockHit runs the mantra absorb and clamps a negative component`() {
        val game = newGame()
        val target = monster()
        var mantra = 0
        game.combatApplyMantraAbsorb = { _, _, value -> mantra++; value }
        game.combatTargetBlockHit = { _, _, _, _, _, _, _ -> -5 to BlockType.ARMOR }

        val damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -20)
        game.combatBlockHit(damage, null, target, true, true, false)

        assertThat(mantra).isEqualTo(1)
        assertThat(damage.primaryValue).isEqualTo(5)
    }

    @Test
    fun `combatBlockHit returns false when only one component is blocked`() {
        val game = newGame()
        val target = monster()
        game.combatTargetBlockHit = { _, _, combatType, _, _, _, _ ->
            if (combatType == CombatType.FIREDAMAGE) 0 to BlockType.IMMUNITY else 5 to BlockType.NONE
        }

        assertThat(
            game.combatBlockHit(
                CombatDamage(
                    primaryType = CombatType.FIREDAMAGE,
                    primaryValue = -60,
                    secondaryType = CombatType.EARTHDAMAGE,
                    secondaryValue = -40,
                ),
                null,
                target,
                true,
                true,
                false,
            ),
        ).isFalse()
    }

    // ----- default Creature::blockHit core -----

    @Test
    fun `combatBlockHit immune core skips the block count and reports the immunity effect`() {
        val game = newGame()
        val attacker = game.placedPlayer()
        val target = monster()
        var hasDefense = false
        game.combatIsImmune = { _, _ -> true }
        game.combatHasDefense = { hasDefense = true; false }
        val effects = mutableListOf<SentEffect>()
        game.sendMagicEffectWithSource = { player, position, effect, _ -> effects.add(SentEffect(player, position, effect)) }
        val damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100)

        assertThat(game.combatBlockHit(damage, attacker, target, true, true, false)).isFalse()

        assertThat(damage.primaryValue).isZero()
        assertThat(hasDefense).isFalse()
        assertThat(effects).singleElement().satisfies({
            assertThat(it.player).isSameAs(attacker)
            assertThat(it.position).isEqualTo(center)
            assertThat(it.effect).isEqualTo(CONST_ME_BLOCKHIT)
        })
    }

    @Test
    fun `combatBlockHit armor core reduces one point and blocks the hit at zero`() {
        val game = newGame()
        val attacker = game.placedPlayer()
        val target = monster()
        game.getArmor = { 2 }
        val effects = mutableListOf<SentEffect>()
        game.sendMagicEffectWithSource = { player, position, effect, _ -> effects.add(SentEffect(player, position, effect)) }
        val damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -1)

        game.combatBlockHit(damage, attacker, target, true, true, false)

        assertThat(damage.primaryValue).isZero()
        assertThat(effects.single().effect).isEqualTo(CONST_ME_BLOCKHIT)
    }

    @Test
    fun `combatBlockHit mitigation core reduces the surviving damage`() {
        val game = newGame()
        val target = monster()
        game.getMitigation = { 25.0 }
        val damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100)

        game.combatBlockHit(damage, null, target, false, false, false)

        assertThat(damage.primaryValue).isEqualTo(-75)
    }

    @Test
    fun `combatBlockHit absorb core applies target absorb then attacker increase`() {
        val game = newGame()
        val attacker = game.placedPlayer()
        val target = monster()
        game.getAbsorbPercent = { _, _ -> 50 }
        game.getIncreasePercent = { _, _ -> 20 }
        val damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100)

        game.combatBlockHit(damage, attacker, target, false, false, false)

        assertThat(damage.primaryValue).isEqualTo(-60)
    }

    @Test
    fun `combatBlockHit skips defense and armor for a monster under disable-monster-armor`() {
        val game = newGame()
        val target = monster()
        game.disableMonsterArmor = true
        game.getDefense = { 1000 }
        game.getArmor = { 1000 }
        var hasDefense = false
        game.combatHasDefense = { hasDefense = true; false }
        val damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100)

        game.combatBlockHit(damage, null, target, true, true, false)

        assertThat(damage.primaryValue).isEqualTo(-100)
        assertThat(hasDefense).isFalse()
    }

    @Test
    fun `combatBlockHit suppresses the immunity effect and sound for undefined damage`() {
        val game = newGame()
        val attacker = game.placedPlayer()
        val target = monster()
        game.combatIsImmune = { _, _ -> true }
        val effects = mutableListOf<SentEffect>()
        game.sendMagicEffectWithSource = { player, position, effect, _ -> effects.add(SentEffect(player, position, effect)) }
        val sounds = mutableListOf<SoundEffect>()
        attacker.sendSingleSoundEffect = { _, sound, _ -> sounds.add(sound) }
        val damage = CombatDamage(primaryType = CombatType.UNDEFINEDDAMAGE, primaryValue = -100)

        game.combatBlockHit(damage, attacker, target, true, true, false)

        assertThat(damage.primaryValue).isZero()
        assertThat(effects).isEmpty()
        assertThat(sounds).isEmpty()
    }

    // ----- playerCyclopediaCharacterInfo -----

    @Test
    fun `playerCyclopediaCharacterInfo refuses another character id`() {
        val game = newGame()
        val player = game.placedPlayer()
        val noData = mutableListOf<Pair<Int, Int>>()
        game.sendCyclopediaCharacterNoData = { _, infoType, error -> noData.add(infoType to error) }

        game.playerCyclopediaCharacterInfo(player, 99, CYCLOPEDIA_CHARACTERINFO_BASEINFORMATION, 5, 1)

        assertThat(noData).containsExactly(CYCLOPEDIA_CHARACTERINFO_BASEINFORMATION to 2)
    }

    @Test
    fun `playerCyclopediaCharacterInfo routes the base information and general stats`() {
        val game = newGame()
        val player = game.placedPlayer()
        val base = mutableListOf<Player>()
        game.sendCyclopediaCharacterBaseInformation = { base.add(it) }
        val general = mutableListOf<Player>()
        game.sendCyclopediaCharacterGeneralStats = { general.add(it) }

        game.playerCyclopediaCharacterInfo(player, player.id.toInt(), CYCLOPEDIA_CHARACTERINFO_BASEINFORMATION, 5, 1)
        game.playerCyclopediaCharacterInfo(player, player.id.toInt(), CYCLOPEDIA_CHARACTERINFO_GENERALSTATS, 5, 1)

        assertThat(base).containsExactly(player)
        assertThat(general).containsExactly(player)
    }

    @Test
    fun `playerCyclopediaCharacterInfo loads the death history and recent kills`() {
        val game = newGame()
        val player = game.placedPlayer()

        game.playerCyclopediaCharacterInfo(player, player.id.toInt(), CYCLOPEDIA_CHARACTERINFO_RECENTDEATHS, 5, 1)
        assertThat(player.hasAsyncOngoingTask(PlayerAsyncTask.RecentDeaths)).isTrue()

        game.playerCyclopediaCharacterInfo(player, player.id.toInt(), CYCLOPEDIA_CHARACTERINFO_RECENTPVPKILLS, 5, 1)
        assertThat(player.hasAsyncOngoingTask(PlayerAsyncTask.RecentPvPKills)).isTrue()
    }

    @Test
    fun `playerCyclopediaCharacterInfo routes the achievements window`() {
        val game = newGame()
        val player = game.placedPlayer()
        val sent = mutableListOf<Player>()
        game.sendUnlockedSecretAchievements = { sent.add(it) }

        game.playerCyclopediaCharacterInfo(player, player.id.toInt(), CYCLOPEDIA_CHARACTERINFO_ACHIEVEMENTS, 5, 1)

        assertThat(sent).containsExactly(player)
    }

    @Test
    fun `playerCyclopediaCharacterInfo passes the five item lists to the summary`() {
        val game = newGame()
        val player = game.placedPlayer()
        val inventory = listOf(CyclopediaCharacterWriter.ItemSummaryEntry(2160, null, 1))
        val storeInbox = listOf(CyclopediaCharacterWriter.ItemSummaryEntry(2160, 1, 2))
        val stash = mapOf(2160 to 3)
        val depotBox = listOf(CyclopediaCharacterWriter.ItemSummaryEntry(2160, null, 4))
        val inbox = listOf(CyclopediaCharacterWriter.ItemSummaryEntry(2160, null, 5))
        game.getInventoryItemsId = { inventory }
        game.getStoreInboxItemsId = { storeInbox }
        game.getStashItems = { stash }
        game.getDepotChestItemsId = { depotBox }
        game.getDepotInboxItemsId = { inbox }
        val summaries = mutableListOf<List<Any>>()
        game.sendCyclopediaCharacterItemSummary = { p, inv, store, stashItems, depot, box ->
            summaries.add(listOf(p, inv, store, stashItems, depot, box))
        }

        game.playerCyclopediaCharacterInfo(player, player.id.toInt(), CYCLOPEDIA_CHARACTERINFO_ITEMSUMMARY, 5, 1)

        assertThat(summaries).containsExactly(
            listOf<Any>(player, inventory, storeInbox, stash, depotBox, inbox),
        )
    }

    @Test
    fun `playerCyclopediaCharacterInfo routes the window senders`() {
        val game = newGame()
        val player = game.placedPlayer()
        val sent = mutableListOf<String>()
        game.sendCyclopediaCharacterOutfitsMounts = { sent.add("outfits") }
        game.sendCyclopediaCharacterStoreSummary = { sent.add("store") }
        game.sendCyclopediaCharacterInspection = { sent.add("inspection") }
        game.sendCyclopediaCharacterBadges = { sent.add("badges") }
        game.sendCyclopediaCharacterTitles = { sent.add("titles") }
        game.sendCyclopediaCharacterOffenceStats = { sent.add("offence") }
        game.sendCyclopediaCharacterDefenceStats = { sent.add("defence") }
        game.sendCyclopediaCharacterMiscStats = { sent.add("misc") }
        val id = player.id.toInt()

        game.playerCyclopediaCharacterInfo(player, id, CYCLOPEDIA_CHARACTERINFO_OUTFITSMOUNTS, 5, 1)
        game.playerCyclopediaCharacterInfo(player, id, CYCLOPEDIA_CHARACTERINFO_STORESUMMARY, 5, 1)
        game.playerCyclopediaCharacterInfo(player, id, CYCLOPEDIA_CHARACTERINFO_INSPECTION, 5, 1)
        game.playerCyclopediaCharacterInfo(player, id, CYCLOPEDIA_CHARACTERINFO_BADGES, 5, 1)
        game.playerCyclopediaCharacterInfo(player, id, CYCLOPEDIA_CHARACTERINFO_TITLES, 5, 1)
        game.playerCyclopediaCharacterInfo(player, id, CYCLOPEDIA_CHARACTERINFO_OFFENCESTATS, 5, 1)
        game.playerCyclopediaCharacterInfo(player, id, CYCLOPEDIA_CHARACTERINFO_DEFENCESTATS, 5, 1)
        game.playerCyclopediaCharacterInfo(player, id, CYCLOPEDIA_CHARACTERINFO_MISCSTATS, 5, 1)

        assertThat(sent).containsExactly(
            "outfits",
            "store",
            "inspection",
            "badges",
            "titles",
            "offence",
            "defence",
            "misc",
        )
    }

    @Test
    fun `playerCyclopediaCharacterInfo opens the wheel window`() {
        val game = newGame()
        val player = game.placedPlayer()
        val owners = mutableListOf<Long>()
        game.sendOpenWheelWindow = { _, ownerId -> owners.add(ownerId) }

        game.playerCyclopediaCharacterInfo(player, player.id.toInt(), CYCLOPEDIA_CHARACTERINFO_WHEEL, 5, 1)

        assertThat(owners).containsExactly(player.id)
    }

    @Test
    fun `playerCyclopediaCharacterInfo answers no-data for an unknown type`() {
        val game = newGame()
        val player = game.placedPlayer()
        val noData = mutableListOf<Pair<Int, Int>>()
        game.sendCyclopediaCharacterNoData = { _, infoType, error -> noData.add(infoType to error) }

        game.playerCyclopediaCharacterInfo(player, player.id.toInt(), CYCLOPEDIA_CHARACTERINFO_COMBATSTATS, 5, 1)

        assertThat(noData).containsExactly(CYCLOPEDIA_CHARACTERINFO_COMBATSTATS to 1)
    }

    // ----- loadMap / loadMainMap / loadCustomMaps -----

    @Test
    fun `loadMap forwards the chunk flags and position`() {
        val game = newGame()
        val calls = mutableListOf<Triple<String, List<Boolean>, Position>>()
        game.mapLoadMap = { path, mainMap, loadHouses, loadMonsters, loadNpcs, loadZones, pos ->
            calls.add(Triple(path, listOf(mainMap, loadHouses, loadMonsters, loadNpcs, loadZones), pos))
        }

        game.loadMap("world/chunk.otbm", Position(10, 20, 7))

        assertThat(calls).containsExactly(
            Triple("world/chunk.otbm", listOf(false, false, false, false, false), Position(10, 20, 7)),
        )
    }

    @Test
    fun `loadMainMap applies the despawn config and loads the world file`() {
        val game = newGame()
        game.dataDirectory = "pack"
        game.defaultDespawnRange = 3
        game.defaultDespawnRadius = 45
        val despawn = mutableListOf<Pair<Int, Int>>()
        game.setMonsterDespawnRangeAndRadius = { range, radius -> despawn.add(range to radius) }
        val calls = mutableListOf<Triple<String, List<Boolean>, Position>>()
        game.mapLoadMap = { path, mainMap, loadHouses, loadMonsters, loadNpcs, loadZones, pos ->
            calls.add(Triple(path, listOf(mainMap, loadHouses, loadMonsters, loadNpcs, loadZones), pos))
        }

        game.loadMainMap("canary")

        assertThat(despawn).containsExactly(3 to 45)
        assertThat(calls).containsExactly(
            Triple("pack/world/canary.otbm", listOf(true, true, true, true, true), Position(0, 0, 0)),
        )
    }

    @Test
    fun `loadCustomMaps throws when the custom directory cannot be created`() {
        val game = newGame()
        game.ensureCustomMapDirectory = { false }

        assertThatThrownBy { game.loadCustomMaps("pack/world/custom") }
            .isInstanceOf(java.io.IOException::class.java)
            .hasMessage("Failed to create custom map directory pack/world/custom")
    }

    @Test
    fun `loadCustomMaps skips foreign extensions the disabled marker and the main map`() {
        val game = newGame()
        game.mapName = "canary"
        game.listCustomMapFiles = {
            listOf("a.otbm", "notes.txt", "#skip.otbm", "canary.otbm", "c.otbm")
        }
        val loaded = mutableListOf<Triple<String, List<Boolean>, Int>>()
        game.mapLoadMapCustom = { name, loadHouses, loadMonsters, loadNpcs, loadZones, index ->
            loaded.add(Triple(name, listOf(loadHouses, loadMonsters, loadNpcs, loadZones), index))
        }
        var houseInfo = 0
        game.mapLoadHouseInfo = { houseInfo++ }

        game.loadCustomMaps("pack/world/custom")

        assertThat(loaded).containsExactly(
            Triple("a", listOf(true, true, true, true), 0),
            Triple("c", listOf(true, true, true, true), 1),
        )
        assertThat(houseInfo).isEqualTo(1)
    }

    @Test
    fun `loadCustomMaps caps the sweep at fifty maps`() {
        val game = newGame()
        game.listCustomMapFiles = { (0 until 51).map { "map$it.otbm" } }
        val loaded = mutableListOf<Pair<String, Int>>()
        game.mapLoadMapCustom = { name, _, _, _, _, index -> loaded.add(name to index) }

        game.loadCustomMaps("pack/world/custom")

        assertThat(loaded).hasSize(50)
        assertThat(loaded.first()).isEqualTo("map0" to 0)
        assertThat(loaded.last()).isEqualTo("map49" to 49)
    }

    // ----- loadAppearanceProtobuf -----

    @Test
    fun `loadAppearanceProtobuf reports a missing file`() {
        val game = newGame()

        assertThat(game.loadAppearanceProtobuf("missing/appearances.dat")).isEqualTo(FileLoaderError.ERROR_NOT_OPEN)
        assertThat(game.outfitSupportsMount(136)).isTrue()
    }

    @Test
    fun `loadAppearanceProtobuf resets the table on a parse failure`(@TempDir tempDir: File) {
        val game = newGame()
        game.parseAppearances = { null }

        assertThat(game.loadAppearanceProtobuf(File(tempDir, "appearances.dat").path))
            .isEqualTo(FileLoaderError.ERROR_NOT_OPEN)
        assertThat(game.outfitSupportsMount(136)).isTrue()
    }

    @Test
    fun `loadAppearanceProtobuf parses registers and merges the appearance data`(@TempDir tempDir: File) {
        val game = newGame()
        val file = File(tempDir, "appearances.dat")
        file.writeBytes(appearancesMessage())
        val merged = mutableListOf<Int>()
        game.loadItemsFromProtobuf = { parsed -> merged.add(parsed.objects.size) }

        assertThat(game.loadAppearanceProtobuf(file.path)).isEqualTo(FileLoaderError.ERROR_NONE)

        assertThat(game.outfitSupportsMount(136)).isTrue()
        assertThat(game.outfitSupportsMount(21)).isFalse()
        assertThat(game.isMagicEffectRegistered(1)).isTrue()
        assertThat(game.isDistanceEffectRegistered(2)).isTrue()
        assertThat(game.isLookTypeRegistered(136)).isTrue()
        assertThat(merged).containsExactly(1)
    }

    @Test
    fun `loadAppearanceProtobuf skips the registrations when unsafe-script warnings are off`(@TempDir tempDir: File) {
        val game = newGame()
        game.warnUnsafeScripts = false
        val file = File(tempDir, "appearances.dat")
        file.writeBytes(appearancesMessage())

        assertThat(game.loadAppearanceProtobuf(file.path)).isEqualTo(FileLoaderError.ERROR_NONE)

        assertThat(game.outfitSupportsMount(136)).isTrue()
        assertThat(game.isMagicEffectRegistered(1)).isFalse()
        assertThat(game.isDistanceEffectRegistered(2)).isFalse()
        assertThat(game.isLookTypeRegistered(136)).isFalse()
    }

    // ----- helpers -----

    private fun appearancesMessage(): ByteArray {
        val objectAppearance = appearance(100)
        val mountOutfit = appearance(
            136,
            frameGroup(FixedIdle, 0),
            frameGroup(FixedMoving, 1),
        )
        return field(1, objectAppearance) +
            field(2, mountOutfit) +
            field(3, appearance(1)) +
            field(4, appearance(2))
    }

    private fun appearance(id: Int, vararg frameGroups: ByteArray): ByteArray =
        varintField(1, id) + frameGroups.fold(ByteArray(0)) { acc, bytes -> acc + field(2, bytes) }

    private fun frameGroup(fixedFrameGroup: Int, id: Int): ByteArray =
        varintField(1, fixedFrameGroup) + varintField(2, id) + field(3, varintField(3, 2))

    private fun field(number: Int, bytes: ByteArray): ByteArray = tag(number, 2) + varint(bytes.size) + bytes

    private fun varintField(number: Int, value: Int): ByteArray = tag(number, 0) + varint(value)

    private fun tag(number: Int, wireType: Int): ByteArray = varint((number shl 3) or wireType)

    private fun varint(value: Int): ByteArray {
        var remaining = value
        val out = mutableListOf<Byte>()
        do {
            var byte = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining != 0) {
                byte = byte or 0x80
            }
            out.add(byte.toByte())
        } while (remaining != 0)
        return out.toByteArray()
    }

    private companion object {
        private val CYCLOPEDIA_CHARACTERINFO_BASEINFORMATION =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_BASEINFORMATION

        private val CYCLOPEDIA_CHARACTERINFO_GENERALSTATS =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_GENERALSTATS

        private val CYCLOPEDIA_CHARACTERINFO_COMBATSTATS =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_COMBATSTATS

        private val CYCLOPEDIA_CHARACTERINFO_RECENTDEATHS =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_RECENTDEATHS

        private val CYCLOPEDIA_CHARACTERINFO_RECENTPVPKILLS =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_RECENTPVPKILLS

        private val CYCLOPEDIA_CHARACTERINFO_ACHIEVEMENTS =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_ACHIEVEMENTS

        private val CYCLOPEDIA_CHARACTERINFO_ITEMSUMMARY =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_ITEMSUMMARY

        private val CYCLOPEDIA_CHARACTERINFO_OUTFITSMOUNTS =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_OUTFITSMOUNTS

        private val CYCLOPEDIA_CHARACTERINFO_STORESUMMARY =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_STORESUMMARY

        private val CYCLOPEDIA_CHARACTERINFO_INSPECTION =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_INSPECTION

        private val CYCLOPEDIA_CHARACTERINFO_BADGES =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_BADGES

        private val CYCLOPEDIA_CHARACTERINFO_TITLES =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_TITLES

        private val CYCLOPEDIA_CHARACTERINFO_WHEEL =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_WHEEL

        private val CYCLOPEDIA_CHARACTERINFO_OFFENCESTATS =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_OFFENCESTATS

        private val CYCLOPEDIA_CHARACTERINFO_DEFENCESTATS =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_DEFENCESTATS

        private val CYCLOPEDIA_CHARACTERINFO_MISCSTATS =
            CyclopediaWindowWriter.CYCLOPEDIA_CHARACTERINFO_MISCSTATS

        private const val FixedIdle = 0
        private const val FixedMoving = 1

        private const val CONST_ME_BLOCKHIT = 4
        private const val CONST_ME_GREEN_RINGS = 9
    }
}
