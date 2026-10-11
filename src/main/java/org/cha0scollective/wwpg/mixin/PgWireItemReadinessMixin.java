package org.cha0scollective.wwpg.mixin;

import dev.architectury.event.EventResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.cha0scollective.wwpg.extension.ApiTerminals;
import org.patryk3211.powergrid.collections.ModdedDataComponents;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWire;
import org.patryk3211.powergrid.electricity.wire.WireItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject pending API owners before native PG can fall back to a free-standing wire endpoint. */
@Mixin(value=WireItem.class,remap=false)
public abstract class PgWireItemReadinessMixin {
    @Inject(method="useOn",at=@At("HEAD"),cancellable=true)
    private static void wwpg$apiReadiness(Player player,InteractionHand hand,BlockPos pos,Direction direction,
                                         CallbackInfoReturnable<EventResult> cir) {
        // Retain native cancellation, non-wire interactions, and offhand dispatch.
        if(player==null||player.isShiftKeyDown()||hand!=InteractionHand.MAIN_HAND
                ||!(player.level() instanceof ServerLevel level))return;
        var stack=player.getMainHandItem();
        if(!IWire.isWire(level,stack.getItem()))return;
        var connection=stack.get(ModdedDataComponents.CONNECTION_DATA.get());
        var selected=connection==null?null:connection.endpoint();
        var half=connection==null?null:connection.half();
        if(ApiTerminals.blockedWireEndpoint(level,pos)
                ||selected instanceof BlockWireEndpoint selectedBlock&&ApiTerminals.blockedWireEndpoint(level,selectedBlock.getPos())
                ||half instanceof BlockWireEndpoint halfBlock&&ApiTerminals.blockedWireEndpoint(level,halfBlock.getPos())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("wwpg.device.loading"),true);
            cir.setReturnValue(EventResult.interruptFalse());
        }
    }
}
