package org.cha0scollective.wwpg.api;

import java.util.Objects;
import java.util.OptionalDouble;

/** Read-only terminal sample. Invalid samples have no voltage, including during loading. */
public record TerminalMeasurement(MeasurementStatus status, OptionalDouble voltageVolts, String detail) {
    /**
     * Constructs TerminalMeasurement with validated captured values.
     * @param status non-null status; VALID exactly when voltageVolts is present
     * @param voltageVolts non-null optional signed volts; present value must be finite, with magnitude at most 1e15 (inclusive)
     * @param detail non-null explanation of at most 1024 characters
     * @throws IllegalArgumentException if a numeric, key, count or structural constraint is violated
     * @throws NullPointerException if a required reference or collection entry is null
     */
    public TerminalMeasurement {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(voltageVolts, "voltageVolts");
        ApiValidation.detail(detail);
        if ((status == MeasurementStatus.VALID) != voltageVolts.isPresent())
            throw new IllegalArgumentException("only VALID terminal measurements contain voltage");
        voltageVolts.ifPresent(value -> ApiValidation.finite(value, "terminal voltageVolts"));
    }
}
