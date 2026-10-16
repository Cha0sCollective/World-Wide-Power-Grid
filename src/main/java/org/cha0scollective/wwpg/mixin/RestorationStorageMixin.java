package org.cha0scollective.wwpg.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity;
import org.patryk3211.powergrid.electricity.febridge.FEInverterBlockEntity;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native tick-side storage and filter history advance only with an available circuit. */
@Mixin(value={BatteryBlockEntity.class,FEInverterBlockEntity.class},remap=false)
public abstract class RestorationStorageMixin {
    @Inject(method="electricalTick",at=@At("HEAD"),cancellable=true)
    private void wwpg$holdStoredState(CallbackInfo ci) {
        var entity=(BlockEntity)(Object)this;
        if (!(entity.getLevel() instanceof ServerLevel level)) return;
        if (RestorationCoordinator.anyPaused() && RestorationCoordinator.pausedAt(level,entity.getBlockPos())) {
            ci.cancel(); return;
        }
        // A rejected solve retains finite entry values for numerical recovery.
        // They are not another accepted tick of battery draw or FE/filter work.
        var behaviour=((ElectricBlockEntity)(Object)this).getElectricBehaviour();
        if (behaviour == null) return;
        for (var node : behaviour.getInternalNodes()) {
            var network=node.getNetwork();
            if (network != null && !network.isConverged()) { ci.cancel(); return; }
        }
        for (var node : behaviour.getExternalNodes()) {
            var network=node.getNetwork();
            if (network != null && !network.isConverged()) { ci.cancel(); return; }
        }
    }
}
