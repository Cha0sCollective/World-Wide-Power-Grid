package org.cha0scollective.wwpg.compat.cee12.mixin;

import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.SimulationNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import org.cha0scollective.wwpg.bridge.LinearBranch;
import org.cha0scollective.wwpg.bridge.RejectedConnections;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashMap;
import java.util.Map;

@Mixin(value=CircuitBuilder.class,remap=false)
public abstract class CircuitBuilderValidationMixin implements RejectedConnections {
    @Unique private final Map<Node,String> wwpg$rejected=new HashMap<>();
    @Override public Map<Node,String> wwpg$rejectedConnections(){return wwpg$rejected;}
    @Inject(method="connect(IILcom/george_vi/electroenergetics/simulation/electrical_properties/ElectricalProperties;)V",at=@At("HEAD"),cancellable=true)
    private void wwpg$validateIndexed(int first,int second,ElectricalProperties properties,CallbackInfo ci){
        var builder=(CircuitBuilder)(Object)this;
        if(wwpg$reject(org.cha0scollective.wwpg.compat.cee.CeeCompatibility.integration().node(builder,first).node,org.cha0scollective.wwpg.compat.cee.CeeCompatibility.integration().node(builder,second).node,properties))ci.cancel();
    }
    @Inject(method="connect(Lcom/george_vi/electroenergetics/simulation/SimulationNode;Lcom/george_vi/electroenergetics/simulation/SimulationNode;Lcom/george_vi/electroenergetics/simulation/electrical_properties/ElectricalProperties;)V",at=@At("HEAD"),cancellable=true)
    private void wwpg$validateWrapped(SimulationNode first,SimulationNode second,ElectricalProperties properties,CallbackInfo ci){
        if(wwpg$reject(first.node,second.node,properties))ci.cancel();
    }
    @Unique private boolean wwpg$reject(Node first,Node second,ElectricalProperties properties){
        // New microtickers have an unstamped zero resistance until their first tick.
        // Shared synchronization validates their actual stamp after priming.
        if(properties instanceof com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties
                &&properties.resistance()==0&&Double.isFinite(properties.currentSource())&&Double.isFinite(properties.voltageSource()))return false;
        try{LinearBranch.validate(properties);return false;}
        catch(IllegalArgumentException e){wwpg$rejected.put(first,e.getMessage());wwpg$rejected.put(second,e.getMessage());return true;}
    }
}
