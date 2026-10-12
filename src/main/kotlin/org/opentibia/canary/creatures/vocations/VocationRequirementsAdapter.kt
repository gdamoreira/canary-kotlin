package org.opentibia.canary.creatures.vocations

import org.opentibia.canary.systems.offlinetraining.Skill
import org.opentibia.canary.systems.offlinetraining.VocationRequirements

/**
 * Adapts a Canary [Vocation] to the offline-training [VocationRequirements] seam,
 * mirroring `Vocation::getReqSkillTries` / `Vocation::getReqMana`.
 */
class VocationRequirementsAdapter(private val vocation: Vocation) : VocationRequirements {
    override fun getReqSkillTries(skill: Skill, level: Int): Long =
        vocation.getReqSkillTries(skill.value, level)

    override fun getReqMana(magLevel: Int): Long = vocation.getReqMana(magLevel)
}
