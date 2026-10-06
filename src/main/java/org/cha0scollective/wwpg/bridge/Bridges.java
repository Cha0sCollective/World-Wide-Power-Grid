package org.cha0scollective.wwpg.bridge;

import net.minecraft.server.level.ServerLevel;
import java.util.IdentityHashMap;
import java.util.Map;

public final class Bridges {
    private static final Map<ServerLevel, WorldBridge> WORLDS = new IdentityHashMap<>();
    private Bridges() {}
    public static WorldBridge get(ServerLevel level) { return WORLDS.computeIfAbsent(level, WorldBridge::new); }
    public static void unload(ServerLevel level) { WORLDS.remove(level); }
    public static void clear() { WORLDS.clear(); }
}
