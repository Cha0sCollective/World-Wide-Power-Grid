package org.cha0scollective.wwpg.compat.cee12.mixin;

import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import org.cha0scollective.wwpg.bridge.ContactHazardHistory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The history belongs to the native connection and disappears when that connection is removed. */
@Mixin(value=ConnectionEntry.class, remap=false)
abstract class ConnectionHazardHistoryMixin implements ContactHazardHistory {
    @Unique private boolean wwpg$hasHazard;
    @Unique private double wwpg$hazardVoltage;
    @Unique private long wwpg$hazardTick;

    @Override public void wwpg$acceptedHazard(double voltage,long tick) {
        wwpg$hasHazard=Double.isFinite(voltage);
        wwpg$hazardVoltage=voltage;
        wwpg$hazardTick=tick;
    }
    @Override public double wwpg$initializingHazard(long tick) {
        long elapsed=tick-wwpg$hazardTick;
        // Two normal 16-substep frames suffice for warm-up; never retain a hazard indefinitely.
        return wwpg$hasHazard&&elapsed>=0&&elapsed<=4?wwpg$hazardVoltage:Double.NaN;
    }
    @Override public void wwpg$clearHazard() { wwpg$hasHazard=false; }
}
