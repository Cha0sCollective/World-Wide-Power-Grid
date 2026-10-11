package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.infrastructure.*;
import it.unimi.dsi.fastutil.objects.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/** Derive both conductors from the single native saved/rendered cable. */
@Mixin(value=WireAssemblerModule.class,remap=false)
public abstract class BundledWireAssemblerMixin {
    @Shadow @Final InfrastructureSavedData sd;
    @Inject(method="loadLazyConnections",at=@At("RETURN"))
    private void wwpg$conductors(List<ObjectDoublePair<DirectionalNodeConnection>> out,CallbackInfo ci) {
        for(var entry:sd.wireSimulationState.getAllConnections()) {
            if(entry.getValue().wireData.wireType()!=CEEWireTypes.DUPLEX.get()) continue;
            var cable=entry.getKey();
            if((cable.node1().id()&0xffcf)!=0x1000||(cable.node2().id()&0xffcf)!=0x1000) continue;
            double resistance=CEEWireTypes.BUNDLE_CONDUCTOR.get().getResistance()*entry.getValue().wireData.length;
            for(int conductor=1;conductor<=2;conductor++) {
                var a=new InWorldNode(cable.node1().id()|conductor,cable.node1().sourcePos());
                var b=new InWorldNode(cable.node2().id()|conductor,cable.node2().sourcePos());
                if(sd.hasNode(a)&&sd.hasNode(b)) out.add(new ObjectDoubleImmutablePair<>(new DirectionalNodeConnection(a,b),resistance));
            }
        }
    }
}
