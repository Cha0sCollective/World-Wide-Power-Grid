package org.cha0scollective.wwpg.bridge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.mixin.WorldNetworksAccessor;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.cha0scollective.wwpg.mixin.PgSolverWarmupAccessor;
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
        for (var level : server.getAllLevels()) worlds.add(GlobalElectricNetworks.getWorldNetworks(level));
        RestorationCoordinator.joinCoupledWindows(worlds);
        // CEE prepares every dimension at tickServer HEAD, before PG's first
        // level pre-tick. Retain PG's native topology and matrix preparation,
        // but wait until every dimension is ready before advancing any of them.
        PREPARING.set(true);
        try {
            for (var world : worlds) world.preTick();
        } finally {
            PREPARING.remove();
        }
        RestorationCoordinator.coupledClosure(worlds);
        RestorationCoordinator.finishPreparation(worlds);
        // Native preparation can merge/split circuits. Bind extension work to
        // the final owners for this tick, before any electrical advancement.
        for (var world : worlds) org.cha0scollective.wwpg.extension.ApiCircuits.get((ServerLevel) world.world).beginSolving();
        var networks = worlds.stream().map(world -> world.subnetworks.stream().filter(network -> !RestorationCoordinator.paused(network)).toList()).toList();
        for (var world : worlds) Bridges.get((ServerLevel) world.world).scheduledNetworks(
                (int) world.subnetworks.stream().filter(network -> !network.isEmpty() && !RestorationCoordinator.paused(network)).count());
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
            var solver = ((ElectricalNetworkAccessor) network).wwpg$solver();
            int warmBefore = solver instanceof PgSolverWarmupAccessor warm ? warm.wwpg$warmUpTicks() : 0;
            network.singleTick();
            // A backend can return a finite trial state without accepting it.
            // CEE history/gameplay must observe the same acceptance boundary as
            // general API models, including ordinary native warm-up substeps.
            if (!network.isConverged()) {
                int warmAfter = solver instanceof PgSolverWarmupAccessor warm ? warm.wwpg$warmUpTicks() : 0;
                if (SubstepAcceptance.settling(warmBefore, warmAfter, false)) {
                    PentodeInitialization.converged(network);
                    api.invalidate(network);
                    bridge.unavailable(network);
                } else if(PentodeInitialization.hold(network,warmBefore)) {
                    // PG limits a fresh pentode's bias to 0.25 V/iteration at
                    // default settings. A 100 V screen can exceed its first
                    // 200-iteration solve. Keep that trial unavailable without
                    // changing equations, iteration limits, or physical state.
                    api.invalidate(network);
                    bridge.unavailable(network);
                } else {
                    var error = new IllegalStateException("PG electrical substep did not converge (warm-up " + warmBefore + " -> " + warmAfter + ")");
                    api.solveFailure(network, error);
                    bridge.reject(network, error);
                }
            } else PentodeInitialization.converged(network);
        }
        catch(RuntimeException | LinkageError error) { api.solveFailure(network,error);bridge.reject(network,error); }
        api.after(network);
        bridge.afterSolve(network);
        org.cha0scollective.wwpg.extension.NativeApiReadings.after(level,network);
    }
    public static void clear() { LAST_TICK.clear(); PREPARING.remove(); PentodeInitialization.clear(); }
}
