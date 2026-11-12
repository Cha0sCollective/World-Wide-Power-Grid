package org.cha0scollective.wwpg.mixin;

import java.util.Map;
import java.util.Set;
import org.cha0scollective.wwpg.experimental.WireTreeBatch;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;

@Mixin(value = WorldNetworks.class, remap = false)
public interface PgWireTreeBatchAccess extends WireTreeBatch.Access {
    @Invoker("traceTree") boolean wwpg$traceTree(IWireEndpoint endpoint, Set<IWireEndpoint> visited);
    @Accessor("partNodeMap") Map<OwnedFloatingNode, Set<TransmissionLinePart>> wwpg$partNodes();
    @Invoker("makeTransmissionLine") boolean wwpg$makeLine(TransmissionLinePart part);
}
