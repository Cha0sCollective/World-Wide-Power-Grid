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
    private static final class OptionalSable {
        static final boolean LOADED = available();
        private static boolean available() {
            var mods=net.neoforged.fml.ModList.get();
            if (!mods.isLoaded("sable")) return false;
            var version=mods.getModContainerById("sablecompanion").map(mod->mod.getModInfo().getVersion());
            if (version.isPresent() && version.get().getMajorVersion()==1 && version.get().getMinorVersion()>=5) return true;
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.warn(
                    "WWPG: Sable terminal projection requires Companion 1.5+ within API 1.x; found {}. Unrecognized hits retain native picking safely.",
                    version.map(Object::toString).orElse("missing"));
            return false;
        }
    }
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

    public static HitResult pick(Player player, double reach, float partialTick, HitResult original) {
        if (OptionalSable.LOADED) return org.cha0scollective.wwpg.compat.sable.SableTerminalPicking.pick(player,reach,partialTick,original);
        var from=player.getEyePosition(partialTick);
        return pick(player,from,from.add(player.getViewVector(partialTick).scale(reach)),original);
    }

    /** Both the ray and the native hit must belong to this same coordinate frame. */
    public static HitResult pick(Player player, Vec3 from, Vec3 to, HitResult original) {
        if (original.getType() == HitResult.Type.ENTITY || from.equals(to)) return original;
        // Never let a projected, stale or malformed hit expand the physical ray.
        // Unknown moving-world integrations retain their native hit safely.
        if (!Double.isFinite(from.distanceToSqr(to))
                || original.getType()==HitResult.Type.BLOCK && !withinRay(from,to,original.getLocation())) return original;
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

    /** Rigid transforms can introduce small rounding errors at distant storage coordinates. */
    public static boolean withinRay(Vec3 from,Vec3 to,Vec3 point) {
        var direction=to.subtract(from);double length=direction.lengthSqr();
        if (!(length>0) || !Double.isFinite(length)) return false;
        double along=point.subtract(from).dot(direction)/length;
        if (!(along>=-1e-7 && along<=1+1e-7)) return false;
        return from.add(direction.scale(Math.max(0,Math.min(1,along)))).distanceToSqr(point)<=1e-10;
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
