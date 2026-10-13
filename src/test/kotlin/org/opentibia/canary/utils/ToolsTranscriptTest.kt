package org.opentibia.canary.utils

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `src/utils/tools.cpp` helpers, mirroring
 * `tests/unit/utils/string_functions_test.cpp` and
 * `tests/unit/utils/numeric_functions_test.cpp` plus the directly transcribed
 * C++ semantics of the remaining pure helpers.
 */
class ToolsTranscriptTest {

    @Test
    fun `subtracts elapsed time without crossing zero matching Canary numeric_functions_test`() {
        assertThat(Tools.subtractElapsedTime(1000, 250)).isEqualTo(750L)
        assertThat(Tools.subtractElapsedTime(1000, 0)).isEqualTo(1000L)
        assertThat(Tools.subtractElapsedTime(1000, 1000)).isEqualTo(0L)
        assertThat(Tools.subtractElapsedTime(1000, 1001)).isEqualTo(0L)
        assertThat(Tools.subtractElapsedTime(1, Long.MAX_VALUE)).isEqualTo(0L)
        assertThat(Tools.subtractElapsedTime(0, 1)).isEqualTo(0L)
        assertThat(Tools.subtractElapsedTime(-1, 1)).isEqualTo(0L)
    }

    @Test
    fun `replaces strings matching Canary string_functions_test vectors`() {
        assertThat(Tools.replaceString("", "", "")).isEqualTo("")
        assertThat(Tools.replaceString("subject", "", "x")).isEqualTo("subject")
        assertThat(Tools.replaceString("all together", " ", "_")).isEqualTo("all_together")
        assertThat(Tools.replaceString("beautiful", "u", "")).isEqualTo("beatifl")
        assertThat(Tools.replaceString("empty_empty_empty_", "empty_", "")).isEqualTo("")
        assertThat(Tools.replaceString("I am someone", "someone", "Lucas")).isEqualTo("I am Lucas")
        assertThat(Tools.replaceString("[[123[[[[[[124[[asf[[ccc[[[", "[[", "\\[["))
            .isEqualTo("\\[[123\\[[\\[[\\[[124\\[[asf\\[[ccc\\[[[")
    }

    @Test
    fun `transforms hashes matching Canary canonical vectors`() {
        assertThat(Tools.transformToSHA1("")).isEqualTo("da39a3ee5e6b4b0d3255bfef95601890afd80709")
        assertThat(Tools.transformToSHA1("abc")).isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d")

        assertThat(Tools.transformToSHA256("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        assertThat(Tools.transformToSHA256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test
    fun `undoShift returns the bit position minus two matching countr_zero`() {
        assertThat(Tools.undoShift(0L)).isEqualTo(0)
        assertThat(Tools.undoShift(1L)).isEqualTo(0)
        assertThat(Tools.undoShift(2L)).isEqualTo(0)
        assertThat(Tools.undoShift(4L)).isEqualTo(0)
        assertThat(Tools.undoShift(8L)).isEqualTo(1)
        assertThat(Tools.undoShift(16L)).isEqualTo(2)
        assertThat(Tools.undoShift(Long.MIN_VALUE)).isEqualTo(61)
    }

    @Test
    fun `trim right and trim left strip only the requested character`() {
        assertThat(Tools.trimRight("xxabcxx", 'x')).isEqualTo("xxabc")
        assertThat(Tools.trimRight("xxx", 'x')).isEqualTo("")
        assertThat(Tools.trimRight("", 'x')).isEqualTo("")
        assertThat(Tools.trimRight("abc", 'x')).isEqualTo("abc")

        assertThat(Tools.trimLeft("xxabcxx", 'x')).isEqualTo("abcxx")
        assertThat(Tools.trimLeft("xxx", 'x')).isEqualTo("")
        assertThat(Tools.trimLeft("", 'x')).isEqualTo("")
        assertThat(Tools.trimLeft("abc", 'x')).isEqualTo("abc")
    }

    @Test
    fun `keepFirstWordOnly erases from the first space`() {
        assertThat(Tools.keepFirstWordOnly("hello world")).isEqualTo("hello")
        assertThat(Tools.keepFirstWordOnly("hello")).isEqualTo("hello")
        assertThat(Tools.keepFirstWordOnly("hello ")).isEqualTo("hello")
        assertThat(Tools.keepFirstWordOnly(" hello")).isEqualTo("")
        assertThat(Tools.keepFirstWordOnly("")).isEqualTo("")
    }

    @Test
    fun `lowercase and uppercase helpers are ASCII case folds`() {
        assertThat(Tools.toLowerCaseString("Hello WORLD 123")).isEqualTo("hello world 123")
        assertThat(Tools.asLowerCaseString("Hello WORLD 123")).isEqualTo("hello world 123")
        assertThat(Tools.asUpperCaseString("Hello world")).isEqualTo("HELLO WORLD")
    }

    @Test
    fun `case style helpers match the C++ loops`() {
        assertThat(Tools.toCamelCase("hello_world")).isEqualTo("helloWorld")
        assertThat(Tools.toCamelCase("Hello World")).isEqualTo("helloWorld")
        assertThat(Tools.toCamelCase("hello-world")).isEqualTo("helloWorld")
        assertThat(Tools.toCamelCase("ABC")).isEqualTo("abc")
        assertThat(Tools.toCamelCase("a  b")).isEqualTo("aB")
        assertThat(Tools.toCamelCase("")).isEqualTo("")

        assertThat(Tools.toPascalCase("hello_world")).isEqualTo("HelloWorld")
        assertThat(Tools.toPascalCase("hello world")).isEqualTo("HelloWorld")
        assertThat(Tools.toPascalCase("hELLO")).isEqualTo("HELLO")

        assertThat(Tools.toSnakeCase("helloWorld")).isEqualTo("hello_world")
        assertThat(Tools.toSnakeCase("Hello")).isEqualTo("_hello")
        assertThat(Tools.toSnakeCase("hello world")).isEqualTo("hello_world")
        assertThat(Tools.toSnakeCase("a-b")).isEqualTo("a_b")

        assertThat(Tools.toKebabCase("helloWorld")).isEqualTo("hello-world")
        assertThat(Tools.toKebabCase("Hello")).isEqualTo("-hello")
        assertThat(Tools.toKebabCase("hello world")).isEqualTo("hello-world")
        assertThat(Tools.toKebabCase("a_b")).isEqualTo("a-b")

        assertThat(Tools.toStartCaseWithSpace("hello world")).isEqualTo("Hello world")
        assertThat(Tools.toStartCaseWithSpace("helloWorld")).isEqualTo("Hello World")
        assertThat(Tools.toStartCaseWithSpace("HELLO")).isEqualTo("H E L L O")
        assertThat(Tools.toStartCaseWithSpace("")).isEqualTo("")
    }

    @Test
    fun `explodeString splits with the C++ pre-decrement limit`() {
        assertThat(Tools.explodeString("a,b,c", ",")).containsExactly("a", "b", "c")
        assertThat(Tools.explodeString("a,b,c", ",", 0)).containsExactly("a,b,c")
        assertThat(Tools.explodeString("a,b,c", ",", 1)).containsExactly("a", "b,c")
        assertThat(Tools.explodeString("a,b,c", ",", 2)).containsExactly("a", "b", "c")
        assertThat(Tools.explodeString("a,,b", ",")).containsExactly("a", "", "b")
        assertThat(Tools.explodeString("a,b,", ",")).containsExactly("a", "b", "")
        assertThat(Tools.explodeString("abc", ";")).containsExactly("abc")
        assertThat(Tools.explodeString("abc", "", 0)).containsExactly("abc")
        assertThat(Tools.explodeString("abc", "", 1)).containsExactly("", "abc")
        assertThat(Tools.explodeString("abc", "", 2)).containsExactly("", "", "abc")
    }

    @Test
    fun `explodeString rejects the non terminating empty separator with unlimited limit`() {
        assertThatThrownBy { Tools.explodeString("abc", "") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `vectorAtoi mirrors std stoi parsing`() {
        assertThat(Tools.vectorAtoi(listOf("1", "-2", "003"))).containsExactly(1, -2, 3)
        assertThat(Tools.vectorAtoi(listOf(" 42"))).containsExactly(42)
        assertThat(Tools.vectorAtoi(listOf("42abc"))).containsExactly(42)
        assertThat(Tools.vectorAtoi(listOf("+7"))).containsExactly(7)
        assertThat(Tools.vectorAtoi(listOf("2147483647"))).containsExactly(Int.MAX_VALUE)
        assertThat(Tools.vectorAtoi(listOf("-2147483648"))).containsExactly(Int.MIN_VALUE)
        assertThatThrownBy { Tools.vectorAtoi(listOf("2147483648")) }
            .isInstanceOf(NumberFormatException::class.java)
        assertThatThrownBy { Tools.vectorAtoi(listOf("-2147483649")) }
            .isInstanceOf(NumberFormatException::class.java)
        assertThatThrownBy { Tools.vectorAtoi(listOf("abc")) }
            .isInstanceOf(NumberFormatException::class.java)
        assertThatThrownBy { Tools.vectorAtoi(listOf("")) }
            .isInstanceOf(NumberFormatException::class.java)
        assertThatThrownBy { Tools.vectorAtoi(listOf("   ")) }
            .isInstanceOf(NumberFormatException::class.java)
    }

    @Test
    fun `trimString strips surrounding spaces only`() {
        assertThat(Tools.trimString("  abc  ")).isEqualTo("abc")
        assertThat(Tools.trimString("a b")).isEqualTo("a b")
        assertThat(Tools.trimString("   ")).isEqualTo("")
        assertThat(Tools.trimString("")).isEqualTo("")
    }

    @Test
    fun `ucfirst capitalizes the first non space character`() {
        assertThat(Tools.ucfirst("hello world")).isEqualTo("Hello world")
        assertThat(Tools.ucfirst("  abc")).isEqualTo("  Abc")
        assertThat(Tools.ucfirst(" hELLO")).isEqualTo(" HELLO")
        assertThat(Tools.ucfirst("")).isEqualTo("")
    }

    @Test
    fun `ucwords capitalizes the first letter and letters after spaces`() {
        assertThat(Tools.ucwords("hello world")).isEqualTo("Hello World")
        assertThat(Tools.ucwords("hello")).isEqualTo("Hello")
        assertThat(Tools.ucwords("hello  world")).isEqualTo("Hello  World")
        assertThat(Tools.ucwords("")).isEqualTo("")
    }

    @Test
    fun `booleanString follows the f n 0 rejection`() {
        assertThat(Tools.booleanString("")).isFalse()
        assertThat(Tools.booleanString("false")).isFalse()
        assertThat(Tools.booleanString("no")).isFalse()
        assertThat(Tools.booleanString("0")).isFalse()
        assertThat(Tools.booleanString("False")).isFalse()
        assertThat(Tools.booleanString("true")).isTrue()
        assertThat(Tools.booleanString("yes")).isTrue()
        assertThat(Tools.booleanString("1")).isTrue()
        assertThat(Tools.booleanString(" nope")).isTrue()
    }

    @Test
    fun `getFirstLine stops at the first newline`() {
        assertThat(Tools.getFirstLine("abc\ndef")).isEqualTo("abc")
        assertThat(Tools.getFirstLine("abc")).isEqualTo("abc")
        assertThat(Tools.getFirstLine("\nxyz")).isEqualTo("")
        assertThat(Tools.getFirstLine("")).isEqualTo("")
    }

    @Test
    fun `capitalizeWords lowercases then capitalizes words and apostrophes`() {
        assertThat(Tools.capitalizeWords("hello world")).isEqualTo("Hello World")
        assertThat(Tools.capitalizeWords("o'neill")).isEqualTo("O'Neill")
        assertThat(Tools.capitalizeWords("HELLO")).isEqualTo("Hello")
        assertThat(Tools.capitalizeWords("")).isEqualTo("")
    }

    @Test
    fun `capitalizeWordsIgnoringString skips the ignored span`() {
        assertThat(Tools.capitalizeWordsIgnoringString("hello of world", " of "))
            .isEqualTo("Hello of World")
        assertThat(Tools.capitalizeWordsIgnoringString("hello world", " of "))
            .isEqualTo("Hello World")
    }

    @Test
    fun `toKey lowercases replaces spaces and removes whitespace`() {
        assertThat(Tools.toKey("Hello World")).isEqualTo("hello-world")
        assertThat(Tools.toKey("Hello  World")).isEqualTo("hello--world")
        assertThat(Tools.toKey("a\tb")).isEqualTo("ab")
        assertThat(Tools.toKey("")).isEqualTo("")
    }

    @Test
    fun `caseInsensitiveCompare mirrors the C++ length handling`() {
        assertThat(Tools.caseInsensitiveCompare("Abc", "aBc")).isTrue()
        assertThat(Tools.caseInsensitiveCompare("Abc", "aB")).isFalse()
        assertThat(Tools.caseInsensitiveCompare("Abc", "aBd", 2)).isTrue()
        assertThat(Tools.caseInsensitiveCompare("Abc", "aBd", 3)).isFalse()
        assertThat(Tools.caseInsensitiveCompare("ab", "abc", 5)).isTrue()
        assertThat(Tools.caseInsensitiveCompare("", "")).isTrue()
        assertThat(Tools.caseInsensitiveCompare("", "a")).isFalse()
    }

    @Test
    fun `formatWithArticle rewrites leading articles and a-an`() {
        assertThat(Tools.formatWithArticle("sword")).isEqualTo(" a sword.")
        assertThat(Tools.formatWithArticle("sword", false)).isEqualTo("a sword.")
        assertThat(Tools.formatWithArticle("an apple")).isEqualTo(" an apple.")
        assertThat(Tools.formatWithArticle("a apple")).isEqualTo(" an apple.")
        assertThat(Tools.formatWithArticle("a")).isEqualTo(" an a.")
        assertThat(Tools.formatWithArticle("an")).isEqualTo(" an an.")
        assertThat(Tools.formatWithArticle("")).isEqualTo("")
    }

    @Test
    fun `formatEnumName lowercases and replaces underscores`() {
        assertThat(Tools.formatEnumName("COMBAT_PHYSICALDAMAGE")).isEqualTo("combat physicaldamage")
        assertThat(Tools.formatEnumName("HELLO")).isEqualTo("hello")
        assertThat(Tools.formatEnumName("")).isEqualTo("")
    }

    @Test
    fun `split mirrors std getline tokenization with trimming`() {
        assertThat(Tools.split("")).isEmpty()
        assertThat(Tools.split("a,b")).containsExactly("a", "b")
        assertThat(Tools.split(" a , b ", ',')).containsExactly("a", "b")
        assertThat(Tools.split("a,,b", ',')).containsExactly("a", "", "b")
        assertThat(Tools.split("a,b,", ',')).containsExactly("a", "b")
        assertThat(Tools.split(",a", ',')).containsExactly("", "a")
        assertThat(Tools.split("a b c", ' ')).containsExactly("a", "b", "c")
    }

    @Test
    fun `generateToken matches HOTP SHA1 vectors`() {
        assertThat(Tools.generateToken("12345678901234567890", 0L)).isEqualTo("755224")
        assertThat(Tools.generateToken("12345678901234567890", 1L)).isEqualTo("287082")
        assertThat(Tools.generateToken("12345678901234567890", 37037036L)).isEqualTo("081804")
        assertThat(Tools.generateToken("12345678901234567890", 1234567890L)).isEqualTo("965462")
        assertThat(Tools.generateToken("key", 1L)).isEqualTo("023900")
        assertThat(Tools.generateToken("key", 4294967295L)).isEqualTo("668207")
    }

    @Test
    fun `formatPrice groups trailing zero triples into k`() {
        assertThat(Tools.formatPrice("999")).isEqualTo("999")
        assertThat(Tools.formatPrice("1000")).isEqualTo("1k")
        assertThat(Tools.formatPrice("1000000")).isEqualTo("1kk")
        assertThat(Tools.formatPrice("1000000000")).isEqualTo("1kkk")
        assertThat(Tools.formatPrice("1000000", true)).isEqualTo("1 kk")
    }

    @Test
    fun `formatNumber inserts thousands separators`() {
        assertThat(Tools.formatNumber(0uL)).isEqualTo("0")
        assertThat(Tools.formatNumber(123uL)).isEqualTo("123")
        assertThat(Tools.formatNumber(1234uL)).isEqualTo("1,234")
        assertThat(Tools.formatNumber(1234567uL)).isEqualTo("1,234,567")
        assertThat(Tools.formatNumber(ULong.MAX_VALUE)).isEqualTo("18,446,744,073,709,551,615")
    }

    @Test
    fun `forgeBonus maps the documented ranges`() {
        assertThat(Tools.forgeBonus(-1)).isEqualTo(0)
        assertThat(Tools.forgeBonus(0)).isEqualTo(0)
        assertThat(Tools.forgeBonus(7399)).isEqualTo(0)
        assertThat(Tools.forgeBonus(7400)).isEqualTo(1)
        assertThat(Tools.forgeBonus(8999)).isEqualTo(1)
        assertThat(Tools.forgeBonus(9000)).isEqualTo(2)
        assertThat(Tools.forgeBonus(9500)).isEqualTo(3)
        assertThat(Tools.forgeBonus(9525)).isEqualTo(4)
        assertThat(Tools.forgeBonus(9550)).isEqualTo(5)
        assertThat(Tools.forgeBonus(9950)).isEqualTo(6)
        assertThat(Tools.forgeBonus(9974)).isEqualTo(6)
        assertThat(Tools.forgeBonus(9975)).isEqualTo(7)
        assertThat(Tools.forgeBonus(30000)).isEqualTo(7)
    }

    @Test
    fun `convertIPToString prints the little endian octets`() {
        assertThat(Tools.convertIPToString(0L)).isEqualTo("0.0.0.0")
        assertThat(Tools.convertIPToString(0x0100007FL)).isEqualTo("127.0.0.1")
        assertThat(Tools.convertIPToString(0xFFFFFFFFL)).isEqualTo("255.255.255.255")
    }

    @Test
    fun `adlerChecksum matches the canonical vector and the max size guard`() {
        assertThat(Tools.adlerChecksum("Wikipedia".toByteArray())).isEqualTo(0x11E60398L)
        assertThat(Tools.adlerChecksum(ByteArray(0))).isEqualTo(1L)
        assertThat(Tools.adlerChecksum(ByteArray(65500))).isNotEqualTo(0L)
        assertThat(Tools.adlerChecksum(ByteArray(65501))).isEqualTo(0L)
    }

    @Test
    fun `date formatters use UTC like fmt time point formatting`() {
        assertThat(Tools.formatDate(0L)).isEqualTo("01/01/1970 00:00:00")
        assertThat(Tools.formatDate(1_700_000_000L)).isEqualTo("14/11/2023 22:13:20")
        assertThat(Tools.formatDateShort(0L)).isEqualTo("1970-01-01 00:00:00")
        assertThat(Tools.formatDateShort(1_700_000_000L)).isEqualTo("2023-11-14 22:13:20")
        assertThat(Tools.formatDateTime(0L)).isEqualTo("1970-01-01 00:00:00")
        assertThat(Tools.formatDateTime(1500L)).isEqualTo("1970-01-01 00:00:01")
        assertThat(Tools.formatDateTime(-1500L)).isEqualTo("1969-12-31 23:59:59")
    }

    @Test
    fun `getFormattedTimeRemaining mirrors the C++ branches`() {
        val now = 1_000_000L
        assertThat(Tools.getFormattedTimeRemaining(now + 2 * 86400, now)).isEqualTo("2 days")
        assertThat(Tools.getFormattedTimeRemaining(now + 86400, now)).isEqualTo("")
        assertThat(Tools.getFormattedTimeRemaining(now + 86400 + 3600, now)).isEqualTo("1 hour")
        assertThat(Tools.getFormattedTimeRemaining(now + 86400 + 7200 + 60, now))
            .isEqualTo("2 hours and 1 minute")
        assertThat(Tools.getFormattedTimeRemaining(now + 3600, now)).isEqualTo("1 hour")
        assertThat(Tools.getFormattedTimeRemaining(now + 60, now)).isEqualTo("1 minute")
        assertThat(Tools.getFormattedTimeRemaining(now + 30, now)).isEqualTo(" less than 1 minute")
        assertThat(Tools.getFormattedTimeRemaining(now, now)).isEqualTo("")
        assertThat(Tools.getFormattedTimeRemaining(now - 50, now)).isEqualTo("")
    }

    @Test
    fun `getTimeNow returns seconds and getTimeMsNow returns milliseconds`() {
        assertThat(Tools.getTimeNow()).isGreaterThan(1_600_000_000L)
        assertThat(Tools.getTimeMsNow()).isGreaterThan(1_600_000_000_000L)
        assertThat(Tools.getTimeUsNow()).isGreaterThan(1_600_000_000_000_000L)
    }

    @Test
    fun `getNumberOfCores returns the available processors`() {
        assertThat(Tools.getNumberOfCores()).isGreaterThan(0)
    }

    @Test
    fun `calculateEquipmentLoss mirrors the blessing table`() {
        assertThat(Tools.calculateEquipmentLoss(0)).isEqualTo(10.0)
        assertThat(Tools.calculateEquipmentLoss(1)).isEqualTo(7.0)
        assertThat(Tools.calculateEquipmentLoss(2)).isEqualTo(4.5)
        assertThat(Tools.calculateEquipmentLoss(3)).isEqualTo(2.5)
        assertThat(Tools.calculateEquipmentLoss(4)).isEqualTo(1.0)
        assertThat(Tools.calculateEquipmentLoss(5)).isEqualTo(0.0)
        assertThat(Tools.calculateEquipmentLoss(2, true)).isEqualTo(45.0)
    }

    @Test
    fun `calculateMaxPvpReduction mirrors the C++ formula`() {
        assertThat(Tools.calculateMaxPvpReduction(0)).isEqualTo(80)
        assertThat(Tools.calculateMaxPvpReduction(1)).isEqualTo(82)
        assertThat(Tools.calculateMaxPvpReduction(3)).isEqualTo(85)
        assertThat(Tools.calculateMaxPvpReduction(5)).isEqualTo(88)
        assertThat(Tools.calculateMaxPvpReduction(5, true)).isEqualTo(94)
    }

    @Test
    fun `getVocationIdFromClientId maps the CIP ids`() {
        assertThat(Tools.getVocationIdFromClientId(1L)).isEqualTo(4L)
        assertThat(Tools.getVocationIdFromClientId(2L)).isEqualTo(3L)
        assertThat(Tools.getVocationIdFromClientId(3L)).isEqualTo(1L)
        assertThat(Tools.getVocationIdFromClientId(4L)).isEqualTo(2L)
        assertThat(Tools.getVocationIdFromClientId(5L)).isEqualTo(9L)
        assertThat(Tools.getVocationIdFromClientId(0L)).isEqualTo(0L)
        assertThat(Tools.getVocationIdFromClientId(6L)).isEqualTo(0xFFFFFFFFL)
        assertThat(Tools.getVocationIdFromClientId(0xFFFFFFFFL)).isEqualTo(0xFFFFFFFFL)
    }

    @Test
    fun `getPrimaryDirection picks the dominant cardinal axis`() {
        val from = Position(100, 100, 7)
        assertThat(Tools.getPrimaryDirection(from, Position(101, 100, 7))).isEqualTo(Direction.East)
        assertThat(Tools.getPrimaryDirection(from, Position(99, 100, 7))).isEqualTo(Direction.West)
        assertThat(Tools.getPrimaryDirection(from, Position(100, 101, 7))).isEqualTo(Direction.South)
        assertThat(Tools.getPrimaryDirection(from, Position(100, 99, 7))).isEqualTo(Direction.North)
        assertThat(Tools.getPrimaryDirection(from, Position(101, 101, 7))).isEqualTo(Direction.South)
        assertThat(Tools.getPrimaryDirection(from, Position(101, 99, 7))).isEqualTo(Direction.North)
        assertThat(Tools.getPrimaryDirection(from, from)).isEqualTo(Direction.North)
    }

    @Test
    fun `getDirection parses the Canary direction aliases`() {
        assertThat(Tools.getDirection("north")).isEqualTo(Direction.North)
        assertThat(Tools.getDirection("n")).isEqualTo(Direction.North)
        assertThat(Tools.getDirection("0")).isEqualTo(Direction.North)
        assertThat(Tools.getDirection("east")).isEqualTo(Direction.East)
        assertThat(Tools.getDirection("s")).isEqualTo(Direction.South)
        assertThat(Tools.getDirection("w")).isEqualTo(Direction.West)
        assertThat(Tools.getDirection("south west")).isEqualTo(Direction.SouthWest)
        assertThat(Tools.getDirection("south-west")).isEqualTo(Direction.SouthWest)
        assertThat(Tools.getDirection("4")).isEqualTo(Direction.SouthWest)
        assertThat(Tools.getDirection("se")).isEqualTo(Direction.SouthEast)
        assertThat(Tools.getDirection("northwest")).isEqualTo(Direction.NorthWest)
        assertThat(Tools.getDirection("ne")).isEqualTo(Direction.NorthEast)
        assertThat(Tools.getDirection("NORTH")).isEqualTo(Direction.North)
        assertThat(Tools.getDirection("")).isEqualTo(Direction.North)
    }
}
