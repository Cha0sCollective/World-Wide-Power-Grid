package org.cha0scollective.wwpg.wiring;

import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.wire.IWire;

/** Shared client crosshair and server wire-tool picking; never changes block/collision shapes. */
public final class CeeTerminalPicking {
    private CeeTerminalPicking() {}

    public static boolean holdsPgWire(Player player) {
        return IWire.isWire(player.level(), player.getMainHandItem().getItem())
                || IWire.isWire(player.level(), player.getOffhandItem().getItem());
    }

    public static HitResult pick(Level level, Vec3 from, Vec3 to, HitResult original) {
        if (original.getType() == HitResult.Type.ENTITY || from.equals(to)) return original;
        // The ordinary hit remains the occlusion limit, including the device's own body.
        if (original.getType() == HitResult.Type.BLOCK) to = original.getLocation();
        var min = BlockPos.containing(Math.min(from.x, to.x) - 1, Math.min(from.y, to.y) - 1, Math.min(from.z, to.z) - 1);
        var max = BlockPos.containing(Math.max(from.x, to.x) + 1, Math.max(from.y, to.y) + 1, Math.max(from.z, to.z) + 1);
        HitResult nearest = original;
        double distance = from.distanceToSqr(to);
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
}
