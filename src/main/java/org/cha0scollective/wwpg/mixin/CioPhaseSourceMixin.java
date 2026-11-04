package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import org.cha0scollective.wwpg.api.cee.CeeStateCheckpoint;
import org.cha0scollective.wwpg.api.cee.CeeSubstepResult;
import org.cha0scollective.wwpg.compat.cio.CioPhaseSourceAccess;
import org.spongepowered.asm.mixin.*;

/** Exact private history of CIO 0.1.240-experimental; optional and version gated. */
@Pseudo
@Mixin(targets = "com.cio.createinteroperable.inverter.cee.InverterDevice$PhaseSource", remap = false)
public abstract class CioPhaseSourceMixin extends MicroTickingElectricalProperties implements CioPhaseSourceAccess {
    @Shadow private double startAngle;
    @Shadow private double step;
    @Shadow private double peak;
    @Shadow private double v;
    @Shadow double sumV2;
    @Shadow double sumI2;
    @Shadow double sumVI;
    @Shadow int samples;
    @Shadow private double[] terminal;
    @Unique private double[] wwpg$acceptedVoltages = new double[0];

    @Override public CeeStateCheckpoint wwpg$checkpoint() {
        double angle = startAngle, increment = step, amplitude = peak, voltage = v;
        double squaresV = sumV2, squaresI = sumI2, power = sumVI;
        int count = samples;
        double r = resistance, i = currentSource, emf = voltageSource;
        boolean ideal = isForcedVoltageSource;
        double[] original = terminal, values = terminal.clone();
        return () -> {
            startAngle = angle; step = increment; peak = amplitude; v = voltage;
            sumV2 = squaresV; sumI2 = squaresI; sumVI = power; samples = count;
            resistance = r; currentSource = i; voltageSource = emf; isForcedVoltageSource = ideal;
            terminal = original;
            System.arraycopy(values, 0, original, 0, values.length);
        };
    }

    @Override public void wwpg$accepted(CeeSubstepResult result) {
        int total = result.totalSubsteps(), index = result.substep();
        if (wwpg$acceptedVoltages.length != 2 * total) wwpg$acceptedVoltages = new double[2 * total];
        // Keep CEE's original array convention: afterTick reads second minus first.
        wwpg$acceptedVoltages[index] = result.voltageVolts();
        wwpg$acceptedVoltages[total + index] = 0;
        afterTick(wwpg$acceptedVoltages, 0, 1, index, total);
    }
}
