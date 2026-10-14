package org.opentibia.canary.systems.forge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for [ItemClassifications], the registry mirroring Canary's
 * `Game` classification store:
 *
 *  - `Game::addItemsClassification` (src/game/game.hpp:208-210)
 *  - `Game::getItemsClassification(id, create)` (src/game/game.cpp:9463-9477)
 *  - `Game::getItemsClassifications` (src/game/game.hpp:591-593)
 *  - `ItemClassification::addTier` (src/items/items_classification.hpp:27-33)
 *  - `ItemType::upgradeClassification` default 0 (src/items/items.hpp:329)
 *
 * Source of truth: ../.projects/canary
 */
class ItemClassificationsTest {

    @Test
    fun `createClassification stores a new classification retrievable by id`() {
        val classifications = ItemClassifications()

        val created = classifications.createClassification(4)

        assertThat(created.id).isEqualTo(4)
        assertThat(classifications.getClassification(4)).isSameAs(created)
        assertThat(classifications.getAll()).contains(created)
    }

    @Test
    fun `createClassification returns the existing classification for a repeated id`() {
        val classifications = ItemClassifications()

        val first = classifications.createClassification(1)
        val second = classifications.createClassification(1)

        assertThat(second).isSameAs(first)
        assertThat(classifications.getAll().count { it.id == 1 }).isEqualTo(1)
    }

    @Test
    fun `getClassification returns null for an unregistered id`() {
        assertThat(ItemClassifications().getClassification(9)).isNull()
    }

    @Test
    fun `the registry carries classification zero as the default`() {
        val classifications = ItemClassifications()

        assertThat(classifications.defaultClassification.id).isEqualTo(ItemClassifications.DEFAULT_CLASSIFICATION_ID)
        assertThat(classifications.defaultClassification).isSameAs(classifications.getClassification(0))
        assertThat(classifications.defaultClassification.tiers).isEmpty()
    }

    @Test
    fun `addClassification registers a prebuilt classification`() {
        val classifications = ItemClassifications()
        val classification = ItemClassification(7)

        classifications.addClassification(classification)

        assertThat(classifications.getClassification(7)).isSameAs(classification)
    }

    @Test
    fun `getAll lists the default classification and every created one`() {
        val classifications = ItemClassifications()

        val one = classifications.createClassification(1)
        val two = classifications.createClassification(2)

        assertThat(classifications.getAll())
            .containsExactly(classifications.defaultClassification, one, two)
    }

    @Test
    fun `addTier stores the tier price table used by forge fusion and transfer`() {
        val classification = ItemClassifications().createClassification(4)

        classification.addTier(
            tierId = 2,
            corePrice = 2,
            regularPrice = 20_000_000,
            convergenceFusionPrice = 110_000_000,
            convergenceTransferPrice = 165_000_000,
        )

        assertThat(classification.tiers[2]).isEqualTo(
            TierInfo(
                corePrice = 2,
                regularPrice = 20_000_000,
                convergenceFusionPrice = 110_000_000,
                convergenceTransferPrice = 165_000_000,
            ),
        )
    }
}
