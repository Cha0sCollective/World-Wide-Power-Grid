package org.cha0scollective.wwpg.bridge;

import com.george_vi.electroenergetics.foundation.electrical_properties.*;
import com.george_vi.electroenergetics.content.rotor.ThreePhaseAlternatorBrushesDevice.PhaseWindingProperties;
import com.george_vi.electroenergetics.content.rotor.VirtualRotor;
import com.google.common.util.concurrent.AtomicDouble;
import java.util.concurrent.atomic.AtomicInteger;
import org.cha0scollective.wwpg.mixin.CeePhaseHistoryAccessor;
import org.cha0scollective.wwpg.mixin.CeeRotorHistoryAccessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeCeeCheckpointTest {
    @Test void rejectedFrameRestoresCapacitorIncludingItsStamp() {
        var capacitor = new CapacitorProperties();
        capacitor.capacitance = .01;
        capacitor.lastVoltage = 7;
        capacitor.resistance = 12;
        capacitor.currentSource = 3;
        capacitor.voltageSource = 4;
        var reject = NativeCeeCheckpoint.capture(capacitor);
        var voltage = new double[32];
        voltage[0] = 20;
        capacitor.tick(voltage, 0, 16, 0, 1);
        capacitor.afterTick(voltage, 0, 1, 0, 16);
        assertEquals(20, capacitor.lastVoltage);
        reject.run();
        assertEquals(7, capacitor.lastVoltage);
        assertEquals(12, capacitor.resistance);
        assertEquals(3, capacitor.currentSource);
        assertEquals(4, capacitor.voltageSource);
        assertEquals(.01, capacitor.capacitance);
        // Restoration is idempotent; retries cannot advance accepted history.
        reject.run();
        assertEquals(7, capacitor.lastVoltage);
    }

    @Test void rejectedFrameRestoresInductorAndDiodeHistory() {
        var inductor = new InductorProperties();
        inductor.lastCurrent = 2;
        inductor.inductance = 1;
        var diode = new DiodeProperties();
        diode.lastVoltage = .3;
        var rejectInductor = NativeCeeCheckpoint.capture(inductor);
        var rejectDiode = NativeCeeCheckpoint.capture(diode);
        var voltage = new double[32]; voltage[0] = 20;
        inductor.afterTick(voltage, 0, 1, 0, 16);
        diode.afterTick(voltage, 0, 1, 0, 16);
        assertNotEquals(2, inductor.lastCurrent);
        assertNotEquals(.3, diode.lastVoltage);
        rejectInductor.run(); rejectDiode.run();
        assertEquals(2, inductor.lastCurrent);
        assertEquals(.3, diode.lastVoltage);
    }

    @Test void rejectedFrameRestoresNativeAccumulatorCharge() {
        var accumulator = new AccumulatorProperties();
        accumulator.storedCharge = 123;
        var reject = NativeCeeCheckpoint.capture(accumulator);
        accumulator.storedCharge = 100;
        reject.run();
        assertEquals(123, accumulator.storedCharge);
    }

    @Test void windingCheckpointsRestoreSharedRotorAndEnergy() {
        var rotor = new TestRotor();
        rotor.angle = 30; rotor.actualOmega = 2;
        rotor.wwpg$angleError(.5); rotor.wwpg$torqueAccumulated(3);
        var energy = new AtomicDouble(100);
        var work = new AtomicInteger(0);
        var phaseA = new TestPhase(0, energy, work, rotor);
        var phaseB = new TestPhase(120, energy, work, rotor);
        phaseA.targetVoltage = phaseB.targetVoltage = 20;
        // All phases must capture before any shared-state mutation.
        var rejectA = NativeCeeCheckpoint.capture(phaseA);
        var rejectB = NativeCeeCheckpoint.capture(phaseB);
        phaseA.tick(new double[32], 0, 16, 0, 1);
        phaseB.tick(new double[32], 0, 16, 0, 1);
        assertEquals(32, rotor.angle);
        assertEquals(2, work.get());
        energy.set(50); rotor.actualOmega = 8;
        rotor.wwpg$angleError(2); rotor.wwpg$torqueAccumulated(9);
        rejectA.run(); rejectB.run();
        assertEquals(30, rotor.angle);
        assertEquals(2, rotor.actualOmega);
        assertEquals(.5, rotor.wwpg$angleError());
        assertEquals(3, rotor.wwpg$torqueAccumulated());
        assertEquals(100, energy.get());
        assertEquals(0, work.get());
        assertEquals(0, phaseA.v);
        assertEquals(0, phaseB.v);
    }

    // A unit JVM does not apply Mixins; these implement the same numerical access.
    private static final class TestRotor extends VirtualRotor implements CeeRotorHistoryAccessor {
        private double torque, error;
        public double wwpg$torqueAccumulated() { return torque; }
        public void wwpg$torqueAccumulated(double value) { torque = value; }
        public double wwpg$angleError() { return error; }
        public void wwpg$angleError(double value) { error = value; }
    }
    private static final class TestPhase extends PhaseWindingProperties implements CeePhaseHistoryAccessor {
        private final AtomicDouble energy;
        private final AtomicInteger work;
        private final VirtualRotor rotor;
        TestPhase(float offset, AtomicDouble energy, AtomicInteger work, VirtualRotor rotor) {
            super(offset, energy, work, rotor);
            this.energy = energy; this.work = work; this.rotor = rotor;
        }
        public AtomicDouble wwpg$storedEnergy() { return energy; }
        public AtomicInteger wwpg$workCounter() { return work; }
        public VirtualRotor wwpg$virtualRotor() { return rotor; }
    }
}
