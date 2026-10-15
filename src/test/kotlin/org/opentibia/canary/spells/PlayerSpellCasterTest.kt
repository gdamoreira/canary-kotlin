package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the production [PlayerSpellCaster] adapter, focused on
 * the composed aim-at-target state:
 *
 *  - `Player::getAimAtTargetSpells` (`player.cpp:13369-13371`) populated by
 *    `Player::updateAimAtTargetSpells` (`player.cpp:13361-13367`, reached from
 *    `ProtocolGame::parseAimAtTarget`, protocolgame.cpp:12196-12209);
 *  - `applyInstantSpellDirection` (`spells.cpp:47-59`) reading it through the
 *    [SpellCaster] seam.
 */
class PlayerSpellCasterTest {

    private val world = FakeSpellWorld(learnSpells = false, rateSpellCooldown = 1.0)
    private val scripts = FakeSpellScriptRunner()
    private val engine = SpellEngine(Spells(), world, scripts)

    private fun player(position: Position = Position(100, 100, 7)): Player =
        Player(
            id = 1,
            name = "Hero",
            maxHealth = 150,
            health = 150,
            maxMana = 200,
            mana = 200,
            position = position,
        ).apply {
            level = 20
            direction = Direction.South
            // `Player::canSee(pos)` gates on the client viewport
            // (`Player::hasClient`, player.cpp:1402); a bare test player has no
            // protocol sink, so the sight gate is enabled explicitly.
            hasClient = { true }
        }

    private fun directionSpell(spellId: Int = 7): InstantSpell = InstantSpell().apply {
        spellType = SpellType.SPELL_INSTANT
        name = "test direction spell"
        words = "testspell"
        this.spellId = spellId
        group = SpellGroup.SPELLGROUP_ATTACK
        aggressive = true
        level = 1
        mana = 0
        needDirection = true
    }

    @Test
    fun `the aim set reflects the composed player state`() {
        val player = player()
        val caster = PlayerSpellCaster(player)

        assertThat(caster.getAimAtTargetSpells()).isEmpty()

        player.updateAimAtTargetSpells(7, 1)
        player.updateAimAtTargetSpells(9, 1)
        assertThat(caster.getAimAtTargetSpells()).containsExactlyInAnyOrder(7, 9)

        player.updateAimAtTargetSpells(7, 0)
        assertThat(caster.getAimAtTargetSpells()).containsExactly(9)
    }

    @Test
    fun `a marked direction spell turns toward the attacked creature`() {
        // spells.cpp:50-57: VARIANT_TARGETPOSITION + `getPrimaryDirection`.
        val player = player()
        player.updateAimAtTargetSpells(7, 1)
        player.setAttackedCreature(
            Monster(
                id = 42,
                name = "Rat",
                maxHealth = 100,
                health = 100,
                position = Position(102, 101, 7),
            ),
        )
        val caster = PlayerSpellCaster(player)

        assertThat(engine.playerCastInstant(caster, directionSpell())).isTrue()

        val variant = scripts.instantCalls.single().third
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_TARGETPOSITION)
        // dx = 2 > dy = 1 -> East -> caster position + east.
        assertThat(variant.pos).isEqualTo(Position(101, 100, 7))
    }

    @Test
    fun `an unmarked direction spell keeps the caster facing`() {
        val player = player()
        player.setAttackedCreature(
            Monster(
                id = 42,
                name = "Rat",
                maxHealth = 100,
                health = 100,
                position = Position(102, 101, 7),
            ),
        )
        val caster = PlayerSpellCaster(player)

        assertThat(engine.playerCastInstant(caster, directionSpell(spellId = 8))).isTrue()

        val variant = scripts.instantCalls.single().third
        assertThat(variant.type).isEqualTo(LuaVariantType.VARIANT_TARGETPOSITION)
        // Facing South keeps the position below the caster.
        assertThat(variant.pos).isEqualTo(Position(100, 101, 7))
    }

    @Test
    fun `the adapter projects the live player state`() {
        val player = player()
        val caster = PlayerSpellCaster(player)

        assertThat(caster.getID()).isEqualTo(1L)
        assertThat(caster.getName()).isEqualTo("Hero")
        assertThat(caster.getPosition()).isEqualTo(Position(100, 100, 7))
        assertThat(caster.getDirection()).isEqualTo(Direction.South)
        assertThat(caster.getLevel()).isEqualTo(20)
        assertThat(caster.getMagicLevel()).isEqualTo(player.getMagicLevel())
        assertThat(caster.getMana()).isEqualTo(200)
        assertThat(caster.getMaxMana()).isEqualTo(200)
        assertThat(caster.getSoul()).isEqualTo(player.features.vitals.getSoul())
        assertThat(caster.getVocationId()).isEqualTo(player.getVocationId())
        assertThat(caster.getWeaponType()).isEqualTo(WeaponType_t.WEAPON_NONE)
        assertThat(caster.getGroupId()).isZero()
        assertThat(caster.getSpellBook()).isSameAs(player.spellBook)
    }

    @Test
    fun `the adapter applies the mana clamp and the fight tick write`() {
        val player = player()
        val caster = PlayerSpellCaster(player)

        caster.changeMana(-50)
        assertThat(player.mana).isEqualTo(150)
        caster.changeMana(500)
        assertThat(player.mana).isEqualTo(200)
        caster.changeMana(-500)
        assertThat(player.mana).isZero()

        caster.addInFightTicks()
        assertThat(player.hasCondition(ConditionType.CONDITION_INFIGHT)).isTrue()
    }
}
