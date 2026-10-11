package org.cha0scollective.wwpg.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.cha0scollective.wwpg.api.neoforge.ElectricalProviders;
import org.cha0scollective.wwpg.extension.ApiTerminals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe physical removal before the same block/provider can be placed again. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class ApiDeviceRemovalMixin {
    @Inject(method="onRemove",at=@At("HEAD"))
    private void wwpg$retireApiOwner(Level level,BlockPos pos,BlockState replacement,boolean moved,CallbackInfo ci) {
        var previous=(BlockState)(Object)this;
        if(level instanceof ServerLevel server&&previous.getBlock()!=replacement.getBlock()
                &&ElectricalProviders.registeredBlocks().contains(previous.getBlock()))
            ApiTerminals.ownerRemoved(server,pos,previous);
    }
}
