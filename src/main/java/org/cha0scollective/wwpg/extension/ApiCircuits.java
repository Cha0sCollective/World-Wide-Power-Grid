package org.cha0scollective.wwpg.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.cha0scollective.wwpg.api.*;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import java.util.*;

/** Sole topology and lifecycle owner for extension models. All queries are read-only. */
public final class ApiCircuits {
    private static final Map<ServerLevel, ApiCircuits> WORLDS = new IdentityHashMap<>();
    private final ServerLevel level;
    private final Set<BlockPos> candidates = new HashSet<>();
    private final Map<BlockPos, Device> devices = new HashMap<>();
    private final Map<BlockPos, DeviceResults> failures = new HashMap<>();
    private final Map<BlockPos,ElectricalDevice> failedProviders = new HashMap<>();
    private final Map<BlockPos,Object> failedAnchors = new HashMap<>();
    private final Map<BlockPos,Object> commitFaults = new HashMap<>();
    private final LinkedHashMap<String, String> errors = new LinkedHashMap<>();
    private TickContext context;
    private long preparedTick = Long.MIN_VALUE, commits, created, removed;
    private int preparedServerTick = Integer.MIN_VALUE;
    private boolean solving;
    private final Map<ElectricalNetwork,Integer> steps = new IdentityHashMap<>();
    private final Set<ElectricalNetwork> invalidNetworks = Collections.newSetFromMap(new IdentityHashMap<>());

    private ApiCircuits(ServerLevel level) { this.level = level; }
    public static ApiCircuits get(ServerLevel level) { return WORLDS.computeIfAbsent(level, ApiCircuits::new); }
    public static void nominate(ServerLevel level, BlockPos pos) {
        var circuits=get(level);circuits.candidates.add(pos.immutable());circuits.commitFaults.remove(pos);
    }
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level)
            ApiDeviceDiscovery.chunkLoaded(level,event.getChunk());
    }
    public static void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) nominate(level,event.getPos());
    }
    public static void unload(ServerLevel level) { WORLDS.remove(level); }
    /** Detach an old provider's models before its terminal sidecar removes native nodes. */
    public static void retireOwner(ServerLevel level, BlockPos pos, String providerId, String slotId) {
        var circuits=WORLDS.get(level);
        if(circuits==null)return;
        if(circuits.solving)throw new IllegalStateException("Extension terminal removal during electrical solve");
        var device=circuits.devices.get(pos);
        if(device!=null && device.identity.providerId().equals(providerId) && device.identity.slotId().equals(slotId)) {
            circuits.devices.remove(pos);
            circuits.retire(device);
        }
    }
    public static void clear() { WORLDS.clear(); }
    public static int step(ServerLevel level,ElectricalNetwork network) { return get(level).steps.getOrDefault(network,0); }
    /** Read-only lifecycle instrumentation; never creates a world manager or simulation objects. */
    public static int objectCount(ServerLevel level,BlockPos pos) {
        var owner=WORLDS.get(level);
        var device=owner==null?null:owner.devices.get(pos);
        return device==null?0:device.nodes.size()+device.elements.size()+device.grounds.size();
    }
    public static DeviceResults results(ServerLevel level, BlockPos pos) {
        var owner = WORLDS.get(level);
        if (owner != null && !ApiTerminals.pendingRemoval(level,pos)
                && owner.ready(pos) && owner.preparedServerTick==level.getServer().getTickCount()) {
            var failure = owner.failures.get(pos);
            if(failure!=null&&failure.context().equals(owner.context))return failure;
            var device = owner.devices.get(pos);
            if(device!=null&&device.results!=null&&device.results.context().equals(owner.context)
                    && (device.results.status()==MeasurementStatus.SOLVE_FAILED || device.results.status()==MeasurementStatus.ISOLATED))
                return device.results;
            var entity=ApiTerminals.loadedBlockEntity(level,pos);
            Object anchor=entity==null?Terminals.loadedState(level,pos).getBlock():entity;
            // GameTest/player interactions may run between solve and gameplay commit.
            // Publish the frame whose owning add-on has actually consumed it, never
            // expose new charge ahead of the block entity's authoritative saved state.
            if (device != null && device.anchor==anchor && device.committedResults != null
                    && device.committedServerTick >= level.getServer().getTickCount()-1) return device.committedResults;
        }
        return DeviceResults.unavailable(new TickContext(level.getGameTime(),.05,Math.max(1,ModdedConfigs.server().electricity.solver.multiTicks.get())),
                MeasurementStatus.NOT_READY,"No current committed result at " + level.dimension().location() + " " + pos.toShortString());
    }

    public void prepare() {
        if (preparedServerTick == level.getServer().getTickCount()) return;
        if (solving) throw new IllegalStateException("API topology mutation during solve");
        preparedTick = level.getGameTime();
        preparedServerTick = level.getServer().getTickCount();
        context = new TickContext(level.getGameTime(), .05, ModdedConfigs.server().electricity.solver.multiTicks.get());
        steps.clear();
        invalidNetworks.clear();
        failures.clear();
        failedProviders.clear();
        failedAnchors.clear();
        ApiTerminals.refresh(level);
        candidates.addAll(ApiTerminals.known(level).keySet());
        var world = GlobalElectricNetworks.getWorldNetworks(level);
        for (var pos : List.copyOf(candidates)) {
            var old = devices.get(pos);
            if (!ready(pos)) { if (old != null) { retire(old); devices.remove(pos); } continue; }
            try {
                var provider = ApiTerminals.device(level,pos);
                if (provider == null) {
                    if (old != null) { retire(old);failedProviders.put(pos,old.provider);failedAnchors.put(pos,old.anchor); }
                    devices.remove(pos);
                    if(ApiTerminals.failures(level).containsKey(pos)) failures.put(pos,DeviceResults.unavailable(context,MeasurementStatus.ISOLATED,ApiTerminals.failures(level).get(pos)));
                    else { candidates.remove(pos);failures.put(pos,DeviceResults.unavailable(context,MeasurementStatus.NOT_READY,"Electrical provider unavailable")); }
                    continue;
                }
                failedProviders.put(pos,provider);
                failedAnchors.put(pos,loadedAnchor(pos));
                // Native content already owns its models. A second registration cannot acquire the same terminals.
                if (Terminals.nativePg(level,pos) != null || Terminals.cee(level,pos) != null)
                    throw new IllegalArgumentException("Provider " + provider.providerId() + " conflicts with an upstream-owned electrical device");
                var identity = new DeviceIdentity(level.dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ(),provider.providerId(),provider.slotId());
                var anchor = level.getBlockEntity(pos);
                Object owner = anchor==null ? level.getBlockState(pos).getBlock() : anchor;
                if(commitFaults.get(pos)==owner)throw new IllegalStateException("Previous extension commit failed; call ElectricalDevices.changed after repair");
                if (old != null && (old.anchor != owner || !old.identity.equals(identity))) { retire(old); devices.remove(pos); old = null; }
                var adapter=ApiTerminals.prepare(level,pos,provider);
                var description = Objects.requireNonNull(provider.prepare(context),"Provider returned a null circuit");
                if (!new HashSet<>(description.terminals()).equals(new HashSet<>(provider.terminals())))
                    throw new IllegalArgumentException("Prepared terminals differ from read-only terminal geometry");
                var device = old == null ? new Device(identity,provider,owner) : old;
                device.provider=provider;
                device.description = description;
                devices.put(pos,device);
                var wantedNodes = new LinkedHashMap<String,OwnedFloatingNode>();
                for (var terminal : description.terminals()) {
                    var node = adapter.behaviour().getTerminal(terminal.nativeId());
                    if (node == null) throw new IllegalArgumentException("Unresolved terminal " + terminal.key() + " id=" + terminal.nativeId());
                    wantedNodes.put(terminal.key(),node);
                }
                for (var node : description.internalNodes()) {
                    var previous=device.nodes.get(node.key());
                    wantedNodes.put(node.key(), previous!=null&&previous.endpoint instanceof ApiInternalEndpoint ? previous : internal(identity,node.key()));
                }
                var wantedElements = new HashMap<String,Element>();
                description.elements().forEach(e -> wantedElements.put(e.id(),e));
                for (var id : List.copyOf(device.elements.keySet())) {
                    var current = device.elements.get(id);
                    var wanted = wantedElements.get(id);
                    if (wanted == null || !current.matches(wanted,wantedNodes)) { remove(current); device.elements.remove(id); }
                }
                for (var entry : device.grounds.entrySet().stream().toList()) {
                    if (!description.grounds().contains(entry.getKey()) || entry.getValue().node() != wantedNodes.get(entry.getKey())) {
                        var network = entry.getValue().network(); entry.getValue().remove(); if (network!=null) { network.setDirty(); world.scheduleIslandDiscovery(network); }
                        device.grounds.remove(entry.getKey());
                    }
                }
                for (var entry : device.nodes.entrySet()) {
                    if (entry.getValue() != wantedNodes.get(entry.getKey()) && entry.getValue().endpoint instanceof ApiInternalEndpoint) {
                        var network = entry.getValue().getNetwork(); entry.getValue().remove(); world.scheduleIslandDiscovery(network);
                    }
                }
                device.nodes.clear(); device.nodes.putAll(wantedNodes);
                for (var element : description.elements()) {
                    var current = device.elements.get(element.id());
                    if (current == null) {
                        var endpoints = elementNodes(element,wantedNodes);
                        var first = endpoints.getFirst();
                        if (first.getNetwork()==null) first.endpoint.joinNetwork(level,world.newNetwork());
                        for (var other : endpoints) world.prepareForConnection(first,other);
                        current = new ApiElement(element,wantedNodes,first.getNetwork()); device.elements.put(element.id(),current); ++created;
                    }
                    current.update(element); current.before(context,0);
                }
                for (var ground : description.grounds()) if (!device.grounds.containsKey(ground)) {
                    var node = wantedNodes.get(ground);
                    if (node.getNetwork()==null) node.endpoint.joinNetwork(level,world.newNetwork());
                    device.grounds.put(ground,new ApiGround(node));
                }
                device.samples.clear(); wantedNodes.forEach((key,node)->device.samples.put(key,new NodeSamples(node)));
                device.results = DeviceResults.unavailable(context,MeasurementStatus.NOT_READY,"Awaiting this tick's solve");
                devices.put(pos,device);
                failedProviders.remove(pos);
                failedAnchors.remove(pos);
            } catch (RuntimeException | LinkageError error) {
                var broken = devices.remove(pos); if (broken != null) retire(broken);
                failures.put(pos,DeviceResults.unavailable(context,MeasurementStatus.ISOLATED,"Invalid extension description: "+error.getClass().getSimpleName()));
                diagnostic(pos,error);
            }
        }
    }

    private boolean ready(BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null
                && level.getChunkSource().isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(pos))
                && level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(pos));
    }
    private Object loadedAnchor(BlockPos pos) {
        var entity=ApiTerminals.loadedBlockEntity(level,pos);
        return entity==null?Terminals.loadedState(level,pos).getBlock():entity;
    }
    private static OwnedFloatingNode internal(DeviceIdentity identity,String key) {
        return new ApiInternalEndpoint(identity,key).node;
    }
    private static List<OwnedFloatingNode> elementNodes(Element e,Map<String,OwnedFloatingNode> nodes) {
        var keys = new ArrayList<>(List.of(e.positiveNode(),e.negativeNode()));
        switch (e.model()) {
            case ElectricalModel.Transformer t -> { keys.add(t.secondaryPositiveNode()); keys.add(t.secondaryNegativeNode()); }
            case ElectricalModel.ControlledVoltageSource c -> { keys.add(c.controlPositiveNode()); keys.add(c.controlNegativeNode()); }
            case ElectricalModel.ControlledCurrentSource c -> { keys.add(c.controlPositiveNode()); keys.add(c.controlNegativeNode()); }
            default -> { }
        }
        return keys.stream().distinct().map(nodes::get).toList();
    }
    public void before(ElectricalNetwork network,int step) {
        solving=true;
        for(var device:devices.values()) for(var element:device.elements.values()) if(element.network()==network) element.before(context,step);
    }
    public void after(ElectricalNetwork network) {
        for(var device:devices.values()) {
            for(var element:device.elements.values()) if(element.network()==network) element.after();
            for(var sample:device.samples.values()) if(sample.node.getNetwork()==network) sample.add(network.isConverged());
        }
        solving=false;
        steps.merge(network,1,Integer::sum);
    }
    public void finish() {
        solving=false;
        for(var device:devices.values()) {
            try {
            boolean valid=device.elements.values().stream().allMatch(e->e.valid(context) && !invalidNetworks.contains(e.network()))
                    && device.samples.values().stream().allMatch(n->n.valid(context) && !invalidNetworks.contains(n.node.getNetwork()));
            if(!valid) { device.elements.values().forEach(ApiElement::rejectTick);device.results=DeviceResults.unavailable(context,MeasurementStatus.SOLVE_FAILED,"Incomplete or nonconverged electrical substeps"); continue; }
            var nodes=new HashMap<String,NodeResult>(); device.samples.forEach((key,s)->nodes.put(key,s.result()));
            var elements=new HashMap<String,ElementResult>(); var states=new HashMap<String,ElementState>();
            device.elements.forEach((id,e)->{ elements.put(id,e.result(context)); var state=e.state(); if(state!=null)states.put(id,state); });
            device.results=new DeviceResults(context,MeasurementStatus.VALID,nodes,elements,states,"");
            } catch(RuntimeException error) {
                device.elements.values().forEach(ApiElement::rejectTick);
                device.results=DeviceResults.unavailable(context,MeasurementStatus.SOLVE_FAILED,"Invalid numerical result");
                diagnostic(new BlockPos(device.identity.x(),device.identity.y(),device.identity.z()),error);
            }
        }
    }
    /** Faulting upstream adapters invalidate the same shared network's extension results. */
    public void invalidate(ElectricalNetwork network) {
        if(network!=null)invalidNetworks.add(network);
        NativeApiReadings.invalidate(level,network);
    }
    public void solveFailure(ElectricalNetwork network,Throwable error) {
        invalidate(network);
        for(var entry:devices.entrySet())if(entry.getValue().nodes.values().stream().anyMatch(node->node.getNetwork()==network))
            diagnostic(entry.getKey(),error);
    }
    public void commit() {
        if(context==null)return;
        for(var entry:List.copyOf(failedProviders.entrySet())) {
            if(!ready(entry.getKey()) || failedAnchors.get(entry.getKey())!=loadedAnchor(entry.getKey()))continue;
            if(commitFaults.get(entry.getKey())==failedAnchors.get(entry.getKey()))continue;
            try { entry.getValue().commit(failures.get(entry.getKey()));++commits; }
            catch(RuntimeException | LinkageError error){
                commitFaults.put(entry.getKey(),failedAnchors.get(entry.getKey()));
                failures.put(entry.getKey(),DeviceResults.unavailable(context,MeasurementStatus.ISOLATED,"Extension gameplay commit failed"));
                diagnostic(entry.getKey(),error);
            }
        }
        failedProviders.clear();
        failedAnchors.clear();
        for(var entry:devices.entrySet()) {
            var device=entry.getValue(); if(device.committed==context.gameTime() || !ready(entry.getKey()) || device.anchor!=loadedAnchor(entry.getKey()))continue;
            device.committed=context.gameTime();
            try {
                device.provider.commit(device.results); ++commits;
                device.committedResults=device.results;
                device.committedServerTick=preparedServerTick;
            } catch(RuntimeException | LinkageError error) {
                commitFaults.put(entry.getKey(),device.anchor);
                device.results=DeviceResults.unavailable(context,MeasurementStatus.ISOLATED,"Extension gameplay commit failed");
                device.committedResults=device.results;
                device.committedServerTick=preparedServerTick;
                diagnostic(entry.getKey(),error);
            }
        }
    }
    private void remove(ApiElement element) {
        var network=element.network(); element.remove(); ++removed;
        if(network!=null)GlobalElectricNetworks.getWorldNetworks(level).scheduleIslandDiscovery(network);
    }
    private void retire(Device device) {
        device.elements.values().forEach(this::remove); device.elements.clear();
        for(var ground:device.grounds.values()) { var network=ground.network(); ground.remove(); if(network!=null){network.setDirty();GlobalElectricNetworks.getWorldNetworks(level).scheduleIslandDiscovery(network);} }
        device.grounds.clear();
        for(var node:device.nodes.values()) if(node.endpoint instanceof ApiInternalEndpoint)node.remove();
        device.results=DeviceResults.unavailable(context,MeasurementStatus.NOT_READY,"Device detached");
    }
    private void diagnostic(BlockPos pos,Throwable error) {
        String text="API " + level.dimension().location()+" "+pos.toShortString()+": "+error.getClass().getSimpleName()+": "+error.getMessage();
        if(text.length()>2048)text=text.substring(0,2048);
        if(errors.putIfAbsent(text,text)==null)WorldWidePowerGrid.LOGGER.warn(text);
        while(errors.size()>128)errors.remove(errors.keySet().iterator().next());
    }
    public List<String> diagnostics() { return List.copyOf(errors.values()); }
    public String status() { return "API devices="+devices.size()+", created="+created+", removed="+removed+", commits="+commits; }
    public Object elementIdentity(BlockPos pos,String id) { var d=devices.get(pos); return d==null?null:d.elements.get(id); }

    private static final class Device {
        final DeviceIdentity identity; ElectricalDevice provider; final Object anchor;
        final Map<String,OwnedFloatingNode> nodes=new LinkedHashMap<>(); final Map<String,ApiElement> elements=new HashMap<>();
        final Map<String,ApiGround> grounds=new HashMap<>(); final Map<String,NodeSamples> samples=new HashMap<>();
        CircuitDescription description; DeviceResults results,committedResults; long committed=Long.MIN_VALUE;
        int committedServerTick=Integer.MIN_VALUE;
        Device(DeviceIdentity identity,ElectricalDevice provider,Object anchor) { this.identity=identity; this.provider=provider;this.anchor=anchor; }
    }
    private static final class NodeSamples {
        final OwnedFloatingNode node; double last,sum,squares; int count; boolean failed;
        NodeSamples(OwnedFloatingNode node) { this.node=node; }
        void add(boolean converged) { double v=node.getVoltage(); if(!converged||!Double.isFinite(v)){failed=true;return;} last=v;sum+=v;squares+=v*v;++count; }
        boolean valid(TickContext c) { return !failed&&count==c.substeps(); }
        NodeResult result() { return new NodeResult(last,sum/count,Math.sqrt(squares/count)); }
    }
}
