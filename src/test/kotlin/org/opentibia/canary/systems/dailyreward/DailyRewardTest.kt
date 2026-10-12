package org.opentibia.canary.systems.dailyreward

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DailyRewardTest {

    @Test
    fun `daily reward bonus enum preserves canary numbering`() {
        assertThat(DailyRewardBonus.DAILY_REWARD_FIRST.value).isEqualTo(2)
        assertThat(DailyRewardBonus.DAILY_REWARD_HP_REGENERATION.value).isEqualTo(2)
        assertThat(DailyRewardBonus.DAILY_REWARD_MP_REGENERATION.value).isEqualTo(3)
        assertThat(DailyRewardBonus.DAILY_REWARD_STAMINA_REGENERATION.value).isEqualTo(4)
        assertThat(DailyRewardBonus.DAILY_REWARD_DOUBLE_HP_REGENERATION.value).isEqualTo(5)
        assertThat(DailyRewardBonus.DAILY_REWARD_DOUBLE_MP_REGENERATION.value).isEqualTo(6)
        assertThat(DailyRewardBonus.DAILY_REWARD_SOUL_REGENERATION.value).isEqualTo(7)
        assertThat(DailyRewardBonus.DAILY_REWARD_LAST.value).isEqualTo(7)
    }

    @Test
    fun `daily reward status enum preserves canary numbering`() {
        assertThat(DailyRewardStatus.DAILY_REWARD_COLLECTED.value).isEqualTo(0)
        assertThat(DailyRewardStatus.DAILY_REWARD_NOTCOLLECTED.value).isEqualTo(1)
        assertThat(DailyRewardStatus.DAILY_REWARD_NOTAVAILABLE.value).isEqualTo(2)
    }

    @Test
    fun `isDailyReward defaults to not collected`() {
        val player = PlayerDailyReward()
        assertThat(player.isDailyReward).isEqualTo(DailyRewardStatus.DAILY_REWARD_NOTCOLLECTED.value)
    }

    @Test
    fun `setDailyReward stores the raw status value`() {
        val player = PlayerDailyReward()
        player.setDailyReward(DailyRewardStatus.DAILY_REWARD_COLLECTED.value)
        assertThat(player.isDailyReward).isEqualTo(DailyRewardStatus.DAILY_REWARD_COLLECTED.value)
        player.setDailyReward(DailyRewardStatus.DAILY_REWARD_NOTAVAILABLE.value)
        assertThat(player.isDailyReward).isEqualTo(DailyRewardStatus.DAILY_REWARD_NOTAVAILABLE.value)
    }

    @Test
    fun `setDailyReward performs no same-day rejection in the engine`() {
        // Canary's Player::setDailyReward is a pure assignment; the "already claimed
        // today" guard lives in Lua (DailyReward.isRewardTaken / selectDailyReward),
        // so the engine holder records the latest assigned value verbatim.
        val player = PlayerDailyReward()
        player.setDailyReward(DailyRewardStatus.DAILY_REWARD_COLLECTED.value)
        player.setDailyReward(DailyRewardStatus.DAILY_REWARD_COLLECTED.value)
        assertThat(player.isDailyReward).isEqualTo(DailyRewardStatus.DAILY_REWARD_COLLECTED.value)
    }

    @Test
    fun `daily reward streak kv round-trips through the scoped key`() {
        val player = PlayerDailyReward()
        player.kv.scoped("daily-reward").set("streak", 4L)
        assertThat(player.kv.scoped("daily-reward").get("streak")).isEqualTo(4L)
        // ScopedKV::buildKey composes "prefix.key"
        assertThat(player.kv.get("daily-reward.streak")).isEqualTo(4L)
    }

    @Test
    fun `missing kv entries read as null`() {
        val player = PlayerDailyReward()
        assertThat(player.kv.scoped("daily-reward").get("streak")).isNull()
    }

    @Test
    fun `streak advances and persists the advanced value`() {
        val player = PlayerDailyReward()
        val scoped = player.kv.scoped("daily-reward")
        scoped.set("streak", 1L)
        scoped.set("streak", scoped.get("streak")!! + 1)
        assertThat(scoped.get("streak")).isEqualTo(2L)
    }

    @Test
    fun `daily reward xp boost kv uses the top level key`() {
        val player = PlayerDailyReward()
        player.kv.set("daily-reward-xp-boost", 30L)
        assertThat(player.kv.get("daily-reward-xp-boost")).isEqualTo(30L)
    }

    @Test
    fun `nested scopes compose keys like ScopedKV`() {
        val player = PlayerDailyReward()
        player.kv.scoped("daily-reward").scoped("inner").set("k", 9L)
        assertThat(player.kv.get("daily-reward.inner.k")).isEqualTo(9L)
    }

    @Test
    fun `remove deletes a kv entry`() {
        val player = PlayerDailyReward()
        player.kv.set("daily-reward-xp-boost", 5L)
        player.kv.remove("daily-reward-xp-boost")
        assertThat(player.kv.get("daily-reward-xp-boost")).isNull()
    }
}
