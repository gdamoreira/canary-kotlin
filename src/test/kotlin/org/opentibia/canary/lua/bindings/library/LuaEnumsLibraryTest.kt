package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript tests for the Lua enum registrations, mirroring `LuaEnums::init`
 * (src/lua/functions/core/game/lua_enums.cpp).
 */
class LuaEnumsLibraryTest {

    private fun environment(): LuaEnvironment =
        LuaEnvironment().apply { initState() }

    @Test
    fun `combat condition and skill enums match the C++ values`() {
        val environment = environment()

        environment.loadChunk(
            """
            physical = COMBAT_PHYSICALDAMAGE
            healing = COMBAT_HEALING
            poison = CONDITION_POISON
            combatId = CONDITIONID_COMBAT
            paramTicks = CONDITION_PARAM_TICKS
            none = SKILL_NONE
            fist = SKILL_FIST
            maglevel = SKILL_MAGLEVEL
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("physical").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("healing").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("poison").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("combatId").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("paramTicks").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("none").toint()).isEqualTo(-1)
        assertThat(environment.getGlobal("fist").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("maglevel").toint()).isEqualTo(13)
    }

    @Test
    fun `item tile and return-value enums match the C++ registrations`() {
        val environment = environment()

        environment.loadChunk(
            """
            actionId = ITEM_ATTRIBUTE_ACTIONID
            duration = ITEM_ATTRIBUTE_DURATION
            depot = ITEM_TYPE_DEPOT
            rewardChest = ITEM_TYPE_REWARDCHEST
            protection = TILESTATE_PROTECTIONZONE
            noLogout = TILESTATE_NOLOGOUT
            noError = RETURNVALUE_NOERROR
            notPossible = RETURNVALUE_NOTPOSSIBLE
            itemNotYours = RETURNVALUE_ITEMISNOTYOURS
            fluidWater = FLUID_WATER
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("actionId").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("duration").toint()).isEqualTo(18)
        assertThat(environment.getGlobal("depot").toint()).isEqualTo(28)
        assertThat(environment.getGlobal("rewardChest").toint()).isEqualTo(37)
        assertThat(environment.getGlobal("protection").toint()).isEqualTo(1 shl 7)
        assertThat(environment.getGlobal("noLogout").toint()).isEqualTo(1 shl 9)
        assertThat(environment.getGlobal("noError").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("notPossible").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("itemNotYours").toint()).isEqualTo(88)
        assertThat(environment.getGlobal("fluidWater").toint()).isEqualTo(1)
    }

    @Test
    fun `talk message and event enums match the C++ registrations`() {
        val environment = environment()

        environment.loadChunk(
            """
            say = TALKTYPE_SAY
            channelY = TALKTYPE_CHANNEL_Y
            login = MESSAGE_LOGIN
            potion = MESSAGE_POTION
            think = MONSTERS_EVENT_THINK
            spawn = MONSTERS_EVENT_ON_SPAWN
            core = RELOAD_TYPE_CORE
            script = RELOAD_TYPE_SCRIPTS
            attackMode = FIGHTMODE_ATTACK
            spell = ORIGIN_SPELL
            instant = SPELL_INSTANT
            rune = SPELL_RUNE
            worldNoPvp = WORLD_TYPE_NO_PVP
            zonePvp = ZONE_PVP
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("say").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("channelY").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("login").toint()).isEqualTo(17)
        assertThat(environment.getGlobal("potion").toint()).isEqualTo(52)
        assertThat(environment.getGlobal("think").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("spawn").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("core").toint()).isEqualTo(11)
        assertThat(environment.getGlobal("script").toint()).isEqualTo(13)
        assertThat(environment.getGlobal("attackMode").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("spell").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("instant").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("rune").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("worldNoPvp").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("zonePvp").toint()).isEqualTo(2)
    }

    @Test
    fun `direction game state and creature event enums match the C++ values`() {
        val environment = environment()

        environment.loadChunk(
            """
            north = DIRECTION_NORTH
            northeast = DIRECTION_NORTHEAST
            normal = GAME_STATE_NORMAL
            maintain = GAME_STATE_MAINTAIN
            login = CREATURE_EVENT_LOGIN
            extended = CREATURE_EVENT_EXTENDED_OPCODE
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("north").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("northeast").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("normal").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("maintain").toint()).isEqualTo(6)
        assertThat(environment.getGlobal("login").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("extended").toint()).isEqualTo(12)
    }

    @Test
    fun `sound weapon skull and player flag enums match the C++ values`() {
        val environment = environment()

        environment.loadChunk(
            """
            silence = SOUND_EFFECT_TYPE_SILENCE
            swordAtk = SOUND_EFFECT_TYPE_MELEE_ATK_SWORD
            weaponSword = WEAPON_SWORD
            weaponFist = WEAPON_FIST
            skullRed = SKULL_RED
            flagCombat = CannotUseCombat
            flagLast = FlagLast
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("silence").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("swordAtk").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("weaponSword").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("weaponFist").toint()).isEqualTo(9)
        assertThat(environment.getGlobal("skullRed").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("flagCombat").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("flagLast").toint()).isEqualTo(40)
    }

    @Test
    fun `sound ambient and music enums match the C++ registrations`() {
        val environment = environment()

        environment.loadChunk(
            """
            ambientSilence = SOUND_EFFECT_TYPE_AMBIENT_SILENCE
            ambientCreatures = SOUND_EFFECT_TYPE_AMBIENT_INSECTS_CREATURES
            ambientStorm = SOUND_EFFECT_TYPE_AMBIENT_WIND_NOISES_STORM
            ambientBubbling = SOUND_EFFECT_TYPE_AMBIENT_BUBBLING_NOISES
            musicSilence = SOUND_EFFECT_TYPE_MUSIC_SILENCE
            musicTitle = SOUND_EFFECT_TYPE_MUSIC_TYPE_MUSIC_TITLE
            musicOne = SOUND_EFFECT_TYPE_MUSIC_TYPE_MUSIC_1
            musicTwo = SOUND_EFFECT_TYPE_MUSIC_TYPE_MUSIC_2
            musicImmediate = SOUND_EFFECT_TYPE_MUSIC_TYPE_MUSIC_IMMEDIATE
            musicImmediateTwo = SOUND_EFFECT_TYPE_MUSIC_TYPE_MUSIC_IMMEDIATE_2
            musicNineteen = SOUND_EFFECT_TYPE_MUSIC_TYPE_MUSIC_19
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("ambientSilence").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("ambientCreatures").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("ambientStorm").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("ambientBubbling").toint()).isEqualTo(89)
        assertThat(environment.getGlobal("musicSilence").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("musicTitle").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("musicOne").toint()).isEqualTo(8)
        assertThat(environment.getGlobal("musicTwo").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("musicImmediate").toint()).isEqualTo(22)
        assertThat(environment.getGlobal("musicImmediateTwo").toint()).isEqualTo(25)
        assertThat(environment.getGlobal("musicNineteen").toint()).isEqualTo(29)
    }
}
