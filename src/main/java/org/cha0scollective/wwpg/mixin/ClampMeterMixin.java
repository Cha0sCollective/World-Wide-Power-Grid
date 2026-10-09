package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.clamp_meter.ClampMeterItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.cha0scollective.wwpg.equipment.HandheldMeters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClampMeterItem.class, remap = false)
public abstract class ClampMeterMixin {
    @Inject(method = "onUseTick", at = @At("HEAD"))
    private void wwpg$syncCurrent(net.minecraft.world.level.Level level, LivingEntity entity, ItemStack stack, int remaining, CallbackInfo ci) {
        if(level instanceof net.minecraft.server.level.ServerLevel server)HandheldMeters.tickClamp(stack,server);
    }
    @Inject(method = "onStopUsing", at = @At("TAIL"))
    private void wwpg$disconnect(ItemStack stack, LivingEntity entity, int count, CallbackInfo ci) { HandheldMeters.clearPgWire(stack); }
}
