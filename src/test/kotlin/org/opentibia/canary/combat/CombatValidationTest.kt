package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the combat target-validation layer.
 *
 * Every expectation is derived from the exact C++ control flow in
 * `Combat::canTargetCreature`, `Combat::canDoCombat` (both overloads),
 * `Combat::isPlayerCombat`, `Combat::isProtected`, `Combat::isInPvpZone` and
 * `Combat::ConditionToDamageType`/`Combat::DamageToConditionType`
 * (src/creatures/combat/combat.cpp:245-572).
 */
class CombatValidationTest {

    @Test
    fun `ConditionToDamageType and DamageToConditionType mirror the C++ switches`() {
        // combat.cpp:245-276
        assertThat(Combat.ConditionToDamageType(ConditionType.CONDITION_FIRE)).isEqualTo(CombatType.FIREDAMAGE)
        assertThat(Combat.ConditionToDamageType(ConditionType.CONDITION_ENERGY)).isEqualTo(CombatType.ENERGYDAMAGE)
        assertThat(Combat.ConditionToDamageType(ConditionType.CONDITION_BLEEDING)).isEqualTo(CombatType.PHYSICALDAMAGE)
        assertThat(Combat.ConditionToDamageType(ConditionType.CONDITION_DROWN)).isEqualTo(CombatType.DROWNDAMAGE)
        assertThat(Combat.ConditionToDamageType(ConditionType.CONDITION_POISON)).isEqualTo(CombatType.EARTHDAMAGE)
        assertThat(Combat.ConditionToDamageType(ConditionType.CONDITION_FREEZING)).isEqualTo(CombatType.ICEDAMAGE)
        assertThat(Combat.ConditionToDamageType(ConditionType.CONDITION_DAZZLED)).isEqualTo(CombatType.HOLYDAMAGE)
        assertThat(Combat.ConditionToDamageType(ConditionType.CONDITION_CURSED)).isEqualTo(CombatType.DEATHDAMAGE)
        assertThat(Combat.ConditionToDamageType(ConditionType.CONDITION_NONE)).isEqualTo(CombatType.NONE)

        // combat.cpp:278-307
        assertThat(Combat.DamageToConditionType(CombatType.FIREDAMAGE)).isEqualTo(ConditionType.CONDITION_FIRE)
        assertThat(Combat.DamageToConditionType(CombatType.ENERGYDAMAGE)).isEqualTo(ConditionType.CONDITION_ENERGY)
        assertThat(Combat.DamageToConditionType(CombatType.DROWNDAMAGE)).isEqualTo(ConditionType.CONDITION_DROWN)
        assertThat(Combat.DamageToConditionType(CombatType.EARTHDAMAGE)).isEqualTo(ConditionType.CONDITION_POISON)
        assertThat(Combat.DamageToConditionType(CombatType.ICEDAMAGE)).isEqualTo(ConditionType.CONDITION_FREEZING)
        assertThat(Combat.DamageToConditionType(CombatType.HOLYDAMAGE)).isEqualTo(ConditionType.CONDITION_DAZZLED)
        assertThat(Combat.DamageToConditionType(CombatType.DEATHDAMAGE)).isEqualTo(ConditionType.CONDITION_CURSED)
        assertThat(Combat.DamageToConditionType(CombatType.PHYSICALDAMAGE)).isEqualTo(ConditionType.CONDITION_BLEEDING)
        assertThat(Combat.DamageToConditionType(CombatType.NONE)).isEqualTo(ConditionType.CONDITION_NONE)
    }

    @Test
    fun `isPlayerCombat detects players and player-owned summons`() {
        // combat.cpp:309-319
        val player = FakeCombatPlayer()
        assertThat(Combat.isPlayerCombat(player)).isTrue()

        val summon = FakeCombatActor()
        summon.summonValue = true
        summon.masterValue = player
        assertThat(Combat.isPlayerCombat(summon)).isTrue()

        val monsterSummon = FakeCombatActor()
        monsterSummon.summonValue = true
        monsterSummon.masterValue = FakeCombatActor()
        assertThat(Combat.isPlayerCombat(monsterSummon)).isFalse()

        assertThat(Combat.isPlayerCombat(FakeCombatActor())).isFalse()
    }

    @Test
    fun `isInPvpZone requires both creatures in a pvp zone`() {
        // combat.cpp:428-430
        val attacker = FakeCombatActor()
        val target = FakeCombatActor()
        attacker.zoneTypeValue = ZoneType.ZONE_PVP
        target.zoneTypeValue = ZoneType.ZONE_PVP
        assertThat(Combat.isInPvpZone(attacker, target)).isTrue()

        target.zoneTypeValue = ZoneType.ZONE_NORMAL
        assertThat(Combat.isInPvpZone(attacker, target)).isFalse()
    }

    @Test
    fun `isProtected mirrors the level vocation and black skull gates`() {
        // combat.cpp:432-447
        val attacker = FakeCombatPlayer()
        val target = FakeCombatPlayer()

        assertThat(Combat.isProtected(attacker, target, protectionLevel = 50)).isFalse()

        target.levelValue = 10
        assertThat(Combat.isProtected(attacker, target, protectionLevel = 50)).isTrue()
        target.levelValue = 100

        attacker.levelValue = 10
        assertThat(Combat.isProtected(attacker, target, protectionLevel = 50)).isTrue()
        attacker.levelValue = 100

        // a non-combat vocation paired with VOCATION_NONE (id 0) protects.
        attacker.vocationCanCombatValue = false
        attacker.vocationIdValue = 0
        assertThat(Combat.isProtected(attacker, target, protectionLevel = 0)).isTrue()
        attacker.vocationCanCombatValue = true
        attacker.vocationIdValue = 1

        attacker.skullValue = Skulls_t.SKULL_BLACK
        attacker.skullClientValue = Skulls_t.SKULL_NONE
        assertThat(Combat.isProtected(attacker, target, protectionLevel = 0)).isTrue()

        attacker.skullClientValue = Skulls_t.SKULL_YELLOW
        assertThat(Combat.isProtected(attacker, target, protectionLevel = 0)).isFalse()
    }

    @Test
    fun `canTargetCreature rejects self targeting and protection zones`() {
        // combat.cpp:321-374
        val attacker = FakeCombatPlayer()
        assertThat(Combat.canTargetCreature(attacker, attacker)).isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISPLAYER)

        val target = FakeCombatPlayer()
        attacker.zoneTypeValue = ZoneType.ZONE_PROTECTION
        assertThat(Combat.canTargetCreature(attacker, target))
            .isEqualTo(ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE)

        attacker.zoneTypeValue = ZoneType.ZONE_NORMAL
        target.zoneTypeValue = ZoneType.ZONE_PROTECTION
        assertThat(Combat.canTargetCreature(attacker, target))
            .isEqualTo(ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE)

        // IgnoreProtectionZone skips the whole zone block.
        attacker.flags.add(PlayerFlags_t.IgnoreProtectionZone)
        assertThat(Combat.canTargetCreature(attacker, target)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `canTargetCreature honors the nopvp zone gates for player combat targets`() {
        // combat.cpp:336-345
        val attacker = FakeCombatPlayer()
        val target = FakeCombatPlayer()
        attacker.zoneTypeValue = ZoneType.ZONE_NOPVP
        assertThat(Combat.canTargetCreature(attacker, target))
            .isEqualTo(ReturnValue.ACTIONNOTPERMITTEDINANOPVPZONE)

        attacker.zoneTypeValue = ZoneType.ZONE_NORMAL
        target.zoneTypeValue = ZoneType.ZONE_NOPVP
        assertThat(Combat.canTargetCreature(attacker, target))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKAPERSONINPROTECTIONZONE)
    }

    @Test
    fun `canTargetCreature reports cannot-use-combat and unattackable targets`() {
        // combat.cpp:348-354
        val attacker = FakeCombatPlayer()
        val playerTarget = FakeCombatPlayer()
        attacker.flags.add(PlayerFlags_t.CannotUseCombat)
        assertThat(Combat.canTargetCreature(attacker, playerTarget))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISPLAYER)

        val monsterTarget = FakeCombatActor()
        assertThat(Combat.canTargetCreature(attacker, monsterTarget))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISCREATURE)

        attacker.flags.clear()
        monsterTarget.attackableValue = false
        assertThat(Combat.canTargetCreature(attacker, monsterTarget))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISCREATURE)
    }

    @Test
    fun `canTargetCreature applies protected players and the secure mode gate`() {
        // combat.cpp:356-371
        val attacker = FakeCombatPlayer()
        val target = FakeCombatPlayer()
        target.levelValue = 10
        assertThat(Combat.canTargetCreature(attacker, target, protectionLevel = 50))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISPLAYER)

        target.levelValue = 100
        attacker.secureModeValue = true
        assertThat(Combat.canTargetCreature(attacker, target))
            .isEqualTo(ReturnValue.TURNSECUREMODETOATTACKUNMARKEDPLAYERS)

        // expert-pvp worlds skip the secure mode gate entirely.
        assertThat(Combat.canTargetCreature(attacker, target, expertPvpEnabled = true))
            .isEqualTo(ReturnValue.NOERROR)

        // a skull-client result other than SKULL_NONE also passes.
        attacker.skullClientValue = Skulls_t.SKULL_YELLOW
        assertThat(Combat.canTargetCreature(attacker, target)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `canDoCombat rejects mismatched aggressive targets`() {
        // combat.cpp:449-572
        val attacker = FakeCombatPlayer()
        val target = FakeCombatActor()
        assertThat(Combat.canDoCombat(attacker, target, aggressive = false)).isEqualTo(ReturnValue.NOERROR)

        val tile = FakeCombatTile()
        tile.properties.add(Combat.CONST_PROP_BLOCKPROJECTILE)
        target.tileValue = tile
        assertThat(Combat.canDoCombat(attacker, target, aggressive = true))
            .isEqualTo(ReturnValue.NOTENOUGHROOM)
    }

    @Test
    fun `canDoCombat gates player targets on protection zone and pvp world type`() {
        val attacker = FakeCombatPlayer()
        val target = FakeCombatPlayer()

        val pzTile = FakeCombatTile()
        pzTile.flags.add(TileFlags.TILESTATE_PROTECTIONZONE)
        target.tileValue = pzTile
        assertThat(Combat.canDoCombat(attacker, target, aggressive = true))
            .isEqualTo(ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE)

        target.permittedConditionInPzValue = true
        assertThat(Combat.canDoCombat(attacker, target, aggressive = true)).isEqualTo(ReturnValue.NOERROR)
        target.permittedConditionInPzValue = false
        target.tileValue = null

        // NO_PVP world: players may only be attacked inside a pvp zone.
        assertThat(Combat.canDoCombat(attacker, target, aggressive = true, noPvpWorld = true))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISPLAYER)
        target.zoneTypeValue = ZoneType.ZONE_PVP
        attacker.zoneTypeValue = ZoneType.ZONE_PVP
        assertThat(Combat.canDoCombat(attacker, target, aggressive = true, noPvpWorld = true))
            .isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `canDoCombat gates player targets on flags factions and npcs`() {
        val attacker = FakeCombatPlayer()

        val target = FakeCombatPlayer()
        target.flags.add(PlayerFlags_t.CannotBeAttacked)
        assertThat(Combat.canDoCombat(attacker, target, aggressive = true))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISPLAYER)
        target.flags.clear()

        attacker.flags.add(PlayerFlags_t.CannotAttackPlayer)
        assertThat(Combat.canDoCombat(attacker, target, aggressive = true))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISPLAYER)
        attacker.flags.clear()

        // same non-default, non-player faction denies.
        attacker.factionValue = Faction.Lion
        target.factionValue = Faction.Lion
        assertThat(Combat.canDoCombat(attacker, target, aggressive = true))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISPLAYER)

        val npc = FakeCombatActor()
        npc.npcValue = true
        assertThat(Combat.canDoCombat(attacker, npc, aggressive = true))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISCREATURE)
    }

    @Test
    fun `canDoCombat gates monster targets and friendly fire`() {
        val attacker = FakeCombatPlayer()
        val monster = FakeCombatActor()
        monster.monsterValue = FakeCombatMonster()

        attacker.flags.add(PlayerFlags_t.CannotAttackMonster)
        assertThat(Combat.canDoCombat(attacker, monster, aggressive = true))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISCREATURE)
        attacker.flags.clear()

        // a summon owned by a player inside a nopvp zone is protected.
        val summon = FakeCombatActor()
        summon.summonValue = true
        summon.monsterValue = FakeCombatMonster()
        summon.masterValue = FakeCombatPlayer()
        summon.zoneTypeValue = ZoneType.ZONE_NOPVP
        assertThat(Combat.canDoCombat(attacker, summon, aggressive = true))
            .isEqualTo(ReturnValue.ACTIONNOTPERMITTEDINANOPVPZONE)

        // a masterless default-faction monster may not attack a plain monster.
        val monsterAttacker = FakeCombatActor()
        monsterAttacker.monsterValue = FakeCombatMonster()
        assertThat(Combat.canDoCombat(monsterAttacker, monster, aggressive = true))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISCREATURE)

        // MONSTER_PERF_TEST_FRIENDLY_FIRE allows exactly that case.
        assertThat(
            Combat.canDoCombat(
                monsterAttacker,
                monster,
                aggressive = true,
                monsterPerfTestFriendlyFire = true,
            )
        ).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `canDoCombat checks the hostile-faction rule for monster targets`() {
        // combat.cpp:524-530: a non-default/non-player faction attacker whose
        // monster is not an enemy of the target's faction denies the hit.
        val attacker = FakeCombatActor()
        attacker.monsterValue = FakeCombatMonster()
        attacker.factionValue = Faction.Lion

        val target = FakeCombatActor()
        target.monsterValue = FakeCombatMonster()
        target.factionValue = Faction.Marid

        assertThat(Combat.canDoCombat(attacker, target, aggressive = true))
            .isEqualTo(ReturnValue.YOUMAYNOTATTACKTHISCREATURE)

        // Marid is an enemy faction: the hit passes the faction gate.
        attacker.monsterValue = FakeCombatMonster(mutableSetOf(Faction.Marid))
        assertThat(Combat.canDoCombat(attacker, target, aggressive = true))
            .isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `canDoCombat validates tiles for area combat`() {
        // combat.cpp:376-426
        val caster = FakeCombatPlayer()
        assertThat(Combat.canDoCombat(caster, null as CombatTile?, aggressive = true))
            .isEqualTo(ReturnValue.NOERROR)

        val tile = FakeCombatTile()
        assertThat(Combat.canDoCombat(caster, tile, aggressive = false)).isEqualTo(ReturnValue.NOERROR)

        tile.properties.add(Combat.CONST_PROP_BLOCKPROJECTILE)
        assertThat(Combat.canDoCombat(caster, tile, aggressive = true)).isEqualTo(ReturnValue.CANNOTTHROW)

        // magic walls are the only throwable blocked-projectile items.
        tile.itemIdList.add(Combat.ITEM_MAGICWALL)
        assertThat(Combat.canDoCombat(caster, tile, aggressive = true)).isEqualTo(ReturnValue.NOERROR)
        tile.itemIdList.clear()
        tile.itemIdList.add(Combat.ITEM_MAGICWALL_SAFE)
        assertThat(Combat.canDoCombat(caster, tile, aggressive = true)).isEqualTo(ReturnValue.NOERROR)
        tile.properties.clear()
        tile.itemIdList.clear()

        tile.flags.add(TileFlags.TILESTATE_PROTECTIONZONE)
        assertThat(Combat.canDoCombat(caster, tile, aggressive = true))
            .isEqualTo(ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE)
        tile.flags.clear()

        tile.teleport = true
        assertThat(Combat.canDoCombat(caster, tile, aggressive = true)).isEqualTo(ReturnValue.CANNOTTHROW)
        tile.teleport = false

        // floor checks compare the caster and tile z.
        caster.positionValue = caster.positionValue.copy(z = 6)
        tile.positionValue = tile.positionValue.copy(z = 7)
        assertThat(Combat.canDoCombat(caster, tile, aggressive = true))
            .isEqualTo(ReturnValue.FIRSTGODOWNSTAIRS)

        caster.positionValue = caster.positionValue.copy(z = 8)
        assertThat(Combat.canDoCombat(caster, tile, aggressive = true))
            .isEqualTo(ReturnValue.FIRSTGOUPSTAIRS)

        // IgnoreProtectionZone short-circuits after the z checks.
        caster.flags.add(PlayerFlags_t.IgnoreProtectionZone)
        caster.positionValue = caster.positionValue.copy(z = 7)
        assertThat(Combat.canDoCombat(caster, tile, aggressive = true)).isEqualTo(ReturnValue.NOERROR)
    }
}
