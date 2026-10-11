package org.cha0scollective.wwpg.examples.upstream;

import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;

/** Native CEE property with a capacitor and lamp resistance in parallel. */
public final class StorageIndicatorProperties extends MicroTickingElectricalProperties {
    // Packaged acceptance fault injection; never persisted or exposed to players.
    int failAfterSubstep = -1;
    boolean failDuringCapture;
    public static final double CAPACITANCE_FARADS = .001;
    public static final double LOAD_RESISTANCE_OHMS = 1000;
    public double savedVoltage;
    public long acceptedSubsteps;

    void prepareSubstep(double seconds) {
        double capacitorConductance = CAPACITANCE_FARADS / seconds;
        resistance = 1 / (capacitorConductance + 1 / LOAD_RESISTANCE_OHMS);
        currentSource = -capacitorConductance * savedVoltage;
    }

    void acceptVoltage(double voltage) {
        savedVoltage = voltage;
        ++acceptedSubsteps;
    }

    @Override public void tick(double[] voltages, int step, int total, int first, int second) {
        prepareSubstep(.05 / total);
    }

    @Override public void afterTick(double[] voltages, int first, int second, int step, int total) {
        acceptVoltage(voltages[first * total + step] - voltages[second * total + step]);
    }
}
