package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.CombatType
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.systems.bestiary.BestiaryRace

/**
 * Transcribes the Canary monster schema:
 * - `class MonsterType` / `struct MonsterInfo` / `struct spellBlock_t` /
 *   `struct summonBlock_t` / `struct voiceBlock_t` (src/creatures/monsters/monsters.hpp:35-192)
 * - `class Monsters` registry (src/creatures/monsters/monsters.hpp:242-267,
 *   src/creatures/monsters/monsters.cpp:348-425)
 * - `enum RaceType_t`, `enum RespawnPeriod_t`, `enum MonstersEvent_t`,
 *   `struct RespawnType` (src/creatures/creatures_definitions.hpp:367-371, 504-514, 553-562,
 *   1760-1763)
 * - `enum class BosstiaryRarity_t` (src/io/io_bosstiary.hpp:12-19)
 */
class MonsterTypeTest {

    // --- MonsterType identity and methods ---

    @Test
    fun `constructor seeds name, typeName and nameDescription from the name`() {
        val monsterType = MonsterType("Demon")

        assertThat(monsterType.name).isEqualTo("Demon")
        assertThat(monsterType.typeName).isEqualTo("Demon")
        assertThat(monsterType.nameDescription).isEqualTo("Demon")
        assertThat(monsterType.variantName).isEmpty()
    }

    @Test
    fun `identity fields remain mutable like the public C++ members`() {
        val monsterType = MonsterType("Demon")

        monsterType.name = "Demon renamed"
        monsterType.typeName = "type"
        monsterType.nameDescription = "description"
        monsterType.variantName = "variant"

        assertThat(monsterType.name).isEqualTo("Demon renamed")
        assertThat(monsterType.typeName).isEqualTo("type")
        assertThat(monsterType.nameDescription).isEqualTo("description")
        assertThat(monsterType.variantName).isEqualTo("variant")
    }

    @Test
    fun `base speed delegates to info`() {
        val monsterType = MonsterType("Demon")

        assertThat(monsterType.getBaseSpeed()).isEqualTo(110)

        monsterType.setBaseSpeed(250)

        assertThat(monsterType.getBaseSpeed()).isEqualTo(250)
        assertThat(monsterType.info.baseSpeed).isEqualTo(250)
    }

    @Test
    fun `isBoss follows the bosstiary class`() {
        val monsterType = MonsterType("Demon")

        assertThat(monsterType.isBoss()).isFalse()

        monsterType.info.bosstiaryClass = "Boss"

        assertThat(monsterType.isBoss()).isTrue()
    }

    // --- MonsterInfo defaults ---

    @Test
    fun `combat defaults mirror MonsterInfo`() {
        val info = MonsterType("Demon").info

        assertThat(info.health).isEqualTo(100)
        assertThat(info.healthMax).isEqualTo(100)
        assertThat(info.baseSpeed).isEqualTo(110)
        assertThat(info.experience).isZero()
        assertThat(info.manaCost).isZero()
        assertThat(info.staticAttackChance).isEqualTo(95)
        assertThat(info.defense).isZero()
        assertThat(info.armor).isZero()
        assertThat(info.critChance).isZero()
        assertThat(info.targetDistance).isEqualTo(1)
        assertThat(info.runAwayHealth).isZero()
        assertThat(info.changeTargetChance).isZero()
        assertThat(info.changeTargetSpeed).isZero()
        assertThat(info.maxSummons).isZero()
        assertThat(info.soulCore).isZero()
        assertThat(info.yellChance).isZero()
        assertThat(info.yellSpeedTicks).isZero()
        assertThat(info.strategiesTargetNearest).isZero()
        assertThat(info.strategiesTargetHealth).isZero()
        assertThat(info.strategiesTargetDamage).isZero()
        assertThat(info.strategiesTargetRandom).isZero()
        assertThat(info.targetPreferPlayer).isFalse()
        assertThat(info.targetPreferMaster).isFalse()
    }

    @Test
    fun `appearance and race defaults mirror MonsterInfo`() {
        val info = MonsterType("Demon").info

        assertThat(info.skull).isEqualTo(Skulls_t.SKULL_NONE)
        assertThat(info.race).isEqualTo(RaceType.RACE_BLOOD)
        assertThat(info.outfit).isEqualTo(Outfit())
        assertThat(info.light).isEqualTo(LightInfo())
        assertThat(info.lookcorpse).isZero()
        assertThat(info.respawnType).isEqualTo(RespawnType())
        assertThat(info.respawnType.period).isEqualTo(RespawnPeriod.RESPAWNPERIOD_ALL)
        assertThat(info.respawnType.underground).isFalse()
    }

    @Test
    fun `flag defaults mirror MonsterInfo`() {
        val info = MonsterType("Demon").info

        assertThat(info.canPushItems).isFalse()
        assertThat(info.canPushCreatures).isFalse()
        assertThat(info.pushable).isTrue()
        assertThat(info.isSummonable).isFalse()
        assertThat(info.isIllusionable).isFalse()
        assertThat(info.isConvinceable).isFalse()
        assertThat(info.isAttackable).isTrue()
        assertThat(info.isHostile).isTrue()
        assertThat(info.hiddenHealth).isFalse()
        assertThat(info.isBlockable).isFalse()
        assertThat(info.isFamiliar).isFalse()
        assertThat(info.isRewardBoss).isFalse()
        assertThat(info.canWalkOnEnergy).isTrue()
        assertThat(info.canWalkOnFire).isTrue()
        assertThat(info.canWalkOnPoison).isTrue()
        assertThat(info.isForgeCreature).isTrue()
        assertThat(info.isPreyable).isTrue()
        assertThat(info.isPreyExclusive).isFalse()
        assertThat(info.faction).isEqualTo(Faction.Default)
        assertThat(info.enemyFactions).isEmpty()
    }

    @Test
    fun `bestiary and bosstiary defaults mirror MonsterInfo`() {
        val info = MonsterType("Demon").info

        assertThat(info.bestiaryOccurrence).isZero()
        assertThat(info.bestiaryStars).isZero()
        assertThat(info.bestiaryToUnlock).isZero()
        assertThat(info.bestiaryFirstUnlock).isZero()
        assertThat(info.bestiarySecondUnlock).isZero()
        assertThat(info.bestiaryCharmsPoints).isZero()
        assertThat(info.raceid).isZero()
        assertThat(info.bestiaryLocations).isEmpty()
        assertThat(info.bestiaryClass).isEmpty()
        assertThat(info.bestiaryRace).isEqualTo(BestiaryRace.BESTY_RACE_NONE)
        assertThat(info.bosstiaryRace).isEqualTo(BosstiaryRarity_t.BOSS_INVALID)
        assertThat(info.bosstiaryClass).isEmpty()
        assertThat(info.mitigation).isZero()
    }

    @Test
    fun `sound and event callback defaults mirror MonsterInfo`() {
        val info = MonsterType("Demon").info

        assertThat(info.soundChance).isZero()
        assertThat(info.soundSpeedTicks).isZero()
        assertThat(info.soundVector).isEmpty()
        assertThat(info.deathSound).isEqualTo(SoundEffect.SILENCE)
        assertThat(info.creatureAppearEvent).isEqualTo(-1)
        assertThat(info.creatureDisappearEvent).isEqualTo(-1)
        assertThat(info.creatureMoveEvent).isEqualTo(-1)
        assertThat(info.creatureSayEvent).isEqualTo(-1)
        assertThat(info.monsterAttackedByPlayerEvent).isEqualTo(-1)
        assertThat(info.thinkEvent).isEqualTo(-1)
        assertThat(info.spawnEvent).isEqualTo(-1)
        assertThat(info.eventType).isEqualTo(MonstersEvent.MONSTERS_EVENT_NONE)
    }

    @Test
    fun `container defaults start empty`() {
        val info = MonsterType("Demon").info

        assertThat(info.elementMap).isEmpty()
        assertThat(info.reflectMap).isEmpty()
        assertThat(info.healingMap).isEmpty()
        assertThat(info.voiceVector).isEmpty()
        assertThat(info.lootItems).isEmpty()
        assertThat(info.scripts).isEmpty()
        assertThat(info.attackSpells).isEmpty()
        assertThat(info.defenseSpells).isEmpty()
        assertThat(info.summons).isEmpty()
        assertThat(info.m_conditionImmunities).isEmpty()
        assertThat(info.m_damageImmunities).isEmpty()
        assertThat(info.scriptInterface).isNull()
    }

    @Test
    fun `element maps key by combat type`() {
        val info = MonsterType("Demon").info

        info.elementMap[CombatType.COMBAT_FIREDAMAGE] = 20
        info.reflectMap[CombatType.COMBAT_DEATHDAMAGE] = 10
        info.healingMap[CombatType.COMBAT_HEALING] = 30

        assertThat(info.elementMap).containsEntry(CombatType.COMBAT_FIREDAMAGE, 20)
        assertThat(info.reflectMap).containsEntry(CombatType.COMBAT_DEATHDAMAGE, 10)
        assertThat(info.healingMap).containsEntry(CombatType.COMBAT_HEALING, 30)
    }

    @Test
    fun `immunity sets accept condition and combat values`() {
        val info = MonsterType("Demon").info

        info.m_conditionImmunities += ConditionType.CONDITION_FIRE
        info.m_damageImmunities += CombatType.COMBAT_FIREDAMAGE

        assertThat(info.m_conditionImmunities).containsExactly(ConditionType.CONDITION_FIRE)
        assertThat(info.m_damageImmunities).containsExactly(CombatType.COMBAT_FIREDAMAGE)
    }

    @Test
    fun `scripts stay unique and ordered like the C++ set`() {
        val info = MonsterType("Demon").info

        info.scripts += "b"
        info.scripts += "a"
        info.scripts += "a"

        assertThat(info.scripts).containsExactly("a", "b")
    }

    // --- nested blocks ---

    @Test
    fun `spell block defaults mirror spellBlock_t`() {
        val block = SpellBlock()

        assertThat(block.spell).isNull()
        assertThat(block.chance).isEqualTo(100)
        assertThat(block.speed).isEqualTo(2000)
        assertThat(block.range).isZero()
        assertThat(block.minCombatValue).isZero()
        assertThat(block.maxCombatValue).isZero()
        assertThat(block.combatSpell).isFalse()
        assertThat(block.isMelee).isFalse()
        assertThat(block.soundImpactEffect).isEqualTo(SoundEffect.SILENCE)
        assertThat(block.soundCastEffect).isEqualTo(SoundEffect.SILENCE)
    }

    @Test
    fun `attack and defense spell lists hold spell blocks`() {
        val info = MonsterType("Demon").info

        info.attackSpells += SpellBlock(chance = 50)
        info.defenseSpells += SpellBlock(speed = 1000)

        assertThat(info.attackSpells).containsExactly(SpellBlock(chance = 50))
        assertThat(info.defenseSpells).containsExactly(SpellBlock(speed = 1000))
    }

    @Test
    fun `summon block defaults mirror summonBlock_t`() {
        val block = SummonBlock()

        assertThat(block.name).isEmpty()
        assertThat(block.chance).isZero()
        assertThat(block.speed).isZero()
        assertThat(block.count).isZero()
        assertThat(block.force).isFalse()
    }

    @Test
    fun `voice block carries text and yell flag`() {
        val voice = VoiceBlock(text = "Demon", yellText = true)

        assertThat(voice.text).isEqualTo("Demon")
        assertThat(voice.yellText).isTrue()
    }

    // --- enum transcriptions ---

    @Test
    fun `race enum mirrors RaceType_t values`() {
        assertThat(RaceType.entries.map { it.value }).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8)
        assertThat(RaceType.RACE_BLOOD.value).isEqualTo(2)
        assertThat(RaceType.RACE_CANDY.value).isEqualTo(8)
    }

    @Test
    fun `respawn period enum mirrors RespawnPeriod_t values`() {
        assertThat(RespawnPeriod.RESPAWNPERIOD_ALL.value).isZero()
        assertThat(RespawnPeriod.RESPAWNPERIOD_DAY.value).isEqualTo(1)
        assertThat(RespawnPeriod.RESPAWNPERIOD_NIGHT.value).isEqualTo(2)
    }

    @Test
    fun `monsters event enum mirrors MonstersEvent_t values`() {
        assertThat(MonstersEvent.entries.map { it.value }).containsExactly(0, 1, 2, 3, 4, 5, 6, 7)
    }

    @Test
    fun `bosstiary rarity mirrors BosstiaryRarity_t including BOSS_INVALID`() {
        assertThat(BosstiaryRarity_t.RARITY_BANE.value).isZero()
        assertThat(BosstiaryRarity_t.RARITY_ARCHFOE.value).isEqualTo(1)
        assertThat(BosstiaryRarity_t.RARITY_NEMESIS.value).isEqualTo(2)
        assertThat(BosstiaryRarity_t.BOSS_INVALID.value).isEqualTo(10)
    }

    // --- Monsters registry ---

    @Test
    fun `registry looks monsters up case-insensitively`() {
        val monsters = Monsters()
        val demon = MonsterType("Demon")

        assertThat(monsters.tryAddMonsterType("Demon", demon)).isTrue()
        assertThat(monsters.getMonsterType("demon")).isSameAs(demon)
        assertThat(monsters.getMonsterType("DEMON")).isSameAs(demon)
    }

    @Test
    fun `registry returns null for unknown names`() {
        val monsters = Monsters()

        assertThat(monsters.getMonsterType("missing", silent = true)).isNull()
    }

    @Test
    fun `registry rejects duplicate names case-insensitively`() {
        val monsters = Monsters()
        val first = MonsterType("Demon")
        val second = MonsterType("demon")

        assertThat(monsters.tryAddMonsterType("Demon", first)).isTrue()
        assertThat(monsters.tryAddMonsterType("demon", second)).isFalse()
        assertThat(monsters.getMonsterType("Demon")).isSameAs(first)
    }

    @Test
    fun `registry clear drops types and advances the reload epoch`() {
        val monsters = Monsters()
        monsters.tryAddMonsterType("Demon", MonsterType("Demon"))
        assertThat(monsters.getReloadEpoch()).isEqualTo(1L)

        monsters.clear()

        assertThat(monsters.getReloadEpoch()).isEqualTo(2L)
        assertThat(monsters.getMonsterType("Demon", silent = true)).isNull()
    }

    @Test
    fun `registry reload epoch wraps from the unsigned maximum to one`() {
        val monsters = Monsters()
        monsters.reloadEpoch = -1L

        monsters.clear()

        assertThat(monsters.getReloadEpoch()).isEqualTo(1L)
    }
}
