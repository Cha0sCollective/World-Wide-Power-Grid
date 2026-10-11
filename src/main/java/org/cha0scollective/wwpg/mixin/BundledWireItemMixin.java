package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.bundled_wire.*;
import com.george_vi.electroenergetics.events.datagen.CEEAdvancements;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllSoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Complete only the unimplemented second-click path in the published item. */
@Mixin(value=BundledWireItem.class,remap=false)
public abstract class BundledWireItemMixin {
    @Inject(method="useOn",at=@At(value="INVOKE",target="Lcom/george_vi/electroenergetics/content/bundled_wire/BundledWireNodeConfigurator;getCableNodeFor(Lnet/minecraft/core/BlockPos;ZZ)Lcom/george_vi/electroenergetics/foundation/nodes/InWorldNode;",ordinal=0),cancellable=true)
    private void wwpg$connectCable(UseOnContext context,CallbackInfoReturnable<InteractionResult> cir) {
        if(!(context.getLevel() instanceof ServerLevel level)) {cir.setReturnValue(InteractionResult.SUCCESS);return;}
        var stack=context.getItemInHand();var player=context.getPlayer();var selection=stack.get(CEEDataComponents.SELECTED_WIRE_TERMINATION);
        var target=context.getClickedPos();var state=level.getBlockState(target);
        if(player==null||selection==null||!(state.getBlock() instanceof BundledWireTerminationBlock hovered)) return;
        var original=org.cha0scollective.wwpg.wiring.Terminals.loadedState(level,selection.pos());
        if(!(original.getBlock() instanceof BundledWireTerminationBlock start)||start.wireType!=hovered.wireType
                ||original.getValue(BundledWireTerminationBlock.ROLL)!=selection.roll()
                ||original.getValue(BundledWireTerminationBlock.FLIP)!=selection.flip()) {
            stack.remove(CEEDataComponents.SELECTED_WIRE_TERMINATION);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("The selected duplex termination changed or is unloaded. Select it again."),true);
            cir.setReturnValue(InteractionResult.FAIL);return;
        }
        var a=BundledWireNodeConfigurator.getCableNodeFor(selection.pos(),selection.roll(),selection.flip());
        var b=BundledWireNodeConfigurator.getCableNodeFor(target,state.getValue(BundledWireTerminationBlock.ROLL),state.getValue(BundledWireTerminationBlock.FLIP));
        var sd=InfrastructureSavedData.load(level);
        if(a.equals(b)||sd.isConnected(a,b)||Math.sqrt(a.sourcePos().distSqr(b.sourcePos()))>CEEWireTypes.DUPLEX.get().getMaxLength()) {
            AllSoundEvents.DENY.playOnServer(level,target);cir.setReturnValue(InteractionResult.FAIL);return;
        }
        start.ensureNodesExist(level,selection.pos(),original);hovered.ensureNodesExist(level,target,state);
        if(!sd.hasNode(a)||!sd.hasNode(b)) {cir.setReturnValue(InteractionResult.FAIL);return;}
        // The native cable owns rendering, saving, length and recovery. Its two
        // electrical conductors are reconstructed by the assembler hook.
        sd.connect(a,b,CEEWireTypes.DUPLEX.get());
        CEEAdvancements.CONNECT_WIRES.awardTo(player);
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level,target);
        stack.remove(CEEDataComponents.SELECTED_WIRE_TERMINATION);
        if(!player.isCreative()) {
            stack.shrink(1);
            if(!CEEConfigs.server().alternateWirePlacement.get()) player.getInventory().placeItemBackInInventory(CEEItems.EMPTY_SPOOL.asStack());
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
