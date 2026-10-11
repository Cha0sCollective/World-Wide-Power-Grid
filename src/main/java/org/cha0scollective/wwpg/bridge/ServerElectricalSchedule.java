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
        // NeoForge still posts level pre-ticks while /tick freeze is active.
        // runsNormally also allows explicit /tick step without hidden substeps.
        if(!server.tickRateManager().runsNormally())return;
        int tick = server.getTickCount();
        if (Integer.valueOf(tick).equals(LAST_TICK.get(server))) return;
        LAST_TICK.put(server, tick);
        var worlds = new ArrayList<WorldNetworks>();
        for (var level : server.getAllLevels()) org.cha0scollective.wwpg.extension.ApiCircuits.get(level).prepare();
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
        for(var world:worlds) {
            var level=(ServerLevel)world.world;
            org.cha0scollective.wwpg.extension.NativeApiReadings.prepare(level,
                    new org.cha0scollective.wwpg.api.TickContext(level.getGameTime(),.05,steps));
        }
        for (int step = 0; step < steps; step++) {
            for (int index = 0; index < worlds.size(); index++) {
                var level = (ServerLevel) worlds.get(index).world;
                for (var network : networks.get(index)) solve(level, network);
            }
        }
        for (var world : worlds) {
            ((WorldNetworksAccessor) world).wwpg$performance().end();
            Bridges.get((ServerLevel) world.world).finishSolving();
            org.cha0scollective.wwpg.extension.ApiCircuits.get((ServerLevel)world.world).finish();
            org.cha0scollective.wwpg.extension.NativeApiReadings.finish((ServerLevel)world.world);
        }
    }

    public static void solve(ServerLevel level, ElectricalNetwork network) {
        var bridge = Bridges.get(level);
        bridge.beforeSolve(network);
        var api = org.cha0scollective.wwpg.extension.ApiCircuits.get(level);
        api.before(network, org.cha0scollective.wwpg.extension.ApiCircuits.step(level,network));
        try {
            network.singleTick();
            // A backend can return a finite trial state without accepting it.
            // CEE history/gameplay must observe the same acceptance boundary as
            // general API models, including ordinary native warm-up substeps.
            if (!network.isConverged()) {
                var error = new IllegalStateException("PG electrical substep did not converge");
                api.solveFailure(network, error);
                bridge.reject(network, error);
            }
        }
        catch(RuntimeException | LinkageError error) { api.solveFailure(network,error);bridge.reject(network,error); }
        api.after(network);
        bridge.afterSolve(network);
        org.cha0scollective.wwpg.extension.NativeApiReadings.after(level,network);
    }
    public static void clear() { LAST_TICK.clear(); PREPARING.remove(); }
}
