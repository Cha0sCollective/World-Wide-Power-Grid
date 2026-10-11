package org.cha0scollective.wwpg.extension;

import org.cha0scollective.wwpg.api.*;
import org.cha0scollective.wwpg.bridge.PropertyCurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.*;
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire;
import org.patryk3211.powergrid.electricity.sim.solver.IAdmittanceAdder;
import java.util.*;

/** Persistent backend-independent model wrapper. Never holds mutable world objects. */
final class ApiElement {
    private final Element structure;
    private Element desired;
    private final IElectricNode positive, negative;
    private PropertyCurrentSourceWire linear;
    private VoltageSourceCoupling voltage;
    private PNJunctionWire diode;
    private TransformerCoupling transformer;
    private ControlCoupling controlled;
    private double storage;
    private double tickStorage;
    private double equivalentCurrent;
    private double sumV, squaresV, sumI, squaresI, sumP, lastV, lastI;
    private int samples;
    private boolean failed;

    ApiElement(Element element, Map<String, ? extends IElectricNode> nodes, ElectricalNetwork network) {
        structure = desired = element;
        positive = nodes.get(element.positiveNode()); negative = nodes.get(element.negativeNode());
        switch (element.model()) {
            case ElectricalModel.VoltageSource ignored -> voltage = new VoltageSourceCoupling(positive, negative, 0f);
            case ElectricalModel.AcVoltageSource ignored -> voltage = new VoltageSourceCoupling(positive, negative, 0f);
            case ElectricalModel.Diode d -> diode = new PNJunctionWire(d.saturationCurrentAmps(), d.seriesResistanceOhms(),
                    d.temperatureCelsius(), d.idealityFactor(), positive, negative);
            case ElectricalModel.Transformer t -> transformer = TransformerCoupling.create((float)t.ratio(), positive, negative,
                    nodes.get(t.secondaryPositiveNode()), nodes.get(t.secondaryNegativeNode()));
            case ElectricalModel.ControlledVoltageSource c -> controlled = new ControlCoupling(positive, negative,
                    nodes.get(c.controlPositiveNode()), nodes.get(c.controlNegativeNode()), c.gain(), false);
            case ElectricalModel.ControlledCurrentSource c -> controlled = new ControlCoupling(positive, negative,
                    nodes.get(c.controlPositiveNode()), nodes.get(c.controlNegativeNode()), c.transconductanceSiemens(), true);
            default -> linear = new PropertyCurrentSourceWire(positive, negative, 0);
        }
        if (element.model() instanceof ElectricalModel.Capacitor c) storage = c.initialVoltageVolts();
        if (element.model() instanceof ElectricalModel.Inductor l) storage = l.initialCurrentAmps();
        if (linear != null) network.addWire(linear);
        if (diode != null) network.addWire(diode);
        if (voltage != null) network.addNode(voltage);
        if (transformer != null) network.addNode(transformer);
        if (controlled != null) network.addNode(controlled);
    }

    boolean matches(Element element, Map<String, ? extends IElectricNode> nodes) {
        if (!structure.positiveNode().equals(element.positiveNode()) || !structure.negativeNode().equals(element.negativeNode())
                || structure.model().getClass() != element.model().getClass()
                || positive != nodes.get(element.positiveNode()) || negative != nodes.get(element.negativeNode())) return false;
        return switch (element.model()) {
            case ElectricalModel.Diode d -> d.saturationCurrentAmps() == ((ElectricalModel.Diode)structure.model()).saturationCurrentAmps()
                    && d.seriesResistanceOhms() == ((ElectricalModel.Diode)structure.model()).seriesResistanceOhms();
            case ElectricalModel.Transformer t -> t.secondaryPositiveNode().equals(((ElectricalModel.Transformer)structure.model()).secondaryPositiveNode())
                    && t.secondaryNegativeNode().equals(((ElectricalModel.Transformer)structure.model()).secondaryNegativeNode())
                    && transformer.coupledNodes().contains(nodes.get(t.secondaryPositiveNode()))
                    && transformer.coupledNodes().contains(nodes.get(t.secondaryNegativeNode()));
            case ElectricalModel.ControlledVoltageSource c -> controlled.matches(nodes.get(c.controlPositiveNode()), nodes.get(c.controlNegativeNode()));
            case ElectricalModel.ControlledCurrentSource c -> controlled.matches(nodes.get(c.controlPositiveNode()), nodes.get(c.controlNegativeNode()));
            default -> true;
        };
    }

    void update(Element element) {
        desired = element;
        tickStorage=storage;
        if (element.model() instanceof ElectricalModel.Transformer t) transformer.setRatio((float)t.ratio());
        if (element.model() instanceof ElectricalModel.Diode d) { diode.setTemperatureCelsius(d.temperatureCelsius()); diode.setIdealityFactor(d.idealityFactor()); }
        if (element.model() instanceof ElectricalModel.ControlledVoltageSource c) controlled.update(c.gain());
        if (element.model() instanceof ElectricalModel.ControlledCurrentSource c) controlled.update(c.transconductanceSiemens());
        sumV = squaresV = sumI = squaresI = sumP = lastV = lastI = 0;
        samples = 0; failed = false;
    }

    void before(TickContext context, int step) {
        double dt = context.substepSeconds();
        equivalentCurrent = 0;
        switch (desired.model()) {
            case ElectricalModel.Resistor r -> linear.setConductance(1 / r.resistanceOhms());
            case ElectricalModel.Switch s -> linear.setConductance(s.closed() ? 1 / s.onResistanceOhms() : 0);
            case ElectricalModel.CurrentSource c -> { linear.setConductance(0); equivalentCurrent = c.currentAmps(); }
            case ElectricalModel.VoltageSource s -> voltage.setVoltage(s.voltageVolts());
            case ElectricalModel.AcVoltageSource s -> voltage.setVoltage(s.offsetVoltageVolts() + s.peakVoltageVolts()
                    * Math.sin(2 * Math.PI * s.frequencyHertz() * (context.startTimeSeconds() + (step + 1) * dt) + s.phaseRadians()));
            case ElectricalModel.Capacitor c -> { double g = c.capacitanceFarads() / dt; linear.setConductance(g); equivalentCurrent = -g * storage; }
            case ElectricalModel.Inductor l -> { linear.setConductance(dt / l.inductanceHenries()); equivalentCurrent = storage; }
            default -> { }
        }
        // PG injects positive source current into node1; the API orients branch current node1 -> node2.
        if (linear != null) linear.setCurrent(-equivalentCurrent);
    }

    void after() {
        if (failed) return;
        var network = network();
        double v = positive.getVoltage() - negative.getVoltage();
        double i = current();
        if (network == null || !network.isConverged() || !Double.isFinite(v) || !Double.isFinite(i)) { failed = true; return; }
        lastV = v; lastI = i;
        sumV += v; squaresV += v*v; sumI += i; squaresI += i*i; sumP += v*i; ++samples;
        // Trial Newton iterations never reach this callback. Only one accepted PG substep advances history.
        if (desired.model() instanceof ElectricalModel.Capacitor) storage = v;
        if (desired.model() instanceof ElectricalModel.Inductor) storage = i;
    }

    double current() {
        if (linear != null) return linear.current() + equivalentCurrent;
        if (voltage != null) return voltage.getCurrent();
        if (diode != null) return diode.current();
        if (controlled != null) return controlled.getStateValue();
        // PG coupling state is minus secondary passive current; primary passive current is ratio * state.
        return ((ElectricalModel.Transformer)desired.model()).ratio() * transformer.getStateValue();
    }
    boolean valid(TickContext context) { return !failed && samples == context.substeps(); }
    ElementResult result(TickContext context) {
        if (!valid(context)) throw new IllegalStateException("Element " + desired.id() + " has no complete accepted solve");
        return new ElementResult(lastV, sumV/samples, Math.sqrt(squaresV/samples), lastI, sumI/samples,
                Math.sqrt(squaresI/samples), sumP/samples, sumP/samples*context.tickSeconds());
    }
    ElementState state() {
        if (desired.model() instanceof ElectricalModel.Capacitor) return new ElementState.Capacitor(storage);
        if (desired.model() instanceof ElectricalModel.Inductor) return new ElementState.Inductor(storage);
        return null;
    }
    void rejectTick() { storage=tickStorage; }
    ElectricalNetwork network() { return positive.getNetwork(); }
    void remove() {
        if (linear != null) linear.remove(); if (voltage != null) voltage.remove(); if (diode != null) diode.remove();
        if (transformer != null) transformer.remove(); if (controlled != null) controlled.remove();
    }

    /** Direct PG matrix constraints; no delayed equivalent-source feedback. */
    private static final class ControlCoupling extends CouplingNode {
        private final IElectricNode p, n, cp, cn;
        private double gain;
        private final boolean currentSource;
        ControlCoupling(IElectricNode p, IElectricNode n, IElectricNode cp, IElectricNode cn, double gain, boolean currentSource) {
            this.p=p; this.n=n; this.cp=cp; this.cn=cn; this.gain=gain; this.currentSource=currentSource;
        }
        @Override public boolean isSource() { return true; }
        @Override public Collection<IElectricNode> coupledNodes() { return List.of(p,n,cp,cn); }
        @Override public void couple(IAdmittanceAdder a) {
            a.add(p.getIndex(), index, 1); a.add(n.getIndex(), index, -1);
            if (currentSource) a.add(index,index,1);
            else { a.add(index,p.getIndex(),1); a.add(index,n.getIndex(),-1); }
            a.add(index,cp.getIndex(),-gain); a.add(index,cn.getIndex(),gain);
        }
        boolean matches(IElectricNode cp, IElectricNode cn) { return this.cp==cp && this.cn==cn; }
        void update(double gain) { if (Double.compare(this.gain,gain)!=0 && network!=null) network.setDirty(); this.gain=gain; }
    }
}
