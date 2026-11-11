package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.content.rotor.ThreePhaseAlternatorBrushesDevice.PhaseWindingProperties;
import com.george_vi.electroenergetics.foundation.electrical_properties.*;
import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import org.cha0scollective.wwpg.mixin.CeePhaseHistoryAccessor;
import org.cha0scollective.wwpg.mixin.CeeRotorHistoryAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Captures the pinned built-in CEE models' callback-mutated numerical history. */
final class NativeCeeCheckpoint {
    private NativeCeeCheckpoint() {}

    static Runnable capture(MicroTickingElectricalProperties properties) {
        return new Frame().capture(properties)::restore;
    }

    /** A frame owns shared history until every participating winding has finished. */
    static final class Frame {
        private final Map<Object, Group> rotors = new IdentityHashMap<>();
        Snapshot capture(MicroTickingElectricalProperties properties) {
            Group group = properties instanceof PhaseWindingProperties winding
                    ? rotors.computeIfAbsent(((CeePhaseHistoryAccessor)winding).wwpg$virtualRotor(), key -> new Group())
                    : new Group();
            return new Snapshot(group, NativeCeeCheckpoint.capture(properties, group));
        }
    }
    static final class Snapshot {
        private final Group group;
        private final Runnable local;
        Snapshot(Group group, Runnable local) { this.group=group; this.local=local; }
        void hold(boolean failure) { group.held=true; group.failed|=failure; }
        boolean held() { return group.held; }
        boolean failed() { return group.failed; }
        void restore() { group.restore(); local.run(); }
    }
    private static final class Group {
        final Map<Object,Runnable> shared = new IdentityHashMap<>();
        boolean held, failed, restored;
        void capture(Object owner, Supplier<Runnable> checkpoint) { shared.computeIfAbsent(owner, key -> checkpoint.get()); }
        void restore() { if(!restored) { shared.values().forEach(Runnable::run); restored=true; } }
    }

    private static Runnable capture(MicroTickingElectricalProperties properties, Group group) {
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
            double v = winding.v;
            group.capture(rotor, () -> {
                double angle=rotor.angle, omega=rotor.actualOmega;
                double torque=rotorAccess.wwpg$torqueAccumulated(), error=rotorAccess.wwpg$angleError();
                return () -> { rotor.angle=angle;rotor.actualOmega=omega;
                    rotorAccess.wwpg$torqueAccumulated(torque);rotorAccess.wwpg$angleError(error); };
            });
            group.capture(access.wwpg$storedEnergy(), () -> {
                var energy=access.wwpg$storedEnergy();double saved=energy.get();return () -> energy.set(saved);
            });
            group.capture(access.wwpg$workCounter(), () -> {
                var work=access.wwpg$workCounter();int saved=work.get();return () -> work.set(saved);
            });
            history = () -> winding.v=v;
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
