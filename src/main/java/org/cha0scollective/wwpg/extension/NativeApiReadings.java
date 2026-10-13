package org.cha0scollective.wwpg.extension;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.NodeResult;
import org.cha0scollective.wwpg.api.TickContext;
import org.cha0scollective.wwpg.api.VoltageMeasurement;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.cha0scollective.wwpg.wiring.LineVoltages;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.base.IElectric;
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
    private TickContext context;
    private int serverTick;
    private Map<Key, Frame> frames = new HashMap<>(), previousFrames = new HashMap<>();
    private final Map<BlockPos, Owner> owners = new HashMap<>();
    private final Map<ElectricalNetwork, List<Frame>> direct = new IdentityHashMap<>();
    private final Map<ElectricalNetwork, List<LineFrame>> interpolated = new IdentityHashMap<>();
    private final Map<ElectricalNetwork, Integer> steps = new IdentityHashMap<>();
    private final Map<ElectricalNetwork, Object> references = new IdentityHashMap<>();
    private ReferenceGroups referenceGroups = new ReferenceGroups();
    private final Set<ElectricalNetwork> rejected = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<TransmissionLine, Map<OwnedFloatingNode, Double>> lineFractions = new IdentityHashMap<>();
    private final Map<TransmissionLine, LineFrame> lines = new IdentityHashMap<>();
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
        var recorder = WORLDS.get(level);
        if (recorder == null || recorder.world != GlobalElectricNetworks.getWorldNetworks((LevelAccessor)level)) {
            recorder = new NativeApiReadings(level, context);
            WORLDS.put(level, recorder);
        } else {
            recorder.context = context;
            recorder.serverTick = level.getServer().getTickCount();
            var scratch = recorder.previousFrames;
            recorder.previousFrames = recorder.frames;
            recorder.frames = scratch;
            recorder.frames.clear(); recorder.owners.clear(); recorder.direct.clear(); recorder.interpolated.clear();
            recorder.steps.clear(); recorder.references.clear(); recorder.rejected.clear(); recorder.lineFractions.clear(); recorder.lines.clear();
            recorder.referenceGroups = new ReferenceGroups(); recorder.finished = false;
        }
        if (recorder.world != null) recorder.snapshot(recorder.previousFrames);
    }

    private void snapshot(Map<Key, Frame> previous) {
        // A native explicit ground gives a shared reference. Otherwise unrelated
        // floating circuits retain distinct solver references.
        var grounded = org.cha0scollective.wwpg.bridge.Bridges.get(level).physicalReferenceNetworks(world.subnetworks);
        for (var network : world.subnetworks) references.put(network, grounded.contains(network) ? EARTH_REFERENCE : network);
        for (var line : world.transmissionLines.values()) {
            var first = line.getNode1().getNetwork();
            var second = line.getNode2().getNetwork();
            if (first != null && second != null) unite(first, second);
        }
        for (var entry : List.copyOf(world.globalExternalNodes.entrySet())) {
            if (!(entry.getKey() instanceof BlockWireEndpoint endpoint)) continue;
            Owner owner;
            try { owner = owners.computeIfAbsent(endpoint.getPos(), this::owner); }
            catch (RuntimeException | LinkageError error) {
                var state = Terminals.loadedState(level, endpoint.getPos());
                var entity = ApiTerminals.loadedBlockEntity(level, endpoint.getPos());
                var invalid = new Frame(new Key(endpoint.getPos(), endpoint.getTerminal()), entry.getValue(), state.getBlock(),
                        entity == null ? state.getBlock() : entity, context.substeps());
                invalid.unavailable(MeasurementStatus.ISOLATED, "Terminal geometry is invalid: " + error.getClass().getSimpleName());
                frames.put(invalid.key, invalid); continue;
            }
            if (owner == null) continue;
            var state = owner.state;
            Object anchor = owner.entity == null ? state.getBlock() : owner.entity;
            var key = new Key(endpoint.getPos(), endpoint.getTerminal());
            var frame = previous.get(key);
            if (frame == null || frame.node != entry.getValue() || frame.anchor != anchor || frame.block != state.getBlock() || frame.values.length != context.substeps())
                frame = new Frame(key, entry.getValue(), state.getBlock(), anchor, context.substeps());
            else frame.reset();
            frames.put(frame.key, frame);
            try {
                if (owner.nativeIds != null) {
                    if (!owner.nativeIds.contains(endpoint.getTerminal())) { frame.unavailable(MeasurementStatus.NOT_READY, "Native terminal is unavailable"); continue; }
                } else if (owner.ceeIds != null) {
                    if (!owner.ceeIds.contains(endpoint.getTerminal())) {
                        frame.unavailable(MeasurementStatus.NOT_READY, "CEE terminal is unavailable"); continue;
                    }
                    var isolation = org.cha0scollective.wwpg.bridge.Bridges.get(level)
                            .isolatedTerminalReason(endpoint.getPos(), endpoint.getTerminal());
                    if (isolation.isPresent()) {
                        frame.unavailable(MeasurementStatus.ISOLATED, isolation.get()); continue;
                    }
                } else {
                    var api = owner.api;
                    if (api == null || api.ownerChanged() || api.terminalDefinition(endpoint.getTerminal()) == null) {
                        frame.unavailable(MeasurementStatus.NOT_READY, "Extension terminal is unavailable"); continue;
                    }
                    frame.extension = true;
                }
                var network = frame.node.getNetwork();
                if (org.cha0scollective.wwpg.bridge.RestorationCoordinator.paused(network)) {
                    frame.unavailable(MeasurementStatus.NOT_READY, "Circuit is restoring its chunks and wires"); continue;
                }
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
                    var fractions = lineFractions.computeIfAbsent(line, ignored -> LineVoltages.sample(
                            line.segments.stream().map(segment -> new LineVoltages.Segment<>(segment.getNode2(), segment.getResistance())).toList(),
                            line.getNode1(), line.getNode2(), 0, 1, line.getResistance()));
                    frame.fraction = fractions.getOrDefault(frame.node, 1d);
                    var first = line.getNode1().getNetwork();
                    var second = line.getNode2().getNetwork();
                    if (org.cha0scollective.wwpg.bridge.RestorationCoordinator.paused(first) || org.cha0scollective.wwpg.bridge.RestorationCoordinator.paused(second)) {
                        frame.unavailable(MeasurementStatus.NOT_READY, "Circuit is restoring its chunks and wires"); continue;
                    }
                    frame.required = first == second ? List.of(first) : List.of(first, second);
                    frame.reference = reference(first);
                    var lineFrame = lines.get(line);
                    if (lineFrame == null) {
                        lineFrame = new LineFrame(line, frame.required);
                        lines.put(line, lineFrame);
                        for (var required : frame.required)
                            interpolated.computeIfAbsent(required, ignored -> new ArrayList<>()).add(lineFrame);
                    }
                    lineFrame.frames.add(frame);
                }
            } catch (RuntimeException | LinkageError error) {
                frame.unavailable(MeasurementStatus.ISOLATED, "Terminal geometry is invalid: " + error.getClass().getSimpleName());
            }
        }
    }
    private record Owner(BlockState state, BlockEntity entity, Set<Integer> nativeIds, Set<Integer> ceeIds, ApiElectric api) { }
    private Owner owner(BlockPos pos) {
        if (!ApiTerminals.ready(level, pos)) return null;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        var state = chunk.getBlockState(pos);
        var entity = chunk.getBlockEntity(pos, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
        if (state.hasBlockEntity() && entity == null) return null;
        var nativePg = entity instanceof IElectric electricEntity ? electricEntity
                : state.getBlock() instanceof IElectric electricBlock ? electricBlock : null;
        Set<Integer> nativeIds = null, ceeIds = null;
        if (nativePg != null) {
            nativeIds = new HashSet<>();
            int terminalCount = nativePg.terminalCount();
            for (int local = 0; local < terminalCount; local++) {
                var placement = nativePg.terminal(state, local);
                if (placement != null) nativeIds.add(nativePg.terminalIndexAt(state, placement.getOrigin()));
            }
        } else if (state.getBlock() instanceof ElectricalDeviceBlock<?> cee) {
            ceeIds = new HashSet<>();
            for (int id : cee.getNodePositions(level, pos, state).keySet())
                if (cee.isNodeAccessible(level, pos, state, id)) ceeIds.add(id);
        }
        return new Owner(state, entity, nativeIds, ceeIds, ApiTerminals.existing(level, pos));
    }

    private Object reference(ElectricalNetwork network) { return referenceGroups.root(references.getOrDefault(network, network)); }
    private void unite(ElectricalNetwork first, ElectricalNetwork second) {
        referenceGroups.unite(reference(first), reference(second), EARTH_REFERENCE);
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
        for (var line : recorder.interpolated.getOrDefault(network, List.of())) {
            int next = line.count;
            boolean ready = true, valid = true;
            for (var required : line.required) {
                ready &= recorder.steps.getOrDefault(required, 0) == next + 1;
                valid &= required.isConverged() && !recorder.rejected.contains(required);
            }
            if (ready) {
                double first = line.line.getNode1().getVoltage();
                double second = line.line.getNode2().getVoltage();
                for (var frame : line.frames)
                    frame.sample(next, first * (1 - frame.fraction) + second * frame.fraction, valid);
                ++line.count;
            }
        }
    }

    /** Reject a whole tick if later bridge validation invalidates an earlier network sample. */
    public static void invalidate(ServerLevel level, ElectricalNetwork network) {
        var recorder = WORLDS.get(level);
        if (recorder == null || network == null) return;
        recorder.rejected.add(network);
        for (var frame : recorder.direct.getOrDefault(network, List.of()))
            frame.unavailable(MeasurementStatus.SOLVE_FAILED, "Containing circuit did not produce an accepted solution");
        for (var line : recorder.interpolated.getOrDefault(network, List.of()))
            for (var frame : line.frames)
                frame.unavailable(MeasurementStatus.SOLVE_FAILED, "Containing circuit did not produce an accepted solution");
    }

    /** Publish only complete current-tick waveforms. Call after all failure validation. */
    public static void finish(ServerLevel level) {
        var recorder = WORLDS.get(level);
        if (recorder == null) return;
        for (var frame : recorder.frames.values()) {
            if (frame.status != null) continue;
            if (frame.count != recorder.context.substeps()) frame.unavailable(MeasurementStatus.NOT_READY, "Incomplete electrical timestep");
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
        if (frame.result == null) {
            try { frame.result = summarize(frame.values); }
            catch (RuntimeException error) {
                frame.unavailable(MeasurementStatus.SOLVE_FAILED, "Invalid voltage samples");
                return VoltageMeasurement.unavailable(context, frame.status, frame.detail);
            }
        }
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

    private static final class LineFrame {
        final TransmissionLine line;
        final List<ElectricalNetwork> required;
        final List<Frame> frames = new ArrayList<>();
        int count;
        LineFrame(TransmissionLine line, List<ElectricalNetwork> required) { this.line = line; this.required = required; }
    }

    private static final class Frame {
        final Key key;
        final OwnedFloatingNode node;
        final Block block;
        final Object anchor;
        final double[] values;
        List<ElectricalNetwork> required = List.of();
        TransmissionLine line;
        double fraction;
        Object reference;
        boolean extension;
        int count;
        MeasurementStatus status;
        String detail = "";
        NodeResult result;

        Frame(Key key, OwnedFloatingNode node, Block block, Object anchor, int substeps) {
            this.key = key; this.node = node; this.block = block; this.anchor = anchor; values = new double[substeps];
        }
        void reset() { required = List.of(); line = null; reference = null; extension = false; count = 0; status = null; detail = ""; result = null; }
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
