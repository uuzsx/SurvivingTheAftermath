package com.pancake.surviving_the_aftermath.api;

import net.neoforged.bus.api.Event;

public interface IAftermathEvent {
    Event getForge();
    Object getKubeJS();
}
