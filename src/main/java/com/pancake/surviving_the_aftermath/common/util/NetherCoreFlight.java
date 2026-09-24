package com.pancake.surviving_the_aftermath.common.util;

import net.minecraft.world.phys.Vec3;

/** Persistent flight state on the vanilla eye entity; its item renderer displays the core. */
public interface NetherCoreFlight {
    void aftermath$launch(Vec3 destination, boolean returnItem);
}
