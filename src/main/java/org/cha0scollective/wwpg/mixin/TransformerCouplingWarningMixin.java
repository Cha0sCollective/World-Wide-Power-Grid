package org.cha0scollective.wwpg.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.cha0scollective.wwpg.bridge.OwnedTransformerCouplings;
import org.patryk3211.powergrid.electricity.sim.node.TransformerCoupling;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TransformerCoupling.class, remap = false)
abstract class TransformerCouplingWarningMixin {
    @WrapWithCondition(method = "<init>", at = @At(value = "INVOKE",
            target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;)V"), require = 1)
    private boolean wwpg$warnForForeignIdealCouplings(Logger logger, String message) {
        return !"Zero-resistance transformer couplings can cause unstable behaviour.".equals(message)
                || !OwnedTransformerCouplings.constructing();
    }
}
