package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.client.ElectricPropertiesOverlay;
import com.george_vi.electroenergetics.content.clamp_meter.ClampMeterItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.cha0scollective.wwpg.equipment.HandheldMeters;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClampMeterItem.class, remap = false)
public abstract class ClampMeterClientMixin {
    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    private void wwpg$pgReading(Level level, LivingEntity entity, ItemStack stack, int remaining, CallbackInfo ci) {
        if (!level.isClientSide) return;
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.hasUUID(HandheldMeters.PG_WIRE)) return;
        var hit = Minecraft.getInstance().hitResult;
        if (hit instanceof EntityHitResult target && target.getEntity() instanceof BaseWireEntity wire
                && wire.isAlive() && wire.getUUID().equals(data.getUUID(HandheldMeters.PG_WIRE))) {
            float current = data.getFloat(HandheldMeters.CURRENT);
            ElectricPropertiesOverlay.INSTANCE.setAmmeter(Float.isFinite(current) ? Math.abs(current) : 0);
        } else entity.stopUsingItem();
        ci.cancel();
    }
}
