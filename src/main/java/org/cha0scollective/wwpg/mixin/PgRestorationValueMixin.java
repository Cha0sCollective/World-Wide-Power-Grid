package org.cha0scollective.wwpg.mixin;

import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.cha0scollective.wwpg.bridge.ServerElectricalSchedule;
import org.cha0scollective.wwpg.bridge.NativeStateRead;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.INode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.patryk3211.powergrid.electricity.sim.solver.IMNA;
import org.patryk3211.powergrid.electricity.sim.solver.NativeMNA;
import java.util.List;

@Mixin(value = ElectricalNetwork.class, remap = false)
public abstract class PgRestorationValueMixin implements RestorationCoordinator.PauseMarker {
    @Unique private long wwpg$pauseEpoch;
    @Override public long wwpg$pauseEpoch() { return wwpg$pauseEpoch; }
    @Override public void wwpg$pauseEpoch(long epoch) { wwpg$pauseEpoch = epoch; }
    @Shadow private IMNA mna;
    @Shadow protected List<INode> nodes;
    @Shadow protected abstract Double tryGetValue(INode node);
    /**
     * @author WWPG
     * @reason Keep assigned-row reads primitive; retain PG's tracked-node fallback and mask restoring circuits.
     */
    @Overwrite
    public double getValue(INode node) {
        if (RestorationCoordinator.paused((ElectricalNetwork) (Object) this) && !ServerElectricalSchedule.preparing()) return 0;
        if (mna == null) return 0;
        // PG assigns -1 to eliminated nodes; their tracked-node path remains upstream.
        // Assigned rows can avoid its leaf-map probe and boxed temporary Double.
        int index = node.getIndex();
        if (mna != null && index >= 0 && index < nodes.size() && nodes.get(index) == node) {
            // Stock native assigned rows need only column zero. Custom backends
            // and NativeMNA subclasses retain their virtual matrix accessor.
            double value = mna.getClass() == NativeMNA.class
                    ? NativeStateRead.assignedRow((PgNativeStateAccessor) mna, index)
                    : mna.stateVector().safe_get(index, 0);
            return Double.isFinite(value) ? value : 0;
        }
        var value = tryGetValue(node);
        return value == null ? 0 : value;
    }
}
