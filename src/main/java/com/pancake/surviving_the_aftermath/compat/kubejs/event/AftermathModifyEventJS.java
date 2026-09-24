package com.pancake.surviving_the_aftermath.compat.kubejs.event;

import com.pancake.surviving_the_aftermath.api.module.IAftermathModule;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.Identifier;

import java.util.Collection;

public class AftermathModifyEventJS implements KubeEvent {
    private final Identifier identifier;
    private final Collection<IAftermathModule> aftermathModules;

    public AftermathModifyEventJS(Identifier identifier, Collection<IAftermathModule> aftermathModules) {
        this.identifier = identifier;
        this.aftermathModules = aftermathModules;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public Collection<IAftermathModule> getAftermathModules() {
        return aftermathModules;
    }

    public void remove(String name) {
        aftermathModules.removeIf(aftermathModule -> aftermathModule.getModuleName().equals(name));
    }

    public void add(IAftermathModule aftermathModule) {
        aftermathModules.add(aftermathModule);
    }
}