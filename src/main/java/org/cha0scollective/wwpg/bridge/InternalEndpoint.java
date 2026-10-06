package org.cha0scollective.wwpg.bridge;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEndpointType;

/** Solver-only node; reconstructed from CEE's device description, never a serialized wire endpoint. */
final class InternalEndpoint implements IWireEndpoint {
    private final EndpointKey key;
    private final OwnedFloatingNode node = new OwnedFloatingNode(this);
    InternalEndpoint(EndpointKey key) { this.key = key; }
    @Override public OwnedFloatingNode getNode(Level world) { return node; }
    @Override public void joinNetwork(Level world, ElectricalNetwork network) { network.addNode(node); }
    @Override public Vec3 getExactPosition(Level world) { throw new UnsupportedOperationException("Internal endpoint " + key); }
    @Override public WireEndpointType type() { throw new UnsupportedOperationException("Internal endpoint is not serializable"); }
    @Override public void read(CompoundTag nbt) { throw new UnsupportedOperationException(); }
    @Override public void write(CompoundTag nbt) { throw new UnsupportedOperationException(); }
}
