package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.WireSimulationState;
import org.cha0scollective.wwpg.bridge.MovingWireCuts;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Mixin(value = WireSimulationState.class, remap = false)
abstract class CeeMovingWireCutMixin implements MovingWireCuts {
    @Shadow @Final private List<Map<AttachedNode, WireSimulationState.CutWireEntry>> cutsByHandle;
    @Shadow public abstract void reloadLazyConnections();

    @Override public boolean wwpg$transferCut(WireSimulationState.WireCutHandle handle, AttachedNode node,
            ConnectionEntry from, ConnectionEntry to, float point) {
        if (!handle.valid() || from == to || !Float.isFinite(point) || point < 0 || point > 1) return false;
        var cuts = cutsByHandle.get(((CeeCutHandleAccessor)handle).wwpg$id());
        var previous = cuts.get(node);
        if (previous == null || previous.handle() != handle || previous.originalList() != from.cuts) return false;
        var moved = new WireSimulationState.CutWireEntry(node, point, handle, to.cuts);
        from.cuts.remove(previous);
        to.cuts.add(moved);
        to.cuts.sort(Comparator.comparingDouble(WireSimulationState.CutWireEntry::point));
        cuts.put(node, moved);
        reloadLazyConnections();
        return true;
    }
}
