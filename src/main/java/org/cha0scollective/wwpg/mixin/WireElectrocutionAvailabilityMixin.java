package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.WireElectrocutionModule;
import org.cha0scollective.wwpg.bridge.CeeResultsAvailability;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.cha0scollective.wwpg.bridge.ContactHazardHistory;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Clears rejected wires' hazard caches without inventing an external voltage reading. */
@Mixin(value = WireElectrocutionModule.class, remap = false)
public abstract class WireElectrocutionAvailabilityMixin {
    @Shadow @Final private ServerLevel level;
    @Redirect(method = "finishSimulation", at = @At(value = "INVOKE", target =
            "Lcom/george_vi/electroenergetics/simulation/infrastructure/ConnectionEntry;getWireVoltageToGround(Lcom/george_vi/electroenergetics/simulation/SimulationResults;Lcom/george_vi/electroenergetics/foundation/nodes/Node;Lcom/george_vi/electroenergetics/foundation/nodes/Node;)D"))
    private double wwpg$clearUnavailableHazard(ConnectionEntry connection, SimulationResults results, Node first, Node second) {
        var history=connection instanceof ContactHazardHistory h?h:null;
        if (results instanceof CeeResultsAvailability bridge && (!bridge.available(first, second)
                || connection.cuts != null && connection.cuts.stream().anyMatch(cut -> !bridge.available(cut.node())))) {
            var frame=Bridges.get(level);
            boolean settling=(bridge.available(first)||frame.initializingContact(results,first))
                    &&(bridge.available(second)||frame.initializingContact(results,second))
                    &&(connection.cuts==null||connection.cuts.stream().allMatch(cut->bridge.available(cut.node())
                    ||frame.initializingContact(results,cut.node())));
            if(settling&&history!=null) {
                double previous=history.wwpg$initializingHazard(level.getGameTime());
                if(Double.isFinite(previous))return previous;
            }
            if(history!=null)history.wwpg$clearHazard();
            return 0;
        }
        double voltage=connection.getWireVoltageToGround(results, first, second);
        if(history!=null)history.wwpg$acceptedHazard(voltage,level.getGameTime());
        return voltage;
    }
}
