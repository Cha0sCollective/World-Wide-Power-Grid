package org.cha0scollective.wwpg.wiring;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.*;

public final class Terminals {
    private static final Map<Level, Map<BlockPos, CeeElectric>> CEE = new IdentityHashMap<>();
    private Terminals() {}

    public static BlockState loadedState(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
        }
        return level.hasChunkAt(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    public static IElectric nativePg(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        net.minecraft.world.level.block.entity.BlockEntity be;
        if (level instanceof ServerLevel server) {
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) return null;
            be = chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
        } else be = level.getBlockEntity(pos);
        if (be instanceof IElectric electric) return electric;
        var block = loadedState(level, pos).getBlock();
        return block instanceof IElectric electric ? electric : null;
    }

    public static CeeElectric cee(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        var block = loadedState(level, pos).getBlock();
        if (!(block instanceof ElectricalDeviceBlock<?>)) return null;
        var map = CEE.computeIfAbsent(level, l -> new HashMap<>());
        var current = map.get(pos);
        if (current != null && (current.block() == block || current.updateNativeVariant(loadedState(level, pos)))) return current;
        var adapter = new CeeElectric(level, pos.immutable(), (ElectricalDeviceBlock<?>) block);
        map.put(pos.immutable(), adapter);
        return adapter;
    }

    public static Vec3 pgLocalPosition(Level level, BlockPos pos, int id) {
        var electric = nativePg(level, pos);
        if (electric == null || id < 0) return null;
        var state = loadedState(level, pos);
        // A multiblock can expose two terminals locally while its controller
        // assigns IDs 2 and 3 to this part (PG's medium transformer).
        for (int local = 0; local < electric.terminalCount(); ++local) {
            var placement = electric.terminal(state, local);
            if (placement != null && electric.terminalIndexAt(state, placement.getOrigin()) == id)
                return placement.getOrigin();
        }
        return null;
    }

    public static InWorldNode closestPg(Level level, BlockPos pos, Vec3 clicked, double threshold) {
        var electric = nativePg(level, pos);
        if (electric == null) return null;
        InWorldNode found = null;
        double distance = threshold * threshold;
        for (int i = 0; i < electric.terminalCount(); ++i) {
            var state = loadedState(level, pos);
            var placement = electric.terminal(state, i);
            if (placement == null) continue;
            int id = electric.terminalIndexAt(state, placement.getOrigin());
            if (id < 0) continue;
            double d = placement.getOrigin().add(pos.getX(), pos.getY(), pos.getZ()).distanceToSqr(clicked);
            if (d <= distance) { distance = d; found = new InWorldNode(id, pos); }
        }
        return found;
    }

    public static void ensurePgNode(ServerLevel level, InWorldNode node) {
        if (node == null || pgLocalPosition(level, node.sourcePos(), node.id()) == null) return;
        var infrastructure = InfrastructureSavedData.load(level);
        if (!infrastructure.hasNode(node)) infrastructure.createNode(node);
    }

    public static void removePgNodes(ServerLevel level, BlockPos pos) {
        // ElectricBehaviour.remove runs after the world block state may already be air.
        InfrastructureSavedData.load(level).removeNodes(pos);
    }

    public static void refreshPgNodes(ServerLevel level, BlockPos pos) {
        var infrastructure = InfrastructureSavedData.load(level);
        var registered = infrastructure.getNodesAt(pos);
        if (registered == null || registered.isEmpty()) return;
        var electric = nativePg(level, pos);
        if (electric == null) return;
        var ids = new ArrayList<Integer>();
        var state = loadedState(level, pos);
        for (int local = 0; local < electric.terminalCount(); ++local) {
            var placement = electric.terminal(state, local);
            if (placement == null) continue;
            int id = electric.terminalIndexAt(state, placement.getOrigin());
            if (id >= 0 && !ids.contains(id)) ids.add(id);
        }
        infrastructure.registerOrUpdateNodes(pos, ids);
    }

    public static void refresh(Level level) {
        var map = CEE.get(level);
        if (map == null) return;
        map.entrySet().removeIf(e -> {
            if (!level.hasChunkAt(e.getKey())) return false;
            if (level instanceof ServerLevel server && server.getChunkSource().getChunkNow(e.getKey().getX() >> 4, e.getKey().getZ() >> 4) == null)
                return false;
            var adapter = e.getValue();
            var state = loadedState(level, e.getKey());
            if (state.getBlock() != adapter.block() && !adapter.updateNativeVariant(state)) {
                adapter.behaviour().breakConnections();
                return true;
            }
            adapter.removeInvalidConnections();
            return false;
        });
    }
    public static void removeCee(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            var world = GlobalElectricNetworks.getWorldNetworks(level);
            // The sidecar may not exist yet when saved wire entities are still loading.
            var endpoints = world.globalExternalNodes.entrySet().stream()
                    .filter(e -> e.getKey() instanceof BlockWireEndpoint block && block.getPos().equals(pos)).toList();
            for (var e : endpoints) {
                var parts = world.findConnectedWires(e.getKey());
                if (parts != null) for (var part : List.copyOf(parts))
                    if (part.persistentOwnerId instanceof WorldNetworks.SimpleId id) RemovedWires.get(server).remember(id.id());
            }
            var map = CEE.get(level);
            var adapter = map == null ? null : map.remove(pos);
            if (adapter != null) adapter.behaviour().breakConnections();
            for (var e : endpoints) world.nodeHolderRemoved(e.getValue());
            return;
        }
        var map = CEE.get(level);
        if (map == null) return;
        var adapter = map.remove(pos);
        if (adapter != null) adapter.behaviour().breakConnections();
    }
    public static void unload(Level level) { CEE.remove(level); }
    public static void clear() { CEE.clear(); }
}
