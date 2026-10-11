package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.WireElectrocutionModule;
import org.cha0scollective.wwpg.bridge.BridgeSimulationResults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Clears rejected wires' hazard caches without inventing an external voltage reading. */
@Mixin(value = WireElectrocutionModule.class, remap = false)
public abstract class WireElectrocutionAvailabilityMixin {
    @Redirect(method = "finishSimulation", at = @At(value = "INVOKE", target =
            "Lcom/george_vi/electroenergetics/simulation/infrastructure/ConnectionEntry;getWireVoltageToGround(Lcom/george_vi/electroenergetics/simulation/SimulationResults;Lcom/george_vi/electroenergetics/foundation/nodes/Node;Lcom/george_vi/electroenergetics/foundation/nodes/Node;)D"))
    private double wwpg$clearUnavailableHazard(ConnectionEntry connection, SimulationResults results, Node first, Node second) {
        if (results instanceof BridgeSimulationResults bridge && (!bridge.available(first, second)
                || connection.cuts != null && connection.cuts.stream().anyMatch(cut -> !bridge.available(cut.node()))))
            return 0;
        return connection.getWireVoltageToGround(results, first, second);
    }
}
