package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.content.rotor.ThreePhaseAlternatorBrushesDevice.PhaseWindingProperties;
import com.george_vi.electroenergetics.foundation.electrical_properties.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import org.cha0scollective.wwpg.mixin.CeePhaseHistoryAccessor;
import org.cha0scollective.wwpg.mixin.CeeRotorHistoryAccessor;

/** Captures the pinned built-in CEE models' callback-mutated numerical history. */
final class NativeCeeCheckpoint {
    private NativeCeeCheckpoint() {}

    static Runnable capture(MicroTickingElectricalProperties properties) {
        double resistance = properties.resistance;
        double currentSource = properties.currentSource;
        double voltageSource = properties.voltageSource;
        boolean forced = properties.isForcedVoltageSource;
        Runnable history;
        if (properties instanceof CapacitorProperties capacitor) {
            double voltage = capacitor.lastVoltage;
            history = () -> capacitor.lastVoltage = voltage;
        } else if (properties instanceof InductorProperties inductor) {
            double current = inductor.lastCurrent;
            history = () -> inductor.lastCurrent = current;
        } else if (properties instanceof DiodeProperties diode) {
            double voltage = diode.lastVoltage;
            history = () -> diode.lastVoltage = voltage;
        } else if (properties instanceof AccumulatorProperties accumulator) {
            double charge = accumulator.storedCharge;
            history = () -> accumulator.storedCharge = charge;
        } else if (properties instanceof PhaseWindingProperties winding) {
            var access = (CeePhaseHistoryAccessor) winding;
            var rotor = access.wwpg$virtualRotor();
            var rotorAccess = (CeeRotorHistoryAccessor) rotor;
            double v = winding.v, energy = access.wwpg$storedEnergy().get();
            int work = access.wwpg$workCounter().get();
            double angle = rotor.angle, omega = rotor.actualOmega;
            double torque = rotorAccess.wwpg$torqueAccumulated(), error = rotorAccess.wwpg$angleError();
            history = () -> {
                winding.v = v;
                access.wwpg$storedEnergy().set(energy);
                access.wwpg$workCounter().set(work);
                rotor.angle = angle;
                rotor.actualOmega = omega;
                rotorAccess.wwpg$torqueAccumulated(torque);
                rotorAccess.wwpg$angleError(error);
            };
        } else {
            // Creative AC sources mutate only their native electrical stamp.
            history = () -> {};
        }
        return () -> {
            history.run();
            properties.resistance = resistance;
            properties.currentSource = currentSource;
            properties.voltageSource = voltageSource;
            properties.isForcedVoltageSource = forced;
        };
    }
}
