package com.equi.channelingoverhaul.gametest

import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityType

/** The mods we depend on work with our NeoForge, on code paths the other tests don't reach. */
object DependencyGameTests {
    /**
     * A mob hit runs the damage events Apothic Attributes listens to, so a build
     * for another NeoForge fails here rather than on the first hit in a real game.
     */
    fun mobHit(helper: GameTestHelper) {
        val cat = helper.spawnWithNoFreeWill(EntityType.CAT, 2, 1, 2)
        val chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, 4, 1, 2)
        val health = chicken.health
        helper.assertTrue(cat.doHurtTarget(chicken), "the cat didn't hit")
        helper.assertTrue(chicken.health < health, "no damage: ${chicken.health} of $health")
        helper.succeed()
    }
}
