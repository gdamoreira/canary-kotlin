package org.opentibia.canary.lua.bindings.events

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.ScriptError
import org.opentibia.canary.map.Position

/**
 * Covers the Canary event-function registrations missing from the first binding pass:
 * the `on<Callback>` setters and the remaining state setters of `Action`, `TalkAction`,
 * `CreatureEvent`, `GlobalEvent` and `MoveEvent` (src/lua/functions/events).
 */
class EventBindingsTest {
    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `action onUse callback setter registers the action`() {
        val environment = env()
        environment.loadChunk(
            """
            local a = Action()
            a:id(3483)
            setter_result = a:onUse(function(player, item, fromPosition, target, toPosition, isHotkey) return true end)
            a:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("setter_result").toboolean()).isTrue()
        assertThat(environment.actions.get(3483)!!.onUse.isfunction()).isTrue()
    }

    @Test
    fun `action onUse rejects a non-function callback`() {
        val environment = env()
        environment.loadChunk(
            """
            local a = Action()
            rejected = a:onUse(42)
            """.trimIndent()
        )
        assertThat(environment.getGlobal("rejected").toboolean()).isFalse()
    }

    @Test
    fun `action id collects every item id argument`() {
        val environment = env()
        environment.loadChunk(
            """
            local a = Action()
            function a.onUse(...) return true end
            a:id(2145, 2148)
            a:register()
            """.trimIndent()
        )

        assertThat(environment.actions.get(2145)).isNotNull
        assertThat(environment.actions.get(2148)).isNotNull
        assertThat(environment.actions.get(2148)!!.itemIds).containsExactly(2145, 2148)
    }

    @Test
    fun `action aid uid and position register in their own indexes`() {
        val environment = env()
        environment.globals.set("trigger_pos", environment.userdata.wrap("Position", Position(10, 20, 7)))
        environment.loadChunk(
            """
            local by_aid = Action()
            function by_aid.onUse(...) return true end
            by_aid:aid(100)
            by_aid:register()

            local by_uid = Action()
            function by_uid.onUse(...) return true end
            by_uid:uid(200)
            by_uid:register()

            local by_pos = Action()
            function by_pos.onUse(...) return true end
            by_pos:position(trigger_pos)
            by_pos:register()
            """.trimIndent()
        )

        assertThat(environment.actions.getByActionId(100)).isNotNull
        assertThat(environment.actions.getByUniqueId(200)).isNotNull
        assertThat(environment.actions.getByPosition(Position(10, 20, 7))).isNotNull
    }

    @Test
    fun `action blockWalls and checkFloor set the Canary use flags`() {
        val environment = env()
        environment.loadChunk(
            """
            local a = Action()
            a:id(9)
            a:allowFarUse(true)
            a:blockWalls(false)
            a:checkFloor(false)
            function a.onUse(...) return true end
            a:register()
            """.trimIndent()
        )

        val action = environment.actions.get(9)!!
        assertThat(action.allowFarUse).isTrue()
        assertThat(action.checkLineOfSight).isFalse()
        assertThat(action.checkFloor).isFalse()
    }

    @Test
    fun `action register without any id aid uid or position fails loudly`() {
        val environment = env()
        assertThatThrownBy {
            environment.loadChunk(
                """
                local a = Action()
                function a.onUse(...) return true end
                a:register()
                """.trimIndent()
            )
        }.isInstanceOf(ScriptError::class.java)
    }

    @Test
    fun `creature event callback setters infer the event type`() {
        val environment = env()
        environment.loadChunk(
            """
            local login = CreatureEvent("SetterLogin")
            login_setter = login:onLogin(function(player) return true end)
            login:register()

            local death = CreatureEvent("SetterDeath")
            death_setter = death:onDeath(function(creature, corpse, killer) return true end)
            death:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("login_setter").toboolean()).isTrue()
        assertThat(environment.getGlobal("death_setter").toboolean()).isTrue()
        assertThat(environment.creatureEvents.get("SetterLogin")!!.eventType).isEqualTo("login")
        assertThat(environment.creatureEvents.get("SetterDeath")!!.eventType).isEqualTo("death")
    }

    @Test
    fun `creature event callback setters reject non-functions`() {
        val environment = env()
        environment.loadChunk("local ev = CreatureEvent('Bad') bad = ev:onKill(nil)")
        assertThat(environment.getGlobal("bad").toboolean()).isFalse()
    }

    @Test
    fun `global event callback setters infer the event type`() {
        val environment = env()
        environment.loadChunk(
            """
            local startup = GlobalEvent("SetterStartup")
            startup_setter = startup:onStartup(function() return true end)
            startup:register()

            local record = GlobalEvent("SetterRecord")
            record_setter = record:onRecord(function(current, old) return true end)
            record:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("startup_setter").toboolean()).isTrue()
        assertThat(environment.getGlobal("record_setter").toboolean()).isTrue()
        assertThat(environment.globalEvents.get("SetterStartup")!!.eventType).isEqualTo("startup")
        assertThat(environment.globalEvents.get("SetterRecord")!!.eventType).isEqualTo("record")
    }

    @Test
    fun `move event premium and callback setter are captured`() {
        val environment = env()
        environment.loadChunk(
            """
            local move = MoveEvent()
            move:id(1234)
            move_premium = move:premium(true)
            move_setter = move:onEquip(function(player, item, slot, isCheck) return true end)
            move:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("move_premium").toboolean()).isTrue()
        assertThat(environment.getGlobal("move_setter").toboolean()).isTrue()
        val event = environment.moveEvents.getByItemId(1234).single()
        assertThat(event.eventType).isEqualTo("equip")
        assertThat(event.premium).isTrue()
    }

    @Test
    fun `talkaction onSay setter registers the spoken words`() {
        val environment = env()
        environment.loadChunk(
            """
            local t = TalkAction("!setter")
            t_setter = t:onSay(function(player, words, param) return true end)
            t:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("t_setter").toboolean()).isTrue()
        assertThat(environment.talkActions.get("!setter")!!.onSay.isfunction()).isTrue()
    }

    @Test
    fun `talkaction group type accepts Canary strings and numbers`() {
        val environment = env()
        environment.loadChunk(
            """
            local by_string = TalkAction("!god")
            function by_string.onSay(...) return true end
            string_result = by_string:groupType("god")
            by_string:register()

            local by_number = TalkAction("!gm")
            function by_number.onSay(...) return true end
            number_result = by_number:groupType(4)
            by_number:register()

            local bogus = TalkAction("!bogus")
            bogus_result = bogus:groupType("wizard")
            """.trimIndent()
        )

        assertThat(environment.getGlobal("string_result").toboolean()).isTrue()
        assertThat(environment.getGlobal("number_result").toboolean()).isTrue()
        assertThat(environment.getGlobal("bogus_result").toboolean()).isFalse()
        assertThat(environment.talkActions.get("!god")!!.groupType).isEqualTo(6)
        assertThat(environment.talkActions.get("!gm")!!.groupType).isEqualTo(4)
    }

    @Test
    fun `talkaction name description and group type accessors round-trip`() {
        val environment = env()
        environment.loadChunk(
            """
            local t = TalkAction("!info")
            t:groupType("tutor")
            t:setDescription("Shows information")
            function t.onSay(...) return true end
            t:register()

            read_name = t:getName()
            read_description = t:getDescription()
            read_group = t:getGroupType()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("read_name").tojstring()).isEqualTo("!info")
        assertThat(environment.getGlobal("read_description").tojstring()).isEqualTo("Shows information")
        assertThat(environment.getGlobal("read_group").toint()).isEqualTo(2)
    }
}
