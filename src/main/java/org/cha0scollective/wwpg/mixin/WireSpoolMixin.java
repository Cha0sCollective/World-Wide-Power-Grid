package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WireSpoolItem.class, remap = false)
public abstract class WireSpoolMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void wwpg$pgSpool(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        var level = context.getLevel(); var stack = context.getItemInHand();
        if (level instanceof ServerLevel server) Terminals.ensurePgNode(server, stack.get(CEEDataComponents.SELECTED_NODE));
        if (Terminals.nativePg(level, context.getClickedPos()) == null || context.getPlayer() == null) return;
        var node = Terminals.closestPg(level, context.getClickedPos(), context.getClickLocation(), 1.5);
        if (node == null) { cir.setReturnValue(InteractionResult.PASS); return; }
        ((WireSpoolItem) (Object) this).interactDetachedNode(node, context.getPlayer(), level, stack);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
    @Inject(method = "interactDetachedNode", at = @At("HEAD"))
    private void wwpg$ensurePgNodes(InWorldNode node, Player player, Level level, ItemStack stack, CallbackInfo ci) {
        if (level instanceof ServerLevel server) {
            Terminals.ensurePgNode(server, node);
            Terminals.ensurePgNode(server, stack.get(CEEDataComponents.SELECTED_NODE));
        }
    }
}
