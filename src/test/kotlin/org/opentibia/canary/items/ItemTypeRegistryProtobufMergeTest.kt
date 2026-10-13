package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.io.appearances.Appearances
import java.io.File

/**
 * Transcript tests for `Items::loadFromProtobuf` (`src/items/items.cpp:153-279`) and the
 * protobuf category/clothes/slot merges, run against the shipped
 * `data/items/appearances.dat` plus hand-encoded wire payloads.
 *
 * Every expected value was read from the real asset with a reference wire-format
 * decoder; the synthetic payloads cover the branches the shipped asset does not
 * exercise (missing flags, animation random start phase).
 */
class ItemTypeRegistryProtobufMergeTest {

    @Test
    fun `merges the real appearances dat into item types`() {
        val registry = Items()
        registry.loadFromProtobuf(appearances)

        assertThat(registry.size()).isEqualTo(54267)
        assertThat(registry.hasItemType(54266)).isTrue()
        assertThat(registry.hasItemType(54267)).isFalse()
    }

    @Test
    fun `merges gold coin name stackable take and multiuse flags`() {
        val registry = loadedRegistry()
        val goldCoin = registry.getItemType(3031)

        assertThat(goldCoin.id).isEqualTo(3031)
        assertThat(goldCoin.name).isEqualTo("gold coin")
        assertThat(goldCoin.description).isEmpty()
        assertThat(goldCoin.isStackable).isTrue()
        assertThat(goldCoin.multiUse).isTrue()
        assertThat(goldCoin.isPickupable).isTrue()
        assertThat(goldCoin.isMovable).isTrue()
        assertThat(goldCoin.isCorpse).isFalse()
        assertThat(goldCoin.type).isEqualTo(ItemTypes_t.ITEM_TYPE_NONE)
        assertThat(goldCoin.group).isEqualTo(ItemGroup_t.ITEM_GROUP_NONE)
        assertThat(goldCoin.lightLevel).isEqualTo(0)
        assertThat(goldCoin.lightColor).isEqualTo(0)
        assertThat(goldCoin.upgradeClassification).isEqualTo(0)
        assertThat(goldCoin.wareId).isEqualTo(0)
        assertThat(goldCoin.alwaysOnTopOrder).isEqualTo(0)
        assertThat(registry.getItemIdByName("GOLD COIN")).isEqualTo(3031)
    }

    @Test
    fun `merges bank light blocking and speed flags from object 100`() {
        val registry = loadedRegistry()
        val object100 = registry.getItemType(100)

        assertThat(object100.group).isEqualTo(ItemGroup_t.ITEM_GROUP_GROUND)
        assertThat(object100.isGroundTile).isTrue()
        assertThat(object100.speed).isEqualTo(0)
        assertThat(object100.blockSolid).isTrue()
        assertThat(object100.blockProjectile).isTrue()
        assertThat(object100.blockPathFind).isFalse()
        assertThat(object100.isPickupable).isFalse()
        assertThat(object100.isMovable).isFalse()
        assertThat(object100.lightLevel).isEqualTo(3)
        assertThat(object100.lightColor).isEqualTo(156)
        assertThat(object100.alwaysOnTopOrder).isEqualTo(0)
    }

    @Test
    fun `merges bank waypoints speed and force use from object 386`() {
        val registry = loadedRegistry()
        val object386 = registry.getItemType(386)

        assertThat(object386.group).isEqualTo(ItemGroup_t.ITEM_GROUP_GROUND)
        assertThat(object386.speed).isEqualTo(120)
        assertThat(object386.forceUse).isTrue()
        assertThat(object386.canReadText).isFalse()
        assertThat(object386.lookThrough).isFalse()
    }

    @Test
    fun `merges always on top order from clip bottom and top flags`() {
        val registry = loadedRegistry()

        assertThat(registry.getItemType(290).alwaysOnTopOrder).isEqualTo(1)
        assertThat(registry.getItemType(134).alwaysOnTopOrder).isEqualTo(2)
        assertThat(registry.getItemType(244).alwaysOnTopOrder).isEqualTo(3)
        assertThat(registry.getItemType(1270).alwaysOnTopOrder).isEqualTo(2)
        assertThat(registry.getItemType(1270).isHorizontal).isTrue()
        assertThat(registry.getItemType(1270).isVertical).isFalse()
        assertThat(registry.getItemType(1271).isVertical).isTrue()
        assertThat(registry.getItemType(1271).isHorizontal).isFalse()
    }

    @Test
    fun `merges containers heights and corpse flags`() {
        val registry = loadedRegistry()

        val coffin = registry.getItemType(1987)
        assertThat(coffin.type).isEqualTo(ItemTypes_t.ITEM_TYPE_CONTAINER)
        assertThat(coffin.group).isEqualTo(ItemGroup_t.ITEM_GROUP_CONTAINER)
        assertThat(coffin.isContainer).isTrue()
        assertThat(coffin.hasHeight).isTrue()
        assertThat(coffin.blockSolid).isTrue()
        assertThat(coffin.isMovable).isFalse()

        assertThat(registry.getItemType(114).hasHeight).isTrue()
        assertThat(registry.getItemType(111).isCorpse).isTrue()
        assertThat(registry.getItemType(111).forceUse).isTrue()
        assertThat(registry.getItemType(4240).isCorpse).isTrue()
    }

    @Test
    fun `merges liquid groups from pool and container flags`() {
        val registry = loadedRegistry()

        assertThat(registry.getItemType(2886).group).isEqualTo(ItemGroup_t.ITEM_GROUP_SPLASH)
        assertThat(registry.getItemType(2886).isSplash).isTrue()
        assertThat(registry.getItemType(869).group).isEqualTo(ItemGroup_t.ITEM_GROUP_FLUID)
        assertThat(registry.getItemType(869).isFluidContainer).isTrue()
    }

    @Test
    fun `merges market category into the item type and ware id`() {
        val registry = loadedRegistry()

        val sword = registry.getItemType(860)
        assertThat(sword.type).isEqualTo(ItemTypes_t.ITEM_TYPE_SWORD)
        assertThat(sword.wareId).isEqualTo(860)
        assertThat(sword.upgradeClassification).isEqualTo(1)
        // Clothes slot 6 sets SLOTP_LEFT (32), already part of the SLOTP_HAND default.
        assertThat(sword.slotPosition).isEqualTo(48)

        val pillow = registry.getItemType(2400)
        assertThat(pillow.type).isEqualTo(ItemTypes_t.ITEM_TYPE_DECORATION)
        assertThat(pillow.wareId).isEqualTo(2400)
        assertThat(pillow.name).isEqualTo("round purple pillow")

        assertThat(registry.getItemType(50157).type).isEqualTo(ItemTypes_t.ITEM_TYPE_FIST)
        assertThat(registry.getItemType(50157).isDualWielding).isTrue()
        // Clothes slot 0 is undefined behaviour in C++ and truncates to 0 on x86.
        assertThat(registry.getItemType(50157).slotPosition).isEqualTo(48)
        assertThat(registry.getItemType(50157).upgradeClassification).isEqualTo(4)
    }

    @Test
    fun `merges clothes slot into the slot position bitmask`() {
        val registry = loadedRegistry()

        assertThat(registry.getItemType(139).slotPosition).isEqualTo(49)
        assertThat(registry.getItemType(645).slotPosition).isEqualTo(112)
        assertThat(registry.getItemType(645).upgradeClassification).isEqualTo(2)
        assertThat(registry.getItemType(645).name).isEqualTo("blue legs")
    }

    @Test
    fun `merges write write once hang and look through flags`() {
        val registry = loadedRegistry()

        assertThat(registry.getItemType(2598).canReadText).isTrue()
        assertThat(registry.getItemType(2598).isHangable).isTrue()
        assertThat(registry.getItemType(2833).canReadText).isTrue()
        assertThat(registry.getItemType(2833).name).isEqualTo("parchment")
        assertThat(registry.getItemType(386).canReadText).isFalse()
        assertThat(registry.getItemType(296).lookThrough).isTrue()
        assertThat(registry.getItemType(296).alwaysOnTopOrder).isEqualTo(1)
    }

    @Test
    fun `merges wrap unwrap and decoration kit ids`() {
        val registry = loadedRegistry()

        val wrapped = registry.getItemType(2025)
        assertThat(wrapped.wrapContainer).isTrue()
        assertThat(wrapped.wrapable).isTrue()
        assertThat(wrapped.wrapableTo).isEqualTo(23398)
        assertThat(wrapped.rotatable).isTrue()

        val kit = registry.getItemType(23398)
        assertThat(kit.name).isEqualTo("decoration kit")
        assertThat(kit.wrapContainer).isTrue()
        assertThat(kit.isWrapKit).isTrue()
    }

    @Test
    fun `merges podium wear expire and expire stop flags`() {
        val registry = loadedRegistry()

        assertThat(registry.getItemType(35973).isPodium).isTrue()
        assertThat(registry.getItemType(35973).hasHeight).isTrue()
        assertThat(registry.getItemType(660).wearOut).isTrue()
        assertThat(registry.getItemType(12731).clockExpire).isTrue()
        assertThat(registry.getItemType(908).expire).isTrue()
        assertThat(registry.getItemType(907).expireStop).isTrue()
    }

    @Test
    fun `merges avoid into block path find`() {
        val registry = loadedRegistry()

        assertThat(registry.getItemType(105).blockPathFind).isTrue()
        assertThat(registry.getItemType(105).lightLevel).isEqualTo(2)
        assertThat(registry.getItemType(105).lightColor).isEqualTo(104)
    }

    @Test
    fun `proficiency id is only stored when the proficiency table knows it`() {
        val unknown = loadedRegistry()
        assertThat(unknown.getItemType(860).proficiencyId).isEqualTo(0)

        val known = Items()
        known.loadFromProtobuf(appearances, knownProficiencyIds = setOf(239))
        assertThat(known.getItemType(860).proficiencyId).isEqualTo(239)
    }

    @Test
    fun `animation type is only merged when the old protocol support is on`() {
        val withoutAnimation = loadedRegistry()
        assertThat(withoutAnimation.getItemType(114).animationType).isEqualTo(ItemAnimation_t.ANIMATION_NONE)
        assertThat(withoutAnimation.getItemType(105).animationType).isEqualTo(ItemAnimation_t.ANIMATION_NONE)

        val withAnimation = Items()
        withAnimation.loadFromProtobuf(appearances, supportAnimation = true)
        assertThat(withAnimation.getItemType(114).animationType).isEqualTo(ItemAnimation_t.ANIMATION_DESYNC)
        assertThat(withAnimation.getItemType(105).animationType).isEqualTo(ItemAnimation_t.ANIMATION_DESYNC)
    }

    @Test
    fun `animation random start phase selects the random animation type`() {
        val random = Items()
        random.loadFromProtobuf(Appearances.load(appearancesMessage(
            appearance(id = 7, flags = cumulativeFlag(), frameGroup = animationFrameGroup(randomStartPhase = true))
        )), supportAnimation = true)
        assertThat(random.getItemType(7).animationType).isEqualTo(ItemAnimation_t.ANIMATION_RANDOM)

        val desync = Items()
        desync.loadFromProtobuf(Appearances.load(appearancesMessage(
            appearance(id = 8, flags = cumulativeFlag(), frameGroup = animationFrameGroup(randomStartPhase = false))
        )), supportAnimation = true)
        assertThat(desync.getItemType(8).animationType).isEqualTo(ItemAnimation_t.ANIMATION_DESYNC)

        val disabled = Items()
        disabled.loadFromProtobuf(Appearances.load(appearancesMessage(
            appearance(id = 9, flags = cumulativeFlag(), frameGroup = animationFrameGroup(randomStartPhase = true))
        )))
        assertThat(disabled.getItemType(9).animationType).isEqualTo(ItemAnimation_t.ANIMATION_NONE)
    }

    @Test
    fun `objects without flags are skipped before the vector grows`() {
        val registry = Items()
        registry.loadFromProtobuf(Appearances.load(appearancesMessage(appearance(id = 5))))

        assertThat(registry.size()).isEqualTo(0)
        assertThat(registry.hasItemType(5)).isFalse()
        assertThat(registry.get(5)).isNull()
        assertThat(registry.getItemType(5).id).isEqualTo(0)
    }

    @Test
    fun `unknown proto market categories fall back to other`() {
        val registry = Items()
        registry.loadFromProtobuf(Appearances.load(appearancesMessage(
            appearance(id = 10, flags = marketFlag(category = 28, tradeAsObjectId = 100))
        )))

        assertThat(registry.getItemType(10).type).isEqualTo(ItemTypes_t.ITEM_TYPE_OTHER)
        assertThat(registry.getItemType(10).wareId).isEqualTo(100)
    }

    private fun loadedRegistry(): Items {
        val registry = Items()
        registry.loadFromProtobuf(appearances)
        return registry
    }

    companion object {
        private val appearances: Appearances by lazy {
            Appearances.load(File("data/items/appearances.dat"))
        }
    }
}

// Hand-encoded protobuf helpers for `src/protobuf/appearances.proto`.

private fun varint(value: Int): ByteArray {
    var remaining = value
    val bytes = ArrayList<Byte>()
    while (true) {
        val chunk = remaining and 0x7F
        remaining = remaining ushr 7
        if (remaining == 0) {
            bytes.add(chunk.toByte())
            return bytes.toByteArray()
        }
        bytes.add((chunk or 0x80).toByte())
    }
}

private fun tag(field: Int, wireType: Int): ByteArray = varint((field shl 3) or wireType)

private fun varintField(field: Int, value: Int): ByteArray = tag(field, 0) + varint(value)

private fun lengthDelimitedField(field: Int, payload: ByteArray): ByteArray =
    tag(field, 2) + varint(payload.size) + payload

private fun appearancesMessage(vararg objects: ByteArray): ByteArray =
    objects.fold(ByteArray(0)) { message, objectAppearance ->
        message + lengthDelimitedField(1, objectAppearance)
    }

private fun appearance(
    id: Int,
    flags: ByteArray? = null,
    frameGroup: ByteArray? = null,
    name: String? = null,
): ByteArray {
    var message = varintField(1, id)
    if (frameGroup != null) {
        message += lengthDelimitedField(2, frameGroup)
    }
    if (flags != null) {
        message += lengthDelimitedField(3, flags)
    }
    if (name != null) {
        message += lengthDelimitedField(4, name.toByteArray(Charsets.UTF_8))
    }
    return message
}

/** `AppearanceFlags.cumulative` (field 6). */
private fun cumulativeFlag(): ByteArray = varintField(6, 1)

/** `AppearanceFlags.market` (field 36) with category (field 1) and trade_as_object_id (field 2). */
private fun marketFlag(category: Int, tradeAsObjectId: Int): ByteArray =
    lengthDelimitedField(36, varintField(1, category) + varintField(2, tradeAsObjectId))

/**
 * `Appearance.frame_group` (field 2) carrying a sprite animation (SpriteInfo field 6,
 * SpriteAnimation field 3 = random_start_phase).
 */
private fun animationFrameGroup(randomStartPhase: Boolean): ByteArray {
    val animation = if (randomStartPhase) varintField(3, 1) else ByteArray(0)
    val spriteInfo = lengthDelimitedField(6, animation)
    return lengthDelimitedField(3, spriteInfo)
}
