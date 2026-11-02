package org.cha0scollective.wwpg.wiring;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.george_vi.electroenergetics.CEETags;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.wire.IWire;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItem;

/** Shared client crosshair and server wire-tool picking; never changes block/collision shapes. */
public final class CeeTerminalPicking {
    private CeeTerminalPicking() {}

    public static boolean holdsPgWire(Player player) {
        return IWire.isWire(player.level(), player.getMainHandItem().getItem())
                || IWire.isWire(player.level(), player.getOffhandItem().getItem());
    }

    public static boolean isPgProbe(ItemStack stack) {
        return stack.getItem() instanceof MultimeterItem;
    }

    /** Tool intent only; never changes native wire identity or connection dispatch. */
    public static boolean isPgTerminalTool(Level level, ItemStack stack) {
        return isPgProbe(stack) || level != null && IWire.isWire(level, stack.getItem());
    }

    public static boolean holdsTerminalTool(Player player) {
        // PG probes operate in the main hand; retain native offhand wire highlighting.
        return isPgTerminalTool(player.level(), player.getMainHandItem())
                || IWire.isWire(player.level(), player.getOffhandItem().getItem());
    }

    /** Keep the visible cover and recessed-terminal picking on the same tool predicate. */
    public static boolean revealsPanel(Level level, ItemStack stack) {
        return stack.is(CEETags.ELECTRICAL_PANEL_SEE_THROUGH)
                || isPgTerminalTool(level, stack);
    }

    public static HitResult pick(Player player, Vec3 from, Vec3 to, HitResult original) {
        if (original.getType() == HitResult.Type.ENTITY || from.equals(to)) return original;
        var level = player.level();
        var panel = panelTerminal(player, from, to, original);
        // An opened panel exposes its own recessed terminals. All other devices
        // and intervening blocks retain the ordinary body's occlusion limit.
        if (original.getType() == HitResult.Type.BLOCK) to = original.getLocation();
        var min = BlockPos.containing(Math.min(from.x, to.x) - 1, Math.min(from.y, to.y) - 1, Math.min(from.z, to.z) - 1);
        var max = BlockPos.containing(Math.max(from.x, to.x) + 1, Math.max(from.y, to.y) + 1, Math.max(from.z, to.z) + 1);
        HitResult nearest = panel == null ? original : panel;
        double distance = from.distanceToSqr(nearest.getLocation());
        // Native regulator and capacitor pins can extend into the neighboring block.
        // Inspect only loaded owners near this short aiming ray; keep no geometry cache.
        for (var pos : BlockPos.betweenClosed(min, max)) {
            if (!(Terminals.loadedState(level, pos).getBlock() instanceof ElectricalDeviceBlock<?>)) continue;
            // Match Terminals.electric: a native PG implementation owns its own picking.
            if (Terminals.nativePg(level, pos) != null) continue;
            var adapter = Terminals.cee(level, pos);
            var hit = adapter == null ? null : adapter.clip(from, to);
            if (hit != null && from.distanceToSqr(hit.getLocation()) < distance) {
                nearest = hit;
                distance = from.distanceToSqr(hit.getLocation());
            }
        }
        return nearest;
    }

    private static BlockHitResult panelTerminal(Player player, Vec3 from, Vec3 to, HitResult original) {
        // Match CEE's main-hand cover visibility; an offhand wire must not pick
        // through a cover that CEE is still displaying as closed.
        if (!revealsPanel(player.level(), player.getMainHandItem())
                || !(original instanceof BlockHitResult hit) || original.getType() != HitResult.Type.BLOCK) return null;
        var level = player.level();
        var pos = hit.getBlockPos();
        var state = Terminals.loadedState(level, pos);
        if (!(state.getBlock() instanceof ElectricalPanelBlock)
                || hit.getDirection() != state.getValue(ElectricalPanelBlock.FACING)
                || Terminals.nativePg(level, pos) != null) return null;
        var adapter = Terminals.cee(level, pos);
        var terminal = adapter == null ? null : adapter.clip(from, to);
        // Never extend reach or see beyond this panel to another owner.
        return terminal != null && new AABB(pos).contains(terminal.getLocation()) ? terminal : null;
    }
}
