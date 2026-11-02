package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.CEETags;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelClientTicker;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.cha0scollective.wwpg.wiring.CeeTerminalPicking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/** Extend CEE's existing main-hand cover animation without changing attachment previews. */
@Mixin(value = ElectricalPanelClientTicker.class, remap = false)
public abstract class CeePanelCoverMixin {
    @Redirect(method = "tick", slice = @Slice(from = @At(value = "FIELD",
            target = "Lcom/george_vi/electroenergetics/CEETags;ELECTRICAL_PANEL_SEE_THROUGH:Lnet/minecraft/tags/TagKey;"),
            to = @At(value = "FIELD",
            target = "Lcom/george_vi/electroenergetics/CEETags;ELECTRICAL_PANEL_ATTACHMENT:Lnet/minecraft/tags/TagKey;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z"),
            require = 1, allow = 1)
    private static boolean wwpg$revealForPgWire(ItemStack stack, TagKey<Item> tag) {
        return tag == CEETags.ELECTRICAL_PANEL_SEE_THROUGH
                ? CeeTerminalPicking.revealsPanel(Minecraft.getInstance().level, stack) : stack.is(tag);
    }
}
