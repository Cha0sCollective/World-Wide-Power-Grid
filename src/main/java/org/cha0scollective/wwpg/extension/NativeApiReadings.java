package org.cha0scollective.wwpg.extension;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.NodeResult;
import org.cha0scollective.wwpg.api.TickContext;
import org.cha0scollective.wwpg.api.VoltageMeasurement;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLine;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.*;

/** Read-only terminal snapshots from the existing PG schedule, including native devices. */
public final class NativeApiReadings {
    private static final Map<ServerLevel, NativeApiReadings> WORLDS = new IdentityHashMap<>();
    private static final Object EARTH_REFERENCE = new Object();
    private final ServerLevel level;
    private final WorldNetworks world;
    private final TickContext context;
    private final int serverTick;
    private final Map<Key, Frame> frames = new HashMap<>();
    private final Map<ElectricalNetwork, List<Frame>> direct = new IdentityHashMap<>();
    private final List<Frame> interpolated = new ArrayList<>();
    private final Map<ElectricalNetwork, Integer> steps = new IdentityHashMap<>();
    private final Map<ElectricalNetwork, Object> references = new IdentityHashMap<>();
    private final Set<ElectricalNetwork> rejected = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean finished;

    private record Key(BlockPos pos, int nativeId) {
        Key { pos = pos.immutable(); }
    }

    private NativeApiReadings(ServerLevel level, TickContext context) {
        this.level = level;
        this.context = context;
        serverTick = level.getServer().getTickCount();
        world = GlobalElectricNetworks.getWorldNetworks((LevelAccessor) level);
    }

    /** Run after all native preTick topology/matrix preparation and before the first solve. */
    public static void prepare(ServerLevel level, TickContext context) {
        var recorder = new NativeApiReadings(level, context);
        WORLDS.put(level, recorder);
        if (recorder.world != null) recorder.snapshot();
    }

    private void snapshot() {
        // A native explicit ground gives a shared reference. Otherwise unrelated
        // floating circuits retain distinct solver references.
        for (var network : world.subnetworks) references.put(network,
                org.cha0scollective.wwpg.bridge.Bridges.get(level).hasPhysicalReference(network) ? EARTH_REFERENCE : network);
        for (var line : world.transmissionLines.values()) {
            var first = line.getNode1().getNetwork();
            var second = line.getNode2().getNetwork();
            if (first != null && second != null) unite(first, second);
        }
        for (var entry : List.copyOf(world.globalExternalNodes.entrySet())) {
            if (!(entry.getKey() instanceof BlockWireEndpoint endpoint) || !ApiTerminals.ready(level, endpoint.getPos())) continue;
            var state = Terminals.loadedState(level, endpoint.getPos());
            var entity = ApiTerminals.loadedBlockEntity(level, endpoint.getPos());
            if (state.hasBlockEntity() && entity == null) continue;
            var frame = new Frame(new Key(endpoint.getPos(), endpoint.getTerminal()), entry.getValue(), state.getBlock(),
                    entity == null ? state.getBlock() : entity, context.substeps());
            frames.put(frame.key, frame);
            try {
                var nativePg = Terminals.nativePg(level, endpoint.getPos());
                if (nativePg != null) {
                    boolean accessible = false;
                    for (int local = 0; local < nativePg.terminalCount(); ++local) {
                        var placement = nativePg.terminal(state, local);
                        if (placement != null && nativePg.terminalIndexAt(state, placement.getOrigin()) == endpoint.getTerminal()) {
                            accessible = true; break;
                        }
                    }
                    if (!accessible) { frame.unavailable(MeasurementStatus.NOT_READY, "Native terminal is unavailable"); continue; }
                } else if (state.getBlock() instanceof ElectricalDeviceBlock<?> cee) {
                    if (!cee.getNodePositions(level, endpoint.getPos(), state).containsKey(endpoint.getTerminal())
                            || !cee.isNodeAccessible(level, endpoint.getPos(), state, endpoint.getTerminal())) {
                        frame.unavailable(MeasurementStatus.NOT_READY, "CEE terminal is unavailable"); continue;
                    }
                    var isolation = org.cha0scollective.wwpg.bridge.Bridges.get(level)
                            .isolatedTerminalReason(endpoint.getPos(), endpoint.getTerminal());
                    if (isolation.isPresent()) {
                        frame.unavailable(MeasurementStatus.ISOLATED, isolation.get()); continue;
                    }
                } else {
                    var api = ApiTerminals.existing(level, endpoint.getPos());
                    if (api == null || api.ownerChanged() || api.terminalDefinition(endpoint.getTerminal()) == null) {
                        frame.unavailable(MeasurementStatus.NOT_READY, "Extension terminal is unavailable"); continue;
                    }
                    frame.extension = true;
                }
                var network = frame.node.getNetwork();
                if (network != null) {
                    frame.required = List.of(network);
                    frame.reference = reference(network);
                    direct.computeIfAbsent(network, ignored -> new ArrayList<>()).add(frame);
                } else {
                    var line = world.findLineMiddle(frame.node);
                    if (line == null || line.getNode1().getNetwork() == null || line.getNode2().getNetwork() == null) {
                        frame.unavailable(MeasurementStatus.NOT_READY, "Terminal is not in a prepared circuit"); continue;
                    }
                    frame.line = line;
                    var first = line.getNode1().getNetwork();
                    var second = line.getNode2().getNetwork();
                    frame.required = first == second ? List.of(first) : List.of(first, second);
                    frame.reference = reference(first);
                    interpolated.add(frame);
                }
            } catch (RuntimeException | LinkageError error) {
                frame.unavailable(MeasurementStatus.ISOLATED, "Terminal geometry is invalid: " + error.getClass().getSimpleName());
            }
        }
    }

    private Object reference(ElectricalNetwork network) { return references.getOrDefault(network, network); }
    private void unite(ElectricalNetwork first, ElectricalNetwork second) {
        Object firstReference = reference(first), secondReference = reference(second);
        if (firstReference == secondReference) return;
        Object keep = secondReference == EARTH_REFERENCE ? secondReference : firstReference;
        Object replace = keep == firstReference ? secondReference : firstReference;
        references.replaceAll((network, existing) -> existing == replace ? keep : existing);
        references.put(first, keep); references.put(second, keep);
    }

    /** Called once after each final network solution; never called for nonlinear trial iterations. */
    public static void after(ServerLevel level, ElectricalNetwork network) {
        var recorder = WORLDS.get(level);
        if (recorder == null || recorder.finished) return;
        int step = recorder.steps.getOrDefault(network, 0);
        recorder.steps.put(network, step + 1);
        boolean accepted = network.isConverged() && !recorder.rejected.contains(network);
        for (var frame : recorder.direct.getOrDefault(network, List.of())) {
            if (frame.node.getNetwork() != network) frame.unavailable(MeasurementStatus.NOT_READY, "Terminal network changed during solve");
            else frame.sample(step, frame.node.getVoltage(), accepted);
        }
        // Mid-line voltages depend on both ends. Wait until both containing
        // networks have accepted the same substep before interpolating.
        for (var frame : recorder.interpolated) {
            int next = frame.count;
            if (frame.required.stream().allMatch(required -> recorder.steps.getOrDefault(required, 0) == next + 1)) {
                boolean valid = frame.required.stream().allMatch(required -> required.isConverged() && !recorder.rejected.contains(required));
                frame.sample(next, frame.line.voltageFor(frame.node), valid);
            }
        }
    }

    /** Reject a whole tick if later bridge validation invalidates an earlier network sample. */
    public static void invalidate(ServerLevel level, ElectricalNetwork network) {
        var recorder = WORLDS.get(level);
        if (recorder == null || network == null) return;
        recorder.rejected.add(network);
        for (var frame : recorder.frames.values()) if (frame.required.contains(network))
            frame.unavailable(MeasurementStatus.SOLVE_FAILED, "Containing circuit did not produce an accepted solution");
    }

    /** Publish only complete current-tick waveforms. Call after all failure validation. */
    public static void finish(ServerLevel level) {
        var recorder = WORLDS.get(level);
        if (recorder == null) return;
        for (var frame : recorder.frames.values()) {
            if (frame.status != null) continue;
            if (frame.count != recorder.context.substeps()) frame.unavailable(MeasurementStatus.NOT_READY, "Incomplete electrical timestep");
            else {
                try { frame.result = summarize(frame.values); }
                catch (RuntimeException error) { frame.unavailable(MeasurementStatus.SOLVE_FAILED, "Invalid voltage samples"); }
            }
        }
        recorder.finished = true;
    }

    public static VoltageMeasurement readTerminal(ServerLevel level, BlockPos pos, int nativeId) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(pos, "pos");
        if (nativeId < 0) throw new IllegalArgumentException("Native terminal ID must be nonnegative");
        var recorder = WORLDS.get(level);
        if (recorder == null) return unavailable(level, "No current terminal snapshot");
        var frame = recorder.frames.get(new Key(pos, nativeId));
        var invalid = recorder.check(frame);
        return invalid == null ? new VoltageMeasurement(recorder.context, MeasurementStatus.VALID, Optional.of(frame.result), "") : invalid;
    }

    public static VoltageMeasurement readVoltageBetween(ServerLevel level, BlockPos posA, int idA, BlockPos posB, int idB) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(posA, "posA"); Objects.requireNonNull(posB, "posB");
        if (idA < 0 || idB < 0) throw new IllegalArgumentException("Native terminal IDs must be nonnegative");
        var recorder = WORLDS.get(level);
        if (recorder == null) return unavailable(level, "No current terminal snapshot");
        var first = recorder.frames.get(new Key(posA, idA));
        var second = recorder.frames.get(new Key(posB, idB));
        var invalid = recorder.check(first); if (invalid != null) return invalid;
        invalid = recorder.check(second); if (invalid != null) return invalid;
        if (first.reference != second.reference)
            return VoltageMeasurement.unavailable(recorder.context, MeasurementStatus.NOT_READY, "Terminals do not share an electrical reference");
        try {
            return new VoltageMeasurement(recorder.context, MeasurementStatus.VALID,
                    Optional.of(difference(first.values, second.values)), "");
        } catch (RuntimeException error) {
            return VoltageMeasurement.unavailable(recorder.context, MeasurementStatus.SOLVE_FAILED, "Invalid differential voltage samples");
        }
    }

    private VoltageMeasurement check(Frame frame) {
        if (!finished || serverTick != level.getServer().getTickCount()) return unavailable(level, "No current completed electrical timestep");
        if (frame == null || !ApiTerminals.ready(level, frame.key.pos()) || ApiTerminals.pendingRemoval(level,frame.key.pos()))
            return unavailable(level, "Terminal or chunk is not ready, or its previous owner is awaiting retirement");
        var state = Terminals.loadedState(level, frame.key.pos());
        var entity = ApiTerminals.loadedBlockEntity(level, frame.key.pos());
        Object anchor = entity == null ? state.getBlock() : entity;
        if (frame.block != state.getBlock() || frame.anchor != anchor
                || world.globalExternalNodes.get(new BlockWireEndpoint(frame.key.pos(), frame.key.nativeId())) != frame.node)
            return unavailable(level, "Terminal owner changed after the electrical snapshot");
        if (frame.extension) {
            var snapshot = ApiCircuits.results(level, frame.key.pos());
            if (snapshot.status() != MeasurementStatus.VALID)
                return VoltageMeasurement.unavailable(context, snapshot.status(), snapshot.detail());
        }
        if (frame.status != null) return VoltageMeasurement.unavailable(context, frame.status, frame.detail);
        if (frame.result == null) return unavailable(level, "No accepted terminal readings");
        return null;
    }

    private static VoltageMeasurement unavailable(ServerLevel level, String detail) {
        return VoltageMeasurement.unavailable(new TickContext(level.getGameTime(), .05,
                Math.max(1, ModdedConfigs.server().electricity.solver.multiTicks.get())), MeasurementStatus.NOT_READY, detail);
    }

    /** Pure waveform reduction is shared with regression tests; no solver state is read here. */
    static NodeResult summarize(double[] values) {
        if (values.length == 0) throw new IllegalArgumentException("No voltage samples");
        double mean = 0, rms = 0;
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite voltage sample");
            mean += value / values.length;
            rms = Math.hypot(rms, value / Math.sqrt(values.length));
        }
        return new NodeResult(values[values.length - 1], mean, rms);
    }

    static NodeResult difference(double[] first, double[] second) {
        if (first.length != second.length) throw new IllegalArgumentException("Voltage waveforms do not share a timestep");
        var differential = new double[first.length];
        for (int i = 0; i < differential.length; ++i) differential[i] = first[i] - second[i];
        return summarize(differential);
    }

    public static void unload(ServerLevel level) { WORLDS.remove(level); }
    public static void clear() { WORLDS.clear(); }

    private static final class Frame {
        final Key key;
        final OwnedFloatingNode node;
        final Block block;
        final Object anchor;
        final double[] values;
        List<ElectricalNetwork> required = List.of();
        TransmissionLine line;
        Object reference;
        boolean extension;
        int count;
        MeasurementStatus status;
        String detail = "";
        NodeResult result;

        Frame(Key key, OwnedFloatingNode node, Block block, Object anchor, int substeps) {
            this.key = key; this.node = node; this.block = block; this.anchor = anchor; values = new double[substeps];
        }
        void unavailable(MeasurementStatus status, String detail) { this.status = status; this.detail = ApiDiagnostics.detail(detail); result = null; }
        void sample(int step, double value, boolean accepted) {
            if (status != null) return;
            if (!accepted || !Double.isFinite(value)) {
                unavailable(MeasurementStatus.SOLVE_FAILED, "Containing circuit did not produce an accepted solution"); return;
            }
            if (step != count || count >= values.length) {
                unavailable(MeasurementStatus.NOT_READY, "Voltage samples arrived out of order"); return;
            }
            values[count++] = value;
        }
    }
}
