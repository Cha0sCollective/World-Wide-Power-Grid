package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.electrical_panel.attachments.*;
import com.george_vi.electroenergetics.content.electrical_panel.special_interaction.AnalogLeverPanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.special_interaction.SteeringWheelPanelAttachment;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.cha0scollective.wwpg.wiring.CeeTerminalPicking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve normal panel actions while terminal tools pass to their own handlers. */
@Mixin(value = {CutOffSwitchPanelAttachment.class, MomentarySwitchPanelAttachment.class,
        EStopPanelAttachment.class, MCBPanelAttachment.class, BaseEnergyMeterAttachment.class,
        AnalogLeverPanelAttachment.class, SteeringWheelPanelAttachment.class, IndicatorBulbPanelAttachment.class}, remap = false)
abstract class CeePanelControlToolMixin {
    @Inject(method = "onInteract", at = @At("HEAD"), cancellable = true)
    private void wwpg$terminalToolsDoNotOperateControls(ItemStack stack, Player player, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (CeeTerminalPicking.isPgTerminalTool(player.level(), stack))
            cir.setReturnValue(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
    }
}
