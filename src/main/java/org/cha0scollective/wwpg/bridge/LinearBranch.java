package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

/** A persistent PG stamp. Parameter changes do not replace its nodes or wires. */
public final class LinearBranch {
    private final PropertyCurrentSourceWire norton;
    private final VoltageSourceCoupling voltage;
    private final java.util.List<org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire> nonlinearGrounds=new java.util.ArrayList<>();
    private ElectricalProperties properties;

    public LinearBranch(ElectricalNetwork network, IElectricNode first, IElectricNode second,
                        ElectricalProperties properties, boolean voltageSource) {
        validate(properties);
        var model=org.cha0scollective.wwpg.compat.cee.CeeCompatibility.integration().nonlinear(properties);
        norton = model==null ? new PropertyCurrentSourceWire(first, second, properties.conductance())
                : new NonlinearPropertyWire(first,second,properties,model);
        if(model!=null) {
            var endpoints=java.util.List.of(first,second);
            for(int index=0;index<endpoints.size();index++) {
                double leakage=index==0?model.gMin():model.reverseGMin();if(leakage==0)continue;
                var ground=new PropertyCurrentSourceWire(endpoints.get(index),null,leakage);
                network.addWire(ground);nonlinearGrounds.add(ground);
            }
        }
        network.addWire(norton);
        voltage = voltageSource ? new VoltageSourceCoupling(first, second, 0f) : null;
        if (voltage != null) network.addNode(voltage);
        update(properties);
    }

    public void update(ElectricalProperties properties) {
        validate(properties);
        this.properties = properties;
        if(norton instanceof NonlinearPropertyWire nonlinear)
            nonlinear.rebind(properties,org.cha0scollective.wwpg.compat.cee.CeeCompatibility.integration().nonlinear(properties));
        double conductance = properties.conductance();
        if (Double.compare(norton.conductance(), conductance) != 0) norton.setConductance(conductance);
        // CEE 1.2 changed the Norton source orientation. Keep stamping and readings paired.
        norton.setCurrent(org.cha0scollective.wwpg.compat.cee.CeeCompatibility.integration().pgSourceCurrent(properties.currentSource()));
        if (voltage != null) voltage.setVoltage(-properties.voltageSource());
    }

    public void beginFrame(){if(norton instanceof NonlinearPropertyWire nonlinear)nonlinear.beginFrame();}
    public boolean hasVoltageSource() { return voltage != null; }
    void reconnect(org.patryk3211.powergrid.electricity.sim.NetworkGraph graph, IElectricNode first, IElectricNode second) {
        if (voltage != null) throw new IllegalStateException("Cannot move an ideal source");
        norton.reconnect(graph, first, second);
    }
    public ElectricalNetwork network() { return norton.getNode1().getNetwork(); }
    public boolean attached() { var network = network(); return network != null && norton.getNetwork() == network && (voltage == null || voltage.getNetwork() == network); }
    public double current() {
        if(norton instanceof NonlinearPropertyWire nonlinear)return nonlinear.currentAt(norton.potentialDifference());
        return norton.current() - norton.getCurrent() + (voltage == null ? 0 : voltage.getCurrent());
    }
    public double current(double firstVoltage, double secondVoltage) {
        if(norton instanceof NonlinearPropertyWire nonlinear)return nonlinear.currentAt(firstVoltage-secondVoltage);
        return (firstVoltage - secondVoltage) * norton.conductance() - norton.getCurrent()
                + (voltage == null ? 0 : voltage.getCurrent());
    }
    public void remove() {
        norton.remove();
        nonlinearGrounds.forEach(org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire::remove);
        if (voltage != null) voltage.remove();
    }
    public void remove(org.patryk3211.powergrid.electricity.sim.NetworkGraph graph) {
        OwnedWireRemoval.remove(norton,graph);
        nonlinearGrounds.forEach(ground->OwnedWireRemoval.remove(ground,graph));
        if (voltage != null) {
            var recorded=voltage.getNetwork();
            var owner=norton.getNode1().getNetwork();
            if(recorded!=null){recorded.setDirty();recorded.removeNode(voltage);}
            if(owner!=null&&owner!=recorded){owner.setDirty();owner.removeNode(voltage);}
            graph.decouple(voltage);
        }
    }

    public static void validate(ElectricalProperties properties) {
        if (!Double.isFinite(properties.conductance()) || properties.conductance() < 0
                || !Double.isFinite(properties.currentSource()) || !Double.isFinite(properties.voltageSource()))
            throw new IllegalArgumentException("Invalid electrical parameters: " + properties);
    }
}
