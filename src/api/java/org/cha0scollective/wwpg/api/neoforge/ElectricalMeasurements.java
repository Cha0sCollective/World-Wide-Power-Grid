package org.cha0scollective.wwpg.api.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.api.DeviceResults;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.TickContext;
import org.cha0scollective.wwpg.api.VoltageMeasurement;

import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Read-only server measurements for devices participating through the extension
 * capability. A query never prepares a device, loads chunks, changes topology, or
 * advances electrical history. A missing runtime produces NOT_READY results.
 * Device snapshots use extension identities. Terminal voltage queries also support
 * existing native CEE and Power Grid terminals without inventing device identities.
 */
public final class ElectricalMeasurements {
    private ElectricalMeasurements() { }

    /** Latest accepted or unavailable snapshot for the extension at this location. */
    public static DeviceResults read(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        if (Holder.SERVICE == null)
            return DeviceResults.unavailable(new TickContext(level.getGameTime(), .05, 1),
                    MeasurementStatus.NOT_READY, "WWPG measurement runtime is not installed");
        return Objects.requireNonNull(Holder.SERVICE.read(level, position), "WWPG measurement service returned null");
    }

    /** Voltage relative to the containing circuit's solver reference, including mean and RMS. */
    public static VoltageMeasurement readTerminal(ServerLevel level, BlockPos position, int nativeId) {
        checkTerminal(level, position, nativeId);
        if (Holder.SERVICE == null) return runtimeUnavailable(level);
        return Objects.requireNonNull(Holder.SERVICE.readTerminal(level, position, nativeId), "WWPG measurement service returned null");
    }

    /**
     * Signed voltage at A minus B. RMS uses simultaneous differential samples;
     * subtracting two terminal RMS readings does not give this value. Circuits
     * must share a reference through their network, wires, or explicit grounding.
     */
    public static VoltageMeasurement readVoltageBetween(ServerLevel level, BlockPos positionA, int nativeIdA,
                                                        BlockPos positionB, int nativeIdB) {
        checkTerminal(level, positionA, nativeIdA);
        checkTerminal(level, positionB, nativeIdB);
        if (Holder.SERVICE == null) return runtimeUnavailable(level);
        return Objects.requireNonNull(Holder.SERVICE.readVoltageBetween(level, positionA, nativeIdA, positionB, nativeIdB),
                "WWPG measurement service returned null");
    }

    private static void checkTerminal(ServerLevel level, BlockPos position, int nativeId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        if (nativeId < 0) throw new IllegalArgumentException("native terminal ID must be nonnegative");
    }

    private static VoltageMeasurement runtimeUnavailable(ServerLevel level) {
        return VoltageMeasurement.unavailable(new TickContext(level.getGameTime(), .05, 1),
                MeasurementStatus.NOT_READY, "WWPG measurement runtime is not installed");
    }

    private static final class Holder {
        private static final MeasurementService SERVICE = discover();

        private static MeasurementService discover() {
            var services = ServiceLoader.load(MeasurementService.class, ElectricalMeasurements.class.getClassLoader())
                    .stream().limit(2).toList();
            if (services.size() > 1)
                throw new IllegalStateException("Multiple WWPG measurement services installed: "
                        + services.get(0).type().getName() + ", " + services.get(1).type().getName());
            return services.isEmpty() ? null : services.getFirst().get();
        }
    }
}
