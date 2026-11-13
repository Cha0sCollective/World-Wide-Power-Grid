package org.cha0scollective.wwpg.compat.cee11;

import com.george_vi.electroenergetics.simulation.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.*;
import com.george_vi.electroenergetics.foundation.electrical_properties.*;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.cha0scollective.wwpg.compat.cee.*;
import org.cha0scollective.wwpg.bridge.BridgeSimulationResults;
import org.cha0scollective.wwpg.extension.CeeAdapters;
import java.util.Set;

public final class Cee11Integration implements CeeIntegration {
    public CeeGraph.NodeView node(CircuitBuilder builder,int ordinal) {
        var n=builder.getNode(ordinal);return new CeeGraph.NodeView(n.node,n.ordinal,n.adjacency,n.groundConductance);
    }
    public Long2ObjectMap<MicroTickingElectricalProperties> dynamics(CircuitBuilder builder,CeeGraph graph){return builder.microTickers;}
    public ElectricalProperties zeroConductance(){return ElectricalProperties.ZERO_CONDUCTANCE;}
    public boolean simple(ElectricalProperties p){return p.getClass()==ElectricalProperties.class;}
    public boolean inverse(ElectricalProperties p){return p.getClass()==MicroTickingInvertedElectricalProperties.class;}
    public double pgSourceCurrent(double current){return -current;}
    public Runnable checkpoint(MicroTickingElectricalProperties p) {
        boolean forced=p.isForcedVoltageSource;
        Runnable history;
        if(p instanceof CapacitorProperties c){double v=c.lastVoltage;history=()->c.lastVoltage=v;}
        else if(p instanceof DiodeProperties d){double v=d.lastVoltage;history=()->d.lastVoltage=v;}
        else history=()->{};
        return ()->{p.isForcedVoltageSource=forced;history.run();};
    }
    public ElectricalProperties snapshot(CeeAdapters.Binding binding,boolean inverted){return new Snapshot(binding,inverted);}
    public SimulationResults results(double[] v,int steps,Object2DoubleOpenHashMap<DirectionalNodeConnection> currents,
            CircuitBuilder builder,InfrastructureSavedData sd,Set<Node> unavailable){
        return BridgeSimulationResults.takeOwnershipOfCurrents(v,steps,currents,builder,sd,unavailable);
    }
    private static final class Snapshot extends ElectricalProperties implements CapturedProperties {
        private final CeeAdapters.Binding binding;private final boolean inverted;
        Snapshot(CeeAdapters.Binding binding,boolean inverted){super(1,0,0);this.binding=binding;this.inverted=inverted;}
        public double resistance(){return conductance()==0?1e11:1/conductance();}
        public double conductance(){return binding.parameters().conductanceSiemens();}
        public double currentSource(){return (inverted?-1:1)*binding.parameters().currentSourceAmps();}
        public double voltageSource(){return (inverted?-1:1)*binding.parameters().voltageSourceVolts();}
        public boolean isVoltageSource(){return binding.parameters().idealVoltageSource();}
        public boolean isCurrentSource(){return currentSource()!=0;}
        public boolean isSimpleResistor(){return !isVoltageSource()&&!isCurrentSource();}
        public ElectricalProperties invert(){return inverted?binding.properties():binding.invertedProperties();}
        public boolean equals(Object other){return other==this;}
        public int hashCode(){return System.identityHashCode(this);}
    }
}
