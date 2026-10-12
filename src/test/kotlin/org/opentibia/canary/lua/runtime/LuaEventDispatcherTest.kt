package org.opentibia.canary.lua.runtime

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position

/**
 * Proves that [LuaEventDispatcher] invokes the Lua callbacks registered through the
 * revscriptsys constructors with Canary's exact argument order and userdata wrapping.
 * Each Lua handler records what it observed into a global, which the test asserts.
 */
class LuaEventDispatcherTest {
    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `action onUse receives player item fromPosition target toPosition isHotkey in order`() {
        val environment = env()
        environment.loadChunk(
            """
            local a = Action()
            a:id(1945)
            function a.onUse(player, item, fromPosition, target, toPosition, isHotkey)
                seen_player = player:getName()
                seen_item = item:getId()
                seen_from = fromPosition:toString()
                seen_target_uid = target.uid
                seen_to = toPosition:toString()
                seen_hotkey = isHotkey
            end
            a:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        val handled = LuaEventDispatcher.actionOnUse(
            environment, 1945, player, Item(1945),
            Position(100, 100, 7), null, Position(101, 101, 7), true,
        )

        assertThat(handled).isTrue()
        assertThat(environment.getGlobal("seen_player").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_item").toint()).isEqualTo(1945)
        assertThat(environment.getGlobal("seen_from").tojstring()).isEqualTo("( 100, 100, 7 )")
        assertThat(environment.getGlobal("seen_target_uid").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("seen_to").tojstring()).isEqualTo("( 101, 101, 7 )")
        assertThat(environment.getGlobal("seen_hotkey").toboolean()).isTrue()
    }

    @Test
    fun `action onUse wraps a non-nil target thing as item userdata`() {
        val environment = env()
        environment.loadChunk(
            """
            local a = Action()
            a:id(1945)
            function a.onUse(player, item, fromPosition, target, toPosition, isHotkey)
                seen_target_item = target:getId()
            end
            a:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        LuaEventDispatcher.actionOnUse(
            environment, 1945, player, Item(1945),
            Position(100, 100, 7), Item(2000), Position(100, 100, 7), false,
        )

        assertThat(environment.getGlobal("seen_target_item").toint()).isEqualTo(2000)
    }

    @Test
    fun `action onUse returns false when no action is registered for the id`() {
        val environment = env()
        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

        assertThat(
            LuaEventDispatcher.actionOnUse(
                environment, 9999, player, Item(9999),
                Position(100, 100, 7), null, Position(100, 100, 7), false,
            )
        ).isFalse()
    }

    @Test
    fun `talkaction onSay receives player words param in order`() {
        val environment = env()
        environment.loadChunk(
            """
            local t = TalkAction("!test")
            function t.onSay(player, words, param)
                seen_say_player = player:getName()
                seen_words = words
                seen_param = param
            end
            t:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        val handled = LuaEventDispatcher.talkActionOnSay(environment, "!test", player, "hello")

        assertThat(handled).isTrue()
        assertThat(environment.getGlobal("seen_say_player").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_words").tojstring()).isEqualTo("!test")
        assertThat(environment.getGlobal("seen_param").tojstring()).isEqualTo("hello")
    }

    @Test
    fun `talkaction onSay returns false when the words are not registered`() {
        val environment = env()
        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))

        assertThat(LuaEventDispatcher.talkActionOnSay(environment, "!missing", player, "")).isFalse()
    }

    @Test
    fun `instant spell onCastSpell receives creature and variant`() {
        val environment = env()
        environment.loadChunk(
            """
            local s = Spell("instant")
            s:name("Test Strike")
            s:id(1)
            s:words("exura test")
            function s.onCastSpell(creature, variant)
                seen_cast_creature = creature:getName()
                seen_variant_type = variant.type
                seen_variant_number = variant.number
            end
            s:register()
            """.trimIndent()
        )

        val monster = Monster(2, "Rat", 20, 20, position = Position(50, 50, 7))
        val handled = LuaEventDispatcher.spellOnCastSpell(
            environment, "exura test", monster, LuaVariant(type = LuaVariantType.NUMBER, number = 42),
        )

        assertThat(handled).isTrue()
        assertThat(environment.getGlobal("seen_cast_creature").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("seen_variant_type").toint()).isEqualTo(LuaVariantType.NUMBER.ordinal)
        assertThat(environment.getGlobal("seen_variant_number").toint()).isEqualTo(42)
    }

    @Test
    fun `rune spell onCastSpell receives creature variant and isHotkey`() {
        val environment = env()
        environment.loadChunk(
            """
            local r = Spell("rune")
            r:name("Test Rune")
            r:runeId(3200)
            function r.onCastSpell(creature, variant, isHotkey)
                seen_rune_type = variant.type
                seen_rune_string = variant.string
                seen_rune_hotkey = isHotkey
            end
            r:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        val handled = LuaEventDispatcher.spellOnCastRune(
            environment, 3200, player, LuaVariant(type = LuaVariantType.STRING, text = "ab"), true,
        )

        assertThat(handled).isTrue()
        assertThat(environment.getGlobal("seen_rune_type").toint()).isEqualTo(LuaVariantType.STRING.ordinal)
        assertThat(environment.getGlobal("seen_rune_string").tojstring()).isEqualTo("ab")
        assertThat(environment.getGlobal("seen_rune_hotkey").toboolean()).isTrue()
    }

    @Test
    fun `creature event onLogin receives the player`() {
        val environment = env()
        environment.loadChunk(
            """
            local e = CreatureEvent("TestLogin")
            function e.onLogin(player)
                seen_login = player:getName()
            end
            e:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        assertThat(LuaEventDispatcher.creatureEventOnLogin(environment, "TestLogin", player)).isTrue()
        assertThat(environment.getGlobal("seen_login").tojstring()).isEqualTo("Hero")
    }

    @Test
    fun `creature event onThink receives creature and interval`() {
        val environment = env()
        environment.loadChunk(
            """
            local e = CreatureEvent("TestThink")
            function e.onThink(creature, interval)
                seen_think_creature = creature:getName()
                seen_think_interval = interval
            end
            e:register()
            """.trimIndent()
        )

        val monster = Monster(2, "Rat", 20, 20, position = Position(50, 50, 7))
        assertThat(LuaEventDispatcher.creatureEventOnThink(environment, "TestThink", monster, 1000)).isTrue()
        assertThat(environment.getGlobal("seen_think_creature").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("seen_think_interval").toint()).isEqualTo(1000)
    }

    @Test
    fun `creature event onDeath receives all six arguments in order`() {
        val environment = env()
        environment.loadChunk(
            """
            local e = CreatureEvent("TestDeath")
            function e.onDeath(creature, corpse, killer, mostDamageKiller, lastHitUnjustified, mostDamageUnjustified)
                seen_death_creature = creature:getName()
                seen_death_corpse = corpse:getId()
                seen_death_killer = killer:getName()
                seen_death_most = (mostDamageKiller == nil)
                seen_death_last = lastHitUnjustified
                seen_death_most_unjust = mostDamageUnjustified
            end
            e:register()
            """.trimIndent()
        )

        val creature = Monster(2, "Rat", 20, 0, position = Position(50, 50, 7))
        val player = Player(1, "Hero", 100, 100, position = Position(50, 50, 7))
        val handled = LuaEventDispatcher.creatureEventOnDeath(
            environment, "TestDeath", creature, Item(2159), player, null, true, false,
        )

        assertThat(handled).isTrue()
        assertThat(environment.getGlobal("seen_death_creature").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("seen_death_corpse").toint()).isEqualTo(2159)
        assertThat(environment.getGlobal("seen_death_killer").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_death_most").toboolean()).isTrue()
        assertThat(environment.getGlobal("seen_death_last").toboolean()).isTrue()
        assertThat(environment.getGlobal("seen_death_most_unjust").toboolean()).isFalse()
    }

    @Test
    fun `global event onRecord receives current and old`() {
        val environment = env()
        environment.loadChunk(
            """
            local g = GlobalEvent("TestRecord")
            function g.onRecord(current, old)
                seen_record_current = current
                seen_record_old = old
            end
            g:register()
            """.trimIndent()
        )

        assertThat(LuaEventDispatcher.globalEventOnRecord(environment, "TestRecord", 100, 50)).isTrue()
        assertThat(environment.getGlobal("seen_record_current").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("seen_record_old").toint()).isEqualTo(50)
    }

    @Test
    fun `global event onThink passes the scheduled interval`() {
        val environment = env()
        environment.loadChunk(
            """
            local g = GlobalEvent("TestTimer")
            g:interval(5000)
            function g.onThink(interval)
                seen_timer_interval = interval
            end
            g:register()
            """.trimIndent()
        )

        assertThat(LuaEventDispatcher.globalEventOnThink(environment, "TestTimer")).isTrue()
        assertThat(environment.getGlobal("seen_timer_interval").toint()).isEqualTo(5000)
    }

    @Test
    fun `global event onTime passes the scheduled interval`() {
        val environment = env()
        environment.loadChunk(
            """
            local g = GlobalEvent("TestTime")
            g:interval(2500)
            function g.onTime(interval)
                seen_time_interval = interval
            end
            g:register()
            """.trimIndent()
        )

        assertThat(LuaEventDispatcher.globalEventOnTime(environment, "TestTime")).isTrue()
        assertThat(environment.getGlobal("seen_time_interval").toint()).isEqualTo(2500)
    }

    @Test
    fun `move event onStepIn receives creature item position fromPosition in order`() {
        val environment = env()
        environment.loadChunk(
            """
            local m = MoveEvent()
            m:type("stepin")
            m:id(410)
            function m.onStepIn(creature, item, position, fromPosition)
                seen_step_creature = creature:getName()
                seen_step_item = item:getId()
                seen_step_pos = position:toString()
                seen_step_from = fromPosition:toString()
            end
            m:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        val handled = LuaEventDispatcher.moveEventOnStepIn(
            environment, 410, player, Item(410), Position(100, 100, 7), Position(99, 100, 7),
        )

        assertThat(handled).isTrue()
        assertThat(environment.getGlobal("seen_step_creature").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_step_item").toint()).isEqualTo(410)
        assertThat(environment.getGlobal("seen_step_pos").tojstring()).isEqualTo("( 100, 100, 7 )")
        assertThat(environment.getGlobal("seen_step_from").tojstring()).isEqualTo("( 99, 100, 7 )")
    }

    @Test
    fun `move event onEquip receives player item slot isCheck in order`() {
        val environment = env()
        environment.loadChunk(
            """
            local m = MoveEvent()
            m:type("equip")
            m:id(2160)
            function m.onEquip(player, item, slot, isCheck)
                seen_equip_player = player:getName()
                seen_equip_item = item:getId()
                seen_equip_slot = slot
                seen_equip_check = isCheck
            end
            m:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        val handled = LuaEventDispatcher.moveEventOnEquip(
            environment, 2160, player, Item(2160), 1, false,
        )

        assertThat(handled).isTrue()
        assertThat(environment.getGlobal("seen_equip_player").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_equip_item").toint()).isEqualTo(2160)
        assertThat(environment.getGlobal("seen_equip_slot").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("seen_equip_check").toboolean()).isFalse()
    }

    @Test
    fun `creature event onLogout receives the player`() {
        val environment = env()
        environment.loadChunk(
            """
            local e = CreatureEvent("TestLogout")
            function e.onLogout(player)
                seen_logout = player:getName()
            end
            e:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        assertThat(LuaEventDispatcher.creatureEventOnLogout(environment, "TestLogout", player)).isTrue()
        assertThat(environment.getGlobal("seen_logout").tojstring()).isEqualTo("Hero")
    }

    @Test
    fun `creature event onPrepareDeath receives creature killer and realDamage`() {
        val environment = env()
        environment.loadChunk(
            """
            local e = CreatureEvent("TestPrepareDeath")
            function e.onPrepareDeath(creature, killer, realDamage)
                seen_prepare_creature = creature:getName()
                seen_prepare_killer = killer:getName()
                seen_prepare_damage = realDamage
            end
            e:register()
            """.trimIndent()
        )

        val creature = Monster(2, "Rat", 20, 0, position = Position(50, 50, 7))
        val killer = Player(1, "Hero", 100, 100, position = Position(50, 50, 7))
        assertThat(
            LuaEventDispatcher.creatureEventOnPrepareDeath(environment, "TestPrepareDeath", creature, killer, 37)
        ).isTrue()
        assertThat(environment.getGlobal("seen_prepare_creature").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("seen_prepare_killer").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_prepare_damage").toint()).isEqualTo(37)
    }

    @Test
    fun `creature event onKill receives creature target and lastHit`() {
        val environment = env()
        environment.loadChunk(
            """
            local e = CreatureEvent("TestKill")
            function e.onKill(creature, target, lastHit)
                seen_kill_creature = creature:getName()
                seen_kill_target = target:getName()
                seen_kill_last_hit = lastHit
            end
            e:register()
            """.trimIndent()
        )

        val creature = Player(1, "Hero", 100, 100, position = Position(50, 50, 7))
        val target = Monster(2, "Rat", 20, 0, position = Position(50, 50, 7))
        assertThat(LuaEventDispatcher.creatureEventOnKill(environment, "TestKill", creature, target, true)).isTrue()
        assertThat(environment.getGlobal("seen_kill_creature").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_kill_target").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("seen_kill_last_hit").toboolean()).isTrue()
    }

    @Test
    fun `creature event onAdvance receives player skill oldLevel newLevel`() {
        val environment = env()
        environment.loadChunk(
            """
            local e = CreatureEvent("TestAdvance")
            function e.onAdvance(player, skill, oldLevel, newLevel)
                seen_advance_player = player:getName()
                seen_advance_skill = skill
                seen_advance_old = oldLevel
                seen_advance_new = newLevel
            end
            e:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        assertThat(LuaEventDispatcher.creatureEventOnAdvance(environment, "TestAdvance", player, 3, 10, 11)).isTrue()
        assertThat(environment.getGlobal("seen_advance_player").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_advance_skill").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("seen_advance_old").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("seen_advance_new").toint()).isEqualTo(11)
    }

    @Test
    fun `creature event onModalWindow receives player modalWindowId buttonId choiceId`() {
        val environment = env()
        environment.loadChunk(
            """
            local e = CreatureEvent("TestModal")
            function e.onModalWindow(player, modalWindowId, buttonId, choiceId)
                seen_modal_player = player:getName()
                seen_modal_id = modalWindowId
                seen_modal_button = buttonId
                seen_modal_choice = choiceId
            end
            e:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        assertThat(
            LuaEventDispatcher.creatureEventOnModalWindow(environment, "TestModal", player, 5, 2, 1)
        ).isTrue()
        assertThat(environment.getGlobal("seen_modal_player").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_modal_id").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("seen_modal_button").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("seen_modal_choice").toint()).isEqualTo(1)
    }

    @Test
    fun `creature event onTextEdit receives player item and text`() {
        val environment = env()
        environment.loadChunk(
            """
            local e = CreatureEvent("TestTextEdit")
            function e.onTextEdit(player, item, text)
                seen_text_player = player:getName()
                seen_text_item = item:getId()
                seen_text = text
            end
            e:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        assertThat(
            LuaEventDispatcher.creatureEventOnTextEdit(environment, "TestTextEdit", player, Item(1987), "hi")
        ).isTrue()
        assertThat(environment.getGlobal("seen_text_player").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_text_item").toint()).isEqualTo(1987)
        assertThat(environment.getGlobal("seen_text").tojstring()).isEqualTo("hi")
    }

    @Test
    fun `creature event onExtendedOpcode receives player opcode and buffer`() {
        val environment = env()
        environment.loadChunk(
            """
            local e = CreatureEvent("TestOpcode")
            function e.onExtendedOpcode(player, opcode, buffer)
                seen_opcode_player = player:getName()
                seen_opcode = opcode
                seen_buffer = buffer
            end
            e:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        assertThat(
            LuaEventDispatcher.creatureEventOnExtendedOpcode(environment, "TestOpcode", player, 200, "data")
        ).isTrue()
        assertThat(environment.getGlobal("seen_opcode_player").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_opcode").toint()).isEqualTo(200)
        assertThat(environment.getGlobal("seen_buffer").tojstring()).isEqualTo("data")
    }

    @Test
    fun `move event onStepOut receives creature item position fromPosition in order`() {
        val environment = env()
        environment.loadChunk(
            """
            local m = MoveEvent()
            m:type("stepout")
            m:id(410)
            function m.onStepOut(creature, item, position, fromPosition)
                seen_out_creature = creature:getName()
                seen_out_pos = position:toString()
                seen_out_from = fromPosition:toString()
            end
            m:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        assertThat(
            LuaEventDispatcher.moveEventOnStepOut(
                environment, 410, player, null, Position(100, 100, 7), Position(99, 100, 7),
            )
        ).isTrue()
        assertThat(environment.getGlobal("seen_out_creature").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_out_pos").tojstring()).isEqualTo("( 100, 100, 7 )")
        assertThat(environment.getGlobal("seen_out_from").tojstring()).isEqualTo("( 99, 100, 7 )")
    }

    @Test
    fun `move event onDeEquip receives player item slot isCheck in order`() {
        val environment = env()
        environment.loadChunk(
            """
            local m = MoveEvent()
            m:type("deequip")
            m:id(2160)
            function m.onDeEquip(player, item, slot, isCheck)
                seen_deequip_player = player:getName()
                seen_deequip_slot = slot
            end
            m:register()
            """.trimIndent()
        )

        val player = Player(1, "Hero", 100, 100, position = Position(100, 100, 7))
        assertThat(
            LuaEventDispatcher.moveEventOnDeEquip(environment, 2160, player, Item(2160), 4, true)
        ).isTrue()
        assertThat(environment.getGlobal("seen_deequip_player").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("seen_deequip_slot").toint()).isEqualTo(4)
    }

    @Test
    fun `move event onAddItem receives moveitem tileitem and position in order`() {
        val environment = env()
        environment.loadChunk(
            """
            local m = MoveEvent()
            m:type("additem")
            m:id(410)
            function m.onAddItem(moveitem, tileitem, pos)
                seen_add_move = moveitem:getId()
                seen_add_tile = tileitem:getId()
                seen_add_pos = pos:toString()
            end
            m:register()
            """.trimIndent()
        )

        assertThat(
            LuaEventDispatcher.moveEventOnAddItem(environment, 410, Item(410), Item(500), Position(10, 20, 7))
        ).isTrue()
        assertThat(environment.getGlobal("seen_add_move").toint()).isEqualTo(410)
        assertThat(environment.getGlobal("seen_add_tile").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("seen_add_pos").tojstring()).isEqualTo("( 10, 20, 7 )")
    }

    @Test
    fun `move event onRemoveItem receives moveitem tileitem and position in order`() {
        val environment = env()
        environment.loadChunk(
            """
            local m = MoveEvent()
            m:type("removeitem")
            m:id(410)
            function m.onRemoveItem(moveitem, tileitem, pos)
                seen_remove_move = moveitem:getId()
                seen_remove_tile = tileitem:getId()
                seen_remove_pos = pos:toString()
            end
            m:register()
            """.trimIndent()
        )

        assertThat(
            LuaEventDispatcher.moveEventOnRemoveItem(environment, 410, Item(410), Item(500), Position(10, 20, 7))
        ).isTrue()
        assertThat(environment.getGlobal("seen_remove_move").toint()).isEqualTo(410)
        assertThat(environment.getGlobal("seen_remove_tile").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("seen_remove_pos").tojstring()).isEqualTo("( 10, 20, 7 )")
    }
}
