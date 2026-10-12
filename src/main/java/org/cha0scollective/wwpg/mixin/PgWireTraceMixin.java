package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.wiring.WireTreeTraversal;
import org.patryk3211.powergrid.PowerGrid;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(value = WorldNetworks.class, remap = false)
public abstract class PgWireTraceMixin {
    @Shadow @Final private Map<OwnedFloatingNode, Set<TransmissionLinePart>> partNodeMap;
    @Shadow @Final public Map<IWireEndpoint, OwnedFloatingNode> globalExternalNodes;
    @Shadow public abstract void addAndMigrateNode(IWireEndpoint endpoint);
    @Shadow protected abstract boolean makeTransmissionLine(TransmissionLinePart part);

    @Inject(method = "traceTree", at = @At("HEAD"), cancellable = true)
    private void wwpg$traceWithoutRecursion(IWireEndpoint endpoint, Set<IWireEndpoint> visited,
                                           CallbackInfoReturnable<Boolean> cir) {
        var owner = (WorldNetworks) (Object) this;
        cir.setReturnValue(WireTreeTraversal.trace(endpoint, visited, new WireTreeTraversal.Graph<IWireEndpoint, TransmissionLinePart>() {
            public List<TransmissionLinePart> enter(IWireEndpoint node) {
                var parts = partNodeMap.get(globalExternalNodes.get(node));
                if (parts == null) return null;
                addAndMigrateNode(node);
                return List.copyOf(parts);
            }
            public boolean resolved(TransmissionLinePart part) { return part.getLine() != null; }
            public IWireEndpoint other(TransmissionLinePart part, IWireEndpoint node) {
                if (part.getEndpoint1().equals(node)) return part.getEndpoint2();
                if (part.getEndpoint2().equals(node)) return part.getEndpoint1();
                PowerGrid.LOGGER.warn("WWPG: wire part was expected to contain endpoint {}", node);
                return null;
            }
            public boolean valid(IWireEndpoint node) { return node.isValid(owner.world); }
            public void resolve(TransmissionLinePart part) { makeTransmissionLine(part); }
        }));
    }
}
