package org.cha0scollective.wwpg.compat.cee12.mixin;

import com.george_vi.electroenergetics.content.electrical_panel.special_interaction.ThrottleWheelPanelAttachment;
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

/** The new 1.2 throttle control needs the same terminal-tool guard as other controls. */
@Mixin(value=ThrottleWheelPanelAttachment.class,remap=false)
abstract class ThrottleWheelToolMixin {
    @Inject(method="onInteract",at=@At("HEAD"),cancellable=true)
    private void wwpg$terminalToolsDoNotOperateThrottle(ItemStack stack,Player player,InteractionHand hand,
            BlockHitResult hit,CallbackInfoReturnable<ItemInteractionResult> result) {
        if(CeeTerminalPicking.isPgTerminalTool(player.level(),stack))
            result.setReturnValue(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
    }
}
