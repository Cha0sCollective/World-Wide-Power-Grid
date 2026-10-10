package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.clamp_meter.ClampMeterWireInteractionBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.cha0scollective.wwpg.equipment.HandheldMeters;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extend CEE's existing meter interaction without adding registry entries. */
@Mixin(value=ClampMeterWireInteractionBehaviour.class,remap=false)
public abstract class MultimeterWireMixin {
    @Inject(method="isActiveFor",at=@At("HEAD"),cancellable=true)
    private void wwpg$multimeter(ItemStack stack,Player player,CallbackInfoReturnable<Boolean> cir){
        if(stack.getItem() instanceof MultimeterItem&&!player.isShiftKeyDown())cir.setReturnValue(true);
    }
    @Inject(method="interactWire",at=@At("HEAD"),cancellable=true)
    private void wwpg$attach(NodeConnectionPoint point,Level level,Player player,ItemStack stack,CallbackInfo ci){
        if(!(stack.getItem() instanceof MultimeterItem))return;
        if(level instanceof ServerLevel server)HandheldMeters.attachMultimeter(point,server,player,stack);
        ci.cancel();
    }
}
