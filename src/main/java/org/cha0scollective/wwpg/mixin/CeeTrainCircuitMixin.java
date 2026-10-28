package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.railway_electrification.pantograph.TrainPantographEntry;
import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.simulation.infrastructure.CatenaryModule;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.WireSimulationState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.cha0scollective.wwpg.bridge.TrainDimensions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import java.util.Collection;

@Mixin(value = CatenaryModule.class, remap = false)
abstract class CeeTrainCircuitMixin {
    @Shadow @Final private ServerLevel level;

    @ModifyExpressionValue(method = "buildCircuit", at = @At(value = "FIELD",
            target = "Lcom/george_vi/electroenergetics/content/railway_electrification/ElectricTrainData;lastVoltage:D"))
    private double wwpg$unavailableIsNotALoadCoefficient(double voltage) {
        // Retain the unavailable public reading. Only the next load's initial
        // coefficient uses CEE's existing cold-start (zero -> 3000 V) fallback.
        return Double.isFinite(voltage) ? voltage : 0;
    }

    @ModifyExpressionValue(method = "finishSimulation", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
    private Collection<Train> wwpg$onlyOwningDimension(Collection<Train> trains) {
        return trains.stream().filter(train -> TrainDimensions.owns(train, level.dimension())).toList();
    }

    // Defer just the handover's remove/create pair to the immediately following
    // creation. Disconnects and lost contacts retain their native removal path.
    @WrapOperation(method = "buildCircuit", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/george_vi/electroenergetics/simulation/infrastructure/WireSimulationState;removeCut(Lcom/george_vi/electroenergetics/simulation/infrastructure/WireSimulationState$WireCutHandle;Lcom/george_vi/electroenergetics/foundation/nodes/AttachedNode;)V"))
    private void wwpg$deferHandoverRemoval(WireSimulationState state, WireSimulationState.WireCutHandle handle,
            AttachedNode node, Operation<Void> original) { }

    @WrapOperation(method = "buildCircuit", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/george_vi/electroenergetics/simulation/infrastructure/WireSimulationState;createCut(Lcom/george_vi/electroenergetics/simulation/infrastructure/WireSimulationState$WireCutHandle;Lcom/george_vi/electroenergetics/simulation/infrastructure/ConnectionEntry;F)Lcom/george_vi/electroenergetics/foundation/nodes/AttachedNode;"))
    private AttachedNode wwpg$moveExistingContact(WireSimulationState state, WireSimulationState.WireCutHandle handle,
            ConnectionEntry target, float point, Operation<AttachedNode> original, @Local TrainPantographEntry pantograph) {
        var retained = Bridges.get(level).moveCatenaryCut(state, handle, pantograph.node, pantograph.onConnection, target, point);
        if (retained != null) return retained;
        state.removeCut(handle, pantograph.node);
        return original.call(state, handle, target, point);
    }

    @WrapOperation(method = "buildCircuit", at = @At(value = "INVOKE",
            target = "Lcom/george_vi/electroenergetics/simulation/infrastructure/WireSimulationState;relocateCut(Lcom/george_vi/electroenergetics/simulation/infrastructure/WireSimulationState$WireCutHandle;Lcom/george_vi/electroenergetics/foundation/nodes/AttachedNode;F)V"))
    private void wwpg$retainAdjacentContactOrder(WireSimulationState state, WireSimulationState.WireCutHandle handle,
            AttachedNode node, float point, Operation<Void> original, @Local TrainPantographEntry pantograph) {
        Bridges.get(level).noteCatenaryRelocation(pantograph.onConnection, node, point);
        original.call(state, handle, node, point);
    }
}
