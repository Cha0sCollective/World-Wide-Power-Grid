package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.WireLifetimeModule;
import com.george_vi.electroenergetics.simulation.infrastructure.WireSimulationState;
import org.cha0scollective.wwpg.bridge.BridgeSimulationResults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** A rejected circuit cannot heat/fail wires; unrelated native circuits continue. */
@Mixin(value = WireLifetimeModule.class, remap = false)
public abstract class WireLifetimeAvailabilityMixin {
    @Redirect(method = "finishSimulation", at = @At(value = "INVOKE",
            target = "Lcom/george_vi/electroenergetics/simulation/infrastructure/WireSimulationState;getAllConnections()Ljava/util/Set;"))
    private Set<Map.Entry<InWorldNodeConnection, ConnectionEntry>> wwpg$availableWires(
            WireSimulationState state, SimulationResults results) {
        var connections = state.getAllConnections();
        if (!(results instanceof BridgeSimulationResults bridge)) return connections;
        return connections.stream().filter(entry -> bridge.available(entry.getKey().node1(), entry.getKey().node2())
                        && (entry.getValue().cuts == null || entry.getValue().cuts.stream().allMatch(cut -> bridge.available(cut.node()))))
                .collect(Collectors.toSet());
    }
}
