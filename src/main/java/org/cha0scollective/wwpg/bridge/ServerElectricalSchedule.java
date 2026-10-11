package org.cha0scollective.wwpg.bridge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.mixin.WorldNetworksAccessor;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** One synchronous PG substep schedule for all stationary dimensions. */
public final class ServerElectricalSchedule {
    private static final WeakHashMap<MinecraftServer, Integer> LAST_TICK = new WeakHashMap<>();
    // An integrated server can prepare while the client ticks a Ponder scene.
    // Only the server thread may defer its native world solves.
    private static final ThreadLocal<Boolean> PREPARING = ThreadLocal.withInitial(() -> false);

    private ServerElectricalSchedule() {}
    public static boolean preparing() { return PREPARING.get(); }

    public static void preTick(ServerLevel trigger) {
        var server = trigger.getServer();
        int tick = server.getTickCount();
        if (Integer.valueOf(tick).equals(LAST_TICK.get(server))) return;
        LAST_TICK.put(server, tick);
        var worlds = new ArrayList<WorldNetworks>();
        // CEE prepares every dimension at tickServer HEAD, before PG's first
        // level pre-tick. Retain PG's native topology and matrix preparation,
        // but wait until every dimension is ready before advancing any of them.
        PREPARING.set(true);
        try {
            for (var level : server.getAllLevels()) {
                var world = GlobalElectricNetworks.getWorldNetworks(level);
                worlds.add(world);
                world.preTick();
            }
        } finally {
            PREPARING.remove();
        }
        var networks = worlds.stream().map(world -> List.copyOf(world.subnetworks)).toList();
        int steps = ModdedConfigs.server().electricity.solver.multiTicks.get();
        for (int step = 0; step < steps; step++) {
            for (int index = 0; index < worlds.size(); index++) {
                var level = (ServerLevel) worlds.get(index).world;
                for (var network : networks.get(index)) solve(level, network);
            }
        }
        for (var world : worlds) {
            ((WorldNetworksAccessor) world).wwpg$performance().end();
            Bridges.get((ServerLevel) world.world).finishSolving();
        }
    }

    public static void solve(ServerLevel level, ElectricalNetwork network) {
        var bridge = Bridges.get(level);
        bridge.beforeSolve(network);
        network.singleTick();
        bridge.afterSolve(network);
    }
    public static void clear() { LAST_TICK.clear(); PREPARING.remove(); }
}
