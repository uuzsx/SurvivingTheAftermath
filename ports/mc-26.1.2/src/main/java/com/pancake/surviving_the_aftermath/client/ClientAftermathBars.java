package com.pancake.surviving_the_aftermath.client;

import com.pancake.surviving_the_aftermath.common.network.AftermathNetwork.BarPacket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientAftermathBars {
    private static final Map<UUID, BarPacket> BARS = new HashMap<>();
    public static void accept(BarPacket packet) {
        if (packet.texture() == null) BARS.remove(packet.id());
        else if (packet.offsets().length == 6) BARS.put(packet.id(), packet);
    }
    public static BarPacket get(UUID id) { return BARS.get(id); }
    public static void clear() { BARS.clear(); }
}
