package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.wiring.WireTreeTraversal;
import org.cha0scollective.wwpg.experimental.WireTreeBatch;
import org.patryk3211.powergrid.PowerGrid;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLinePart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(value = WorldNetworks.class, remap = false)
public abstract class PgWireTraceMixin implements WireTreeBatch.Revision {
    @Unique private long wwpg$treeRevision;
    @Override public long wwpg$treeRevision() { return wwpg$treeRevision; }
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
                var previous = globalExternalNodes.get(node);
                var parts = partNodeMap.get(previous);
                if (parts == null) return null;
                addAndMigrateNode(node);
                // Endpoint migration can change earlier resolved regions. Unknown
                // endpoint callbacks also prevent retaining their predecessors.
                if (WireTreeBatch.RETAIN_COVERAGE && (globalExternalNodes.get(node) != previous
                        || node.getClass() != org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint.class))
                    wwpg$treeRevision++;
                return List.copyOf(parts);
            }
            public boolean resolved(TransmissionLinePart part) { return part.getLine() != null; }
            public IWireEndpoint other(TransmissionLinePart part, IWireEndpoint node) {
                if (part.getEndpoint1().equals(node)) return part.getEndpoint2();
                if (part.getEndpoint2().equals(node)) return part.getEndpoint1();
                PowerGrid.LOGGER.warn("WWPG: wire part was expected to contain endpoint {}", node);
                return null;
            }
            public boolean valid(IWireEndpoint node) {
                if (WireTreeBatch.RETAIN_COVERAGE
                        && node.getClass() != org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint.class)
                    wwpg$treeRevision++;
                return node.isValid(owner.world);
            }
            public void resolve(TransmissionLinePart part) {
                // A failed connection can still migrate/split nodes. Invalidate
                // before every unresolved attempt, not only successful ones.
                if (WireTreeBatch.RETAIN_COVERAGE && part.getLine() == null) wwpg$treeRevision++;
                makeTransmissionLine(part);
            }
        }));
    }
}
