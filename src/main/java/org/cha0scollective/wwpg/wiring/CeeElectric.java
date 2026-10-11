package org.cha0scollective.wwpg.wiring;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.content.bulb.BulbBlock;
import com.george_vi.electroenergetics.content.fuse.FuseBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ITerminalPlacement;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLine;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.*;
import java.util.function.Function;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

/** Exposes CEE's existing terminal IDs to native PG wire items and entities. */
public final class CeeElectric implements IElectric {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "wwpg");
    private static final Supplier<BlockEntityType<Holder>> HOLDER_TYPE = TYPES.register("terminal_holder", () -> BlockEntityType.Builder.of(Holder::new, Blocks.AIR).build(null));
    public static void register(IEventBus bus) { TYPES.register(bus); }
    private final Level level;
    private final BlockPos pos;
    private ElectricalDeviceBlock<?> block;
    private final EndpointBehaviour behaviour;

    CeeElectric(Level level, BlockPos pos, ElectricalDeviceBlock<?> block) {
        this.level = level; this.pos = pos; this.block = block;
        var holder = new Holder(pos, Blocks.AIR.defaultBlockState());
        holder.setLevel(level);
        behaviour = new EndpointBehaviour(holder);
    }
    ElectricalDeviceBlock<?> block() { return block; }
    boolean updateNativeVariant(BlockState state) {
        // Native bulb/fuse failure and repair retain the same device and terminals.
        // Keep the sidecar, nodes and wire owners through that block transition.
        if (!(state.getBlock() instanceof ElectricalDeviceBlock<?> replacement)
                || replacement.getDevice() != block.getDevice()) return false;
        if (!(block instanceof BulbBlock && replacement instanceof BulbBlock)
                && !(block instanceof FuseBlock && replacement instanceof FuseBlock)) return false;
        block = replacement;
        return true;
    }
    public ElectricBehaviour behaviour() { return behaviour; }
    private Map<Integer, Vec3> accessible() {
        var state = Terminals.loadedState(level, pos);
        if (state.getBlock() != block) return Map.of();
        var terminals = new TreeMap<Integer, Vec3>();
        for (var e : block.getNodePositions(level, pos, state).entrySet())
            if (block.isNodeAccessible(level, pos, state, e.getKey())) terminals.put(e.getKey(), e.getValue());
        return terminals;
    }
    @Override public int terminalCount() { return accessible().keySet().stream().mapToInt(i -> i + 1).max().orElse(0); }
    @Override public ITerminalPlacement terminal(BlockState state, int index) {
        var location = accessible().get(index);
        if (location == null) return null;
        double radius = block.getNodeSize(level, pos, state, index) / 2.0;
        return new ITerminalPlacement() {
            @Override public boolean check(Vec3 p) {
                return Math.abs(p.x - location.x) <= radius && Math.abs(p.y - location.y) <= radius && Math.abs(p.z - location.z) <= radius;
            }
            @Override public Vec3 getOrigin() { return location; }
        };
    }
    @Override public int terminalIndexAt(BlockState state, Vec3 point) {
        for (int id : accessible().keySet()) if (terminal(state, id).check(point)) return id;
        return -1;
    }
    @Override public ITerminalPlacement terminalAt(BlockState state, Vec3 point) {
        int id = terminalIndexAt(state, point);
        return id < 0 ? null : terminal(state, id);
    }
    @Override public ElectricBehaviour getBehaviour(Level world, BlockPos position, BlockState state) { return behaviour; }

    void removeInvalidConnections() {
        for (var e : List.copyOf(behaviour.getConnections().entrySet())) {
            if (behaviour.hasTerminal(e.getKey().getTerminal())) continue;
            for (var wire : List.copyOf(e.getValue())) wire.endpointRemoved(e.getKey());
        }
    }

    private final class EndpointBehaviour extends ElectricBehaviour {
        EndpointBehaviour(Holder holder) { super(holder, false); }
        @Override public OwnedFloatingNode getTerminal(int index) {
            if (!hasTerminal(index)) return null;
            var endpoint = new BlockWireEndpoint(pos, index);
            // PG publishes the WorldNetworks object before reading saved line
            // parts. Those parts resolve CEE endpoints here. Re-entering the
            // Level overload would recursively completeLoad the same saved NBT.
            var world=GlobalElectricNetworks.getWorldNetworks((net.minecraft.world.level.LevelAccessor)level);
            if(world==null)world=GlobalElectricNetworks.getWorldNetworks(level);
            return world.globalExternalNodes.computeIfAbsent(endpoint, OwnedFloatingNode::new);
        }
        @Override public boolean hasTerminal(int index) { return accessible().containsKey(index); }
        @Override public List<OwnedFloatingNode> getExternalNodes() {
            return accessible().keySet().stream().map(this::getTerminal).toList();
        }
        @Override public void joinNetwork(ElectricalNetwork network, int index) {
            var node = getTerminal(index);
            if (node != null && node.getNetwork() != network) {
                if (node.getNetwork() == null) network.addNode(node);
                else network.merge(node.getNetwork());
            }
        }
        @Override public void writeToSync(FriendlyByteBuf buffer, boolean doubles, Function<OwnedFloatingNode, TransmissionLine> getter) {
            var ids = accessible().keySet();
            buffer.writeVarInt(ids.size());
            for (int id : ids) { buffer.writeVarInt(id); writeToBuffer(buffer, getTerminal(id).getVoltage(), doubles); }
        }
        @Override public void readFromSync(FriendlyByteBuf buffer, boolean doubles) {
            int count = buffer.readVarInt();
            for (int i = 0; i < count; ++i) {
                int id = buffer.readVarInt(); double v = readFromBuffer(buffer, doubles);
                var node = getTerminal(id);
                if (node != null) node.setStateValue(v);
            }
        }
    }

    /** In-memory behaviour owner. It is never placed in a chunk or serialized as a block entity. */
    private static final class Holder extends SmartBlockEntity implements IElectricEntity {
        Holder(BlockPos pos, BlockState state) { super(HOLDER_TYPE.get(), pos, state); }
        @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
        @Override public void buildCircuit(IElectricEntity.CircuitBuilder builder) {}
    }
}
