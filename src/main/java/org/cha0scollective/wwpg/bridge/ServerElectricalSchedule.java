package org.cha0scollective.wwpg.bridge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.api.TickContext;
import org.cha0scollective.wwpg.extension.ApiCircuits;
import org.cha0scollective.wwpg.extension.NativeApiReadings;
import org.cha0scollective.wwpg.experimental.ScopedRestoration;
import org.cha0scollective.wwpg.mixin.WorldNetworksAccessor;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.cha0scollective.wwpg.mixin.PgSolverStateAccessor;
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
        for (var level : server.getAllLevels()) ApiCircuits.get(level).prepare();
        for (var level : server.getAllLevels()) worlds.add(GlobalElectricNetworks.getWorldNetworks(level));
        ScopedRestoration.prepare(worlds);
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
        ScopedRestoration.finishPreparation(worlds);
        RestorationCoordinator.coupledClosure(worlds);
        RestorationCoordinator.finishPreparation(worlds);
        // Native preparation can merge/split circuits. Bind extension work to
        // the final owners for this tick, before any electrical advancement.
        for (var world : worlds) ApiCircuits.get((ServerLevel) world.world).beginSolving();
        var networks = worlds.stream().map(world -> world.subnetworks.stream().filter(network -> !RestorationCoordinator.paused(network)).toList()).toList();
        for (var world : worlds) Bridges.get((ServerLevel) world.world).scheduledNetworks(
                (int) world.subnetworks.stream().filter(network -> !network.isEmpty() && !RestorationCoordinator.paused(network)).count());
        int steps = ModdedConfigs.server().electricity.solver.multiTicks.get();
        for(var world:worlds) {
            var level=(ServerLevel)world.world;
            NativeApiReadings.prepare(level, new TickContext(level.getGameTime(), .05, steps));
        }
        try {
            // PG owns generator sampling, torque accumulation and the ordinary rotor tick.
            for (int step = 0; step < steps; step++) {
                for (int index = 0; index < worlds.size(); index++) {
                    var level = (ServerLevel) worlds.get(index).world;
                    for (var network : networks.get(index)) solve(level, network);
                }
            }
            for (var world : worlds) {
                var level = (ServerLevel) world.world;
                ((WorldNetworksAccessor) world).wwpg$performance().end();
                Bridges.get(level).finishSolving();
                ApiCircuits.get(level).finish();
                NativeApiReadings.finish(level);
            }
        } catch (RuntimeException | LinkageError failure) {
            abortFrame(worlds, failure);
            // Do not resume if a checkpoint or unavailable publication failed.
            if (failure.getSuppressed().length != 0) throw failure;
        }
    }

    private static void abortFrame(List<WorldNetworks> worlds, Throwable failure) {
        for (var world : worlds) {
            var level = (ServerLevel) world.world;
            ApiCircuits.get(level).abortFrame();
            NativeApiReadings.abortFrame(level);
            for (var network : world.subnetworks) {
                if (((ElectricalNetworkAccessor) network).wwpg$solver() instanceof PgSolverStateAccessor solver)
                    solver.wwpg$setConverged(false);
            }
            Bridges.get(level).abortFrame(failure);
        }
    }

    public static void solve(ServerLevel level, ElectricalNetwork network) {
        var bridge = Bridges.get(level);
        var api = ApiCircuits.get(level);
        api.beginSubstep();
        try {
            bridge.beforeSolve(network);
            api.before(network, ApiCircuits.step(level, network));
            solveBackend(network, bridge, api);
            api.after(network);
            bridge.afterSolve(network);
            NativeApiReadings.after(level, network);
        } finally {
            api.endSubstep();
        }
    }

    private static void solveBackend(ElectricalNetwork network, WorldBridge bridge, ApiCircuits api) {
        try {
            var solver = ((ElectricalNetworkAccessor) network).wwpg$solver();
            int warmBefore = solver instanceof PgSolverStateAccessor warm ? warm.wwpg$warmUpTicks() : 0;
            network.singleTick();
            // A backend can return a finite trial state without accepting it.
            // CEE history/gameplay must observe the same acceptance boundary as
            // general API models, including ordinary native warm-up substeps.
            if (!network.isConverged()) {
                int warmAfter = solver instanceof PgSolverStateAccessor warm ? warm.wwpg$warmUpTicks() : 0;
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
    }
    public static void clear() { LAST_TICK.clear(); PREPARING.remove(); PentodeInitialization.clear(); }
}
