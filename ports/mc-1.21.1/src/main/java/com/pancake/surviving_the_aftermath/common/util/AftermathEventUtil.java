package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.api.AftermathState;
import com.pancake.surviving_the_aftermath.api.IAftermath;

import com.pancake.surviving_the_aftermath.api.module.IAftermathModule;
import com.pancake.surviving_the_aftermath.common.event.AftermathEvent;
import com.pancake.surviving_the_aftermath.compat.kubejs.util.AftermathEventJsUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModList;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class AftermathEventUtil {
    private static boolean post(net.neoforged.bus.api.Event event) {
        NeoForge.EVENT_BUS.post(event);
        return !(event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) || !cancellable.isCanceled();
    }

    public static boolean start(IAftermath aftermath, Set<UUID> players, ServerLevel level) {
        AftermathState previous = aftermath.getState();
        aftermath.setState(AftermathState.START);
        boolean postForge = post(new AftermathEvent.Start(aftermath, players, level));
        if (isKubejs()) {
            boolean postJS = AftermathEventJsUtil.start(aftermath, players, level);
            boolean accepted = postForge && postJS;
            if (!accepted) aftermath.setState(previous);
            return accepted;
        }
        if (!postForge) aftermath.setState(previous);
        return postForge;
    }
    public static boolean ready(IAftermath aftermath, Set<UUID> players, ServerLevel level) {
        AftermathState previous = aftermath.getState();
        aftermath.setState(AftermathState.READY);
        boolean postForge = post(new AftermathEvent.Ready(aftermath, players, level));
        if (isKubejs()){
            boolean postJS = AftermathEventJsUtil.ready(aftermath, players, level);
            boolean accepted = postForge && postJS;
            if (!accepted) aftermath.setState(previous);
            return accepted;
        }
        if (!postForge) aftermath.setState(previous);
        return postForge;
    }
    public static boolean ongoing(IAftermath aftermath, Set<UUID> players, ServerLevel level) {
        aftermath.setState(AftermathState.ONGOING);
        boolean postForge = post(new AftermathEvent.Ongoing(aftermath, players, level));
        if (isKubejs()){
            boolean postJS = AftermathEventJsUtil.ongoing(aftermath, players, level);
            return postForge && postJS;
        }
        return postForge;
    }
    public static boolean victory(IAftermath aftermath, Set<UUID> players, ServerLevel level) {
        aftermath.setState(AftermathState.VICTORY);
        boolean postForge = post(new AftermathEvent.Victory(aftermath, players, level));
        if (isKubejs()){
            boolean postJS = AftermathEventJsUtil.victory(aftermath, players, level);
            return postForge && postJS;
        }
        return postForge;
    }
    public static boolean celebrating(IAftermath aftermath, Set<UUID> players, ServerLevel level) {
        AftermathState previous = aftermath.getState();
        aftermath.setState(AftermathState.CELEBRATING);
        boolean postForge = post(new AftermathEvent.Celebrating(aftermath, players, level));
        if (isKubejs()){
            boolean postJS = AftermathEventJsUtil.celebrating(aftermath, players, level);
            boolean accepted = postForge && postJS;
            if (!accepted) aftermath.setState(previous);
            return accepted;
        }
        if (!postForge) aftermath.setState(previous);
        return postForge;
    }
    public static boolean lose(IAftermath aftermath, Set<UUID> players, ServerLevel level) {
        aftermath.setState(AftermathState.LOSE);
        boolean postForge = post(new AftermathEvent.Lose(aftermath, players, level));
        if (isKubejs()){
            boolean postJS = AftermathEventJsUtil.lose(aftermath, players, level);
            return postForge && postJS;
        }
        return postForge;
    }
    public static boolean end(IAftermath aftermath, Set<UUID> players, ServerLevel level) {
        aftermath.setState(AftermathState.END);
        boolean postForge = post(new AftermathEvent.End(aftermath, players, level));
        if (isKubejs()){
            boolean postJS = AftermathEventJsUtil.end(aftermath, players, level);
            return postForge && postJS;
        }
        return postForge;
    }

    public static boolean modify(ResourceLocation location, Collection<IAftermathModule> aftermathModules) {
        if (isKubejs()){
            return AftermathEventJsUtil.modify(location, aftermathModules);
        }
        return true;
    }

    private static boolean isKubejs() {
        return ModList.get().isLoaded("kubejs");
    }

}
