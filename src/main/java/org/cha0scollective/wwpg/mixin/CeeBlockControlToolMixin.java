package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.cut_off_switch.CutOffSwitchBlock;
import com.george_vi.electroenergetics.content.cut_off_switch.EmergencyStopBlock;
import com.george_vi.electroenergetics.content.cut_off_switch.MomentarySwitchBlock;
import com.george_vi.electroenergetics.content.energy_meter.EnergyMeterBlock;
import com.george_vi.electroenergetics.content.energy_meter.TriPolarEnergyMeterBlock;
import com.george_vi.electroenergetics.content.transmission_distribution.hv_switch.HVSwitchBlock;
import net.minecraft.core.BlockPos;
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

/** Match these native controls' CEE-spool guard for PG wiring/probe clicks. */
@Mixin(value = {CutOffSwitchBlock.class, EmergencyStopBlock.class, MomentarySwitchBlock.class,
        HVSwitchBlock.class, EnergyMeterBlock.class, TriPolarEnergyMeterBlock.class}, remap = false)
abstract class CeeBlockControlToolMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void wwpg$terminalToolsDoNotOperateControls(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit,
            CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (CeeTerminalPicking.isPgTerminalTool(level, stack))
            cir.setReturnValue(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
    }
}
