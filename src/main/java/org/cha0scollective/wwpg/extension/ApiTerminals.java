package org.cha0scollective.wwpg.extension;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.cha0scollective.wwpg.api.ElectricalDevice;
import org.cha0scollective.wwpg.api.neoforge.ElectricalCapabilities;
import org.cha0scollective.wwpg.api.neoforge.ElectricalProviders;
import org.cha0scollective.wwpg.wiring.RemovedWires;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Loaded-only capability discovery and native-wire geometry for stationary extensions. */
public final class ApiTerminals {
    private static final Map<Level, Map<BlockPos, ApiElectric>> HOLDERS = new IdentityHashMap<>();
    private static final Map<Level, Map<BlockPos, String>> FAILURES = new IdentityHashMap<>();

    private ApiTerminals() {}

    /** Reads a capability without loading chunks, preparing a device, or creating a circuit. */
    public static ElectricalDevice device(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) return null;
        if (level instanceof ServerLevel server
                && server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return null;
        // A native PG/CEE device keeps its native simulation ownership. Declared
        // conflicting extensions still receive a concrete diagnostic.
        var nativeState = Terminals.loadedState(level, pos);
        if (Terminals.nativePg(level, pos) != null || nativeState.getBlock() instanceof ElectricalDeviceBlock<?>) {
            ElectricalProviders.owner(nativeState.getBlock()).ifPresent(owner -> failure(level, pos,
                    "Electrical provider " + owner + " conflicts with an upstream-owned electrical device"));
            return null;
        }
        try {
            var state = Terminals.loadedState(level, pos);
            var blockEntity = loadedBlockEntity(level, pos);
            // A pending block entity is not created just to inspect a capability.
            if (state.hasBlockEntity() && blockEntity == null) return null;
            var result = level.getCapability(ElectricalCapabilities.DEVICE, pos, state, blockEntity, null);
            if (result != null) {
                ElectricalProviders.validate(state.getBlock(), result);
                if (result.providerId() == null || !result.providerId().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")
                        || result.slotId() == null || result.slotId().isBlank())
                    throw new IllegalArgumentException("Invalid electrical provider or slot identity");
            }
            return result;
        } catch (RuntimeException | LinkageError error) {
            failure(level, pos, "Electrical capability lookup failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return null;
        }
    }

    /** Native wire discovery; allocates geometry only, never advances electrical state. */
    public static ApiElectric get(Level level, BlockPos pos) {
        // Native PG entity deserialization queries endpoint validity on its IO
        // worker. Leave those endpoints unresolved until the native server tick:
        // provider discovery, sidecars, and their SavedData belong to that thread.
        if (level instanceof ServerLevel server && !server.getServer().isSameThread()) return null;
        var device = device(level, pos);
        if (device == null) return null;
        try {
            var entries = HOLDERS.computeIfAbsent(level, ignored -> new HashMap<>());
            var adapter = entries.get(pos);
            if (adapter == null) {
                adapter = new ApiElectric(level, pos, device);
                entries.put(pos.immutable(), adapter);
            } else {
                if (!adapter.sameIdentity(device) || adapter.ownerChanged()) {
                    if (level instanceof ServerLevel) {
                        failure(level, pos, "Electrical owner changed; awaiting topology replacement");
                        return null;
                    }
                    adapter = new ApiElectric(level, pos, device);
                    entries.put(pos.immutable(), adapter);
                }
                adapter.update(device);
            }
            var failures = FAILURES.get(level);
            if (failures != null) failures.remove(pos);
            return adapter;
        } catch (RuntimeException | LinkageError error) {
            failure(level, pos, "Electrical terminal discovery failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return null;
        }
    }

    public static boolean ready(ServerLevel level, BlockPos pos) {
        long chunk = ChunkPos.asLong(pos);
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                && level.getChunkSource().isPositionTicking(chunk) && level.areEntitiesLoaded(chunk);
    }

    /** Player wiring waits for physical readiness and safe retirement of any previous incarnation. */
    public static boolean canWire(ServerLevel level,BlockPos pos) {
        return ready(level,pos)&&!pendingRemoval(level,pos);
    }

    /** Test either end of a player connection without loading a selected endpoint's chunk. */
    public static boolean blockedWireEndpoint(ServerLevel level,BlockPos pos) {
        if(pos==null)return false;
        if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)
            return existing(level,pos)!=null||!ApiTerminalIdentities.get(level).ownersAt(pos).isEmpty()
                    ||pendingRemoval(level,pos);
        var block=Terminals.loadedState(level,pos).getBlock();
        // An upstream replacement owns its native readiness semantics. Saved
        // API retirement does not acquire the new owner's wires.
        if(Terminals.nativePg(level,pos)!=null||block instanceof ElectricalDeviceBlock<?>)return false;
        return ElectricalProviders.registeredBlocks().contains(block)?!canWire(level,pos):pendingRemoval(level,pos);
    }

    /** Read-only lifecycle diagnostic; pending retirement survives world/chunk unload. */
    public static boolean pendingRemoval(ServerLevel level,BlockPos pos) {
        return ApiTerminalIdentities.get(level).pendingRemovals().keySet().stream().anyMatch(owner->owner.position().equals(pos));
    }

    public static java.util.Set<WorldNetworks.PartId> pendingPgWireIdentities(ServerLevel level,BlockPos pos) {
        var ids=new java.util.HashSet<WorldNetworks.PartId>();
        ApiTerminalIdentities.get(level).pendingRemovals().forEach((owner,scope)->{if(owner.position().equals(pos))ids.addAll(scope.pgWires());});
        return java.util.Set.copyOf(ids);
    }

    public static java.util.Set<java.util.UUID> pendingCeeWireIdentities(ServerLevel level,BlockPos pos) {
        var ids=new java.util.HashSet<java.util.UUID>();
        ApiTerminalIdentities.get(level).pendingRemovals().forEach((owner,scope)->{if(owner.position().equals(pos))ids.addAll(scope.ceeWires());});
        return java.util.Set.copyOf(ids);
    }

    public static BlockEntity loadedBlockEntity(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk == null ? null : chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
        }
        return level.hasChunkAt(pos) ? level.getBlockEntity(pos) : null;
    }

    /** Current observed identities; callers must separately verify chunk readiness. */
    public static Map<BlockPos, ElectricalDevice> known(Level level) {
        var entries = HOLDERS.get(level);
        if (entries == null) return Map.of();
        var result = new HashMap<BlockPos, ElectricalDevice>();
        for (var entry : entries.entrySet()) result.put(entry.getKey(), entry.getValue().device());
        return Map.copyOf(result);
    }

    public static Map<BlockPos, String> failures(Level level) {
        var result = FAILURES.get(level);
        return result == null ? Map.of() : Map.copyOf(result);
    }

    /** Existing geometry only; does not query a capability or allocate a sidecar. */
    public static ApiElectric existing(Level level, BlockPos pos) {
        var entries = HOLDERS.get(level);
        return entries == null ? null : entries.get(pos);
    }

    /** Persisted terminal identity lookup used before native CEE geometry can request an owner chunk. */
    public static boolean knownSavedTerminal(ServerLevel level,BlockPos pos,int nativeId) {
        var identities=ApiTerminalIdentities.get(level);
        for(var owner:identities.ownersAt(pos))if(identities.history(owner).containsKey(nativeId))return true;
        // A queued physical removal can outlive active terminal history.
        return identities.pendingRemovals().keySet().stream().anyMatch(owner->owner.position().equals(pos));
    }

    /** Internal acceptance diagnostic: reports the format actually read from disk. */
    public static java.util.OptionalInt savedOwnershipFormat(ServerLevel level) {
        return ApiTerminalIdentities.get(level).loadedFormat();
    }

    /** Read-only saved wire identity diagnostic; never resolves terminals or creates native graph nodes. */
    public static java.util.Set<WorldNetworks.PartId> savedPgWireIdentities(ServerLevel level,BlockPos pos) {
        var identities=ApiTerminalIdentities.get(level);var ids=new java.util.HashSet<WorldNetworks.PartId>();
        for(var owner:identities.ownersAt(pos))ids.addAll(identities.pgWires(owner));
        return java.util.Set.copyOf(ids);
    }

    /** Physical block removal only. Queue exact ownership while it is still distinguishable from a new incarnation. */
    public static void ownerRemoved(ServerLevel level,BlockPos pos,net.minecraft.world.level.block.state.BlockState previous) {
        String block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(previous.getBlock()).toString();
        var identities=ApiTerminalIdentities.get(level);
        var adapter=existing(level,pos);
        if(adapter!=null&&adapter.persistentOwner().block().equals(block))adapter.captureReplacementParts();
        var owners=new java.util.HashSet<>(identities.ownersAt(pos));
        if(adapter!=null)owners.add(adapter.persistentOwner());
        for(var owner:owners) {
            if(!owner.block().equals(block))continue;
            var pg=new java.util.HashSet<>(identities.pgWires(owner));var cee=new java.util.HashSet<>(identities.ceeWires(owner));
            if(adapter!=null&&adapter.persistentOwner().equals(owner)) {
                pg.addAll(adapter.observedParts().keySet());cee.addAll(adapter.observedCeeWires());
            }
            identities.queueRemoval(owner,pg,cee);
        }
    }

    /** Read-only pre-migration snapshot; native replacement wires must not inherit saved API wires. */
    public static void captureNativeReplacement(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)
                || server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return;
        var adapter = existing(level, pos);
        if (adapter != null) {
            if (adapter.ownerChanged()) adapter.captureReplacementParts();
            return;
        }
        // Saved ownership is independent of a geometry cache: a player can
        // replace the block before any API terminal has been discovered.
        var identities = ApiTerminalIdentities.get(server);
        for (var owner : identities.ownersAt(pos)) {
            if (owner.block().equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(Terminals.loadedState(level, pos).getBlock()).toString())) continue;
            identities.queueRemoval(owner,identities.pgWires(owner),identities.ceeWires(owner));
        }
    }

    private static void failure(Level level, BlockPos pos, String reason) {
        String provider=ElectricalProviders.owner(Terminals.loadedState(level,pos).getBlock()).orElse("undeclared");
        String diagnostic=level.dimension().location()+" "+pos.toShortString()+" provider="+provider+": "+reason;
        FAILURES.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos.immutable(), ApiDiagnostics.detail(diagnostic));
    }

    /** Safe-phase refresh. Missing unloaded capabilities retain their saved wire identities. */
    public static void refresh(ServerLevel level) {
        queueReplacedSavedOwners(level);
        retirePending(level);
        var entries = HOLDERS.get(level);
        if (entries == null) return;
        for (var pos : List.copyOf(entries.keySet())) {
            if (!ready(level, pos)) continue;
            try {
                refreshPosition(level, pos);
            } catch (RuntimeException | LinkageError error) {
                failure(level, pos, "Electrical terminal refresh failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            }
        }
    }

    private static ApiElectric refreshPosition(ServerLevel level, BlockPos pos) {
        var entries = HOLDERS.get(level);
        var current = entries == null ? null : entries.get(pos);
        var provider = device(level, pos);
        // A missing capability on the same loaded block can mean a provider is
        // temporarily unavailable. Air, another block, or a replaced loaded
        // block entity is a permanent removal; unload is handled separately.
        if (current != null && (current.ownerChanged() || provider != null && !current.sameIdentity(provider))) {
            remove(level, pos);
            current = null;
        }
        if (provider == null) return null;
        var refreshed = get(level, pos);
        if (refreshed != null) {
            refreshed.resumed();
            refreshed.rememberIdentities();
            refreshed.removeInvalidConnections();
            Terminals.refreshPgNodes(level, pos);
        }
        return refreshed;
    }

    /** Validate and persist terminal identities before the simulator resolves any cached PG node. */
    public static ApiElectric prepare(ServerLevel level, BlockPos pos, ElectricalDevice expected) {
        if (!ready(level, pos)) throw new IllegalStateException("API terminal owner is not ready at " + pos.toShortString());
        var adapter = refreshPosition(level, pos);
        if (adapter == null || !adapter.sameIdentity(expected))
            throw new IllegalArgumentException(failures(level).getOrDefault(pos, "API terminal owner is unavailable at " + pos.toShortString()));
        return adapter;
    }

    /** Marks only geometry ownership; saved wires and stable identity history survive unloading. */
    public static void chunkUnloaded(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        var entries = HOLDERS.get(level);
        if (entries == null) return;
        var chunk = event.getChunk().getPos();
        entries.forEach((pos, adapter) -> {
            if ((pos.getX() >> 4) == chunk.x && (pos.getZ() >> 4) == chunk.z) adapter.suspended();
        });
    }

    /** Permanent replacement/removal only; chunk unload must not call this method. */
    public static void remove(Level level, BlockPos pos) {
        var entries = HOLDERS.get(level);
        var adapter = entries == null ? null : entries.remove(pos);
        if (level instanceof ServerLevel server && adapter != null) {
            adapter.captureReplacementParts();
            // Remove the API stamps and exact-reference couplings before native
            // PG removes their terminal nodes from its shared graph.
            var owner = adapter.persistentOwner();
            ApiCircuits.retireOwner(server, pos, owner.provider(), owner.slot());
            var identities = ApiTerminalIdentities.get(server);
            var oldIdentities = adapter.identityHistory();
            var world = GlobalElectricNetworks.getWorldNetworks((net.minecraft.world.level.LevelAccessor) level);
            if (world != null) {
                // Query the nodes this holder actually owned, never whatever a
                // replacement native block now publishes at the same position.
                var nodes = adapter.observedNodes();
                for (var entry : adapter.observedParts().entrySet()) {
                    var part = entry.getValue();
                    if (world.getPart(entry.getKey()) != part) continue;
                    rememberRemovedPart(server, part.persistentOwnerId);
                    // The part can have been migrated to a replacement native
                    // node already. Remove that old wire alone, preserving any
                    // newly placed wire belonging to the replacement.
                    if (part.owner != null) {
                        var endpoint = part.getEndpoint1() instanceof BlockWireEndpoint block && block.getPos().equals(pos)
                                ? part.getEndpoint1() : part.getEndpoint2();
                        part.owner.endpointRemoved(endpoint);
                    } else part.remove();
                }
                for (var entry : nodes.entrySet()) {
                    var parts = world.findConnectedWires(new CapturedEndpoint(pos, entry.getKey(), entry.getValue()));
                    if (parts != null) for (var part : List.copyOf(parts))
                        rememberRemovedPart(server, part.persistentOwnerId);
                }
                adapter.behaviour().breakConnections();
                for (var entry : nodes.entrySet()) {
                    var endpoint = new BlockWireEndpoint(pos, entry.getKey());
                    var node = entry.getValue();
                    if (world.globalExternalNodes.get(endpoint) == node) world.nodeHolderRemoved(node);
                    else {
                        // PG may already have migrated this endpoint to a new
                        // native owner. Its map entry and new node must survive.
                        var parts = world.findConnectedWires(new CapturedEndpoint(pos, entry.getKey(), node));
                        if (parts != null) for (var part : parts) {
                            if (part.owner != null) part.owner.kill();
                            else part.remove();
                        }
                        world.scheduleIslandDiscovery(node.getNetwork());
                        node.remove();
                    }
                }
            }
            var infrastructure = com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData.load(server);
            retireCeeWires(server, adapter.observedCeeWires());
            for (int nativeId : oldIdentities.keySet()) {
                var endpoint = new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(nativeId, pos);
                var data = infrastructure.getNodeData(endpoint);
                if (data == null) continue;
                // A replacement can already own a new wire at this native node.
                // Only exact old UUIDs were retired above; preserve new wires.
                if (infrastructure.getConnections(data).isEmpty() && !hasElectricalReplacement(server, pos)) infrastructure.removeNode(endpoint);
            }
            identities.forget(adapter.persistentOwner());
        }
        var failures = FAILURES.get(level);
        if (failures != null) failures.remove(pos);
    }

    /** Detect unloaded-history replacements even when no wire tool or native lookup was used. */
    private static void queueReplacedSavedOwners(ServerLevel level) {
        var identities=ApiTerminalIdentities.get(level);
        for(var owner:identities.owners()) {
            var pos=owner.position();
            if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null||existing(level,pos)!=null)continue;
            String block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(Terminals.loadedState(level,pos).getBlock()).toString();
            if(!owner.block().equals(block))identities.queueRemoval(owner,identities.pgWires(owner),identities.ceeWires(owner));
        }
    }

    /** Structural cleanup is deferred until the owning chunk and entities are ready. */
    private static void retirePending(ServerLevel level) {
        var identities=ApiTerminalIdentities.get(level);
        for(var entry:identities.pendingRemovals().entrySet()) {
            var owner=entry.getKey();var pos=owner.position();
            if(!ready(level,pos))continue;
            var adapter=existing(level,pos);
            // A same-block replacement can retain its cache key. Retire its
            // old node objects before resolving a fresh incarnation.
            if(adapter!=null&&adapter.persistentOwner().equals(owner))remove(level,pos);
            ApiCircuits.retireOwner(level,pos,owner.provider(),owner.slot());
            // This is the safe topology phase. Complete saved PG parts here so
            // they cannot reappear after cleanup and before the first solve.
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            for(var id:entry.getValue().pgWires()) {
                rememberRemovedPart(level,id);
                var part=world.getPart(id);
                if(part==null)continue;
                if(part.owner!=null) {
                    var endpoint=part.getEndpoint1() instanceof BlockWireEndpoint block&&block.getPos().equals(pos)
                            ?part.getEndpoint1():part.getEndpoint2();
                    part.owner.endpointRemoved(endpoint);
                } else part.remove();
            }
            retireCeeWires(level,entry.getValue().ceeWires());
            identities.forget(owner);
            identities.finishRemoval(owner);
        }
    }

    private static void rememberRemovedPart(ServerLevel level,WorldNetworks.PartId id) {
        if(id instanceof WorldNetworks.SimpleId simple)RemovedWires.get(level).remember(simple.id());
        else if(id instanceof WorldNetworks.ComplexId complex)RemovedWires.get(level).remember(complex.id());
    }

    private static void retireCeeWires(ServerLevel level,java.util.Set<java.util.UUID> ids) {
        if(ids.isEmpty())return;
        var ledger=ApiCeeWireIdentities.get(level);
        var infrastructure=com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData.load(level);
        for(var id:ids)ledger.currentConnection(id).ifPresent(connection->{
            if(infrastructure.getConnectionData(connection)!=null)infrastructure.removeAndDropConnection(connection);
        });
    }

    private static boolean hasElectricalReplacement(ServerLevel level,BlockPos pos) {
        var block=Terminals.loadedState(level,pos).getBlock();
        return Terminals.nativePg(level,pos)!=null||block instanceof ElectricalDeviceBlock<?>
                ||ElectricalProviders.registeredBlocks().contains(block);
    }

    /** Avoid BlockWireEndpoint.getNode(), which can lazily resolve a different live owner. */
    private static final class CapturedEndpoint extends BlockWireEndpoint {
        private final OwnedFloatingNode node;
        CapturedEndpoint(BlockPos pos, int nativeId, OwnedFloatingNode node) { super(pos, nativeId); this.node = node; }
        @Override public OwnedFloatingNode getNode(Level level) { return node; }
    }

    public static void unload(Level level) { HOLDERS.remove(level); FAILURES.remove(level); }
    public static void clear() { HOLDERS.clear(); FAILURES.clear(); }
}
