package org.cha0scollective.wwpg.api.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Safe lifecycle notification for extension devices. Call from server-side native
 * placement, removal, or configuration hooks, especially for blocks without a block
 * entity. Notification invalidates capabilities and queues discovery; it never
 * prepares a circuit, forces a chunk, or mutates topology during solving.
 */
public final class ElectricalDevices {
    private ElectricalDevices() {}

    /**
     * Notify WWPG that the device at a location may have changed. Returns true when
     * the installed runtime queued a loaded location; false if absent or unloaded.
     * Block unload must retain wires and is not permanent device removal.
     */
    public static boolean changed(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        level.invalidateCapabilities(position);
        return Holder.SERVICE != null && Holder.SERVICE.changed(level, position.immutable());
    }

    private static final class Holder {
        private static final DeviceDiscoveryService SERVICE = discover();

        private static DeviceDiscoveryService discover() {
            var services = ServiceLoader.load(DeviceDiscoveryService.class, ElectricalDevices.class.getClassLoader())
                    .stream().limit(2).toList();
            if (services.size() > 1)
                throw new IllegalStateException("Multiple WWPG device discovery services installed: "
                        + services.get(0).type().getName() + ", " + services.get(1).type().getName());
            return services.isEmpty() ? null : services.getFirst().get();
        }
    }
}
