package org.cha0scollective.wwpg.api.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.api.DeviceResults;
import org.cha0scollective.wwpg.api.VoltageMeasurement;
import org.cha0scollective.wwpg.api.TickContext;
import org.cha0scollective.wwpg.api.MeasurementStatus;

/**
 * WWPG runtime discovery service used by ElectricalMeasurements. The WWPG runtime
 * supplies the single implementation through META-INF/services. Add-ons consume
 * the facade; registering a second service is a configuration error.
 */
public interface MeasurementService {
    /** Read the latest extension-device snapshot without solving or loading chunks. */
    DeviceResults read(ServerLevel level, BlockPos position);

    /** Read a native PG, native CEE, or extension terminal without creating endpoints. */
    default VoltageMeasurement readTerminal(ServerLevel level, BlockPos position, int nativeId) {
        return VoltageMeasurement.unavailable(new TickContext(level.getGameTime(), .05, 1),
                MeasurementStatus.NOT_READY, "Terminal measurement service is unavailable");
    }

    /** Signed A-minus-B voltage; RMS is calculated from the differential waveform. */
    default VoltageMeasurement readVoltageBetween(ServerLevel level, BlockPos positionA, int nativeIdA,
                                                 BlockPos positionB, int nativeIdB) {
        return VoltageMeasurement.unavailable(new TickContext(level.getGameTime(), .05, 1),
                MeasurementStatus.NOT_READY, "Differential measurement service is unavailable");
    }
}
