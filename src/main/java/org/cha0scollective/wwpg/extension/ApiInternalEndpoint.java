package org.cha0scollective.wwpg.extension;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.cha0scollective.wwpg.api.DeviceIdentity;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEndpointType;

/** Solver-only nodes are reconstructed from names; never serialized as physical wires. */
final class ApiInternalEndpoint implements IWireEndpoint {
    final OwnedFloatingNode node=new OwnedFloatingNode(this);
    private final DeviceIdentity owner; private final String key;
    ApiInternalEndpoint(DeviceIdentity owner,String key) { this.owner=owner;this.key=key; }
    @Override public OwnedFloatingNode getNode(Level world) { return node; }
    @Override public void joinNetwork(Level world,ElectricalNetwork network) { network.addNode(node); }
    @Override public Vec3 getExactPosition(Level world) { throw new UnsupportedOperationException("Internal API node "+owner+" "+key); }
    @Override public WireEndpointType type() { throw new UnsupportedOperationException("Internal API node is not a physical endpoint"); }
    @Override public void read(CompoundTag tag) { throw new UnsupportedOperationException(); }
    @Override public void write(CompoundTag tag) { throw new UnsupportedOperationException(); }
}
