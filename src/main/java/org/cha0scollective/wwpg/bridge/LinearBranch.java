package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

/** A persistent PG stamp. Parameter changes do not replace its nodes or wires. */
public final class LinearBranch {
    private final CurrentSourceWire norton;
    private final VoltageSourceCoupling voltage;
    private ElectricalProperties properties;

    public LinearBranch(ElectricalNetwork network, IElectricNode first, IElectricNode second,
                        ElectricalProperties properties, boolean voltageSource) {
        validate(properties);
        norton = new PropertyCurrentSourceWire(first, second, properties.conductance());
        network.addWire(norton);
        voltage = voltageSource ? new VoltageSourceCoupling(first, second, 0f) : null;
        if (voltage != null) network.addNode(voltage);
        update(properties);
    }

    public void update(ElectricalProperties properties) {
        validate(properties);
        this.properties = properties;
        norton.setConductance(properties.conductance());
        // CEE injects into the second node; PG injects into the first.
        norton.setCurrent(-properties.currentSource());
        if (voltage != null) voltage.setVoltage(-properties.voltageSource());
    }

    public boolean hasVoltageSource() { return voltage != null; }
    public ElectricalNetwork network() { return norton.getNode1().getNetwork(); }
    public double current() {
        return norton.current() + properties.currentSource() + (voltage == null ? 0 : voltage.getCurrent());
    }
    public void remove() {
        norton.remove();
        if (voltage != null) voltage.remove();
    }

    public static void validate(ElectricalProperties properties) {
        if (!Double.isFinite(properties.conductance()) || properties.conductance() < 0
                || !Double.isFinite(properties.currentSource()) || !Double.isFinite(properties.voltageSource()))
            throw new IllegalArgumentException("Invalid electrical parameters: " + properties);
    }
}
