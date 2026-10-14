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
    private final DiagnosticHistory<Object> diagnostics;
    private final Deque<String> topologyHistory = new ArrayDeque<>();
    private long topologyChanges, lastTopologyTick = -1;
    private long topologyIdentityRevision;
    private StableDescription stableDescription;
    private final Map<ElectricalNetwork, Integer> steps = new IdentityHashMap<>();
    private final Map<ElectricalNetwork, List<ResultNode>> resultNodes = new IdentityHashMap<>();
    private final Map<ElectricalNetwork, List<Branch>> resultBranches = new IdentityHashMap<>();
    private final Map<ElectricalNetwork, List<Winding>> resultWindings = new IdentityHashMap<>();
    private List<SimulatedDevice> devices = List.of();
    private List<Dynamic> dynamics = List.of();
    private List<Adapted> adapters = List.of();
    private List<Branch> substepBranches = List.of();
    private Map<Node, EndpointKey> preparedKeys = Map.of();
    private final Map<EndpointKey, BlockPos> blockOwners = new HashMap<>();
    private final Set<ElectricalProperties> supportedInversions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ElectricalProperties> acSourceInversions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Node,String> adapterPreparationFailures = new IdentityHashMap<>();
    private final Set<ElectricalNetwork> failedAdapterNetworks = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<BlockPos> failedAdapterDevices = new HashSet<>();
    private CircuitBuilder builder;
    private InfrastructureSavedData infrastructure;
    private double[] voltages = new double[0];
    private boolean[] sampledOrdinals = new boolean[0];
    private final Set<BlockPos> pausedDevices = new HashSet<>(), statefulDevices = new HashSet<>();
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

    public WorldBridge(ServerLevel level) {
        this.level = level;
        diagnostics = new DiagnosticHistory<>((key, step) -> key instanceof SolveFailure failure
                ? failure.reason + "; " + failureContext(failure.network, step) : (String) key,
                message -> WorldWidePowerGrid.LOGGER.warn("WWPG {}: {}", level.dimension().location(), message));
    }

    public void prepare(SimulationTicker ticker) {
        diagnostics.advance(level.getGameTime());
        results = null;
        ticker.future = null;
        // CEE's server-head hook runs before Minecraft refreshes runsNormally()
        // and consumes a requested step. Predict this frame from pending state.
        if (level.tickRateManager().isFrozen() && !level.tickRateManager().isSteppingForward()) {
            phase = Phase.IDLE; return;
        }
        require(phase != Phase.SOLVE, "Topology cannot change during a solve");
        phase = Phase.PREPARE;
        RestorationCoordinator.begin(level);
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
        int sampleCount = builder.allNodes().size() * substeps;
        if (voltages.length != sampleCount) voltages = new double[sampleCount];
        else Arrays.fill(voltages, 0);
        adapterPreparationFailures.clear();failedAdapterNetworks.clear();failedAdapterDevices.clear();
        var session = org.cha0scollective.wwpg.extension.CeeAdapters.newSession();
        adapters = new ArrayList<>();
        var bound = Collections.newSetFromMap(new IdentityHashMap<org.cha0scollective.wwpg.extension.CeeAdapters.Binding,Boolean>());
        // Microtickers identify the original dynamic orientation, before the reverse adjacency facade.
        if (session.hasAdapters()) {
        for (var entry : builder.microTickers.long2ObjectEntrySet()) {
            int first=DataPacker.unpackFirstI(entry.getLongKey()),second=DataPacker.unpackSecondI(entry.getLongKey());
            var captured = captureAdapter(session,entry.getValue(),first,second);
            if(captured.isPresent()) {
                var c=captured.get();
                builder.getNode(first).adjacency.put(second,c.binding().properties());
                builder.getNode(second).adjacency.put(first,c.binding().invertedProperties());
                if(bound.add(c.binding()))adapters.add(new Adapted(c.binding(),first,second));
            }
        }
        for(var node:builder.allNodes())for(var other:node.adjacency.keySet().toIntArray()) {
            var captured=captureAdapter(session,node.adjacency.get(other),node.ordinal,other);
            if(captured.isPresent()) {
                var c=captured.get();node.adjacency.put(other,c.properties());builder.getNode(other).adjacency.put(node.ordinal,c.properties().invert());
                if(bound.add(c.binding()))adapters.add(new Adapted(c.binding(),c.inverted()?other:node.ordinal,c.inverted()?node.ordinal:other));
            }
        }
        }
        for(var adapter:adapters)adapter.before(0);
        dynamics = new ArrayList<>();
        for (var entry : builder.microTickers.long2ObjectEntrySet()) {
            if (DYNAMIC_TYPES.contains(entry.getValue().getClass().getName())) {
                var d = new Dynamic(entry.getValue(), DataPacker.unpackFirstI(entry.getLongKey()), DataPacker.unpackSecondI(entry.getLongKey()),
                        NativeCeeCheckpoint.capture(entry.getValue()));
                dynamics.add(d);
            } else if(!org.cha0scollective.wwpg.extension.CeeAdapters.registered(entry.getValue().getClass()))diagnostic("Unsupported property " + entry.getValue().getClass().getName());
        }
        // Capture every winding before any prime mutates their shared rotor.
        // Prime once before PG prepares its matrices, never again in substep zero.
        supportedInversions.clear(); acSourceInversions.clear();
        for (var d : dynamics) { d.before(voltages, 0, substeps); var inverse=d.properties.invert(); supportedInversions.add(inverse); if(d.properties.getClass().getName().endsWith("$ACSource"))acSourceInversions.add(inverse); }
        synchronize(GlobalElectricNetworks.getWorldNetworks(level));
        for (var dynamic : dynamics) {
            dynamic.firstNode = nodes.get(preparedKeys.get(builder.getNode(dynamic.first).node));
            dynamic.secondNode = nodes.get(preparedKeys.get(builder.getNode(dynamic.second).node));
        }
        if (RestorationCoordinator.active(level)) nodes.forEach((key, node) -> { if (node.endpoint instanceof InternalEndpoint) RestorationCoordinator.owner(level, node, ownerOf(key)); });
        steps.clear();
        activeStep = 0;
        completedInStep = 0;
        ++preparedTicks;
        phase = Phase.READY;
    }

    private Optional<org.cha0scollective.wwpg.extension.CeeAdapters.Captured> captureAdapter(
            org.cha0scollective.wwpg.extension.CeeAdapters.Session session,ElectricalProperties source,int first,int second) {
        var a=builder.getNode(first).node;var b=builder.getNode(second).node;
        if(adapterPreparationFailures.containsKey(a)||adapterPreparationFailures.containsKey(b))return Optional.empty();
        try { return session.capture(source); }
        catch(RuntimeException | LinkageError error) {
            String reason="CEE adapter capture failed for "+source.getClass().getName()+": "+error.getClass().getSimpleName()+": "+error.getMessage();
            adapterPreparationFailures.put(a,reason);adapterPreparationFailures.put(b,reason);diagnostic(reason);
            return Optional.empty();
        }
    }

    private boolean supported(ElectricalProperties p) {
        if(org.cha0scollective.wwpg.extension.CeeAdapters.isAdapted(p))return true;
        if (p.getClass() == ElectricalProperties.class) return true;
        if (p.getClass() == TransformerElectricalProperties.class) return true;
        if (DYNAMIC_TYPES.contains(p.getClass().getName())) return true;
        if (p.getClass().getName().endsWith("MicroTickingInvertedElectricalProperties"))
            return supportedInversions.contains(p);
        return false;
    }

    private void synchronize(WorldNetworks world) {
        if (stableDescription != null && stableDescription.update(world)) return;
        var previousTopology = topologyState();
        var wantedNodes = new HashMap<EndpointKey, Node>();
        var wanted = new HashMap<BranchKey, Desired>();
        var wantedTransformers = new HashMap<WindingKey, TransformerElectricalProperties>();
        isolated = new HashSet<>();
        isolationReasons.clear();
        var nodeKeys = new IdentityHashMap<Node, EndpointKey>();
        var previousKeys = preparedKeys;
        preparedKeys = nodeKeys;
        for (var n : builder.allNodes()) {
            try {
                var key = previousKeys.get(n.node);
                if (key == null) key = EndpointKey.of(n.node);
                wantedNodes.put(key, n.node); nodeKeys.put(n.node, key);
            }
            catch (IllegalArgumentException e) { diagnostic(e.getMessage()); }
        }
        ((RejectedConnections)builder).wwpg$rejectedConnections().forEach((node,error)->{
            var key=nodeKeys.get(node);
            if(key!=null){isolate(key,error);diagnostic(error+" at "+key);}
        });
        adapterPreparationFailures.forEach((node,error)->{
            var key=nodeKeys.get(node);
            if(key!=null){isolate(key,error);var owner=ownerOf(key);if(owner!=null)failedAdapterDevices.add(owner);}
        });
        var reverseAcSources = acSourceInversions;
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
                             reverseAcSources.contains(p)));
                    wanted.put(new BranchKey(first, second), new Desired(p, voltageSource, n.node, other, n.ordinal, entry.getIntKey()));
                } catch (IllegalArgumentException e) {
                    isolate(first,e.getMessage()); isolate(second,e.getMessage()); diagnostic(e.getMessage() + " at " + first);
                }
            }
        }
        wanted.entrySet().removeIf(e -> isolated.contains(e.getKey().first()) || isolated.contains(e.getKey().second()));
        wantedTransformers.entrySet().removeIf(e -> e.getKey().keys().stream().anyMatch(isolated::contains));
        dynamics.removeIf(d -> !nodeKeys.containsKey(builder.getNode(d.first).node) || !nodeKeys.containsKey(builder.getNode(d.second).node)
                || isolated.contains(nodeKeys.get(builder.getNode(d.first).node)) || isolated.contains(nodeKeys.get(builder.getNode(d.second).node)));
        adapters.removeIf(d -> {
            boolean removed=isolated.contains(nodeKeys.get(builder.getNode(d.first).node))
                    || isolated.contains(nodeKeys.get(builder.getNode(d.second).node));
            if(removed)d.binding.finish(false);
            return removed;
        });
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
            if (old != resolved) ++topologyIdentityRevision;
            if (old != null && old != resolved) { changed.add(entry.getKey()); retired.add(old); }
        }
        var stale = branches.entrySet().iterator();
        while (stale.hasNext()) {
            var entry = stale.next();
            var desired = wanted.get(entry.getKey());
            if (desired == null || desired.voltageSource != entry.getValue().element.hasVoltageSource()
                    || !entry.getValue().element.attached()
                    || changed.contains(entry.getKey().first()) || changed.contains(entry.getKey().second())) {
                var network = entry.getValue().element.network();
                entry.getValue().element.remove(world.globalGraph);
                world.scheduleIslandDiscovery(network);
                stale.remove(); ++removed;
            }
        }
        transformers.entrySet().removeIf(e -> {
            if (wantedTransformers.containsKey(e.getKey()) && e.getValue().element.network() != null && e.getKey().keys().stream().noneMatch(changed::contains)) return false;
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
            ++topologyIdentityRevision;
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
        var changing = Collections.newSetFromMap(new IdentityHashMap<Branch, Boolean>());
        for (var entry : builder.microTickers.long2ObjectEntrySet()) {
            var first = builder.getNode(DataPacker.unpackFirstI(entry.getLongKey())).node;
            var second = builder.getNode(DataPacker.unpackSecondI(entry.getLongKey())).node;
            if (!nodeKeys.containsKey(first) || !nodeKeys.containsKey(second)) continue;
            var branch = branches.get(BranchKey.of(nodeKeys.get(first), nodeKeys.get(second)));
            if (branch != null) changing.add(branch);
        }
        for (var adapter : adapters) {
            adapter.branch = directedBranch(new DirectionalNodeConnection(builder.getNode(adapter.first).node, builder.getNode(adapter.second).node));
            if (adapter.branch.branch != null) changing.add(adapter.branch.branch);
        }
        substepBranches = List.copyOf(changing);
        blockOwners.keySet().retainAll(nodes.keySet());
        stableDescription = isolated.isEmpty() && adapters.isEmpty() && adapterPreparationFailures.isEmpty()
                && nodeKeys.size() == builder.allNodes().size() ? new StableDescription() : null;
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
        sampledOrdinals = new boolean[builder.allNodes().size()];
        for (var n : builder.allNodes()) {
            EndpointKey key;
            try { key = preparedKey(n.node); } catch (IllegalArgumentException e) { continue; }
            var pg = nodes.get(key);
            if (pg != null && pg.getNetwork() != null && !isolated.contains(key)) {
                resultNodes.computeIfAbsent(pg.getNetwork(), k -> new ArrayList<>()).add(new ResultNode(n.ordinal, key, pg));
                sampledOrdinals[n.ordinal] = true;
            }
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
            try { key = preparedKey(n.node); } catch (IllegalArgumentException e) { continue; }
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
    /** Preferred numerical anchors do not establish a shared physical earth. */
    public boolean hasPhysicalReference(ElectricalNetwork network) {
        long preferred=references.values().stream().filter(wire->wire.getNode1().getNetwork()==network).count();
        return ((ElectricalNetworkAccessor)network).wwpg$groundCount()>preferred;
    }

    /** One snapshot for all networks, without rescanning every anchor per island. */
    public Set<ElectricalNetwork> physicalReferenceNetworks(Collection<ElectricalNetwork> networks) {
        var ownCounts = new IdentityHashMap<ElectricalNetwork, Integer>();
        references.values().forEach(wire -> ownCounts.merge(wire.getNode1().getNetwork(), 1, Integer::sum));
        var physical = Collections.newSetFromMap(new IdentityHashMap<ElectricalNetwork, Boolean>());
        for (var network : networks)
            if (((ElectricalNetworkAccessor) network).wwpg$groundCount() > ownCounts.getOrDefault(network, 0))
                physical.add(network);
        return physical;
    }

    /** Read preparation-time isolation by stable native identity without touching the world. */
    public Optional<String> isolatedTerminalReason(BlockPos position, int nativeId) {
        if (isolated.isEmpty()) return Optional.empty();
        var key = EndpointKey.of(new InWorldNode(nativeId, position));
        if (!isolated.contains(key)) return Optional.empty();
        var reason = isolationReasons.getOrDefault(key, "Electrical terminal is isolated");
        return Optional.of(reason.length() <= 1024 ? reason : reason.substring(0, 1024));
    }

    // PG 0.6.2's incremental conductance update rejects null-ended wires.
    // Rebuild only when grounding changes, retaining the existing nodes and stamps.
    private CurrentSourceWire addGround(OwnedFloatingNode node, double conductance) {
        ++topologyIdentityRevision;
        var network = node.getNetwork();
        var ground = new CurrentSourceWire(node, null, conductance);
        network.addWire(ground);
        network.setDirty();
        return ground;
    }

    private void removeGround(CurrentSourceWire ground) {
        ++topologyIdentityRevision;
        var network = ground.getNetwork();
        OwnedWireRemoval.remove(ground,GlobalElectricNetworks.getWorldNetworks(level).globalGraph);
        if (network != null) network.setDirty();
    }

    public void beforeSolve(ElectricalNetwork network) {
        if (phase == Phase.IDLE || builder == null) return;
        int step = steps.getOrDefault(network, 0);
        require(step < substeps, "PG advanced more substeps than declared");
        if (step != activeStep) {
            require(step == activeStep + 1 && completedInStep == expectedNetworks, "PG substeps arrived out of order");
            activeStep = step; completedInStep = 0;
            for (var d : dynamics) if (!failedDynamic(d)) d.before(voltages, step, substeps);
            for(var adapter:adapters)if (adapter.branch == null || adapter.branch.branch == null || !RestorationCoordinator.paused(adapter.branch.branch.element.network())) adapter.before(step);
            for (var branch : substepBranches) if (!RestorationCoordinator.paused(branch.element.network())) branch.element.update(branch.desired.properties);
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
            if (!Double.isFinite(v)) rejectNumericalSample(network, "Non-finite voltage at " + n.key);
        }
        for (var branch : resultBranches.getOrDefault(network, List.of())) {
            double i = sampledOrdinals[branch.desired.firstOrdinal] && sampledOrdinals[branch.desired.secondOrdinal]
                    ? branch.element.current(voltages[branch.desired.firstOrdinal * substeps + activeStep],
                            voltages[branch.desired.secondOrdinal * substeps + activeStep]) : branch.element.current();
            if (!Double.isFinite(i)) { rejectNumericalSample(network, "Non-finite current at " + branch.desired.first); i = 0; }
            branch.currentSum += i; branch.currentSquares += i * i;
            branch.currentThisStep = i;
        }
        for (var winding : resultWindings.getOrDefault(network, List.of())) {
            // Combine simultaneous currents before squaring. Adding separate RMS
            // readings loses their phase relationship in reactive AC circuits.
            double p = winding.element.primaryCurrent() + winding.primaryLeak.current();
            double s = winding.element.secondaryCurrent() + winding.secondaryLeak.current();
            if (!Double.isFinite(p) || !Double.isFinite(s)) {
                rejectNumericalSample(network, "Non-finite transformer current at " + winding.properties.nodes()); p = s = 0;
            }
            winding.primarySum += p; winding.secondarySum += s;
            winding.primarySquares += p * p; winding.secondarySquares += s * s;
        }
        steps.put(network, activeStep + 1);
        if (++completedInStep == expectedNetworks) {
            for(var adapter:adapters)if (adapter.branch == null || adapter.branch.branch == null || !RestorationCoordinator.paused(adapter.branch.branch.element.network())) adapter.after(activeStep);
            for (var d : dynamics) if(!failedDynamic(d))d.after(voltages, activeStep, substeps);
            ++solvedSubsteps;
        }
    }

    private void rejectNumericalSample(ElectricalNetwork network, String reason) {
        // Keep finite placeholders only for masked arrays; no rejected sample is
        // an accepted zero, a native gameplay input, or persisted CEE history.
        org.cha0scollective.wwpg.extension.ApiCircuits.get(level).invalidate(network);
        reject(network, new IllegalStateException(reason));
    }

    public void finishSolving() {
        if (phase == Phase.IDLE || builder == null) return;
        require(expectedNetworks == 0 || activeStep == substeps - 1 && completedInStep == expectedNetworks,
                "PG did not complete the declared substeps");
        for(var adapter:adapters) {
            var branch=directedBranch(new DirectionalNodeConnection(builder.getNode(adapter.first).node,builder.getNode(adapter.second).node)).branch;
            adapter.binding.finish(branch!=null&&!failedAdapterNetworks.contains(branch.element.network())&&!RestorationCoordinator.paused(branch.element.network())
                    && steps.getOrDefault(branch.element.network(),0)==substeps);
            adapter.binding.failureReason().ifPresent(reason->adapterFault(adapter,reason));
        }
        for (var dynamic : dynamics) if (failedDynamic(dynamic)) dynamic.checkpoint.run();
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
        var unavailable = new HashSet<Node>();
        pausedDevices.clear(); statefulDevices.clear();
        boolean paused = RestorationCoordinator.anyPaused();
        if (paused) {
            for (var device : devices) if (device instanceof com.george_vi.electroenergetics.content.converter.ConverterDevice) statefulDevices.add(device.pos);
            for (var dynamic : dynamics) for (int ordinal : new int[]{dynamic.first, dynamic.second}) {
                var owner = ownerOf(preparedKeys.get(builder.getNode(ordinal).node)); if (owner != null) statefulDevices.add(owner);
            }
        }
        // Healthy, fully mapped circuits have no availability work to perform.
        // An unmapped descriptor must still take the checked path and be masked.
        if (paused || !isolated.isEmpty() || !adapterPreparationFailures.isEmpty()
                || !failedAdapterNetworks.isEmpty() || preparedKeys.size() != builder.allNodes().size()) {
            for (var indexed : builder.allNodes()) {
                try {
                    var key = preparedKey(indexed.node);
                    var node = nodes.get(key);
                    if (node != null && RestorationCoordinator.paused(node.getNetwork())) {
                        unavailable.add(indexed.node);
                        var owner = ownerOf(key); if (owner != null) { pausedDevices.add(owner); RestorationCoordinator.markOwner(level, owner); }
                    }
                    if (isolated.contains(key) || adapterPreparationFailures.containsKey(indexed.node)
                            || node != null && failedAdapterNetworks.contains(node.getNetwork())) {
                        unavailable.add(indexed.node);
                        var owner = ownerOf(key);
                        if (owner != null) failedAdapterDevices.add(owner);
                    }
                } catch (IllegalArgumentException unsupported) {
                    unavailable.add(indexed.node);
                }
            }
        }
        results = BridgeSimulationResults.takeOwnershipOfCurrents(voltages, substeps, currents, builder, infrastructure, unavailable);
        phase = Phase.RESULTS;
    }

    private void putWindingCurrent(Object2DoubleOpenHashMap<DirectionalNodeConnection> currents,
                                   DirectionalNodeConnection connection, double sum, double squares) {
        currents.removeDouble(connection.invert());
        currents.put(connection, Math.copySign(Math.sqrt(squares / substeps), sum));
    }

    private DirectedBranch directedBranch(DirectionalNodeConnection connection) {
        var first = preparedKeys.get(connection.node1());
        if (first == null) first = EndpointKey.of(connection.node1());
        var second = preparedKeys.get(connection.node2());
        if (second == null) second = EndpointKey.of(connection.node2());
        var key = BranchKey.of(first, second);
        return new DirectedBranch(branches.get(key), key.first().equals(first) ? 1 : -1);
    }

    public void commit(SimulationTicker ticker) {
        org.cha0scollective.wwpg.extension.ApiCircuits.get(level).commit();
        if (results == null) return;
        phase = Phase.COMMIT;
        SimulationResults pausedResults = pausedDevices.isEmpty() ? null : new SimulationResults(new double[voltages.length], substeps,
                new Object2DoubleOpenHashMap<DirectionalNodeConnection>(), builder, infrastructure);
        for (var device : devices) {
            if (!device.isValid() || failedAdapterDevices.contains(device.pos)) continue;
            if (pausedDevices.contains(device.pos)) {
                if (!statefulDevices.contains(device.pos)) ((TickingElectricalDevice) device).postTick(pausedResults);
            } else ((TickingElectricalDevice) device).postTick(results);
        }
        // Results retain rejected-node availability. The lifetime hook skips their
        // wires and masked voltage sync clears old client numbers, while unrelated
        // healthy circuits still receive normal gameplay and meter publication.
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
        if(error.length()>2048)error=error.substring(0,2048);
        diagnostics.record(error, level.getGameTime(), -1, -1);
    }
    private void isolate(EndpointKey key, String reason) { isolated.add(key); isolationReasons.put(key, reason); }
    private BlockPos ownerOf(EndpointKey key) {
        if (key == null) return null;
        var attachedOwner=deviceOwners.get(key);
        if(attachedOwner!=null)return attachedOwner;
        if(!key.owner().startsWith("block:"))return null;
        return blockOwners.computeIfAbsent(key, ignored -> {
            var coordinates=key.owner().substring(6).split(",");
            return new BlockPos(Integer.parseInt(coordinates[0]),Integer.parseInt(coordinates[1]),Integer.parseInt(coordinates[2]));
        });
    }
    private EndpointKey preparedKey(Node node) {
        var key = preparedKeys.get(node);
        return key == null ? EndpointKey.of(node) : key;
    }
    private boolean failedDynamic(Dynamic dynamic) {
        return failedDynamicNode(dynamic.firstNode) || failedDynamicNode(dynamic.secondNode);
    }
    private boolean failedDynamicNode(OwnedFloatingNode node) {
        return node != null && (failedAdapterNetworks.contains(node.getNetwork()) || RestorationCoordinator.paused(node.getNetwork()));
    }
    private void adapterFault(Adapted adapter,String reason) {
        diagnostic("CEE adapter "+adapter.binding.providerId()+": "+reason);
        for(int ordinal:new int[]{adapter.first,adapter.second}) {
            var source=builder.getNode(ordinal).node;
            if(phase==Phase.PREPARE)adapterPreparationFailures.put(source,reason);
            var key=EndpointKey.of(source);var owner=ownerOf(key);
            if(owner!=null)failedAdapterDevices.add(owner);
            var node=nodes.get(key);
            if(node==null||node.getNetwork()==null)continue;
            var network=node.getNetwork();failedAdapterNetworks.add(network);
            org.cha0scollective.wwpg.extension.ApiCircuits.get(level).invalidate(network);
            for(var affected:resultNodes.getOrDefault(network,List.of())) {
                var affectedOwner=ownerOf(affected.key);if(affectedOwner!=null)failedAdapterDevices.add(affectedOwner);
            }
        }
    }
    /** Keep a rejected shared frame out of CEE gameplay and extension measurements. */
    public void reject(ElectricalNetwork network,Throwable error) {
        unavailable(network);
        String reason = "Shared electrical solve failed: " + error.getClass().getSimpleName() + ": " + error.getMessage();
        if (reason.length() > 1024) reason = reason.substring(0,1024);
        diagnostics.record(new SolveFailure(network, error.getClass(), reason), level.getGameTime(), network.getStamp(), activeStep);
    }
    private String failureContext(ElectricalNetwork network, int step) {
        var access=(ElectricalNetworkAccessor)network;
        var owners=new TreeSet<String>();
        for(var node:network.getNodes())if(node instanceof OwnedFloatingNode owned && owned.endpoint instanceof BlockWireEndpoint endpoint) {
            var pos=endpoint.getPos(); long chunk=net.minecraft.world.level.ChunkPos.asLong(pos);
            boolean loaded=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;
            owners.add(pos.getX()+","+pos.getY()+","+pos.getZ()+"/"+endpoint.getTerminal()+":"
                    +(loaded?net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString():"unloaded")
                    +":ticking="+level.getChunkSource().isPositionTicking(chunk)+":entities="+level.areEntitiesLoaded(chunk));
        }
        for(var entry:resultNodes.getOrDefault(network,List.of()))owners.add(entry.key.toString());
        var solver = access.wwpg$solver();
        return "backend="+(solver == null ? "retired" : solver.type())+", substep="+step+", sources="+access.wwpg$sourceCount()
                +", nodes="+network.size()+", wires="+access.wwpg$wires().size()+", terminals="+owners.stream().limit(16).toList();
    }
    /** Keep normal upstream settling out of history/gameplay without recording a false solver error. */
    public void unavailable(ElectricalNetwork network) {
        failedAdapterNetworks.add(network);
        for(var affected:resultNodes.getOrDefault(network,List.of())) {
            var owner=ownerOf(affected.key);if(owner!=null)failedAdapterDevices.add(owner);
        }
    }
    public List<String> diagnostics() {
        return diagnostics.snapshot();
    }
    void closeDiagnostics() { diagnostics.flush(); }
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
        return new TopologyState(topologyIdentityRevision,created,removed);
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
    public void scheduledNetworks(int count) { if (phase == Phase.READY) expectedNetworks = count; }
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

    private record TopologyState(long identities, long created, long removed) {}
    private record SolveFailure(ElectricalNetwork network, Class<?> kind, String reason) { }

    /** Current description owned by a branch; its ordinals/type stay fixed until topology changes. */
    private static final class Desired {
        ElectricalProperties properties;
        Node first, second;
        final boolean voltageSource;
        final int firstOrdinal, secondOrdinal;
        Desired(ElectricalProperties properties, boolean voltageSource, Node first, Node second, int firstOrdinal, int secondOrdinal) {
            this.voltageSource = voltageSource; this.firstOrdinal = firstOrdinal; this.secondOrdinal = secondOrdinal;
            refresh(properties, first, second);
        }
        void refresh(ElectricalProperties properties, Node first, Node second) {
            this.properties = properties; this.first = first; this.second = second;
        }
    }
    private record Anchor(net.minecraft.world.level.block.Block block, Object owner) { }
    /** Validate structure and ownership, then update parameters without rebuilding desired-key maps. */
    private final class StableDescription {
        final Node[] descriptions = builder.allNodes().stream().map(indexed -> indexed.node).toArray(Node[]::new);
        final EndpointKey[] keys = Arrays.stream(descriptions).map(preparedKeys::get).toArray(EndpointKey[]::new);
        final OwnedFloatingNode[] mapped = Arrays.stream(keys).map(nodes::get).toArray(OwnedFloatingNode[]::new);
        final int[] adjacencySizes = builder.allNodes().stream().mapToInt(indexed -> indexed.adjacency.size()).toArray();
        final double[] grounding = builder.allNodes().stream().mapToDouble(indexed -> indexed.groundConductance).toArray();
        final it.unimi.dsi.fastutil.ints.Int2IntMap priorities = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(((CircuitBuilderAccessor)builder).wwpg$groundPriorities());
        final List<Branch> bindings = List.copyOf(branches.values());
        final Map<Branch, Boolean> primaryWindings = new IdentityHashMap<>();
        final Map<BlockPos, Anchor> anchors = new HashMap<>();
        final Map<WindingKey, Winding> windings = Map.copyOf(transformers);
        StableDescription() {
            for (var branch : bindings) if (branch.desired.properties instanceof TransformerElectricalProperties transformer)
                primaryWindings.put(branch, transformer.isPrimary());
            for (var description : descriptions) if (description instanceof InWorldNode inWorld) {
                anchors.computeIfAbsent(inWorld.sourcePos(), pos -> {
                    var block = org.cha0scollective.wwpg.wiring.Terminals.loadedState(level, pos).getBlock();
                    var entity = org.cha0scollective.wwpg.extension.ApiTerminals.loadedBlockEntity(level, pos);
                    return new Anchor(block, entity == null ? block : entity);
                });
            }
        }
        boolean update(WorldNetworks world) {
            if (!adapters.isEmpty() || !adapterPreparationFailures.isEmpty()
                    || !((RejectedConnections)builder).wwpg$rejectedConnections().isEmpty()
                    || descriptions.length != builder.allNodes().size()
                    || !priorities.equals(((CircuitBuilderAccessor)builder).wwpg$groundPriorities())) return false;
            for (var winding : windings.values()) if (winding.element.network() == null) return false;
            for (var entry : anchors.entrySet()) {
                var pos = entry.getKey(); var state = org.cha0scollective.wwpg.wiring.Terminals.loadedState(level, pos);
                var entity = org.cha0scollective.wwpg.extension.ApiTerminals.loadedBlockEntity(level, pos);
                var anchor = entry.getValue();
                if (state.getBlock() != anchor.block || (entity == null ? state.getBlock() : entity) != anchor.owner) return false;
            }
            for (int ordinal = 0; ordinal < descriptions.length; ordinal++) {
                var current = builder.getNode(ordinal);
                if (!descriptions[ordinal].equals(current.node) || current.adjacency.size() != adjacencySizes[ordinal]
                        || Double.compare(current.groundConductance, grounding[ordinal]) != 0) return false;
                var pg = mapped[ordinal];
                if (pg == null || !(pg.endpoint instanceof InternalEndpoint) && world.globalExternalNodes.get(pg.endpoint) != pg) return false;
            }
            for (var branch : bindings) {
                var old = branch.desired; var properties = builder.getNode(old.firstOrdinal).adjacency.get(old.secondOrdinal);
                if (properties == null || properties.getClass() != old.properties.getClass() || !supported(properties) || !branch.element.attached()) return false;
                boolean voltage = properties.isVoltageSource() || properties.conductance() == 0 &&
                        (properties.getClass().getName().endsWith("$ACSource") || acSourceInversions.contains(properties));
                if (voltage != branch.element.hasVoltageSource()) return false;
                try { LinearBranch.validate(properties); } catch (IllegalArgumentException invalid) { return false; }
                if (properties instanceof TransformerElectricalProperties transformer) {
                    if (transformer.isPrimary() != primaryWindings.get(branch)) return false;
                    if (transformer.isPrimary() && !windings.containsKey(WindingKey.of(transformer))) return false;
                    try { TransformerStamp.ratio(transformer.ratio()); } catch (IllegalArgumentException invalid) { return false; }
                }
            }
            // Validation is complete. Rebind fresh descriptors in the privately owned map.
            // The structural-rebuild path still keeps its old map while resolving new keys.
            preparedKeys.clear();
            for (int ordinal = 0; ordinal < keys.length; ordinal++) preparedKeys.put(builder.getNode(ordinal).node, keys[ordinal]);
            for (var branch : bindings) {
                var old = branch.desired; var properties = builder.getNode(old.firstOrdinal).adjacency.get(old.secondOrdinal);
                branch.element.update(properties); branch.currentSum = branch.currentSquares = branch.currentThisStep = 0;
                old.refresh(properties, builder.getNode(old.firstOrdinal).node, builder.getNode(old.secondOrdinal).node);
                if (properties instanceof TransformerElectricalProperties transformer && transformer.isPrimary()) {
                    var winding = windings.get(WindingKey.of(transformer));
                    winding.element.update(transformer.ratio()); winding.properties = transformer;
                    winding.primarySum = winding.secondarySum = winding.primarySquares = winding.secondarySquares = 0;
                }
            }
            var changing = Collections.newSetFromMap(new IdentityHashMap<Branch, Boolean>());
            for (var entry : builder.microTickers.long2ObjectEntrySet()) {
                int first = DataPacker.unpackFirstI(entry.getLongKey()), second = DataPacker.unpackSecondI(entry.getLongKey());
                var branch = branches.get(BranchKey.of(keys[first], keys[second])); if (branch != null) changing.add(branch);
            }
            substepBranches = List.copyOf(changing);
            return true;
        }
    }
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
        double current() { return branch == null ? 0 : direction * branch.currentThisStep; }
    }
    private static final class Branch {
        final LinearBranch element;
        Desired desired;
        double currentSum, currentSquares, currentThisStep;
        Branch(LinearBranch element) { this.element = element; }
    }
    private static final class Dynamic {
        final MicroTickingElectricalProperties properties;
        final int first, second;
        final Runnable checkpoint;
        OwnedFloatingNode firstNode, secondNode;
        Dynamic(MicroTickingElectricalProperties properties, int first, int second, Runnable checkpoint) {
            this.properties = properties; this.first = first; this.second = second; this.checkpoint = checkpoint;
        }
        void before(double[] v, int i, int count) { properties.tick(v, i, count, first, second); }
        void after(double[] v, int i, int count) { properties.afterTick(v, first, second, i, count); }
    }
    private final class Adapted {
        DirectedBranch branch;
        final org.cha0scollective.wwpg.extension.CeeAdapters.Binding binding;final int first,second;
        Adapted(org.cha0scollective.wwpg.extension.CeeAdapters.Binding binding,int first,int second){this.binding=binding;this.first=first;this.second=second;}
        void before(int step){
            double previous=step==0?0:voltages[first*substeps+step-1]-voltages[second*substeps+step-1];
            binding.before(previous,step,substeps,.05/substeps);
            binding.failureReason().ifPresent(reason->adapterFault(this,reason));
        }
        void after(int step){
            var current=branch;
            double v=voltages[first*substeps+step]-voltages[second*substeps+step];
            var branch=current.branch;
            boolean accepted=branch!=null&&branch.element.network()!=null&&branch.element.network().isConverged();
            binding.after(v,current.current(),step,substeps,.05/substeps,accepted);
            binding.failureReason().ifPresent(reason->adapterFault(this,reason));
        }
    }
}
