package org.cha0scollective.wwpg.extension;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.cha0scollective.wwpg.api.ElectricalDevice;
import org.cha0scollective.wwpg.api.Terminal;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLine;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

/** Internal native-wire endpoint holder. A capability lookup does not build its circuit. */
public final class ApiElectric implements IElectric {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "wwpg");
    private static final Supplier<BlockEntityType<Holder>> HOLDER_TYPE = TYPES.register("api_terminal_holder",
            () -> BlockEntityType.Builder.of(Holder::new, Blocks.AIR).build(null));

    private final Level level;
    private final BlockPos pos;
    private final String providerId;
    private final String slotId;
    private final Block ownerBlock;
    private final ApiTerminalIdentities.Owner persistentOwner;
    private Object anchor;
    private boolean suspended;
    private final EndpointBehaviour behaviour;
    private final Map<Integer, String> terminalIdentities = new HashMap<>();
    private final Map<String, Integer> nativeIdentities = new HashMap<>();
    private final Map<Integer, OwnedFloatingNode> observedNodes = new HashMap<>();
    private final Map<WorldNetworks.PartId, TransmissionLinePart> observedParts = new HashMap<>();
    private final java.util.Set<java.util.UUID> observedCeeWires = new java.util.HashSet<>();
    private Map<Integer, Terminal> terminals = Map.of();
    private ElectricalDevice device;
    private boolean geometryChanged;
    private boolean replacementCaptured;

    public static void register(IEventBus bus) { TYPES.register(bus); }

    ApiElectric(Level level, BlockPos pos, ElectricalDevice device) {
        this.level = level;
        this.pos = pos.immutable();
        providerId = device.providerId();
        slotId = device.slotId();
        ownerBlock = org.cha0scollective.wwpg.wiring.Terminals.loadedState(level, pos).getBlock();
        persistentOwner = new ApiTerminalIdentities.Owner(this.pos, BuiltInRegistries.BLOCK.getKey(ownerBlock).toString(), providerId, slotId);
        anchor = currentAnchor();
        if (level instanceof ServerLevel server) {
            terminalIdentities.putAll(ApiTerminalIdentities.get(server).history(persistentOwner));
            terminalIdentities.forEach((id, key) -> nativeIdentities.put(key, id));
            observedCeeWires.addAll(ApiTerminalIdentities.get(server).ceeWires(persistentOwner));
        }
        var holder = new Holder(this.pos, Blocks.AIR.defaultBlockState());
        holder.setLevel(level);
        behaviour = new EndpointBehaviour(holder);
        update(device);
        captureSavedNodes();
    }

    /** Internal simulator use; does not invoke preparation or commit callbacks. */
    public ElectricalDevice device() { return device; }
    public ElectricBehaviour behaviour() { return behaviour; }

    boolean sameIdentity(ElectricalDevice replacement) {
        return providerId.equals(replacement.providerId()) && slotId.equals(replacement.slotId());
    }

    private Object currentAnchor() {
        var entity = ApiTerminals.loadedBlockEntity(level, pos);
        return entity == null ? org.cha0scollective.wwpg.wiring.Terminals.loadedState(level, pos).getBlock() : entity;
    }

    /** A chunk reload may recreate the block entity; a loaded replacement must cut old wires. */
    boolean ownerChanged() {
        var state = org.cha0scollective.wwpg.wiring.Terminals.loadedState(level, pos);
        if (state.getBlock() != ownerBlock) return true;
        if (state.hasBlockEntity() && ApiTerminals.loadedBlockEntity(level, pos) == null) return false;
        return !suspended && anchor != currentAnchor();
    }

    void suspended() { suspended = true; }
    void resumed() { anchor = currentAnchor(); suspended = false; }
    ApiTerminalIdentities.Owner persistentOwner() { return persistentOwner; }
    Map<Integer, OwnedFloatingNode> observedNodes() { return Map.copyOf(observedNodes); }
    Map<Integer, String> identityHistory() { return Map.copyOf(terminalIdentities); }
    Map<WorldNetworks.PartId, TransmissionLinePart> observedParts() { return Map.copyOf(observedParts); }
    java.util.Set<java.util.UUID> observedCeeWires() { return java.util.Set.copyOf(observedCeeWires); }

    /** Invoked by safe preparation or actual wire binding, never by terminal geometry discovery. */
    void rememberIdentities() {
        if (level instanceof ServerLevel server) {
            ApiTerminalIdentities.get(server).remember(persistentOwner, terminalDefinitions());
            captureExistingParts();
            var infrastructure = com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData.load(server);
            var ceeIdentities = ApiCeeWireIdentities.get(server);
            var currentCee = new java.util.HashSet<java.util.UUID>();
            for (int nativeId : terminalIdentities.keySet())
                for (var connection : infrastructure.getConnections(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(nativeId, pos))) {
                    var id = ceeIdentities.remember(connection); currentCee.add(id); observedCeeWires.add(id);
                }
            var world = GlobalElectricNetworks.getWorldNetworks((net.minecraft.world.level.LevelAccessor) level);
            if (world != null) {
                var currentPg = new java.util.HashSet<WorldNetworks.PartId>();
                observedParts.forEach((id, part) -> { if (world.getPart(id) == part) currentPg.add(id); });
                ApiTerminalIdentities.get(server).rememberWires(persistentOwner, currentPg, currentCee);
            }
        }
    }

    /** Read existing saved endpoint references without binding terminals or completing network loading. */
    private void captureSavedNodes() {
        if (!(level instanceof ServerLevel)) return;
        var world = GlobalElectricNetworks.getWorldNetworks((net.minecraft.world.level.LevelAccessor) level);
        if (world == null) return;
        for (int nativeId : terminalIdentities.keySet()) {
            var node = world.globalExternalNodes.get(new BlockWireEndpoint(pos, nativeId));
            if (node != null) observedNodes.putIfAbsent(nativeId, node);
        }
        captureExistingParts();
    }

    /** Capture exact old-wire objects before native PG can migrate their endpoint nodes to a replacement. */
    void captureExistingParts() {
        if (!(level instanceof ServerLevel)) return;
        var world = GlobalElectricNetworks.getWorldNetworks((net.minecraft.world.level.LevelAccessor) level);
        if (world == null) return;
        for (var entry : observedNodes.entrySet()) {
            var parts = world.findConnectedWires(new BlockWireEndpoint(pos, entry.getKey()) {
                @Override public OwnedFloatingNode getNode(Level world) { return entry.getValue(); }
            });
            if (parts != null) for (var part : parts) observedParts.put(part.persistentOwnerId, part);
        }
    }

    /** Freeze CEE ownership before a replacement can install wires at the same native endpoints. */
    void captureReplacementParts() {
        if (!replacementCaptured && level instanceof ServerLevel server) {
            captureSavedNodes();
            observedCeeWires.addAll(ApiCeeWireIdentities.get(server).idsAt(pos));
            replacementCaptured = true;
        }
        // PG parts may load later, but only the captured old node references
        // participate; freshly created native node references are never added.
        captureExistingParts();
    }

    void update(ElectricalDevice replacement) {
        if (!sameIdentity(replacement)) throw new IllegalArgumentException("Device provider/slot identity changed");
        var next = new TreeMap<Integer, Terminal>();
        var keys = new java.util.HashSet<String>();
        for (var terminal : List.copyOf(replacement.terminals())) {
            if (terminal == null || terminal.nativeId() < 0 || terminal.nativeId() > 4095
                    || !keys.add(terminal.key()) || next.putIfAbsent(terminal.nativeId(), terminal) != null)
                throw new IllegalArgumentException("Duplicate or invalid electrical terminal");
            String previous = terminalIdentities.get(terminal.nativeId());
            if (previous != null && !previous.equals(terminal.key()))
                throw new IllegalArgumentException("Native terminal " + terminal.nativeId() + " changed stable key from " + previous + " to " + terminal.key());
            Integer previousNative = nativeIdentities.get(terminal.key());
            if (previousNative != null && previousNative != terminal.nativeId())
                throw new IllegalArgumentException("Terminal " + terminal.key() + " changed native identity from " + previousNative + " to " + terminal.nativeId());
        }
        for (var terminal : next.values()) {
            terminalIdentities.putIfAbsent(terminal.nativeId(), terminal.key());
            nativeIdentities.putIfAbsent(terminal.key(), terminal.nativeId());
        }
        device = replacement;
        geometryChanged |= !terminals.equals(next);
        terminals = java.util.Collections.unmodifiableMap(next);
    }

    public Terminal terminalDefinition(int nativeId) { return terminals.get(nativeId); }
    public List<Terminal> terminalDefinitions() { return List.copyOf(terminals.values()); }

    @Override public int terminalCount() {
        return terminals.keySet().stream().mapToInt(id -> id + 1).max().orElse(0);
    }

    @Override public ITerminalPlacement terminal(BlockState state, int nativeId) {
        var definition = terminals.get(nativeId);
        if (definition == null) return null;
        var point = definition.position();
        var origin = new Vec3(point.x(), point.y(), point.z());
        double radius = definition.hitRadius();
        return new ITerminalPlacement() {
            @Override public boolean check(Vec3 p) { return p.distanceToSqr(origin) <= radius * radius; }
            @Override public Vec3 getOrigin() { return origin; }
        };
    }

    @Override public int terminalIndexAt(BlockState state, Vec3 point) {
        int best = -1;
        double distance = Double.POSITIVE_INFINITY;
        for (int nativeId : terminals.keySet()) {
            var placement = terminal(state, nativeId);
            double candidate = point.distanceToSqr(placement.getOrigin());
            if (placement.check(point) && candidate < distance) { best = nativeId; distance = candidate; }
        }
        return best;
    }

    @Override public ITerminalPlacement terminalAt(BlockState state, Vec3 point) {
        int id = terminalIndexAt(state, point);
        return id < 0 ? null : terminal(state, id);
    }

    @Override public ElectricBehaviour getBehaviour(Level world, BlockPos position, BlockState state) { return behaviour; }
    @Override public boolean accepts(ItemStack wireStack) { return WireRegistry.forItem(level, wireStack.getItem()) != null; }
    @Override public net.minecraft.world.InteractionResult onWire(BlockState state, net.minecraft.world.item.context.UseOnContext context) {
        if (level instanceof ServerLevel server && !ApiTerminals.canWire(server,pos)) {
            IElectric.sendMessage(context,net.minecraft.network.chat.Component.translatable("wwpg.device.loading"));
            return net.minecraft.world.InteractionResult.FAIL;
        }
        return IElectric.super.onWire(state,context);
    }

    /** Called only in the topology preparation phase, never from geometry lookup. */
    void removeInvalidConnections() {
        for (var entry : List.copyOf(behaviour.getConnections().entrySet())) {
            if (behaviour.hasTerminal(entry.getKey().getTerminal())) continue;
            for (var wire : List.copyOf(entry.getValue())) wire.endpointRemoved(entry.getKey());
        }
        if (geometryChanged) {
            behaviour.refreshConnectionEntities();
            geometryChanged = false;
        }
    }

    private final class EndpointBehaviour extends ElectricBehaviour {
        EndpointBehaviour(Holder holder) { super(holder, false); }

        @Override public OwnedFloatingNode getTerminal(int nativeId) {
            if (!hasTerminal(nativeId)) return null;
            rememberIdentities();
            var endpoint = new BlockWireEndpoint(pos, nativeId);
            // PG publishes its WorldNetworks before resolving saved endpoints.
            // Avoid recursively completing the same saved network during load.
            var world = GlobalElectricNetworks.getWorldNetworks((net.minecraft.world.level.LevelAccessor) level);
            if (world == null) world = GlobalElectricNetworks.getWorldNetworks(level);
            var node = world.globalExternalNodes.computeIfAbsent(endpoint, OwnedFloatingNode::new);
            observedNodes.put(nativeId, node);
            return node;
        }

        @Override public boolean hasTerminal(int nativeId) { return terminals.containsKey(nativeId); }
        @Override public List<OwnedFloatingNode> getExternalNodes() { return terminals.keySet().stream().map(this::getTerminal).toList(); }

        @Override public void joinNetwork(ElectricalNetwork network, int nativeId) {
            var node = getTerminal(nativeId);
            if (node != null && node.getNetwork() != network) {
                if (node.getNetwork() == null) network.addNode(node);
                else network.merge(node.getNetwork());
            }
        }

        @Override public void writeToSync(FriendlyByteBuf buffer, boolean doubles, Function<OwnedFloatingNode, TransmissionLine> getter) {
            buffer.writeVarInt(terminals.size());
            for (int nativeId : terminals.keySet()) {
                var node = getTerminal(nativeId);
                var line = node.getNetwork() == null ? getter.apply(node) : null;
                buffer.writeVarInt(nativeId);
                writeToBuffer(buffer, line == null ? node.getVoltage() : line.voltageFor(node), doubles);
            }
        }

        @Override public void readFromSync(FriendlyByteBuf buffer, boolean doubles) {
            int count = buffer.readVarInt();
            if (count < 0 || count > 4096) throw new IllegalArgumentException("Invalid API terminal synchronization count");
            for (int i = 0; i < count; ++i) {
                int nativeId = buffer.readVarInt();
                double voltage = readFromBuffer(buffer, doubles);
                var node = getTerminal(nativeId);
                if (node != null && Double.isFinite(voltage)) node.setStateValue(voltage);
            }
        }
    }

    /** A sidecar owner only; never placed, ticked as a block entity, or serialized. */
    private static final class Holder extends SmartBlockEntity implements IElectricEntity {
        Holder(BlockPos pos, BlockState state) { super(HOLDER_TYPE.get(), pos, state); }
        @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
        @Override public void buildCircuit(IElectricEntity.CircuitBuilder builder) {}
    }
}
