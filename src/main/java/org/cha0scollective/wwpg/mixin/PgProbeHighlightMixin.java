package org.cha0scollective.wwpg.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.cha0scollective.wwpg.wiring.CeeTerminalPicking;
import org.patryk3211.powergrid.electricity.info.TerminalHandler;
import org.patryk3211.powergrid.electricity.wire.IWire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Use PG's existing terminal outline for its main-hand probe as well as wires/goggles. */
@Mixin(value = TerminalHandler.class, remap = false)
abstract class PgProbeHighlightMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lorg/patryk3211/powergrid/electricity/wire/IWire;isWire(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/Item;)Z"),
            require = 2, allow = 2)
    private static boolean wwpg$includeMainHandProbe(Level level, Item item) {
        var player = Minecraft.getInstance().player;
        return IWire.isWire(level, item)
                || player != null && CeeTerminalPicking.isPgProbe(player.getMainHandItem());
    }
}
