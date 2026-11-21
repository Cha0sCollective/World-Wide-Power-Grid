package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.WireElectrocutionModule;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.bridge.BridgeSimulationResults;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.cha0scollective.wwpg.bridge.ContactHazardHistory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps CEE contact detection alive during brief warm-up and clears rejected hazards. */
@Mixin(value = WireElectrocutionModule.class, remap = false)
public abstract class WireElectrocutionAvailabilityMixin {
    @Shadow @Final private ServerLevel level;

    @Redirect(method = "finishSimulation", at = @At(value = "INVOKE", target =
            "Lcom/george_vi/electroenergetics/simulation/infrastructure/ConnectionEntry;getWireVoltageToGround(Lcom/george_vi/electroenergetics/simulation/SimulationResults;Lcom/george_vi/electroenergetics/foundation/nodes/Node;Lcom/george_vi/electroenergetics/foundation/nodes/Node;)D"))
    private double wwpg$wireHazard(ConnectionEntry connection, SimulationResults results, Node first, Node second) {
        if (!(results instanceof BridgeSimulationResults bridge))
            return connection.getWireVoltageToGround(results, first, second);

        var history = (ContactHazardHistory) connection;
        long tick = level.getGameTime();
        if (bridge.available(first, second)
                && (connection.cuts == null || connection.cuts.stream().allMatch(cut -> bridge.available(cut.node())))) {
            double voltage = connection.getWireVoltageToGround(results, first, second);
            history.wwpg$recordHazard(voltage, tick);
            return voltage;
        }

        var frame = Bridges.get(level);
        boolean settling = (bridge.available(first) || frame.initializingContact(results, first))
                && (bridge.available(second) || frame.initializingContact(results, second))
                && (connection.cuts == null || connection.cuts.stream().allMatch(cut ->
                    bridge.available(cut.node()) || frame.initializingContact(results, cut.node())));
        if (settling) {
            double previous = history.wwpg$initializingHazard(tick);
            // The retained hazard only keeps contact detection alive. Damage still uses the current solve.
            if (Double.isFinite(previous)) return previous;
        }
        history.wwpg$clearHazard();
        return 0;
    }
}
