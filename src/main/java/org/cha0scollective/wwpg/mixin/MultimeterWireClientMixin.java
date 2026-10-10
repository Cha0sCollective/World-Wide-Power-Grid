package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=WireInteractionBehaviour.class,remap=false)
public abstract class MultimeterWireClientMixin {
    @Inject(method="canInteractOn",at=@At("HEAD"),cancellable=true)
    private void wwpg$terminalPriority(ClientLevel level,BlockState state,BlockPos pos,CallbackInfoReturnable<Boolean> cir){
        var mc=Minecraft.getInstance();
        if(mc.player==null||!(mc.player.getMainHandItem().getItem() instanceof MultimeterItem))return;
        var electric=IElectric.getAt(level,pos);
        // A wire's targeting box overlaps its terminal. Prefer the voltage
        // probe when the crosshair is directly on an accessible terminal.
        boolean terminal=electric!=null&&mc.hitResult instanceof BlockHitResult block
                &&electric.terminalIndexAt(state,block.getLocation().subtract(pos.getX(),pos.getY(),pos.getZ()))>=0;
        cir.setReturnValue(!terminal);
    }
}
