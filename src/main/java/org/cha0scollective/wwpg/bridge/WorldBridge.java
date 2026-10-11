package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.CEESimulatedDeviceFeatureTypes;
import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerElectricalProperties;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.events.FinishElectricSimulationEvent;
import com.george_vi.electroenergetics.foundation.device.TickingElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.DirectionalNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.VoltageSync;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.simulator.SimulationStats;
import com.george_vi.electroenergetics.simulation.simulator.SimulationTicker;
import com.george_vi.electroenergetics.simulation.util.DataPacker;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.NeoForge;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.mixin.CircuitBuilderAccessor;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.*;

/** Owns compatibility topology on one server level. All structural edits happen in prepare. */
public final class WorldBridge {
    public enum Phase { IDLE, PREPARE, READY, SOLVE, RESULTS, COMMIT }
    private final ServerLevel level;
    private final Map<EndpointKey, OwnedFloatingNode> nodes = new HashMap<>();
    private final Map<BranchKey, Branch> branches = new HashMap<>();
    private final Map<EndpointKey, CurrentSourceWire> grounds = new HashMap<>();
    private final Map<EndpointKey, CurrentSourceWire> references = new HashMap<>();
    private final Map<WindingKey, Winding> transformers = new HashMap<>();
    private Set<EndpointKey> isolated = Set.of();
    private final Map<EndpointKey, String> isolationReasons = new HashMap<>();
    private final Map<EndpointKey, BlockPos> deviceOwners = new HashMap<>();
    private final Map<CSolver.SolverBackend, Long> backendSubsteps = new EnumMap<>(CSolver.SolverBackend.class);
    private final LinkedHashMap<String, Diagnostic> diagnostics = new LinkedHashMap<>();
    private final Deque<String> topologyHistory = new ArrayDeque<>();
    private long topologyChanges, lastTopologyTick = -1;
    private final Map<ElectricalNetwork, Integer> steps = new IdentityHashMap<>();
    private final Map<ElectricalNetwork, List<ResultNode>> resultNodes = new IdentityHashMap<>();
    private final Map<ElectricalNetwork, List<Branch>> resultBranches = new IdentityHashMap<>();
    private final Map<ElectricalNetwork, List<Winding>> resultWindings = new IdentityHashMap<>();
    private List<SimulatedDevice> devices = List.of();
    private List<Dynamic> dynamics = List.of();
    private CircuitBuilder builder;
    private InfrastructureSavedData infrastructure;
    private double[] voltages = new double[0];
    private SimulationResults results;
    private Phase phase = Phase.IDLE;
    private int substeps, activeStep, completedInStep, expectedNetworks;
    private long created, removed, preparedTicks, committedTicks, solvedSubsteps;

    private static final Set<String> DYNAMIC_TYPES = Set.of(
            "com.george_vi.electroenergetics.foundation.electrical_properties.CapacitorProperties",
            "com.george_vi.electroenergetics.foundation.electrical_properties.InductorProperties",
            "com.george_vi.electroenergetics.foundation.electrical_properties.DiodeProperties",
            "com.george_vi.electroenergetics.foundation.electrical_properties.AccumulatorProperties",
            "com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice$ACSource",
            "com.george_vi.electroenergetics.content.rotor.ThreePhaseAlternatorBrushesDevice$PhaseWindingProperties");

    public WorldBridge(ServerLevel level) { this.level = level; }

    public void prepare(SimulationTicker ticker) {
        results = null;
        ticker.future = null;
        if (level.tickRateManager().isFrozen()) { phase = Phase.IDLE; return; }
        require(phase != Phase.SOLVE, "Topology cannot change during a solve");
        phase = Phase.PREPARE;
        org.cha0scollective.wwpg.wiring.Terminals.refresh(level);
        infrastructure = ticker.sd;
        substeps = ModdedConfigs.server().electricity.solver.multiTicks.get();
        ticker.microTicks = substeps;
        builder = infrastructure.wireSimulationState.createCircuitBuilder();
        // Native PG endpoints/wire entities do not exist until their chunk has
        // loaded and its block entities are ticking. Entity IO can remain ready
        // after a chunk loses its ticking ticket, while PG has already detached
        // its electrical model. Do not commit that temporary disconnected graph.
        devices = DevicesSavedData.load(level).getDevices(CEESimulatedDeviceFeatureTypes.TICKING_ELECTRICAL.get()).stream()
                .filter(device -> level.getChunkSource().getChunkNow(device.pos.getX() >> 4, device.pos.getZ() >> 4) != null
                        && level.getChunkSource().isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(device.pos))
                        && level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(device.pos)))
                .toList();
        var collector = new BridgeCollector(builder, infrastructure, substeps);
        deviceOwners.clear();
        for (var device : devices) {
            int firstNewNode = builder.allNodes().size();
            ((TickingElectricalDevice) device).preTick(collector);
            // Diagnostic ownership is reconstructed during preparation. These
            // positions never replace the endpoint's native persistent identity.
            for (int index = firstNewNode; index < builder.allNodes().size(); ++index) {
                var node = builder.getNode(index).node;
                if (node.getClass() == com.george_vi.electroenergetics.foundation.nodes.AttachedNode.class)
                    deviceOwners.put(EndpointKey.of(node), device.pos.immutable());
            }
        }
        NeoForge.EVENT_BUS.post(new AddToElectricGraphEvent(builder, level, infrastructure));
        builder.connectAll(new ArrayList<>(infrastructure.wireSimulationState.getLazyConnections()));
        voltages = new double[builder.allNodes().size() * substeps];
        dynamics = new ArrayList<>();
        for (var entry : builder.microTickers.long2ObjectEntrySet()) {
            if (DYNAMIC_TYPES.contains(entry.getValue().getClass().getName())) {
                var d = new Dynamic(entry.getValue(), DataPacker.unpackFirstI(entry.getLongKey()), DataPacker.unpackSecondI(entry.getLongKey()));
                dynamics.add(d);
                // Prime the first stamp before PG prepares its matrices. Do not prime again in substep zero.
                d.before(voltages, 0, substeps);
            } else diagnostic("Unsupported property " + entry.getValue().getClass().getName());
        }
        synchronize(GlobalElectricNetworks.getWorldNetworks(level));
        steps.clear();
        activeStep = 0;
        completedInStep = 0;
        ++preparedTicks;
        phase = Phase.READY;
    }

    private boolean supported(ElectricalProperties p) {
        if (p.getClass() == ElectricalProperties.class) return true;
        if (p.getClass() == TransformerElectricalProperties.class) return true;
        if (DYNAMIC_TYPES.contains(p.getClass().getName())) return true;
        if (p.getClass().getName().endsWith("MicroTickingInvertedElectricalProperties"))
            return dynamics.stream().anyMatch(d -> d.properties.invert() == p);
        return false;
    }

    private void synchronize(WorldNetworks world) {
        var previousTopology = topologyState();
        var wantedNodes = new HashMap<EndpointKey, Node>();
        var wanted = new HashMap<BranchKey, Desired>();
        var wantedTransformers = new HashMap<WindingKey, TransformerElectricalProperties>();
        isolated = new HashSet<>();
        isolationReasons.clear();
        var nodeKeys = new HashMap<Node, EndpointKey>();
        for (var n : builder.allNodes()) {
            try {
                var key = EndpointKey.of(n.node);
                wantedNodes.put(key, n.node); nodeKeys.put(n.node, key);
            }
            catch (IllegalArgumentException e) { diagnostic(e.getMessage()); }
        }
        ((RejectedConnections)builder).wwpg$rejectedConnections().forEach((node,error)->{
            var key=nodeKeys.get(node);
            if(key!=null){isolate(key,error);diagnostic(error+" at "+key);}
        });
        for (var n : builder.allNodes()) {
            var first = nodeKeys.get(n.node);
            if (first == null) continue;
            for (var entry : n.adjacency.int2ObjectEntrySet()) {
                var other = builder.getNode(entry.getIntKey()).node;
                var second = nodeKeys.get(other);
                if (second == null) continue;
                if (first.compareTo(second) >= 0) continue;
                var p = entry.getValue();
                if (!supported(p)) {
                    String reason = "Unsupported property " + p.getClass().getName();
                    isolate(first, reason); isolate(second, reason);
                    diagnostic(reason + " at " + first + " -> " + second);
                    continue;
                }
                try {
                    LinearBranch.validate(p);
                    if (p instanceof TransformerElectricalProperties transformer) {
                        TransformerStamp.ratio(transformer.ratio());
                        if (transformer.isPrimary()) wantedTransformers.put(WindingKey.of(transformer), transformer);
                    }
                    // Ideal AC stays an ideal source at its zero crossings.
                    boolean voltageSource = p.isVoltageSource() || (p.conductance() == 0 &&
                            (p.getClass().getName().endsWith("$ACSource") ||
                             dynamics.stream().anyMatch(d -> d.properties.invert() == p && d.properties.getClass().getName().endsWith("$ACSource"))));
                    wanted.put(new BranchKey(first, second), new Desired(p, voltageSource, n.node, other));
                } catch (IllegalArgumentException e) {
                    isolate(first,e.getMessage()); isolate(second,e.getMessage()); diagnostic(e.getMessage() + " at " + first);
                }
            }
        }
        wanted.entrySet().removeIf(e -> isolated.contains(e.getKey().first()) || isolated.contains(e.getKey().second()));
        wantedTransformers.entrySet().removeIf(e -> e.getKey().keys().stream().anyMatch(isolated::contains));
        dynamics.removeIf(d -> !nodeKeys.containsKey(builder.getNode(d.first).node) || !nodeKeys.containsKey(builder.getNode(d.second).node)
                || isolated.contains(nodeKeys.get(builder.getNode(d.first).node)) || isolated.contains(nodeKeys.get(builder.getNode(d.second).node)));
        for (var n : builder.allNodes()) for (int other : n.adjacency.keySet().toIntArray()) {
            var secondNode = builder.getNode(other).node;
            if (!nodeKeys.containsKey(n.node) || !nodeKeys.containsKey(secondNode)
                    || isolated.contains(nodeKeys.get(n.node)) || isolated.contains(nodeKeys.get(secondNode)))
                n.adjacency.put(other, ElectricalProperties.ZERO_CONDUCTANCE);
        }
        // Native PG rebuilds some external nodes after chunk load or device edits.
        // Resolve them again and retire stamps that still point at the old node objects.
        var changed = new HashSet<EndpointKey>();
        var retired = new ArrayList<OwnedFloatingNode>();
        for (var entry : wantedNodes.entrySet()) {
            var resolved = resolve(world, entry.getKey(), entry.getValue());
            var old = nodes.put(entry.getKey(), resolved);
            if (old != null && old != resolved) { changed.add(entry.getKey()); retired.add(old); }
        }
        var stale = branches.entrySet().iterator();
        while (stale.hasNext()) {
            var entry = stale.next();
            var desired = wanted.get(entry.getKey());
            if (desired == null || desired.voltageSource != entry.getValue().element.hasVoltageSource()
                    || changed.contains(entry.getKey().first()) || changed.contains(entry.getKey().second())) {
                var network = entry.getValue().element.network();
                entry.getValue().element.remove();
                world.scheduleIslandDiscovery(network);
                stale.remove(); ++removed;
            }
        }
        transformers.entrySet().removeIf(e -> {
            if (wantedTransformers.containsKey(e.getKey()) && e.getKey().keys().stream().noneMatch(changed::contains)) return false;
            var network = e.getValue().element.network();
            e.getValue().element.remove(); world.scheduleIslandDiscovery(network); ++removed;
            return true;
        });
        for (var map : List.of(grounds, references)) map.entrySet().removeIf(e -> {
            if (wantedNodes.containsKey(e.getKey()) && !changed.contains(e.getKey()) && !isolated.contains(e.getKey())) return false;
            removeGround(e.getValue()); return true;
        });
        for (var node : retired) if (node.endpoint instanceof InternalEndpoint) node.remove();
        for (var entry : wanted.entrySet()) {
            var key = entry.getKey(); var desired = entry.getValue();
            var branch = branches.get(key);
            if (branch == null) {
                var a = nodes.get(key.first()); var b = nodes.get(key.second());
                var network = world.prepareForConnection(a, b);
                branch = new Branch(new LinearBranch(network, a, b, desired.properties, desired.voltageSource));
                branches.put(key, branch); ++created;
            } else branch.element.update(desired.properties);
            branch.desired = desired;
            branch.currentSum = 0; branch.currentSquares = 0;
        }
        for (var entry : wantedTransformers.entrySet()) {
            var key = entry.getKey(); var p = entry.getValue();
            var winding = transformers.get(key);
            if (winding == null) {
                var p1 = nodes.get(key.p1); var p2 = nodes.get(key.p2);
                var s1 = nodes.get(key.s1); var s2 = nodes.get(key.s2);
                world.prepareForConnection(p1, p2);
                world.prepareForConnection(p1, s1);
                var network = world.prepareForConnection(p1, s2);
                winding = new Winding(new TransformerStamp(network, p1, p2, s1, s2, p.ratio()));
                transformers.put(key, winding); ++created;
            } else winding.element.update(p.ratio());
            winding.properties = p;
            winding.primaryLeak = directedBranch(p.nodes());
            winding.secondaryLeak = directedBranch(p.coupledNodes());
            winding.primarySum = winding.secondarySum = winding.primarySquares = winding.secondarySquares = 0;
        }
        // Physical grounds are applied here. Preferred references are chosen from the
        // combined PG topology after island discovery, not from CEE-only islands.
        var desiredGrounds = new HashMap<EndpointKey, Double>();
        for (var n : builder.allNodes()) {
            if (n.groundConductance > 0 && nodeKeys.containsKey(n.node) && !isolated.contains(EndpointKey.of(n.node)))
                desiredGrounds.put(EndpointKey.of(n.node), n.groundConductance);
        }
        grounds.entrySet().removeIf(e -> {
            if (desiredGrounds.containsKey(e.getKey())) return false;
            world.scheduleIslandDiscovery(e.getValue().getNetwork());
            removeGround(e.getValue()); return true;
        });
        for (var entry : desiredGrounds.entrySet()) {
            var ground = grounds.get(entry.getKey());
            if (ground == null) {
                var node = nodes.get(entry.getKey());
                if (node.getNetwork() == null) node.endpoint.joinNetwork(level, world.newNetwork());
                ground = addGround(node, entry.getValue());
                grounds.put(entry.getKey(), ground);
            } else if (Double.compare(ground.conductance(), entry.getValue()) != 0) {
                ground.setConductance(entry.getValue());
                ground.getNetwork().setDirty();
            }
        }
        nodes.entrySet().removeIf(e -> {
            if (wantedNodes.containsKey(e.getKey())) return false;
            var node = e.getValue();
            if (node.endpoint instanceof InternalEndpoint) {
                world.scheduleIslandDiscovery(node.getNetwork());
                node.remove();
                world.globalExternalNodes.remove(node.endpoint, node);
            } else if (node.getNetwork() == null) {
                world.globalExternalNodes.remove(node.endpoint, node);
            }
            return true;
        });
        recordTopology(previousTopology, !changed.isEmpty(), "prepare");
    }

    private OwnedFloatingNode resolve(WorldNetworks world, EndpointKey key, Node node) {
        if (node instanceof InWorldNode inWorld) {
            var endpoint = new BlockWireEndpoint(inWorld.sourcePos(), inWorld.id());
            // PG's getAt reads the block state and can load the chunk. Unloaded CEE
            // descriptions must use the existing placeholder instead of keeping chunks alive.
            if (!level.hasChunkAt(inWorld.sourcePos())) {
                var previous = nodes.get(key);
                if (previous != null && previous.endpoint instanceof InternalEndpoint) return previous;
                return world.globalExternalNodes.computeIfAbsent(endpoint, OwnedFloatingNode::new);
            }
            var electric = IElectric.getAt(level, inWorld.sourcePos());
            if (electric != null) {
                var nativeNode = endpoint.getNode(level);
                if (nativeNode != null) return nativeNode;
                // CEE internal InWorldNode IDs (such as transformer nodes 4/5)
                // share stable identities but have no physical wire terminal.
                return internal(key);
            }
            var previous = nodes.get(key);
            if (previous != null && previous.endpoint instanceof InternalEndpoint) return previous;
            return world.globalExternalNodes.computeIfAbsent(endpoint, e -> new OwnedFloatingNode(e));
        }
        return internal(key);
    }

    private OwnedFloatingNode internal(EndpointKey key) {
        var previous = nodes.get(key);
        return previous != null && previous.endpoint instanceof InternalEndpoint ? previous : new InternalEndpoint(key).getNode(level);
    }

    public void beginSolving(WorldNetworks world) {
        if (phase != Phase.READY) return;
        selectReferences();
        String testBackend = System.getProperty("wwpg.test.backend");
        if (testBackend != null) {
            var backend = CSolver.SolverBackend.valueOf(testBackend);
            require(backend.isSupported(), "Requested test backend is unavailable: " + backend);
            world.subnetworks.stream().filter(n -> !n.isEmpty()).forEach(n -> n.switchBackend(backend));
        }
        expectedNetworks = (int) world.subnetworks.stream().filter(n -> !n.isEmpty()).count();
        resultNodes.clear(); resultBranches.clear(); resultWindings.clear();
        for (var n : builder.allNodes()) {
            EndpointKey key;
            try { key = EndpointKey.of(n.node); } catch (IllegalArgumentException e) { continue; }
            var pg = nodes.get(key);
            if (pg != null && pg.getNetwork() != null && !isolated.contains(key))
                resultNodes.computeIfAbsent(pg.getNetwork(), k -> new ArrayList<>()).add(new ResultNode(n.ordinal, key, pg));
        }
        branches.values().forEach(b -> resultBranches.computeIfAbsent(b.element.network(), k -> new ArrayList<>()).add(b));
        transformers.values().forEach(w -> resultWindings.computeIfAbsent(w.element.network(), k -> new ArrayList<>()).add(w));
    }

    private void selectReferences() {
        var previousTopology = topologyState();
        var ownCounts = new IdentityHashMap<ElectricalNetwork, Integer>();
        references.values().forEach(g -> ownCounts.merge(g.getNode1().getNetwork(), 1, Integer::sum));
        var priorities = ((CircuitBuilderAccessor) builder).wwpg$groundPriorities();
        var best = new IdentityHashMap<ElectricalNetwork, EndpointKey>();
        var ranks = new IdentityHashMap<ElectricalNetwork, Integer>();
        for (var n : builder.allNodes()) {
            if (!priorities.containsKey(n.ordinal)) continue;
            EndpointKey key;
            try { key = EndpointKey.of(n.node); } catch (IllegalArgumentException e) { continue; }
            if (isolated.contains(key)) continue;
            var node = nodes.get(key);
            var network = node == null ? null : node.getNetwork();
            if (network == null || ((ElectricalNetworkAccessor) network).wwpg$groundCount() > ownCounts.getOrDefault(network, 0)) continue;
            int rank = priorities.get(n.ordinal);
            var old = best.get(network);
            if (old == null || rank > ranks.get(network) || rank == ranks.get(network) && key.compareTo(old) < 0) {
                best.put(network, key); ranks.put(network, rank);
            }
        }
        var wanted = new HashSet<>(best.values());
        references.entrySet().removeIf(e -> {
            if (wanted.contains(e.getKey())) return false;
            removeGround(e.getValue()); return true;
        });
        for (var key : wanted) references.computeIfAbsent(key, k -> addGround(nodes.get(k), 1000));
        recordTopology(previousTopology, false, "references");
    }

    // PG 0.6.2's incremental conductance update rejects null-ended wires.
    // Rebuild only when grounding changes, retaining the existing nodes and stamps.
    private static CurrentSourceWire addGround(OwnedFloatingNode node, double conductance) {
        var network = node.getNetwork();
        var ground = new CurrentSourceWire(node, null, conductance);
        network.addWire(ground);
        network.setDirty();
        return ground;
    }

    private static void removeGround(CurrentSourceWire ground) {
        var network = ground.getNetwork();
        ground.remove();
        if (network != null) network.setDirty();
    }

    public void beforeSolve(ElectricalNetwork network) {
        if (phase == Phase.IDLE || builder == null) return;
        int step = steps.getOrDefault(network, 0);
        require(step < substeps, "PG advanced more substeps than declared");
        if (step != activeStep) {
            require(step == activeStep + 1 && completedInStep == expectedNetworks, "PG substeps arrived out of order");
            activeStep = step; completedInStep = 0;
            for (var d : dynamics) d.before(voltages, step, substeps);
            for (var branch : branches.values()) branch.element.update(branch.desired.properties);
        }
        phase = Phase.SOLVE;
    }

    public void afterSolve(ElectricalNetwork network) {
        if (phase == Phase.IDLE || builder == null) return;
        phase = Phase.RESULTS;
        var backend = ((ElectricalNetworkAccessor) network).wwpg$solver().type();
        backendSubsteps.merge(backend, 1L, Long::sum);
        for (var n : resultNodes.getOrDefault(network, List.of())) {
            double v = n.node.getVoltage();
            voltages[n.ordinal * substeps + activeStep] = Double.isFinite(v) ? v : 0;
            if (!Double.isFinite(v)) diagnostic("Non-finite voltage at " + n.key);
        }
        for (var branch : resultBranches.getOrDefault(network, List.of())) {
            double i = branch.element.current();
            if (!Double.isFinite(i)) { diagnostic("Non-finite current at " + branch.desired.first); i = 0; }
            branch.currentSum += i; branch.currentSquares += i * i;
        }
        for (var winding : resultWindings.getOrDefault(network, List.of())) {
            // Combine simultaneous currents before squaring. Adding separate RMS
            // readings loses their phase relationship in reactive AC circuits.
            double p = winding.element.primaryCurrent() + winding.primaryLeak.current();
            double s = winding.element.secondaryCurrent() + winding.secondaryLeak.current();
            if (!Double.isFinite(p) || !Double.isFinite(s)) {
                diagnostic("Non-finite transformer current at " + winding.properties.nodes()); p = s = 0;
            }
            winding.primarySum += p; winding.secondarySum += s;
            winding.primarySquares += p * p; winding.secondarySquares += s * s;
        }
        steps.put(network, activeStep + 1);
        if (++completedInStep == expectedNetworks) {
            for (var d : dynamics) d.after(voltages, activeStep, substeps);
            ++solvedSubsteps;
        }
    }

    public void finishSolving() {
        if (phase == Phase.IDLE || builder == null) return;
        require(expectedNetworks == 0 || activeStep == substeps - 1 && completedInStep == expectedNetworks,
                "PG did not complete the declared substeps");
        var currents = new Object2DoubleOpenHashMap<DirectionalNodeConnection>();
        for (var branch : branches.values()) {
            var p = branch.desired.properties;
            double current = branch.currentSum / substeps;
            if (!p.isCurrentSource() && !p.isVoltageSource())
                current = Math.copySign(Math.sqrt(branch.currentSquares / substeps), current);
            currents.put(new DirectionalNodeConnection(branch.desired.first, branch.desired.second), current);
        }
        for (var winding : transformers.values()) {
            putWindingCurrent(currents, winding.properties.nodes(), winding.primarySum, winding.primarySquares);
            putWindingCurrent(currents, winding.properties.coupledNodes(), winding.secondarySum, winding.secondarySquares);
        }
        results = new SimulationResults(voltages, substeps, currents, builder, infrastructure);
        phase = Phase.RESULTS;
    }

    private void putWindingCurrent(Object2DoubleOpenHashMap<DirectionalNodeConnection> currents,
                                   DirectionalNodeConnection connection, double sum, double squares) {
        currents.removeDouble(connection.invert());
        currents.put(connection, Math.copySign(Math.sqrt(squares / substeps), sum));
    }

    private DirectedBranch directedBranch(DirectionalNodeConnection connection) {
        var first = EndpointKey.of(connection.node1());
        var key = BranchKey.of(first, EndpointKey.of(connection.node2()));
        return new DirectedBranch(branches.get(key), key.first().equals(first) ? 1 : -1);
    }

    public void commit(SimulationTicker ticker) {
        if (results == null) return;
        phase = Phase.COMMIT;
        for (var device : devices) {
            if (device.isValid()) ((TickingElectricalDevice) device).postTick(results);
        }
        infrastructure.wireLifetimeModule.finishSimulation(results);
        NeoForge.EVENT_BUS.post(new FinishElectricSimulationEvent(results, level, infrastructure));
        if (!builder.allNodes().isEmpty()) infrastructure.setDirty();
        VoltageSync.finishSimulation(infrastructure, level, results);
        ticker.lastResults = results;
        var stats = new SimulationStats();
        stats.totalNodes = builder.allNodes().size(); stats.totalDevices = devices.size(); stats.totalMicroTickers = dynamics.size();
        stats.totalSeparatedNodes = new int[0]; stats.totalOptimizedNodes = new int[0];
        ticker.lastStats = stats; ticker.lastProfilerResults = List.of();
        SimulationTicker.allStats.put(level, stats);
        DevicesSavedData.load(level).setDirty();
        results = null; ++committedTicks;
        phase = Phase.IDLE;
    }

    private static void require(boolean condition, String error) {
        if (!condition) throw new IllegalStateException("WWPG: " + error);
    }
    private void diagnostic(String error) {
        var entry = diagnostics.get(error);
        if (entry == null) {
            entry = new Diagnostic(error, level.getGameTime());
            diagnostics.put(error, entry);
            WorldWidePowerGrid.LOGGER.warn("WWPG {}: {}", level.dimension().location(), error);
        }
        entry.lastTick = level.getGameTime();
        ++entry.count;
        if (diagnostics.size() > 128) diagnostics.remove(diagnostics.keySet().iterator().next());
    }
    private void isolate(EndpointKey key, String reason) { isolated.add(key); isolationReasons.put(key, reason); }
    public List<String> diagnostics() {
        return diagnostics.values().stream().sorted(Comparator.comparingLong((Diagnostic d) -> d.lastTick).reversed())
                .map(d -> "firstTick=" + d.firstTick + ", lastTick=" + d.lastTick + ", count=" + d.count + ": " + d.message).toList();
    }
    public List<String> topologyHistory() { return List.copyOf(topologyHistory); }
    public List<String> describe(BlockPos pos) {
        String owner = "block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        boolean loaded = chunk != null;
        boolean ticking = level.getChunkSource().isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(pos));
        boolean entities = level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(pos));
        boolean ready = loaded && ticking && entities;
        String readiness = !loaded ? "waiting-for-block-chunk" : !ticking ? "waiting-for-block-ticking" : !entities ? "waiting-for-wire-entities" : "ready";
        var lines = new ArrayList<String>();
        lines.add("dimension=" + level.dimension().location() + ", device=" + owner
                + ", block=" + (loaded ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(pos).getBlock()) : "unloaded")
                + ", chunk=" + loaded + ", ticking=" + ticking + ", entities=" + entities
                + ", readiness=" + readiness + ", phase=" + phase + ", gameTick=" + level.getGameTime());
        var mapped = new HashSet<Integer>();
        nodes.entrySet().stream().filter(e -> e.getKey().owner().equals(owner) || pos.equals(deviceOwners.get(e.getKey())))
                .sorted(Map.Entry.comparingByKey()).forEach(e -> {
                    var key = e.getKey(); var node = e.getValue(); var network = node.getNetwork();
                    if (key.owner().equals(owner)) mapped.add(key.terminal());
                    lines.add("identity=" + key.owner() + "/terminal:" + key.terminal() + ", voltage=" + (ready ? node.getVoltage() : "unavailable")
                            + ", backend=" + backend(network) + ", isolated=" + isolated.contains(key)
                            + ", isolationReason=" + isolationReasons.getOrDefault(key,"none")
                            + ", internal=" + (node.endpoint instanceof InternalEndpoint));
                });
        // Read native models only after checking the loaded chunk. Do not call
        // CeeElectric/IElectric.getAt: diagnostics must not create an adapter.
        if (loaded) {
            var electric = org.cha0scollective.wwpg.wiring.Terminals.nativePg(level,pos);
            if (electric != null) {
                var state = chunk.getBlockState(pos);
                var behaviour = electric.getBehaviour(level,pos,state);
                for (int local=0; local<electric.terminalCount(); ++local) {
                    var placement = electric.terminal(state,local);
                    if (placement == null) continue;
                    int terminal = electric.terminalIndexAt(state,placement.getOrigin());
                    if (terminal < 0 || !mapped.add(terminal)) continue;
                    var node = behaviour == null ? null : behaviour.getTerminal(terminal);
                    lines.add("identity=" + owner + "/terminal:" + terminal + ", nativePG=true, voltage="
                            + (ready && node != null ? node.getVoltage() : "unavailable")
                            + ", backend=" + backend(node == null ? null : node.getNetwork()));
                }
            }
            var entity = chunk.getBlockEntity(pos,net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
            if (entity instanceof org.patryk3211.powergrid.electricity.transformer.NetherTransformerBlockEntity transformer) {
                var link = (org.cha0scollective.wwpg.mixin.NetherTransformerAccessor) transformer;
                var controller = link.wwpg$controller();
                lines.add("Nether link=" + link.wwpg$linkId() + ", secondary=" + link.wwpg$isSecondary()
                        + ", peerAttached=" + (controller != null && controller.secondary != null)
                        + ", cachedCurrent=" + (controller == null ? "unavailable" : controller.getCurrent()));
            }
            if (entity instanceof com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity panel)
                for (var attachment : panel.getAttachments()) if (attachment != null)
                    lines.add("panel slot=" + attachment.slot + ", type=" + com.george_vi.electroenergetics.CEERegistries.PANEL_ATTACHMENT_TYPE.getKey(attachment.type)
                            + ", terminals=" + Arrays.toString(attachment.nodes) + ", label=" + attachment.label);
            if (entity instanceof org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity board) {
                var baked = board.getBaked();
                board.getComponentsStream().forEach(component -> {
                    String identity = "board component=" + org.patryk3211.powergrid.circuits.components.ComponentRegistry.getId(component.component)
                            + ", uuid=" + component.uuid + ", x=" + component.x + ", y=" + component.y;
                    lines.add(identity + ", damaged=" + component.destroyed);
                    if (baked == null) return;
                    component.footprint().getPads().values().stream().mapToInt(pad -> pad.nodeIndex()).filter(pad -> pad >= 0).distinct().sorted().forEach(pad -> {
                        var node = baked.getNode(new org.patryk3211.powergrid.circuits.schematic.CircuitSchematic.Node(component,pad));
                        lines.add(identity + "/pad:" + pad + ", voltage=" + (ready && node != null ? node.getVoltage() : "unavailable")
                                + ", backend=" + backend(node == null ? null : node.getNetwork()));
                    });
                });
            }
        }
        if (lines.size() == 1) lines.add("No currently mapped electrical terminals; " + readiness);
        return List.copyOf(lines);
    }
    private static String backend(ElectricalNetwork network) {
        var solver = network == null ? null : ((ElectricalNetworkAccessor) network).wwpg$solver();
        return network == null ? "disconnected" : solver == null ? "uninitialized" : solver.type().name();
    }
    private TopologyState topologyState() {
        return new TopologyState(Set.copyOf(nodes.keySet()),Set.copyOf(grounds.keySet()),Set.copyOf(references.keySet()),created,removed);
    }
    private void recordTopology(TopologyState previous, boolean rebound, String reason) {
        if (!rebound && previous.equals(topologyState())) return;
        ++topologyChanges; lastTopologyTick = level.getGameTime();
        topologyHistory.addLast("tick=" + lastTopologyTick + ", phase=" + reason + ", endpoints=" + nodes.size()
                + ", branches=" + branches.size() + ", grounds=" + grounds.size() + ", references=" + references.size()
                + ", created=" + created + ", removed=" + removed + ", rebound=" + rebound);
        if (topologyHistory.size() > 32) topologyHistory.removeFirst();
    }
    public long createdCount() { return created; }
    public Map<CSolver.SolverBackend, Long> backendCounts() { return Map.copyOf(backendSubsteps); }
    public Object branchIdentity(Node first, Node second) {
        var branch = branches.get(BranchKey.of(EndpointKey.of(first), EndpointKey.of(second)));
        return branch == null ? null : branch.element;
    }
    public String status() {
        return "phase=" + phase + ", endpoints=" + nodes.size() + ", branches=" + branches.size()
                + ", created=" + created + ", removed=" + removed + ", prepared=" + preparedTicks
                + ", committed=" + committedTicks + ", PG substeps=" + solvedSubsteps
                + ", backends=" + backendSubsteps + ", CEE solve attempts=" + SolverAudit.ceeAttempts()
                + ", topologyChanges=" + topologyChanges + ", lastTopologyTick=" + lastTopologyTick;
    }

    private record TopologyState(Set<EndpointKey> nodes, Set<EndpointKey> grounds, Set<EndpointKey> references, long created, long removed) {}
    private static final class Diagnostic {
        final String message;
        final long firstTick;
        long lastTick, count;
        Diagnostic(String message,long tick) { this.message=message; firstTick=lastTick=tick; }
    }

    private record Desired(ElectricalProperties properties, boolean voltageSource, Node first, Node second) {}
    private record ResultNode(int ordinal, EndpointKey key, OwnedFloatingNode node) {}
    private record WindingKey(EndpointKey p1, EndpointKey p2, EndpointKey s1, EndpointKey s2) {
        static WindingKey of(TransformerElectricalProperties p) {
            return new WindingKey(EndpointKey.of(p.nodes().node1()), EndpointKey.of(p.nodes().node2()),
                    EndpointKey.of(p.coupledNodes().node1()), EndpointKey.of(p.coupledNodes().node2()));
        }
        List<EndpointKey> keys() { return List.of(p1, p2, s1, s2); }
    }
    private static final class Winding {
        final TransformerStamp element;
        TransformerElectricalProperties properties;
        DirectedBranch primaryLeak, secondaryLeak;
        double primarySum, secondarySum, primarySquares, secondarySquares;
        Winding(TransformerStamp element) { this.element = element; }
    }
    private record DirectedBranch(Branch branch, int direction) {
        double current() { return branch == null ? 0 : direction * branch.element.current(); }
    }
    private static final class Branch {
        final LinearBranch element;
        Desired desired;
        double currentSum, currentSquares;
        Branch(LinearBranch element) { this.element = element; }
    }
    private record Dynamic(MicroTickingElectricalProperties properties, int first, int second) {
        void before(double[] v, int i, int count) { properties.tick(v, i, count, first, second); }
        void after(double[] v, int i, int count) { properties.afterTick(v, first, second, i, count); }
    }
}
