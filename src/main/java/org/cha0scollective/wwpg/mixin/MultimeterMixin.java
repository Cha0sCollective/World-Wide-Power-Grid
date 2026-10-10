package org.cha0scollective.wwpg.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.cha0scollective.wwpg.equipment.HandheldMeters;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MultimeterItem.class, remap = false)
public abstract class MultimeterMixin {
    @Inject(method = "inventoryTick", at = @At("TAIL"))
    private void wwpg$syncReadings(ItemStack stack, Level level, Entity entity, int slot, boolean selected, CallbackInfo ci) {
        if (level instanceof ServerLevel server) HandheldMeters.tickMultimeter(stack, server);
    }

    @Inject(method = "getMeasurement", at = @At("HEAD"), cancellable = true)
    private void wwpg$ceeCurrent(Level level, ItemStack stack, CallbackInfoReturnable<Float> cir) {
        var meter = (MultimeterItem) (Object) this;
        var data = MultimeterItem.getModeData(stack);
        if (meter.getMode(stack) == 1 && data.contains(HandheldMeters.CURRENT))
            cir.setReturnValue(data.getFloat(HandheldMeters.CURRENT));
    }

    @Inject(method = "onTerminal", at = @At("HEAD"))
    private void wwpg$clearCurrentProbe(Level level, IWireEndpoint endpoint, ItemStack stack, CallbackInfoReturnable<InteractionResult> cir) {
        HandheldMeters.clearCeeWire(stack);
    }

    @Inject(method = "useOnWire", at = @At("HEAD"))
    private void wwpg$nativeCurrent(Player player, ItemStack stack, InteractionHand hand, BaseWireEntity wire, CallbackInfoReturnable<InteractionResult> cir) {
        HandheldMeters.clearCeeWire(stack);
    }

    @Inject(method = "use", at = @At("HEAD"))
    private void wwpg$disconnect(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown()) HandheldMeters.clearCeeWire(player.getItemInHand(hand));
    }
}
