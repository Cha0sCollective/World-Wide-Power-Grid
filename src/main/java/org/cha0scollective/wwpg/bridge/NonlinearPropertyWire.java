package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import org.cha0scollective.wwpg.compat.cee.CeeNonlinear;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.solver.*;
import java.util.*;

/** CEE's exact diode model participates in PG's existing Native/Java Newton hooks. */
final class NonlinearPropertyWire extends PropertyCurrentSourceWire implements ISolverHook {
    private CeeNonlinear model;
    private ElectricalProperties properties;
    private boolean first=true;
    NonlinearPropertyWire(IElectricNode a,IElectricNode b,ElectricalProperties properties,CeeNonlinear model) {
        super(a,b,properties.conductance());rebind(properties,model);
    }
    void rebind(ElectricalProperties properties,CeeNonlinear model) {this.properties=properties;this.model=model;}
    double currentAt(double voltage){return NonlinearTrialCurrent.accepted(model.current(voltage));}
    void beginFrame() {first=true;}
    @Override public void startIteration(int iteration) {
        model.iterate(getNode1().getVoltage(),getNode2().getVoltage(),first);first=false;
        LinearBranch.validate(properties);
        setConductance(properties.conductance());
        setCurrent(properties.currentSource());
    }
    // The source changes during Newton iteration and must never be frozen into RHS.
    @Override public void addStaticResidual(IResidualAdder residual) { }
    @Override public void addResidual(IResidualAdder residual) {
        double voltage=potentialDifference();
        // PG evaluates b - (I(V) - G*V), including its line-search trial states.
        // A far-forward diode trial can overflow exp before PG can backtrack.
        // Give that trial a large finite rejection merit. Publication above
        // still requires the exact, unbounded model current at convergence.
        double correction=NonlinearTrialCurrent.residual(model.current(voltage))-conductance()*voltage;
        if(!Double.isFinite(correction))throw new IllegalArgumentException("Non-finite CEE nonlinear residual");
        residual.add(getNode1().getIndex(),correction);
        residual.add(getNode2().getIndex(),-correction);
    }
    @Override public Collection<IElectricNode> coupledNodes(){return List.of(getNode1(),getNode2());}
}
