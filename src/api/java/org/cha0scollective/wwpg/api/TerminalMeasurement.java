package org.cha0scollective.wwpg.api;

import java.util.Objects;
import java.util.OptionalDouble;

/** Read-only terminal sample. Invalid samples have no voltage, including during loading. */
public record TerminalMeasurement(MeasurementStatus status, OptionalDouble voltageVolts, String detail) {
    public TerminalMeasurement {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(voltageVolts, "voltageVolts");
        ApiValidation.detail(detail);
        if ((status == MeasurementStatus.VALID) != voltageVolts.isPresent())
            throw new IllegalArgumentException("only VALID terminal measurements contain voltage");
        voltageVolts.ifPresent(value -> ApiValidation.finite(value, "terminal voltageVolts"));
    }
}
