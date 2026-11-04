package org.cha0scollective.wwpg.compat.cio;

import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import org.cha0scollective.wwpg.api.cee.*;

/** Executes CIO's own equations; only accepted samples advance its measurements. */
public final class CioInverterAdapter implements CeePropertyAdapter<MicroTickingElectricalProperties> {
    // The version-scoped PhaseSource.tick reads only its angle and step arguments.
    private static final double[] NO_VOLTAGES = new double[0];

    @Override public CeeElectricalParameters prepare(MicroTickingElectricalProperties source) {
        return new CeeElectricalParameters(source.conductance(), source.currentSource(), source.voltageSource(), source.isVoltageSource());
    }
    @Override public boolean dynamic() { return true; }
    @Override public CeeStateCheckpoint checkpoint(MicroTickingElectricalProperties source) {
        return ((CioPhaseSourceAccess) source).wwpg$checkpoint();
    }
    @Override public void beforeSubstep(MicroTickingElectricalProperties source, CeeSubstepContext context) {
        source.tick(NO_VOLTAGES, context.substep(), context.totalSubsteps(), 0, 1);
    }
    @Override public void afterSubstep(MicroTickingElectricalProperties source, CeeSubstepResult result) {
        ((CioPhaseSourceAccess) source).wwpg$accepted(result);
    }
}
