package org.cha0scollective.wwpg.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IElectric.class, remap = false)
public interface PgElectricMixin {
    @Inject(method = "getAt", at = @At("HEAD"), cancellable = true)
    private static void wwpg$ceeTerminals(Level level, BlockPos pos, CallbackInfoReturnable<IElectric> cir) {
        cir.setReturnValue(Terminals.electric(level, pos));
    }

    @Inject(method="makeConnection",at=@At("HEAD"),cancellable=true)
    private static void wwpg$apiConnectionReadiness(Level level,
            org.patryk3211.powergrid.electricity.wire.IWireEndpoint first,
            org.patryk3211.powergrid.electricity.wire.IWireEndpoint second,
            net.minecraft.world.item.context.UseOnContext context,
            CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        if(level instanceof net.minecraft.server.level.ServerLevel server
                &&(first instanceof org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint a
                &&org.cha0scollective.wwpg.extension.ApiTerminals.blockedWireEndpoint(server,a.getPos())
                ||second instanceof org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint b
                &&org.cha0scollective.wwpg.extension.ApiTerminals.blockedWireEndpoint(server,b.getPos()))) {
            IElectric.sendMessage(context,net.minecraft.network.chat.Component.translatable("wwpg.device.loading"));
            cir.setReturnValue(net.minecraft.world.InteractionResult.FAIL);
        }
    }
}
