package org.opentibia.canary.utils

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.creatures.PlayerPronoun_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.map.Position

/**
 * Port of the free helpers of `src/utils/tools.cpp`.
 *
 * The C++ functions that mutate a `std::string&` return the new value here,
 * matching the treatment already used by [replaceString]. Helpers that read
 * process-global state (config, item registry, logger, terminal) take the state
 * as a parameter or seam instead.
 */
object Tools {

    /** `AUTHENTICATOR_DIGITS` (src/core.hpp:12). */
    const val AUTHENTICATOR_DIGITS = 6

    /** `NETWORKMESSAGE_MAXSIZE` (src/utils/const.hpp:16). */
    const val NETWORKMESSAGE_MAXSIZE = 65500

    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    private val DATE_SHORT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun subtractElapsedTime(current: Long, elapsed: Long): Long {
        if (current <= 0 || elapsed < 0) return 0L
        return if (current >= elapsed) current - elapsed else 0L
    }

    fun replaceString(subject: String, search: String, replace: String): String {
        if (search.isEmpty()) return subject
        return subject.replace(search, replace)
    }

    fun transformToSHA1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun transformToSHA256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** `undoShift` (tools.cpp:102-112): `countr_zero` minus two, zero for values below `1 << 2`. */
    fun undoShift(value: Long): Int {
        if (value == 0L) {
            return 0
        }

        val trailingZeros = java.lang.Long.numberOfTrailingZeros(value)
        if (trailingZeros < 2) {
            return 0
        }
        return trailingZeros - 2
    }

    /**
     * `generateToken` (tools.cpp:255-297): HMAC-SHA1 one-time password over the
     * big-endian [ticks] counter truncated to [AUTHENTICATOR_DIGITS] digits.
     *
     * The C++ implementation builds the message from a `std::string`, so the
     * hashing is byte oriented here as well (the existing [transformToSHA1]
     * hashes UTF-8 text and would corrupt counter bytes above `0x7F`).
     */
    fun generateToken(key: String, ticks: Long): String {
        val message = ByteArray(8)
        var remaining = ticks
        for (i in 7 downTo 1) {
            message[i] = (remaining and 0xFF).toByte()
            remaining = remaining ushr 8
        }

        val keyBytes = ByteArray(key.length) { key[it].code.toByte() }
        val iKeyPad = ByteArray(64) { 0x36 }
        val oKeyPad = ByteArray(64) { 0x5C }
        for (i in keyBytes.indices) {
            iKeyPad[i] = (iKeyPad[i].toInt() xor keyBytes[i].toInt()).toByte()
            oKeyPad[i] = (oKeyPad[i].toInt() xor keyBytes[i].toInt()).toByte()
        }

        var hashed = bytesToHex(sha1(iKeyPad + message))

        val outerMessage = ByteArray(64 + hashed.length / 2)
        oKeyPad.copyInto(outerMessage)
        var outerIndex = 64
        var hexIndex = 0
        while (hexIndex < hashed.length) {
            outerMessage[outerIndex++] = hashed.substring(hexIndex, hexIndex + 2).toInt(16).toByte()
            hexIndex += 2
        }

        hashed = bytesToHex(sha1(outerMessage))

        val offset = hashed.substring(39, 40).toInt(16) and 0xF
        val truncHash = hashed.substring(2 * offset, 2 * offset + 8).toLong(16) and 0x7FFFFFFF

        var digits = truncHash.toString()
        val hashLen = digits.length
        val taken = minOf(hashLen, AUTHENTICATOR_DIGITS)
        digits = digits.substring(hashLen - taken)
        return digits.padStart(AUTHENTICATOR_DIGITS, '0')
    }

    /** `trim_right` (tools.cpp:309-311). */
    fun trimRight(source: String, t: Char): String {
        var end = source.length
        while (end > 0 && source[end - 1] == t) {
            end--
        }
        return source.substring(0, end)
    }

    /** `trim_left` (tools.cpp:313-315). */
    fun trimLeft(source: String, t: Char): String {
        var start = 0
        while (start < source.length && source[start] == t) {
            start++
        }
        return source.substring(start)
    }

    /** `keepFirstWordOnly` (tools.cpp:317-324): `erase(find(' '))`, so a leading space clears everything. */
    fun keepFirstWordOnly(str: String): String {
        val spacePos = str.indexOf(' ')
        return if (spacePos != -1) str.substring(0, spacePos) else str
    }

    /** `toLowerCaseString` (tools.cpp:326-328): C locale ASCII fold. */
    fun toLowerCaseString(source: String): String = source.map(::asciiLower).joinToString("")

    /** `asLowerCaseString` (tools.cpp:330-333). */
    fun asLowerCaseString(source: String): String = toLowerCaseString(source)

    /** `asUpperCaseString` (tools.cpp:335-338): C locale ASCII fold. */
    fun asUpperCaseString(source: String): String = source.map(::asciiUpper).joinToString("")

    /** `toCamelCase` (tools.cpp:340-358). */
    fun toCamelCase(str: String): String {
        val result = StringBuilder()
        var capitalizeNext = false

        for (ch in str) {
            if (ch == '_' || isCppSpace(ch) || ch == '-') {
                capitalizeNext = true
            } else if (capitalizeNext) {
                result.append(asciiUpper(ch))
                capitalizeNext = false
            } else {
                result.append(asciiLower(ch))
            }
        }

        return result.toString()
    }

    /** `toPascalCase` (tools.cpp:360-378). */
    fun toPascalCase(str: String): String {
        val result = StringBuilder()
        var capitalizeNext = true

        for (ch in str) {
            if (ch == '_' || isCppSpace(ch) || ch == '-') {
                capitalizeNext = true
            } else if (capitalizeNext) {
                result.append(asciiUpper(ch))
                capitalizeNext = false
            } else {
                result.append(ch)
            }
        }

        return result.toString()
    }

    /** `toSnakeCase` (tools.cpp:380-394). */
    fun toSnakeCase(str: String): String {
        val result = StringBuilder()
        for (ch in str) {
            when {
                ch in 'A'..'Z' -> result.append('_').append(asciiLower(ch))
                isCppSpace(ch) || ch == '-' -> result.append('_')
                else -> result.append(ch)
            }
        }
        return result.toString()
    }

    /** `toKebabCase` (tools.cpp:396-410). */
    fun toKebabCase(str: String): String {
        val result = StringBuilder()
        for (ch in str) {
            when {
                ch in 'A'..'Z' -> result.append('-').append(asciiLower(ch))
                isCppSpace(ch) || ch == '_' -> result.append('-')
                else -> result.append(ch)
            }
        }
        return result.toString()
    }

    /** `toStartCaseWithSpace` (tools.cpp:412-426). */
    fun toStartCaseWithSpace(str: String): String {
        val result = StringBuilder()
        for ((i, ch) in str.withIndex()) {
            if (i == 0 || ch in 'A'..'Z') {
                if (i > 0) {
                    result.append(' ')
                }
                result.append(asciiUpper(ch))
            } else {
                result.append(asciiLower(ch))
            }
        }
        return result.toString()
    }

    /**
     * `explodeString` (tools.cpp:428-439): the `--limit != -1` pre-decrement
     * means `limit` counts splits, `limit == 0` returns the whole string and any
     * negative value splits until the separator is missing.
     *
     * PARITY GAP: an empty separator with an unlimited limit never terminates
     * in C++; the port rejects it instead of hanging.
     */
    fun explodeString(inString: String, separator: String, limit: Int = -1): List<String> {
        if (separator.isEmpty() && limit < 0) {
            throw IllegalArgumentException("explodeString with an empty separator and unlimited limit does not terminate")
        }

        val returnVector = mutableListOf<String>()
        var start = 0
        var remaining = limit
        while (true) {
            if (--remaining == -1) {
                break
            }

            val end = inString.indexOf(separator, start)
            if (end == -1) {
                break
            }

            returnVector.add(inString.substring(start, end))
            start = end + separator.length
        }

        returnVector.add(inString.substring(start))
        return returnVector
    }

    /** `vectorAtoi` (tools.cpp:441-447) over [stoi]'s `std::stoi` semantics. */
    fun vectorAtoi(stringVector: List<String>): List<Int> = stringVector.map(::stoi)

    /** `trimString` (tools.cpp:482-485): strips only spaces, not general whitespace. */
    fun trimString(str: String): String = str.trim(' ')

    /** `convertIPToString` (tools.cpp:487-494): little-endian octet order. */
    fun convertIPToString(ip: Long): String =
        "${ip and 0xFF}.${(ip ushr 8) and 0xFF}.${(ip ushr 16) and 0xFF}.${(ip ushr 24) and 0xFF}"

    /** `formatDate` (tools.cpp:496-504): fmt formats `system_clock` time points in UTC. */
    fun formatDate(time: Long): String =
        Instant.ofEpochSecond(time).atOffset(ZoneOffset.UTC).format(DATE_FORMATTER)

    /** `formatDateShort` (tools.cpp:506-514). */
    fun formatDateShort(time: Long): String =
        Instant.ofEpochSecond(time).atOffset(ZoneOffset.UTC).format(DATE_SHORT_FORMATTER)

    /** `formatDateTime` (tools.cpp:516-524): integer-truncated milliseconds to seconds. */
    fun formatDateTime(ms: Long): String =
        Instant.ofEpochSecond(ms / 1000).atOffset(ZoneOffset.UTC).format(DATE_SHORT_FORMATTER)

    /** `formatEnumName` (tools.cpp:526-531). */
    fun formatEnumName(name: String): String = name.map { if (it == '_') ' ' else asciiLower(it) }.joinToString("")

    /** `getTimeNow` (tools.cpp:533-535). */
    fun getTimeNow(): Long = System.currentTimeMillis() / 1000L

    /** `getTimeMsNow` (tools.cpp:537-540). */
    fun getTimeMsNow(): Long = System.currentTimeMillis()

    /** `getTimeUsNow` (tools.cpp:542-545). */
    fun getTimeUsNow(): Long {
        val now = Instant.now()
        return now.epochSecond * 1_000_000L + now.nano / 1_000L
    }

    /** `getDirection` (tools.cpp:557-579): unknown strings fall back to north. */
    fun getDirection(string: String): Direction = when (string) {
        "north", "n", "0" -> Direction.North
        "east", "e", "1" -> Direction.East
        "south", "s", "2" -> Direction.South
        "west", "w", "3" -> Direction.West
        "southwest", "south west", "south-west", "sw", "4" -> Direction.SouthWest
        "southeast", "south east", "south-east", "se", "5" -> Direction.SouthEast
        "northwest", "north west", "north-west", "nw", "6" -> Direction.NorthWest
        "northeast", "north east", "north-east", "ne", "7" -> Direction.NorthEast
        else -> Direction.North
    }

    /** `adlerChecksum` (tools.cpp:1074-1097). */
    fun adlerChecksum(data: ByteArray, length: Int = data.size): Long {
        if (length > NETWORKMESSAGE_MAXSIZE) {
            return 0L
        }

        var a = 1L
        var b = 0L
        var index = 0
        var remaining = length
        while (remaining > 0) {
            var block = if (remaining > 5552) 5552 else remaining
            remaining -= block

            do {
                a += data[index++].toLong() and 0xFF
                b += a
            } while (--block != 0)

            a %= 65521
            b %= 65521
        }

        return (b shl 16) or a
    }

    /** `ucfirst` (tools.cpp:1099-1107): capitalizes the first non-space character. */
    fun ucfirst(str: String): String {
        val result = StringBuilder(str)
        for (i in result.indices) {
            if (result[i] != ' ') {
                result[i] = asciiUpper(result[i])
                break
            }
        }
        return result.toString()
    }

    /** `ucwords` (tools.cpp:1109-1123). */
    fun ucwords(str: String): String {
        if (str.isEmpty()) {
            return str
        }

        val result = StringBuilder(str)
        result[0] = asciiUpper(result[0])
        for (i in 1 until result.length) {
            if (result[i - 1] == ' ') {
                result[i] = asciiUpper(result[i])
            }
        }
        return result.toString()
    }

    /** `booleanString` (tools.cpp:1125-1132). */
    fun booleanString(str: String): Boolean {
        if (str.isEmpty()) {
            return false
        }

        val ch = asciiLower(str.first())
        return ch != 'f' && ch != 'n' && ch != '0'
    }

    /** `getSkillName` (tools.cpp:1022-1072). */
    fun getSkillName(skillid: Int): String = when (skillid) {
        skills_t.SKILL_FIST.value -> "fist fighting"
        skills_t.SKILL_CLUB.value -> "club fighting"
        skills_t.SKILL_SWORD.value -> "sword fighting"
        skills_t.SKILL_AXE.value -> "axe fighting"
        skills_t.SKILL_DISTANCE.value -> "distance fighting"
        skills_t.SKILL_SHIELD.value -> "shielding"
        skills_t.SKILL_FISHING.value -> "fishing"
        skills_t.SKILL_CRITICAL_HIT_CHANCE.value -> "critical hit chance"
        skills_t.SKILL_CRITICAL_HIT_DAMAGE.value -> "critical extra damage"
        skills_t.SKILL_LIFE_LEECH_CHANCE.value -> "life leech chance"
        skills_t.SKILL_LIFE_LEECH_AMOUNT.value -> "life leech"
        skills_t.SKILL_MANA_LEECH_CHANCE.value -> "mana leech chance"
        skills_t.SKILL_MANA_LEECH_AMOUNT.value -> "mana leech"
        skills_t.SKILL_MAGLEVEL.value -> "magic level"
        skills_t.SKILL_LEVEL.value -> "level"
        else -> "unknown"
    }

    /** `getFirstLine` (tools.cpp:1338-1348). */
    fun getFirstLine(str: String): String {
        val newline = str.indexOf('\n')
        return if (newline == -1) str else str.substring(0, newline)
    }

    /** `stringToItemAttribute` (tools.cpp:1245-1336): unknown names return `NONE`. */
    fun stringToItemAttribute(str: String): ItemAttribute = when (str) {
        "store" -> ItemAttribute.STORE
        "aid" -> ItemAttribute.ACTIONID
        "uid" -> ItemAttribute.UNIQUEID
        "description" -> ItemAttribute.DESCRIPTION
        "text" -> ItemAttribute.TEXT
        "date" -> ItemAttribute.DATE
        "writer" -> ItemAttribute.WRITER
        "name" -> ItemAttribute.NAME
        "article" -> ItemAttribute.ARTICLE
        "pluralname" -> ItemAttribute.PLURALNAME
        "weight" -> ItemAttribute.WEIGHT
        "mantra" -> ItemAttribute.MANTRA
        "attack" -> ItemAttribute.ATTACK
        "defense" -> ItemAttribute.DEFENSE
        "extradefense" -> ItemAttribute.EXTRADEFENSE
        "armor" -> ItemAttribute.ARMOR
        "hitchance" -> ItemAttribute.HITCHANCE
        "shootrange" -> ItemAttribute.SHOOTRANGE
        "owner" -> ItemAttribute.OWNER
        "duration" -> ItemAttribute.DURATION
        "decaystate" -> ItemAttribute.DECAYSTATE
        "corpseowner" -> ItemAttribute.CORPSEOWNER
        "charges" -> ItemAttribute.CHARGES
        "fluidtype" -> ItemAttribute.FLUIDTYPE
        "doorid" -> ItemAttribute.DOORID
        "timestamp" -> ItemAttribute.DURATION_TIMESTAMP
        "amount" -> ItemAttribute.AMOUNT
        "tier" -> ItemAttribute.TIER
        "lootmessagesuffix" -> ItemAttribute.LOOTMESSAGE_SUFFIX
        else -> ItemAttribute.NONE
    }

    /** `stringToSpellGroup` (tools.cpp:1633-1667): unknown and `"5"` return `NONE`. */
    fun stringToSpellGroup(value: String): SpellGroup = when (asLowerCaseString(value)) {
        "attack", "1" -> SpellGroup.SPELLGROUP_ATTACK
        "healing", "2" -> SpellGroup.SPELLGROUP_HEALING
        "support", "3" -> SpellGroup.SPELLGROUP_SUPPORT
        "special", "4" -> SpellGroup.SPELLGROUP_SPECIAL
        "crippling", "6" -> SpellGroup.SPELLGROUP_CRIPPLING
        "focus", "7" -> SpellGroup.SPELLGROUP_FOCUS
        "ultimatestrikes", "8" -> SpellGroup.SPELLGROUP_ULTIMATESTRIKES
        "burstsofnature", "9" -> SpellGroup.SPELLGROUP_BURSTS_OF_NATURE
        "greatbeams", "10" -> SpellGroup.SPELLGROUP_GREAT_BEAMS
        "virtue", "11" -> SpellGroup.SPELLGROUP_VIRTUE
        else -> SpellGroup.SPELLGROUP_NONE
    }

    /** `capitalizeWords` (tools.cpp:1675-1685). */
    fun capitalizeWords(source: String): String {
        val result = StringBuilder(toLowerCaseString(source))
        for (i in result.indices) {
            if (i == 0 || result[i - 1] == ' ' || result[i - 1] == '\'') {
                result[i] = asciiUpper(result[i])
            }
        }
        return result.toString()
    }

    /** `capitalizeWordsIgnoringString` (tools.cpp:1687-1700). */
    fun capitalizeWordsIgnoringString(source: String, stringToIgnore: String): String {
        val result = StringBuilder(toLowerCaseString(source))
        val indexFound = result.indexOf(stringToIgnore)

        for (i in result.indices) {
            if (indexFound > 0 &&
                i > indexFound - 1 &&
                i < indexFound + stringToIgnore.length
            ) {
                continue
            }
            if (i == 0 || result[i - 1] == ' ' || result[i - 1] == '\'') {
                result[i] = asciiUpper(result[i])
            }
        }
        return result.toString()
    }

    /** `validateName` (tools.cpp:1714-1743). */
    fun validateName(name: String): NameEval {
        val prohibitedWords = listOf(
            "owner", "gamemaster", "hoster", "admin", "staff", "tibia", "account", "god",
            "anal", "ass", "fuck", "sex", "hitler", "pussy", "dick", "rape", "cm", "gm",
            "tutor", "counsellor", "god"
        )
        val regexValidChars = Regex("^[a-zA-Z' ]+$")

        if (name.length < 3 || name.length > 18) {
            return NameEval.INVALID_LENGTH
        }

        if (!regexValidChars.matches(name)) {
            return NameEval.INVALID_CHARACTER
        }

        val tokens = name.split(' ').filter { it.isNotEmpty() }
        for (token in tokens) {
            if (token.length < 2) {
                return NameEval.INVALID_TOKEN_LENGTH
            }

            if (prohibitedWords.contains(token)) {
                return NameEval.INVALID_FORBIDDEN
            }
        }

        return NameEval.VALID
    }

    /** `isCaskItem` (tools.cpp:1745-1747). */
    fun isCaskItem(itemId: Int): Boolean =
        itemId in 25879..25883 || itemId in 25889..25893 || itemId in 25899..25902

    /** `getObjectCategoryName` (tools.cpp:1749-1810). */
    fun getObjectCategoryName(category: ObjectCategory_t): String = when (category) {
        ObjectCategory_t.OBJECTCATEGORY_ARMORS -> "Armors"
        ObjectCategory_t.OBJECTCATEGORY_NECKLACES -> "Amulets"
        ObjectCategory_t.OBJECTCATEGORY_BOOTS -> "Boots"
        ObjectCategory_t.OBJECTCATEGORY_CONTAINERS -> "Containers"
        ObjectCategory_t.OBJECTCATEGORY_DECORATION -> "Decoration"
        ObjectCategory_t.OBJECTCATEGORY_FOOD -> "Food"
        ObjectCategory_t.OBJECTCATEGORY_HELMETS -> "Helmets"
        ObjectCategory_t.OBJECTCATEGORY_LEGS -> "Legs"
        ObjectCategory_t.OBJECTCATEGORY_OTHERS -> "Others"
        ObjectCategory_t.OBJECTCATEGORY_POTIONS -> "Potions"
        ObjectCategory_t.OBJECTCATEGORY_RINGS -> "Rings"
        ObjectCategory_t.OBJECTCATEGORY_RUNES -> "Runes"
        ObjectCategory_t.OBJECTCATEGORY_SHIELDS -> "Shields"
        ObjectCategory_t.OBJECTCATEGORY_TOOLS -> "Tools"
        ObjectCategory_t.OBJECTCATEGORY_VALUABLES -> "Valuables"
        ObjectCategory_t.OBJECTCATEGORY_AMMO -> "Weapons: Ammunition"
        ObjectCategory_t.OBJECTCATEGORY_AXES -> "Weapons: Axes"
        ObjectCategory_t.OBJECTCATEGORY_CLUBS -> "Weapons: Clubs"
        ObjectCategory_t.OBJECTCATEGORY_DISTANCEWEAPONS -> "Weapons: Distance"
        ObjectCategory_t.OBJECTCATEGORY_SWORDS -> "Weapons: Swords"
        ObjectCategory_t.OBJECTCATEGORY_WANDS -> "Weapons: Wands"
        ObjectCategory_t.OBJECTCATEGORY_PREMIUMSCROLLS -> "Premium Scrolls"
        ObjectCategory_t.OBJECTCATEGORY_TIBIACOINS -> "Tibia Coins"
        ObjectCategory_t.OBJECTCATEGORY_CREATUREPRODUCTS -> "Creature Products"
        ObjectCategory_t.OBJECTCATEGORY_GOLD -> "Gold"
        ObjectCategory_t.OBJECTCATEGORY_QUIVERS -> "Quiver"
        ObjectCategory_t.OBJECTCATEGORY_FISTWEAPONS -> "Fist Weapons"
        ObjectCategory_t.OBJECTCATEGORY_DEFAULT -> "Unassigned Loot"
        ObjectCategory_t.OBJECTCATEGORY_NONE -> ""
    }

    private val VALID_OBJECT_CATEGORIES: Set<ObjectCategory_t> = setOf(
        ObjectCategory_t.OBJECTCATEGORY_NONE,
        ObjectCategory_t.OBJECTCATEGORY_ARMORS,
        ObjectCategory_t.OBJECTCATEGORY_NECKLACES,
        ObjectCategory_t.OBJECTCATEGORY_BOOTS,
        ObjectCategory_t.OBJECTCATEGORY_CONTAINERS,
        ObjectCategory_t.OBJECTCATEGORY_DECORATION,
        ObjectCategory_t.OBJECTCATEGORY_FOOD,
        ObjectCategory_t.OBJECTCATEGORY_HELMETS,
        ObjectCategory_t.OBJECTCATEGORY_LEGS,
        ObjectCategory_t.OBJECTCATEGORY_OTHERS,
        ObjectCategory_t.OBJECTCATEGORY_POTIONS,
        ObjectCategory_t.OBJECTCATEGORY_RINGS,
        ObjectCategory_t.OBJECTCATEGORY_RUNES,
        ObjectCategory_t.OBJECTCATEGORY_SHIELDS,
        ObjectCategory_t.OBJECTCATEGORY_TOOLS,
        ObjectCategory_t.OBJECTCATEGORY_VALUABLES,
        ObjectCategory_t.OBJECTCATEGORY_AMMO,
        ObjectCategory_t.OBJECTCATEGORY_AXES,
        ObjectCategory_t.OBJECTCATEGORY_CLUBS,
        ObjectCategory_t.OBJECTCATEGORY_DISTANCEWEAPONS,
        ObjectCategory_t.OBJECTCATEGORY_SWORDS,
        ObjectCategory_t.OBJECTCATEGORY_WANDS,
        ObjectCategory_t.OBJECTCATEGORY_PREMIUMSCROLLS,
        ObjectCategory_t.OBJECTCATEGORY_TIBIACOINS,
        ObjectCategory_t.OBJECTCATEGORY_CREATUREPRODUCTS,
        ObjectCategory_t.OBJECTCATEGORY_QUIVERS,
        ObjectCategory_t.OBJECTCATEGORY_FISTWEAPONS,
        ObjectCategory_t.OBJECTCATEGORY_GOLD,
        ObjectCategory_t.OBJECTCATEGORY_DEFAULT,
    )

    /** `isValidObjectCategory` (tools.cpp:1812-1845). */
    fun isValidObjectCategory(category: ObjectCategory_t): Boolean = VALID_OBJECT_CATEGORIES.contains(category)

    /** `forgeBonus` (tools.cpp:1847-1882). */
    fun forgeBonus(number: Int): Int = when {
        number < 7400 -> 0
        number < 9000 -> 1
        number < 9500 -> 2
        number < 9525 -> 3
        number < 9550 -> 4
        number < 9950 -> 5
        number < 9975 -> 6
        else -> 7
    }

    /** `formatPrice` (tools.cpp:1884-1893): reverses, folds `000` into `k`, reverses back. */
    fun formatPrice(price: String, space: Boolean = false): String {
        val folded = price.reversed().replace("000", "k").reversed()
        return if (space) folded.replaceFirst("k", " k") else folded
    }

    /** `getPlayerSubjectPronoun` (tools.cpp:1895-1910). */
    fun getPlayerSubjectPronoun(pronoun: PlayerPronoun_t, sex: PlayerSex_t, name: String): String = when (pronoun) {
        PlayerPronoun_t.PLAYERPRONOUN_THEY -> "they"
        PlayerPronoun_t.PLAYERPRONOUN_SHE -> "she"
        PlayerPronoun_t.PLAYERPRONOUN_HE -> "he"
        PlayerPronoun_t.PLAYERPRONOUN_ZE -> "ze"
        PlayerPronoun_t.PLAYERPRONOUN_NAME -> name
        else -> if (sex == PlayerSex_t.FEMALE) "she" else "he"
    }

    /** `getPlayerObjectPronoun` (tools.cpp:1912-1927). */
    fun getPlayerObjectPronoun(pronoun: PlayerPronoun_t, sex: PlayerSex_t, name: String): String = when (pronoun) {
        PlayerPronoun_t.PLAYERPRONOUN_THEY -> "them"
        PlayerPronoun_t.PLAYERPRONOUN_SHE -> "her"
        PlayerPronoun_t.PLAYERPRONOUN_HE -> "him"
        PlayerPronoun_t.PLAYERPRONOUN_ZE -> "zir"
        PlayerPronoun_t.PLAYERPRONOUN_NAME -> name
        else -> if (sex == PlayerSex_t.FEMALE) "her" else "him"
    }

    /** `getPlayerPossessivePronoun` (tools.cpp:1929-1944). */
    fun getPlayerPossessivePronoun(pronoun: PlayerPronoun_t, sex: PlayerSex_t, name: String): String = when (pronoun) {
        PlayerPronoun_t.PLAYERPRONOUN_THEY -> "their"
        PlayerPronoun_t.PLAYERPRONOUN_SHE -> "her"
        PlayerPronoun_t.PLAYERPRONOUN_HE -> "his"
        PlayerPronoun_t.PLAYERPRONOUN_ZE -> "zir"
        PlayerPronoun_t.PLAYERPRONOUN_NAME -> "$name's"
        else -> if (sex == PlayerSex_t.FEMALE) "her" else "his"
    }

    /** `getPlayerReflexivePronoun` (tools.cpp:1946-1961). */
    fun getPlayerReflexivePronoun(pronoun: PlayerPronoun_t, sex: PlayerSex_t, name: String): String = when (pronoun) {
        PlayerPronoun_t.PLAYERPRONOUN_THEY -> "themself"
        PlayerPronoun_t.PLAYERPRONOUN_SHE -> "herself"
        PlayerPronoun_t.PLAYERPRONOUN_HE -> "himself"
        PlayerPronoun_t.PLAYERPRONOUN_ZE -> "zirself"
        PlayerPronoun_t.PLAYERPRONOUN_NAME -> name
        else -> if (sex == PlayerSex_t.FEMALE) "herself" else "himself"
    }

    /** `getVerbForPronoun` (tools.cpp:1963-1968). */
    fun getVerbForPronoun(pronoun: PlayerPronoun_t, pastTense: Boolean = false): String {
        if (pronoun == PlayerPronoun_t.PLAYERPRONOUN_THEY) {
            return if (pastTense) "were" else "are"
        }
        return if (pastTense) "was" else "is"
    }

    /** `formatWithArticle` (tools.cpp:1970-1993). */
    fun formatWithArticle(value: String, withSpace: Boolean = true): String {
        if (value.isEmpty()) {
            return ""
        }

        var modifiedValue = value
        for (article in listOf("a ", "an ")) {
            if (value.length > article.length &&
                value.regionMatches(0, article, 0, article.length, ignoreCase = true)
            ) {
                modifiedValue = value.substring(article.length)
                break
            }
        }

        if (modifiedValue.isEmpty()) {
            return ""
        }

        val character = asciiLower(modifiedValue.first())
        val article = if (character == 'a' || character == 'e' || character == 'i' || character == 'o' || character == 'u') "an" else "a"
        return (if (withSpace) " " else "") + article + " " + modifiedValue + "."
    }

    /**
     * `split` (tools.cpp:1995-2005): `std::getline` tokenization, so a trailing
     * delimiter does not produce a trailing empty token but interior ones do,
     * and every token is space-trimmed.
     */
    fun split(str: String, delimiter: Char = ','): List<String> {
        if (str.isEmpty()) {
            return emptyList()
        }

        val tokens = mutableListOf<String>()
        var start = 0
        while (start < str.length) {
            val end = str.indexOf(delimiter, start)
            if (end == -1) {
                tokens.add(trimString(str.substring(start)))
                break
            }

            tokens.add(trimString(str.substring(start, end)))
            start = end + 1
        }
        return tokens
    }

    /** `getFormattedTimeRemaining` (tools.cpp:2007-2034). */
    fun getFormattedTimeRemaining(time: Long, now: Long = getTimeNow()): String {
        val timeRemaining = time - now
        val days = (timeRemaining / 86400).toInt()
        if (days > 1) {
            return "$days days"
        }

        val hours = ((timeRemaining % 86400) / 3600).toInt()
        val minutes = ((timeRemaining % 3600) / 60).toInt()
        val seconds = (timeRemaining % 60).toInt()

        val output = StringBuilder()
        if (hours == 0 && minutes == 0 && seconds > 0) {
            output.append(" less than 1 minute")
        } else {
            if (hours > 0) {
                output.append(hours).append(" hour").append(if (hours != 1) "s" else "")
            }
            if (minutes > 0) {
                if (hours > 0) {
                    output.append(" and ")
                }
                output.append(minutes).append(" minute").append(if (minutes != 1) "s" else "")
            }
        }

        return output.toString()
    }

    /** `getNumberOfCores` (tools.cpp:2036-2039). */
    fun getNumberOfCores(): Int = Runtime.getRuntime().availableProcessors()

    /** `getStashSize` (tools.cpp:247-253): the item registry is injected as a stack-size lookup. */
    fun getStashSize(itemList: Map<Int, Int>, stackSize: (Int) -> Int): Int {
        var size = 0
        for ((itemId, itemCount) in itemList) {
            size += ceil(itemCount.toFloat() / stackSize(itemId).toFloat()).toInt()
            size = size and 0xFFFF
        }
        return size
    }

    /** `formatNumber` (tools.cpp:2162-2170). */
    fun formatNumber(number: ULong): String {
        val formattedNumber = StringBuilder(number.toString())
        var pos = formattedNumber.length - 3
        while (pos > 0) {
            formattedNumber.insert(pos, ',')
            pos -= 3
        }
        return formattedNumber.toString()
    }

    /** `sleep_for` (tools.cpp:2172-2174). */
    fun sleepFor(ms: Long) {
        Thread.sleep(ms)
    }

    /** `toKey` (tools.cpp:2181-2188): lowercase, spaces to `-`, drop the remaining whitespace. */
    fun toKey(str: String): String {
        val builder = StringBuilder()
        for (ch in toLowerCaseString(str)) {
            when {
                ch == ' ' -> builder.append('-')
                isCppSpace(ch) -> Unit
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    /** `convertWheelGemAffinityToDomain` (tools.cpp:2190-2204): unknown affinities return 0. */
    fun convertWheelGemAffinityToDomain(affinity: Int): Int = when (affinity) {
        0 -> 1
        1 -> 3
        2 -> 0
        3 -> 2
        else -> 0
    }

    /** `caseInsensitiveCompare` (tools.cpp:2206-2219): `length = null` is `std::string_view::npos`. */
    fun caseInsensitiveCompare(str1: String, str2: String, length: Int? = null): Boolean {
        val compareLength: Int
        if (length == null) {
            if (str1.length != str2.length) {
                return false
            }
            compareLength = str1.length
        } else {
            compareLength = minOf(length, str1.length, str2.length)
        }

        for (i in 0 until compareLength) {
            if (asciiLower(str1[i]) != asciiLower(str2[i])) {
                return false
            }
        }
        return true
    }

    /** `getMaxValuePerSkill` (tools.cpp:2241-2249): the critical-chance config is injected. */
    fun getMaxValuePerSkill(criticalChance: Int): Map<Int, Int> = mapOf(
        skills_t.SKILL_LIFE_LEECH_CHANCE.value to 100,
        skills_t.SKILL_MANA_LEECH_CHANCE.value to 100,
        skills_t.SKILL_CRITICAL_HIT_CHANCE.value to 100 * criticalChance,
    )

    /** `calculateEquipmentLoss` (tools.cpp:2251-2276). */
    fun calculateEquipmentLoss(blessingAmount: Int, isContainer: Boolean = false): Double {
        val lossPercent = when (blessingAmount) {
            0 -> 10.0
            1 -> 7.0
            2 -> 4.5
            3 -> 2.5
            4 -> 1.0
            else -> 0.0
        }
        return if (isContainer) lossPercent * 10 else lossPercent
    }

    /** `calculateMaxPvpReduction` (tools.cpp:2278-2290), including the `uint8_t` result width. */
    fun calculateMaxPvpReduction(blessCount: Int, isPromoted: Boolean = false): Int {
        var result = (80 + 2 * blessCount - blessCount / 3) and 0xFF
        if (blessCount == 5) {
            result = (result - 1) and 0xFF
        }
        if (isPromoted) {
            result = (result + 6) and 0xFF
        }
        return result
    }

    /** `getVocationIdFromClientId` (tools.cpp:2292-2315). */
    fun getVocationIdFromClientId(clientId: Long): Long {
        if (clientId == 0xFFFFFFFFL) {
            return clientId
        }

        return when (clientId) {
            Vocation.VOCATION_KNIGHT_CIP.toLong() -> Vocation.VOCATION_KNIGHT.toLong()
            Vocation.VOCATION_PALADIN_CIP.toLong() -> Vocation.VOCATION_PALADIN.toLong()
            Vocation.VOCATION_SORCERER_CIP.toLong() -> Vocation.VOCATION_SORCERER.toLong()
            Vocation.VOCATION_DRUID_CIP.toLong() -> Vocation.VOCATION_DRUID.toLong()
            Vocation.VOCATION_MONK_CIP.toLong() -> Vocation.VOCATION_MONK.toLong()
            Vocation.VOCATION_NONE.toLong() -> Vocation.VOCATION_NONE.toLong()
            else -> 0xFFFFFFFFL
        }
    }

    /** `getPrimaryDirection` (tools.cpp:2317-2326). */
    fun getPrimaryDirection(from: Position, to: Position): Direction {
        val dx = to.x - from.x
        val dy = to.y - from.y

        return if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            if (dx > 0) Direction.East else Direction.West
        } else {
            if (dy > 0) Direction.South else Direction.North
        }
    }

    /** `getWeaponName` (tools.cpp:1134-1155). */
    fun getWeaponName(weaponType: WeaponType_t): String = when (weaponType) {
        WeaponType_t.WEAPON_FIST -> "fist"
        WeaponType_t.WEAPON_SWORD -> "sword"
        WeaponType_t.WEAPON_CLUB -> "club"
        WeaponType_t.WEAPON_AXE -> "axe"
        WeaponType_t.WEAPON_DISTANCE -> "distance"
        WeaponType_t.WEAPON_WAND -> "wand"
        WeaponType_t.WEAPON_AMMO -> "ammunition"
        WeaponType_t.WEAPON_MISSILE -> "missile"
        else -> ""
    }

    /** `getWeaponType` (tools.cpp:1157-1177): exact lowercase keys, unknown names return `WEAPON_NONE`. */
    fun getWeaponType(name: String): WeaponType_t = when (name) {
        "none" -> WeaponType_t.WEAPON_NONE
        "fist" -> WeaponType_t.WEAPON_FIST
        "sword" -> WeaponType_t.WEAPON_SWORD
        "club" -> WeaponType_t.WEAPON_CLUB
        "axe" -> WeaponType_t.WEAPON_AXE
        "shield" -> WeaponType_t.WEAPON_SHIELD
        "distance" -> WeaponType_t.WEAPON_DISTANCE
        "wand" -> WeaponType_t.WEAPON_WAND
        "ammo" -> WeaponType_t.WEAPON_AMMO
        "missile" -> WeaponType_t.WEAPON_MISSILE
        else -> WeaponType_t.WEAPON_NONE
    }

    private val COMBAT_TYPE_NAMES: Map<CombatType, String> = mapOf(
        CombatType.DROWNDAMAGE to "drown",
        CombatType.DEATHDAMAGE to "death",
        CombatType.ENERGYDAMAGE to "energy",
        CombatType.EARTHDAMAGE to "earth",
        CombatType.FIREDAMAGE to "fire",
        CombatType.HEALING to "healing",
        CombatType.HOLYDAMAGE to "holy",
        CombatType.ICEDAMAGE to "ice",
        CombatType.UNDEFINEDDAMAGE to "undefined",
        CombatType.LIFEDRAIN to "lifedrain",
        CombatType.MANADRAIN to "manadrain",
        CombatType.PHYSICALDAMAGE to "physical",
        CombatType.AGONYDAMAGE to "agony",
        CombatType.NEUTRALDAMAGE to "neutral",
    )

    private val COMBAT_NAME_TYPES: Map<String, CombatType> =
        COMBAT_TYPE_NAMES.entries.associate { (type, name) -> name to type }

    /** `getCombatName` (tools.cpp:1199-1205): unknown combat types return `"unknown"`. */
    fun getCombatName(combatType: CombatType): String = COMBAT_TYPE_NAMES[combatType] ?: "unknown"

    /** `getCombatTypeByName` (tools.cpp:1207-1213): unknown names return `COMBAT_NONE`. */
    fun getCombatTypeByName(combatname: String): CombatType = COMBAT_NAME_TYPES[combatname] ?: CombatType.NONE

    /**
     * `combatTypeToIndex` (tools.cpp:1215-1226): the `magic_enum` index for the
     * declared combat types, otherwise `COMBAT_NONE` (`255`).
     */
    fun combatTypeToIndex(combatType: CombatType): Int =
        if (combatType.ordinal < CombatType.COUNT.value) combatType.ordinal else CombatType.NONE.value

    /** `combatTypeToName` (tools.cpp:1228-1239): `magic_enum` name, or `""` when out of range. */
    fun combatTypeToName(combatType: CombatType): String =
        if (combatType.ordinal < CombatType.COUNT.value) formatEnumName("COMBAT_${combatType.name}") else ""

    /** `indexToCombatType` (tools.cpp:1241-1243): `null` for values without a named enumerator. */
    fun indexToCombatType(v: Int): CombatType? = CombatType.entries.firstOrNull { it.value == v }

    private fun asciiLower(c: Char): Char = if (c in 'A'..'Z') c + 32 else c

    private fun asciiUpper(c: Char): Char = if (c in 'a'..'z') c - 32 else c

    private fun isCppSpace(c: Char): Boolean =
        c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\u000C' || c == '\r'

    private fun sha1(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(bytes)

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /**
     * Replicates `std::stoi` (base 10): skip leading whitespace, accept one
     * sign, require a digit and throw [NumberFormatException] outside the
     * `int32` range. Stops at the first non-digit character.
     */
    private fun stoi(value: String): Int {
        var index = 0
        while (index < value.length && isCppSpace(value[index])) {
            index++
        }

        var negative = false
        if (index < value.length && (value[index] == '+' || value[index] == '-')) {
            negative = value[index] == '-'
            index++
        }

        val digitsStart = index
        var accumulator = 0L
        val limit = if (negative) 2147483648L else 2147483647L
        while (index < value.length && value[index] in '0'..'9') {
            val digit = value[index] - '0'
            if (accumulator > (limit - digit) / 10) {
                throw NumberFormatException("For input string: \"$value\"")
            }
            accumulator = accumulator * 10 + digit
            index++
        }

        if (index == digitsStart) {
            throw NumberFormatException("For input string: \"$value\"")
        }

        return if (negative) (-accumulator).toInt() else accumulator.toInt()
    }
}
