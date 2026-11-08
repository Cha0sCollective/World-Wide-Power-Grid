package org.cha0scollective.wwpg.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.Set;
import org.cha0scollective.wwpg.experimental.PlainWireBatch;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.solver.IMultiHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ElectricalNetwork.class, remap = false)
public abstract class PgPlainWireBatchMixin implements PlainWireBatch.NetworkAccess {
    @Unique private PlainWireBatch wwpg$wireBatch;
    @Unique private boolean wwpg$batchSelected;
    @Unique private long wwpg$batchSteps, wwpg$batchContributions;

    @Override public void wwpg$invalidateWireBatch() { wwpg$wireBatch = null; }
    @Override public long wwpg$batchedSteps() { return wwpg$batchSteps; }
    @Override public long wwpg$batchedContributions() { return wwpg$batchContributions; }

    @Inject(method = {"setDirty", "addWire", "removeWire", "internalRemoveNode", "swapNodes", "makeLeaf",
            "removeLeaf", "populateConductanceMatrix", "clear", "cleanup", "switchBackend"}, at = @At("HEAD"))
    private void wwpg$discardWireBatch(CallbackInfo ci) {
        if (PlainWireBatch.ENABLED) wwpg$invalidateWireBatch();
    }

    @ModifyExpressionValue(method = "singleTick", at = @At(value = "FIELD",
            target = "Lorg/patryk3211/powergrid/electricity/sim/ElectricalNetwork;multiHooks:Ljava/util/Set;"))
    private Set<IMultiHooks> wwpg$selectWireBatch(Set<IMultiHooks> original) {
        if (!PlainWireBatch.ENABLED) return original;
        if (wwpg$wireBatch == null) wwpg$wireBatch = PlainWireBatch.compile((ElectricalNetwork) (Object) this, original);
        var selected = wwpg$wireBatch.select(original);
        wwpg$batchSelected = selected != original;
        return selected;
    }

    @Inject(method = "singleTick", at = @At("HEAD"))
    private void wwpg$beginWireBatch(CallbackInfo ci) { wwpg$batchSelected = false; }

    @Inject(method = "singleTick", at = @At("RETURN"))
    private void wwpg$countWireBatch(CallbackInfo ci) {
        if (wwpg$batchSelected) {
            wwpg$batchSteps++;
            wwpg$batchContributions += wwpg$wireBatch.lastContributions();
        }
    }
}
