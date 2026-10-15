package org.opentibia.canary.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentibia.canary.combat.condition.CombatType
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.monsters.RaceType
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.Items
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.systems.bestiary.BestiaryRace

/**
 * Wave 57 A1: the production [defaultMonsterBoot] must populate `g_monsters()`
 * from the shipped datapack's Lua monster scripts (`{DATA_DIRECTORY}/monster`),
 * exactly like `CanaryServer::loadModules` (src/canary_server.cpp:599) and
 * `GameReload::reloadMonsters` (src/game/functions/game_reload.cpp:216-226):
 * the core `data/scripts/lib` registration helper (`MonsterType:register`,
 * data/scripts/lib/register_monster_type.lua) runs first, then every datapack
 * monster script registers its type through `Game.createMonsterType` and the
 * `MonsterType` setters.
 *
 * The XML definitions remain as a fallback for datapacks that ship them; the
 * shipped `data-canary` datapack has none, so every type below comes from Lua.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonsterTypeBootRegistrationTest {

    private lateinit var monsters: Monsters

    @BeforeAll
    fun bootMonsterRegistry() {
        monsters = defaultMonsterBoot(
            ServerConfig(loginPort = 0, gamePort = 0),
            LuaEnvironment().apply { initState() },
            ConfigManager(ConfigKeys.all.associateWith { it.luaIdentifier }),
            namedItemCatalog(),
        )
    }

    /** Minimal `Item::items` name catalog for the datapack's named loot entries. */
    private fun namedItemCatalog(): Items = Items().apply {
        register(ItemType(id = 3031, name = "gold coin"))
        register(ItemType(id = 3976, name = "worm"))
    }

    private fun type(name: String): MonsterType = monsters.getMonsterType(name, silent = true)
        ?: error("Monster type '$name' was not registered by the datapack Lua scripts")

    @Test
    fun `boot registers the shipped datapack monster types through the lua path`() {
        assertThat(type("dragon").name).isEqualTo("Dragon")
        assertThat(type("demon").name).isEqualTo("Demon")
        assertThat(type("troll").name).isEqualTo("Troll")
        assertThat(type("slime").name).isEqualTo("Slime")
        assertThat(type("cave rat").name).isEqualTo("Cave Rat")
        assertThat(type("goblin").name).isEqualTo("Goblin")
    }

    @Test
    fun `dragon identity, vitals and race come from the datapack script`() {
        val dragon = type("dragon")

        assertThat(dragon.typeName).isEqualTo("Dragon")
        assertThat(dragon.nameDescription).isEqualTo("a dragon")
        assertThat(dragon.info.experience).isEqualTo(700L)
        assertThat(dragon.info.health).isEqualTo(1000)
        assertThat(dragon.info.healthMax).isEqualTo(1000)
        assertThat(dragon.info.baseSpeed).isEqualTo(86)
        assertThat(dragon.info.manaCost).isZero()
        assertThat(dragon.info.race).isEqualTo(RaceType.RACE_BLOOD)
        assertThat(dragon.info.lookcorpse).isEqualTo(4025)
    }

    @Test
    fun `dragon outfit and flags come from the datapack script`() {
        val info = type("dragon").info

        assertThat(info.outfit.lookType).isEqualTo(34)
        assertThat(info.isAttackable).isTrue()
        assertThat(info.isHostile).isTrue()
        assertThat(info.isSummonable).isFalse()
        assertThat(info.isConvinceable).isFalse()
        assertThat(info.pushable).isFalse()
        assertThat(info.isIllusionable).isTrue()
        assertThat(info.isRewardBoss).isFalse()
        assertThat(info.canPushItems).isTrue()
        assertThat(info.canPushCreatures).isTrue()
        assertThat(info.staticAttackChance).isEqualTo(80)
        assertThat(info.targetDistance).isEqualTo(1)
        assertThat(info.runAwayHealth).isEqualTo(300)
        assertThat(info.hiddenHealth).isFalse()
        assertThat(info.isBlockable).isFalse()
        assertThat(info.canWalkOnEnergy).isFalse()
        assertThat(info.canWalkOnFire).isTrue()
        assertThat(info.canWalkOnPoison).isFalse()
        assertThat(info.changeTargetChance).isEqualTo(10)
        assertThat(info.changeTargetSpeed).isEqualTo(4000)
        assertThat(info.strategiesTargetNearest).isEqualTo(70)
        assertThat(info.strategiesTargetHealth).isEqualTo(10)
        assertThat(info.strategiesTargetDamage).isEqualTo(10)
        assertThat(info.strategiesTargetRandom).isEqualTo(10)
    }

    @Test
    fun `dragon bestiary comes from the datapack script`() {
        val info = type("dragon").info

        assertThat(info.raceid).isEqualTo(34)
        assertThat(info.bestiaryClass).isEqualTo("Dragon")
        assertThat(info.bestiaryRace).isEqualTo(BestiaryRace.BESTY_RACE_DRAGON)
        assertThat(info.bestiaryToUnlock).isEqualTo(1000)
        assertThat(info.bestiaryFirstUnlock).isEqualTo(50)
        assertThat(info.bestiarySecondUnlock).isEqualTo(500)
        assertThat(info.bestiaryCharmsPoints).isEqualTo(25)
        assertThat(info.bestiaryStars).isEqualTo(3)
        assertThat(info.bestiaryOccurrence).isZero()
        assertThat(info.bestiaryLocations).startsWith("Thais Ancient Temple")
    }

    @Test
    fun `dragon immunities, elements and defenses come from the datapack script`() {
        val info = type("dragon").info

        assertThat(info.m_conditionImmunities)
            .contains(ConditionType.CONDITION_PARALYZE, ConditionType.CONDITION_INVISIBLE)
        assertThat(info.elementMap[CombatType.COMBAT_FIREDAMAGE]).isEqualTo(100)
        assertThat(info.elementMap[CombatType.COMBAT_ENERGYDAMAGE]).isEqualTo(20)
        assertThat(info.elementMap[CombatType.COMBAT_EARTHDAMAGE]).isEqualTo(80)
        assertThat(info.elementMap[CombatType.COMBAT_ICEDAMAGE]).isEqualTo(-10)
        assertThat(info.defense).isEqualTo(30)
        assertThat(info.armor).isEqualTo(30)
    }

    @Test
    fun `dragon voices, attacks and loot come from the datapack script`() {
        val info = type("dragon").info

        assertThat(info.yellChance).isEqualTo(10)
        assertThat(info.yellSpeedTicks).isEqualTo(5000)
        assertThat(info.voiceVector).hasSize(2)
        assertThat(info.voiceVector.first().text).isEqualTo("GROOAAARRR")
        assertThat(info.voiceVector.first().yellText).isTrue()

        assertThat(info.attackSpells).hasSize(3)
        val melee = info.attackSpells.first()
        assertThat(melee.isMelee).isTrue()
        assertThat(melee.chance).isEqualTo(100)
        assertThat(melee.speed).isEqualTo(2000)
        assertThat(melee.minCombatValue).isEqualTo(-120)
        assertThat(melee.maxCombatValue).isZero()
        val areaAttack = info.attackSpells[1]
        assertThat(areaAttack.isMelee).isFalse()
        assertThat(areaAttack.combatSpell).isTrue()
        assertThat(areaAttack.chance).isEqualTo(15)
        assertThat(areaAttack.range).isEqualTo(7)
        assertThat(areaAttack.minCombatValue).isEqualTo(-140)
        assertThat(areaAttack.maxCombatValue).isEqualTo(-60)

        assertThat(info.defenseSpells).hasSize(1)

        assertThat(info.lootItems).hasSize(22)
        assertThat(info.lootItems.any { it.id == 3028 && it.chance == 380 }).isTrue()
        assertThat(info.lootItems.any { it.id == 3031 && it.countMax == 70 }).isTrue()
    }

    @Test
    fun `named loot resolves through the booted item catalog`() {
        val info = type("azure frog").info

        assertThat(info.lootItems).hasSize(2)
        assertThat(info.lootItems.first().id).isEqualTo(3031)
        assertThat(info.lootItems.first().countMax).isEqualTo(10)
        assertThat(info.lootItems.first().chance).isEqualTo(74230)
        assertThat(info.lootItems[1].id).isEqualTo(3976)
    }

    @Test
    fun `bestiary race ids resolve through the booted registry`() {
        // `Game::addBestiaryList` (game.cpp:9510-9518) fed by the datapack's
        // `monsterType:raceId(id)` setters: `Monsters::getMonsterTypeByRaceId`
        // (monsters.cpp:375-388) resolves the persisted race id, an unknown id
        // stays unresolved.
        val dragon = type("dragon")
        assertThat(dragon.info.raceid).isEqualTo(34)
        assertThat(monsters.getMonsterTypeByRaceId(34)).isSameAs(dragon)
        assertThat(monsters.getMonsterTypeByRaceId(99_999)).isNull()
    }

    @Test
    fun `datapack without monster scripts falls back to the XML definitions`() {
        val dataDir = java.nio.file.Files.createTempDirectory("canary_boot_xml_fallback")
        java.nio.file.Files.createDirectories(dataDir.resolve("monster"))
        java.nio.file.Files.writeString(
            dataDir.resolve("monster").resolve("goblin.xml"),
            """
            <monster name="Goblin" experience="50" speed="190" race="blood">
                <health now="60" max="60"/>
                <loot>
                    <item id="2148" countmax="9" chance="50000"/>
                </loot>
            </monster>
            """.trimIndent(),
        )

        val fallback = defaultMonsterBoot(
            ServerConfig(loginPort = 0, gamePort = 0, dataDir = dataDir.toString()),
            LuaEnvironment().apply { initState() },
            ConfigManager(ConfigKeys.all.associateWith { it.luaIdentifier }),
            Items(),
        )

        val goblin = fallback.getMonsterType("goblin", silent = true)!!
        assertThat(goblin.info.experience).isEqualTo(50L)
        assertThat(goblin.info.baseSpeed).isEqualTo(190)
        assertThat(goblin.info.health).isEqualTo(60)
        assertThat(goblin.info.lootItems).hasSize(1)
    }
}
