package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.wire_spool.HangingWireSpoolItem;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=HangingWireSpoolItem.class,remap=false)
public abstract class HangingWireSpoolMixin {
    @Inject(method="useOn",at=@At(value="INVOKE",target="Lcom/george_vi/electroenergetics/simulation/infrastructure/InfrastructureSavedData;getNodes()Ljava/util/Set;"))
    private void wwpg$registerSelectedPgEndpoint(UseOnContext context,CallbackInfoReturnable<InteractionResult> cir,@Local InWorldNode selected) {
        if(context.getLevel() instanceof ServerLevel level) Terminals.ensurePgNode(level,selected);
    }
}
