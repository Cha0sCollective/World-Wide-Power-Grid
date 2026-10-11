package org.cha0scollective.wwpg.api;

import java.util.Objects;
import java.util.Optional;

/** Accepted terminal or signed differential voltage samples over one electrical tick. */
public record VoltageMeasurement(TickContext context, MeasurementStatus status, Optional<NodeResult> voltage, String detail) {
    public VoltageMeasurement {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(voltage, "voltage");
        ApiValidation.detail(detail);
        if ((status == MeasurementStatus.VALID) != voltage.isPresent())
            throw new IllegalArgumentException("Only VALID voltage measurements may contain numeric readings");
    }

    public static VoltageMeasurement unavailable(TickContext context, MeasurementStatus status, String detail) {
        if (status == MeasurementStatus.VALID) throw new IllegalArgumentException("Unavailable voltage status cannot be VALID");
        return new VoltageMeasurement(context, status, Optional.empty(), detail);
    }
}
