package org.cha0scollective.wwpg.compat.cee;

import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.*;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.cha0scollective.wwpg.extension.CeeAdapters;
import java.util.Set;

/** Binary contracts whose implementations must only link against their own CEE line. */
public interface CeeIntegration {
    CeeGraph.NodeView node(CircuitBuilder builder, int ordinal);
    Long2ObjectMap<MicroTickingElectricalProperties> dynamics(CircuitBuilder builder, CeeGraph graph);
    ElectricalProperties zeroConductance();
    boolean simple(ElectricalProperties properties);
    boolean inverse(ElectricalProperties properties);
    double pgSourceCurrent(double ceeCurrent);
    Runnable checkpoint(MicroTickingElectricalProperties properties);
    ElectricalProperties snapshot(CeeAdapters.Binding binding, boolean inverted);
    SimulationResults results(double[] samples, int substeps,
            Object2DoubleOpenHashMap<DirectionalNodeConnection> currents,
            CircuitBuilder builder, InfrastructureSavedData infrastructure, Set<Node> unavailable);
    default CeeNonlinear nonlinear(ElectricalProperties properties) { return null; }
    interface CapturedProperties { }
}
