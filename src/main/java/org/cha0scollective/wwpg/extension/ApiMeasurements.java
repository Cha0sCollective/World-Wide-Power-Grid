package org.cha0scollective.wwpg.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.cha0scollective.wwpg.api.DeviceResults;
import org.cha0scollective.wwpg.api.VoltageMeasurement;
import org.cha0scollective.wwpg.api.neoforge.MeasurementService;

import java.util.Objects;

/** Runtime implementation of the public read-only measurement facade. */
public final class ApiMeasurements implements MeasurementService {
    /** ServiceLoader constructor. */
    public ApiMeasurements() {}

    @Override public DeviceResults read(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        return ApiCircuits.results(level, position);
    }

    @Override public VoltageMeasurement readTerminal(ServerLevel level, BlockPos position, int nativeId) {
        return NativeApiReadings.readTerminal(level, position, nativeId);
    }

    @Override public VoltageMeasurement readVoltageBetween(ServerLevel level, BlockPos positionA, int nativeIdA,
                                                          BlockPos positionB, int nativeIdB) {
        return NativeApiReadings.readVoltageBetween(level, positionA, nativeIdA, positionB, nativeIdB);
    }
}
