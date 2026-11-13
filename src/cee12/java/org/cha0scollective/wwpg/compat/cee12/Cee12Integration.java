package org.cha0scollective.wwpg.compat.cee12;

import com.george_vi.electroenergetics.simulation.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.*;
import com.george_vi.electroenergetics.foundation.electrical_properties.*;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.cha0scollective.wwpg.compat.cee.*;
import org.cha0scollective.wwpg.compat.cee12.mixin.CapacitorNumerics;
import org.cha0scollective.wwpg.compat.cee12.mixin.DiodeNumerics;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import com.george_vi.electroenergetics.simulation.util.DataPacker;
import org.cha0scollective.wwpg.extension.CeeAdapters;
import java.util.Set;

public final class Cee12Integration implements CeeIntegration {
    public CeeGraph.NodeView node(CircuitBuilder builder,int ordinal) {
        var n=builder.getNode(ordinal);return new CeeGraph.NodeView(n.node,n.ordinal,n.adjacency,n.groundConductance);
    }
    public Long2ObjectMap<MicroTickingElectricalProperties> dynamics(CircuitBuilder builder,CeeGraph graph){
        var result=new Long2ObjectOpenHashMap<MicroTickingElectricalProperties>();
        for(var n:graph.allNodes()) for(var e:n.adjacency.int2ObjectEntrySet()) {
            int other=e.getIntKey();if(other<=n.ordinal)continue;
            var p=e.getValue();
            if(p instanceof MicroTickingElectricalProperties m)result.put(DataPacker.pack(n.ordinal,other),m);
            else if(p.invert() instanceof MicroTickingElectricalProperties m)result.put(DataPacker.pack(other,n.ordinal),m);
        }
        return result;}
    public ElectricalProperties zeroConductance(){return ResistorProperties.ZERO_CONDUCTANCE;}
    public boolean simple(ElectricalProperties p){return p.getClass()==ResistorProperties.class||p.getClass()==NortonProperties.class||p.getClass()==VoltageSourceProperties.class;}
    public boolean inverse(ElectricalProperties p){return p.getClass()==InvertedElectricalProperties.class;}
    public double pgSourceCurrent(double current){return current;}
    public Runnable checkpoint(MicroTickingElectricalProperties p) {
        if(p instanceof CapacitorProperties c){
            double v=c.lastVoltage,i=c.lastCurrent,g=((CapacitorNumerics)c).wwpg$currentConductance();
            return ()->{c.lastVoltage=v;c.lastCurrent=i;((CapacitorNumerics)c).wwpg$currentConductance(g);};
        }
        return ()->{};
    }
    @Override public CeeNonlinear nonlinear(ElectricalProperties properties) {
        boolean inverted=properties.getClass()==InvertedElectricalProperties.class;
        var original=inverted?properties.invert():properties;
        // Exact models only. A custom Norton subclass requires its own supported adapter.
        if(original.getClass()!=DiodeProperties.class)return null;
        var diode=(DiodeProperties)original;var state=(DiodeNumerics)diode;
        return new CeeNonlinear(){
            public void iterate(double a,double b,boolean first){diode.tick(inverted?b:a,inverted?a:b,first);}
            public double current(double voltage){
                double id=state.wwpg$saturationCurrent()*Math.expm1((inverted?-voltage:voltage)/state.wwpg$thermalVoltage());
                return inverted?-id:id;
            }
            public double gMin(){return diode.gMin();}
            public Runnable checkpoint(){double v=state.wwpg$oldVoltage(),g=diode.conductance,i=diode.currentSource;
                return ()->{state.wwpg$oldVoltage(v);diode.conductance=g;diode.currentSource=i;};}
        };
    }
    public ElectricalProperties snapshot(CeeAdapters.Binding binding,boolean inverted){return new Snapshot(binding,inverted);}
    public SimulationResults results(double[] v,int steps,Object2DoubleOpenHashMap<DirectionalNodeConnection> currents,
            CircuitBuilder builder,InfrastructureSavedData sd,Set<Node> unavailable){
        return new Cee12SimulationResults(v,steps,currents,builder,sd,unavailable);
    }
    private static final class Snapshot extends ElectricalProperties implements CapturedProperties {
        private final CeeAdapters.Binding binding;private final boolean inverted;
        Snapshot(CeeAdapters.Binding binding,boolean inverted){super();this.binding=binding;this.inverted=inverted;}
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
