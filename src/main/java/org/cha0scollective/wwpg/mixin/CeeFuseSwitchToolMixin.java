package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.fuse.FuseHolderBlock;
import com.george_vi.electroenergetics.content.fuse.fuse_held.FuseHoldable;
import com.george_vi.electroenergetics.content.fuse.fuse_held.FuseHeldSwitch;
import com.llamalad7.mixinextras.sugar.Local;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.cha0scollective.wwpg.wiring.CeeTerminalPicking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Guard only the installed switch; retain native fuse repair and empty-slot material insertion. */
@Mixin(value = FuseHolderBlock.class, remap = false)
abstract class CeeFuseSwitchToolMixin {
    @Inject(method = "useItemOn", at = @At(value = "INVOKE", target =
            "Lcom/george_vi/electroenergetics/content/fuse/fuse_held/FuseHoldable;interact(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"), cancellable = true)
    private void wwpg$doNotOperateHeldSwitch(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit,
            CallbackInfoReturnable<ItemInteractionResult> cir, @Local Pair<FuseHoldable, CompoundTag> held) {
        if (held.getFirst() instanceof FuseHeldSwitch && CeeTerminalPicking.isPgTerminalTool(level, stack))
            cir.setReturnValue(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
    }
}
