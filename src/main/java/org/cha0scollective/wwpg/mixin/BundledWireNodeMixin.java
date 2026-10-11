package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.bundled_wire.BundledWireNodeConfigurator;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Match the IDs already registered by CEE 1.1.3's termination geometry. */
@Mixin(value=BundledWireNodeConfigurator.class,remap=false)
public abstract class BundledWireNodeMixin {
    @Inject(method="getWireNodeFor",at=@At("HEAD"),cancellable=true)
    private static void wwpg$nativeId(BlockPos pos,boolean roll,boolean flip,int conductor,CallbackInfoReturnable<InWorldNode> cir) {
        int side=(flip?2:0)+(roll?1:0);
        cir.setReturnValue(new InWorldNode(0x1000|(side<<4)|(conductor&15),pos));
    }
}
