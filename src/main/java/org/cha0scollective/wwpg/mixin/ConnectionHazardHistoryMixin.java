package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import org.cha0scollective.wwpg.bridge.ContactHazardHistory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Contact history belongs to the wire connection and is never saved. */
@Mixin(value = ConnectionEntry.class, remap = false)
abstract class ConnectionHazardHistoryMixin implements ContactHazardHistory {
    @Unique private double wwpg$lastHazardVoltage = Double.NaN;
    @Unique private long wwpg$lastHazardTick;

    @Override public void wwpg$recordHazard(double voltage, long tick) {
        wwpg$lastHazardVoltage = Double.isFinite(voltage) ? voltage : Double.NaN;
        wwpg$lastHazardTick = tick;
    }

    @Override public double wwpg$initializingHazard(long tick) {
        long elapsed = tick - wwpg$lastHazardTick;
        return elapsed >= 0 && elapsed <= 4 ? wwpg$lastHazardVoltage : Double.NaN;
    }

    @Override public void wwpg$clearHazard() {
        wwpg$lastHazardVoltage = Double.NaN;
    }
}
