package org.opentibia.canary.io.appearances

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Transcript tests for the real `data/items/appearances.dat` asset
 * (Canary `src/protobuf/appearances.proto`, `Game::loadAppearanceProtobuf` at
 * `src/game/game.cpp:1657` and `Items::loadFromProtobuf` at `src/items/items.cpp:153`).
 *
 * The expected counts and spot-checked values below were read from the shipped
 * asset with a reference wire-format decoder.
 */
class AppearancesTest {

    @Test
    fun `parses the real appearances dat into object, outfit, effect and missile tables`() {
        assertThat(appearances.objects).hasSize(42107)
        assertThat(appearances.outfits).hasSize(1443)
        assertThat(appearances.effects).hasSize(242)
        assertThat(appearances.missiles).hasSize(62)
    }

    @Test
    fun `parses frame groups and sprite info for the Citizen outfit`() {
        val citizen = appearances.getOutfit(136)
        assertThat(citizen).isNotNull
        assertThat(citizen?.id).isEqualTo(136)
        assertThat(citizen?.frameGroups).hasSize(2)

        val idle = citizen?.frameGroups?.get(0)
        assertThat(idle?.fixedFrameGroup).isEqualTo(FixedFrameGroup.OUTFIT_IDLE)
        assertThat(idle?.id).isEqualTo(0)
        assertThat(idle?.spriteInfo?.patternWidth).isEqualTo(4)
        assertThat(idle?.spriteInfo?.patternHeight).isEqualTo(3)
        assertThat(idle?.spriteInfo?.patternDepth).isEqualTo(2)
        assertThat(idle?.spriteInfo?.layers).isEqualTo(2)
        assertThat(idle?.spriteInfo?.spriteIds).hasSize(48)
        assertThat(idle?.spriteInfo?.spriteIds?.take(4)).containsExactly(8096, 8097, 8098, 8099)
        assertThat(idle?.spriteInfo?.boundingSquare).isEqualTo(46)
        assertThat(idle?.spriteInfo?.boundingBoxPerDirection).hasSize(4)
        assertThat(idle?.spriteInfo?.animation).isNull()

        val moving = citizen?.frameGroups?.get(1)
        assertThat(moving?.fixedFrameGroup).isEqualTo(FixedFrameGroup.OUTFIT_MOVING)
        assertThat(moving?.id).isEqualTo(1)
        assertThat(moving?.spriteInfo?.spriteIds).hasSize(384)
        assertThat(moving?.spriteInfo?.animation?.loopType).isEqualTo(AnimationLoopType.INFINITE)
        assertThat(moving?.spriteInfo?.animation?.spritePhases).hasSize(8)
        assertThat(moving?.spriteInfo?.animation?.spritePhases?.first()?.durationMin).isEqualTo(300)
        assertThat(moving?.spriteInfo?.animation?.spritePhases?.first()?.durationMax).isEqualTo(300)
    }

    @Test
    fun `resolves mount appearances from the shared outfit table`() {
        val widowQueen = appearances.getMount(368)
        assertThat(widowQueen).isNotNull
        assertThat(widowQueen?.id).isEqualTo(368)
        assertThat(widowQueen?.frameGroups).hasSize(2)
        assertThat(widowQueen?.frameGroups?.get(0)?.spriteInfo?.spriteIds).hasSize(4)
        assertThat(widowQueen?.frameGroups?.get(1)?.spriteInfo?.spriteIds).hasSize(32)
        assertThat(widowQueen?.frameGroups?.get(1)?.spriteInfo?.animation?.spritePhases).hasSize(8)

        assertThat(appearances.getMount(9999)).isNull()
        assertThat(appearances.getMount(0)).isNull()
    }

    @Test
    fun `parses effect sprite animation metadata`() {
        val effect = appearances.getEffect(1)
        assertThat(effect).isNotNull
        assertThat(effect?.frameGroups).hasSize(1)

        val frameGroup = effect?.frameGroups?.first()
        assertThat(frameGroup?.fixedFrameGroup).isEqualTo(FixedFrameGroup.OBJECT_INITIAL)
        assertThat(frameGroup?.id).isEqualTo(2)
        assertThat(frameGroup?.spriteInfo?.spriteIds).hasSize(6)
        assertThat(frameGroup?.spriteInfo?.patternWidth).isEqualTo(1)
        assertThat(frameGroup?.spriteInfo?.patternHeight).isEqualTo(1)

        val animation = frameGroup?.spriteInfo?.animation
        assertThat(animation?.loopType).isEqualTo(AnimationLoopType.COUNTED)
        assertThat(animation?.loopCount).isEqualTo(1)
        assertThat(animation?.spritePhases).hasSize(6)
        assertThat(animation?.spritePhases?.first()?.durationMin).isEqualTo(100)
        assertThat(animation?.spritePhases?.first()?.durationMax).isEqualTo(100)

        assertThat(appearances.getEffect(9999)).isNull()
    }

    @Test
    fun `parses object names and nested flags for the gold coin`() {
        val goldCoin = appearances.getObject(3031)
        assertThat(goldCoin).isNotNull
        assertThat(goldCoin?.name).isEqualTo("gold coin")
        assertThat(goldCoin?.flags?.cumulative).isTrue()
        assertThat(goldCoin?.flags?.usable).isTrue()
        assertThat(goldCoin?.flags?.multiuse).isTrue()
        assertThat(goldCoin?.flags?.take).isTrue()
        assertThat(goldCoin?.flags?.cyclopediaitem?.cyclopediaType).isEqualTo(3031)

        assertThat(appearances.getObject(102)?.name).isEqualTo("white flower")
        assertThat(appearances.getObject(999999)).isNull()
    }

    @Test
    fun `parses object 100 nested bank light and blocking flags`() {
        val object100 = appearances.getObject(100)
        assertThat(object100).isNotNull
        assertThat(object100?.flags?.bank?.waypoints).isEqualTo(0)
        assertThat(object100?.flags?.unpass).isTrue()
        assertThat(object100?.flags?.unmove).isTrue()
        assertThat(object100?.flags?.unsight).isTrue()
        assertThat(object100?.flags?.clip).isFalse()
        assertThat(object100?.flags?.light?.brightness).isEqualTo(3)
        assertThat(object100?.flags?.light?.color).isEqualTo(156)
        assertThat(object100?.flags?.fullbank).isTrue()

        val spriteInfo = object100?.frameGroups?.first()?.spriteInfo
        assertThat(spriteInfo?.patternWidth).isEqualTo(4)
        assertThat(spriteInfo?.patternHeight).isEqualTo(4)
        assertThat(spriteInfo?.spriteIds).hasSize(16)
        assertThat(spriteInfo?.spriteIds?.take(4)).containsExactly(192615, 192616, 192617, 192618)
        assertThat(spriteInfo?.isOpaque).isTrue()
    }

    @Test
    fun `parses missile appearance sprite info`() {
        val missile = appearances.getMissile(1)
        assertThat(missile).isNotNull
        assertThat(missile?.id).isEqualTo(1)

        val spriteInfo = missile?.frameGroups?.first()?.spriteInfo
        assertThat(spriteInfo?.patternWidth).isEqualTo(3)
        assertThat(spriteInfo?.patternHeight).isEqualTo(3)
        assertThat(spriteInfo?.spriteIds).hasSize(9)
        assertThat(spriteInfo?.boundingSquare).isEqualTo(32)
    }

    @Test
    fun `parses the special meaning appearance ids`() {
        val special = appearances.specialMeaningAppearanceIds
        assertThat(special).isNotNull
        assertThat(special?.goldCoinId).isEqualTo(3031)
        assertThat(special?.platinumCoinId).isEqualTo(3035)
        assertThat(special?.crystalCoinId).isEqualTo(3043)
        assertThat(special?.tibiaCoinId).isEqualTo(22118)
        assertThat(special?.stampedLetterId).isEqualTo(3506)
        assertThat(special?.supplyStashId).isEqualTo(28750)
        assertThat(special?.rewardChestId).isEqualTo(19250)
    }

    @Test
    fun `exposes outfit mount-supported look types matching the Canary merge check`() {
        assertThat(appearances.outfitMountSupportedLookTypes).hasSize(277)
        assertThat(appearances.outfitSupportsMount(136)).isTrue()
        assertThat(appearances.outfitSupportsMount(137)).isTrue()
        assertThat(appearances.outfitSupportsMount(21)).isFalse()
        assertThat(appearances.outfitSupportsMount(368)).isFalse()
    }

    @Test
    fun `tolerates truncated input without throwing`() {
        val bytes = realDat.readBytes()
        val partial = Appearances.load(bytes.copyOf(bytes.size / 2))
        assertThat(partial.objects.size).isBetween(1, 42106)
        assertThat(partial.outfits).isEmpty()

        for (cut in listOf(0, 1, 7, 100, 4096, bytes.size - 1)) {
            assertThatCode { Appearances.load(bytes.copyOf(cut)) }.doesNotThrowAnyException()
        }

        assertThatCode { Appearances.load(ByteArray(0)) }.doesNotThrowAnyException()
        assertThatCode { Appearances.load(ByteArray(32) { 0xFF.toByte() }) }.doesNotThrowAnyException()
    }

    companion object {
        private val realDat = File("data/items/appearances.dat")
        private val appearances: Appearances by lazy { Appearances.load(realDat) }
    }
}
