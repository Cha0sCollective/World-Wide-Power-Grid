package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.simulation.WireType;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The published decorative cable lacks the recovery entries used by native tools. */
@Mixin(value=WireType.class,remap=false)
public abstract class BundledWireRecoveryMixin {
    @Inject(method="getSpooledItem",at=@At("HEAD"),cancellable=true)
    private void wwpg$spool(CallbackInfoReturnable<Item> cir) {
        if((Object)this==CEEWireTypes.DUPLEX.get()) cir.setReturnValue(CEEItems.DUPLEX_WIRE_SPOOL.get());
    }
    @Inject(method="getDrops",at=@At("HEAD"),cancellable=true)
    private void wwpg$strands(CallbackInfoReturnable<Item> cir) {
        if((Object)this==CEEWireTypes.DUPLEX.get()) cir.setReturnValue(CEEItems.INSULATED_WIRE.get());
    }
}
