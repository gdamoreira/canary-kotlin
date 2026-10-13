package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript tests for the remaining `LuaEnums` groups
 * (src/lua/functions/core/game/lua_enums.cpp:123-121, 245-1070, 1709-1738):
 * account/group, daily reward, bug/report, callback/combat params, virtues,
 * factions, client constants, flags, charms, sex/pronoun, text colors, speech
 * bubbles, map marks, item ids, Npc events, Forge, concoctions and the Wheel.
 */
class LuaEnumsRemainderTest {

    private fun environment(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `account group daily reward bug report callback and combat enums match C++`() {
        val environment = environment()
        environment.loadChunk(
            """
            accountNormal = ACCOUNT_TYPE_NORMAL
            accountGod = ACCOUNT_TYPE_GOD
            groupManager = GROUP_TYPE_COMMUNITYMANAGER
            dailyNotAvailable = DAILY_REWARD_NOTAVAILABLE
            bugTechnical = BUG_CATEGORY_TECHNICAL
            reportBot = REPORT_TYPE_BOT
            callbackChainPicker = CALLBACK_PARAM_CHAINPICKER
            combatParamChain = COMBAT_PARAM_CHAIN_EFFECT
            formulaDamage = COMBAT_FORMULA_DAMAGE
            factionLast = FACTION_LAST
            factionFafnar = FACTION_FAFNAR
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("accountNormal").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("accountGod").toint()).isEqualTo(6)
        assertThat(environment.getGlobal("groupManager").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("dailyNotAvailable").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("bugTechnical").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("reportBot").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("callbackChainPicker").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("combatParamChain").toint()).isEqualTo(12)
        assertThat(environment.getGlobal("formulaDamage").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("factionLast").toint()).isEqualTo(9)
        assertThat(environment.getGlobal("factionFafnar").toint()).isEqualTo(9)
    }

    @Test
    fun `virtue monk attr forge concoction and wheel enums match C++`() {
        val environment = environment()
        environment.loadChunk(
            """
            virtueSustain = Virtue_Sustain
            monkSpender = MonkSpell_Spender
            attrJustice = AttrSubId_VirtueOfJustice
            attrBloodRage = AttrSubId_BloodRageProtector
            forgeFiendish = FORGE_FIENDISH_MONSTER
            concoctionKooldown = Concoction_KooldownAid
            concoctionPhysical = Concoction_PhysicalAmplification
            wheelInstantGuiding = WHEEL_INSTANT_GUIDING_PRESENCE
            wheelStageStorm = WHEEL_STAGE_AVATAR_OF_STORM
            wheelGradeMax = WHEEL_GRADE_MAX
            wheelAvatarReduction = WHEEL_AVATAR_SKILL_DAMAGE_REDUCTION
            wheelStatCritChance = WHEEL_STAT_CRITICAL_CHANCE
            wheelBoostCritChance = WHEEL_BOOST_CRITICAL_CHANCE
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("virtueSustain").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("monkSpender").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("attrJustice").toint()).isEqualTo(13)
        assertThat(environment.getGlobal("attrBloodRage").toint()).isEqualTo(9)
        assertThat(environment.getGlobal("forgeFiendish").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("concoctionKooldown").toint()).isEqualTo(36723)
        assertThat(environment.getGlobal("concoctionPhysical").toint()).isEqualTo(36742)
        assertThat(environment.getGlobal("wheelInstantGuiding").toint()).isEqualTo(8)
        assertThat(environment.getGlobal("wheelStageStorm").toint()).isEqualTo(11)
        assertThat(environment.getGlobal("wheelGradeMax").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("wheelAvatarReduction").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("wheelStatCritChance").toint()).isEqualTo(16)
        assertThat(environment.getGlobal("wheelBoostCritChance").toint()).isEqualTo(12)
    }

    @Test
    fun `const me ani prop slot and slotp enums match C++`() {
        val environment = environment()
        environment.loadChunk(
            """
            meNone = CONST_ME_NONE
            meDragonHead = CONST_ME_DRAGONHEAD
            meBlackSmoke = CONST_ME_BLACKSMOKE
            meLast = CONST_ME_LAST
            aniNone = CONST_ANI_NONE
            aniCake = CONST_ANI_CAKE
            aniWeaponType = CONST_ANI_WEAPONTYPE
            propBlockSolid = CONST_PROP_BLOCKSOLID
            propSupportHangable = CONST_PROP_SUPPORTHANGABLE
            slotHead = CONST_SLOT_HEAD
            slotStoreInbox = CONST_SLOT_STORE_INBOX
            slotLast = CONST_SLOT_LAST
            slotpHead = SLOTP_HEAD
            slotpAmmo = SLOTP_AMMO
            slotpTwoHand = SLOTP_TWO_HAND
            slotpWhereEver = SLOTP_WHEREEVER
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("meNone").toint()).isZero()
        assertThat(environment.getGlobal("meDragonHead").toint()).isEqualTo(70)
        assertThat(environment.getGlobal("meBlackSmoke").toint()).isEqualTo(158)
        assertThat(environment.getGlobal("meLast").toint()).isEqualTo(304)
        assertThat(environment.getGlobal("aniNone").toint()).isZero()
        assertThat(environment.getGlobal("aniCake").toint()).isEqualTo(42)
        assertThat(environment.getGlobal("aniWeaponType").toint()).isEqualTo(254)
        assertThat(environment.getGlobal("propBlockSolid").toint()).isZero()
        assertThat(environment.getGlobal("propSupportHangable").toint()).isEqualTo(11)
        assertThat(environment.getGlobal("slotHead").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("slotStoreInbox").toint()).isEqualTo(11)
        assertThat(environment.getGlobal("slotLast").toint()).isEqualTo(11)
        assertThat(environment.getGlobal("slotpHead").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("slotpAmmo").toint()).isEqualTo(512)
        assertThat(environment.getGlobal("slotpTwoHand").toint()).isEqualTo(2048)
        assertThat(environment.getGlobal("slotpWhereEver").tolong()).isEqualTo(0xFFFFFFFFL)
    }

    @Test
    fun `flags lists charms sex pronoun respawn light and screenshots match C++`() {
        val environment = environment()
        environment.loadChunk(
            """
            flagNoLimit = FLAG_NOLIMIT
            flagAutoStack = FLAG_IGNOREAUTOSTACK
            flagLootPouch = FLAG_LOOTPOUCH
            guestList = GUEST_LIST
            subOwnerList = SUBOWNER_LIST
            charmOffensive = CHARM_OFFENSIVE
            charmScavenge = CHARM_SCAVENGE
            charmGut = CHARM_GUT
            sexMale = PLAYERSEX_MALE
            pronounName = PLAYERPRONOUN_NAME
            respawnDay = RESPAWNPERIOD_DAY
            lightSunset = LIGHT_STATE_SUNSET
            lightSunrise = LIGHT_STATE_SUNRISE
            emote = STORAGEVALUE_EMOTE
            immovable = IMMOVABLE_ACTION_ID
            lootChance = MAX_LOOTCHANCE
            screenshotGift = SCREENSHOT_TYPE_GIFTOFLIFE
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("flagNoLimit").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("flagAutoStack").toint()).isEqualTo(128)
        assertThat(environment.getGlobal("flagLootPouch").toint()).isEqualTo(512)
        assertThat(environment.getGlobal("guestList").toint()).isEqualTo(0x100)
        assertThat(environment.getGlobal("subOwnerList").toint()).isEqualTo(0x101)
        assertThat(environment.getGlobal("charmOffensive").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("charmScavenge").toint()).isEqualTo(13)
        assertThat(environment.getGlobal("charmGut").toint()).isEqualTo(14)
        assertThat(environment.getGlobal("sexMale").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("pronounName").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("respawnDay").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("lightSunset").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("lightSunrise").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("emote").toint()).isEqualTo(30008)
        assertThat(environment.getGlobal("immovable").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("lootChance").toint()).isEqualTo(100000)
        assertThat(environment.getGlobal("screenshotGift").toint()).isEqualTo(13)
    }

    @Test
    fun `text color speech bubble and map mark enums match C++`() {
        val environment = environment()
        environment.loadChunk(
            """
            blue = TEXTCOLOR_BLUE
            orange = TEXTCOLOR_ORANGE
            noColor = TEXTCOLOR_NONE
            sailor = SPEECHBUBBLE_SAILOR
            banker = SPEECHBUBBLE_BANKER
            hireling = SPEECHBUBBLE_HIRELING
            greenSouth = MAPMARK_GREENSOUTH
            tick = MAPMARK_TICK
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("blue").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("orange").toint()).isEqualTo(198)
        assertThat(environment.getGlobal("noColor").toint()).isEqualTo(255)
        assertThat(environment.getGlobal("sailor").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("banker").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("hireling").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("greenSouth").toint()).isEqualTo(19)
        assertThat(environment.getGlobal("tick").toint()).isZero()
    }

    @Test
    fun `report reasons client os and webhook colors match C++`() {
        val environment = environment()
        environment.loadChunk(
            """
            inappropriate = REPORT_REASON_NAMEINAPPROPRIATE
            serviceAgreement = REPORT_REASON_SERVICEAGREEMENT
            otcv8Web = CLIENTOS_OTCLIENTV8_WEB
            webhookGreen = WEBHOOK_COLOR_GREEN
            webhookBlue = WEBHOOK_COLOR_BLUE
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("inappropriate").toint()).isZero()
        assertThat(environment.getGlobal("serviceAgreement").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("otcv8Web").toint()).isEqualTo(25)
        assertThat(environment.getGlobal("webhookGreen").toint()).isEqualTo(0x00FF00)
        assertThat(environment.getGlobal("webhookBlue").toint()).isEqualTo(0x0000FF)
    }

    @Test
    fun `item ids and npc events match the C++ registrations`() {
        val environment = environment()
        environment.loadChunk(
            """
            bag = ITEM_BAG
            shoppingBag = ITEM_SHOPPING_BAG
            gold = ITEM_GOLD_COIN
            rewardChest = ITEM_REWARD_CHEST
            storeInbox = ITEM_STORE_INBOX
            forgeSliver = ITEM_FORGE_SLIVER
            hireling = HIRELING_LAMP
            npcThink = NPCS_EVENT_THINK
            npcClose = NPCS_EVENT_PLAYER_CLOSE_CHANNEL
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("bag").toint()).isEqualTo(2853)
        assertThat(environment.getGlobal("shoppingBag").toint()).isEqualTo(21411)
        assertThat(environment.getGlobal("gold").toint()).isEqualTo(3031)
        assertThat(environment.getGlobal("rewardChest").toint()).isEqualTo(19250)
        assertThat(environment.getGlobal("storeInbox").toint()).isEqualTo(23396)
        assertThat(environment.getGlobal("forgeSliver").toint()).isEqualTo(37109)
        assertThat(environment.getGlobal("hireling").toint()).isEqualTo(29432)
        assertThat(environment.getGlobal("npcThink").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("npcClose").toint()).isEqualTo(9)
    }
}
