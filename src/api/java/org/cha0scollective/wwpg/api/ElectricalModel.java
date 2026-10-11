package org.cha0scollective.wwpg.api;

/**
 * Captured electrical parameters supported by API 1.0. Models contain no world
 * callbacks or solver handles. Creation of a new model record is not an electrical
 * timestep. See the package contract for voltage, current, and power orientation.
 */
public sealed interface ElectricalModel permits ElectricalModel.Resistor,
        ElectricalModel.Switch, ElectricalModel.VoltageSource, ElectricalModel.CurrentSource,
        ElectricalModel.AcVoltageSource, ElectricalModel.Capacitor, ElectricalModel.Inductor,
        ElectricalModel.Diode, ElectricalModel.Transformer, ElectricalModel.ControlledVoltageSource,
        ElectricalModel.ControlledCurrentSource {

    /** Passive resistor with strictly positive resistance in ohms. */
    record Resistor(double resistanceOhms) implements ElectricalModel {
        public Resistor { ApiValidation.positive(resistanceOhms, "resistanceOhms"); }
    }

    /** Open circuit when closed is false; otherwise a resistor of onResistanceOhms. */
    record Switch(boolean closed, double onResistanceOhms) implements ElectricalModel {
        public Switch { ApiValidation.positive(onResistanceOhms, "onResistanceOhms"); }
    }

    /** Ideal DC source imposing Vpositive minus Vnegative in volts. */
    record VoltageSource(double voltageVolts) implements ElectricalModel {
        public VoltageSource { ApiValidation.finite(voltageVolts, "voltageVolts"); }
    }

    /** Ideal DC source carrying currentAmps from positive node to negative node. */
    record CurrentSource(double currentAmps) implements ElectricalModel {
        public CurrentSource { ApiValidation.finite(currentAmps, "currentAmps"); }
    }

    /**
     * Ideal sinusoidal voltage source: offset + peak*sin(2*pi*frequency*t + phase).
     * t is absolute simulated seconds at the end of each electrical substep.
     * Amplitude is peak volts, frequency is hertz, and phase is radians.
     */
    record AcVoltageSource(double peakVoltageVolts, double frequencyHertz,
                           double phaseRadians, double offsetVoltageVolts) implements ElectricalModel {
        public AcVoltageSource {
            ApiValidation.nonnegative(peakVoltageVolts, "peakVoltageVolts");
            ApiValidation.nonnegative(frequencyHertz, "frequencyHertz");
            ApiValidation.finite(phaseRadians, "phaseRadians");
            ApiValidation.finite(offsetVoltageVolts, "offsetVoltageVolts");
        }
    }

    /**
     * Ideal capacitor integrated by backward Euler, without implicit leakage.
     * initialVoltageVolts initializes reconstructed state only, in element orientation.
     */
    record Capacitor(double capacitanceFarads, double initialVoltageVolts) implements ElectricalModel {
        public Capacitor {
            ApiValidation.positive(capacitanceFarads, "capacitanceFarads");
            ApiValidation.finite(initialVoltageVolts, "initialVoltageVolts");
        }
    }

    /**
     * Ideal inductor integrated by backward Euler, without implicit series resistance.
     * initialCurrentAmps initializes reconstructed state only, from positive to negative.
     */
    record Inductor(double inductanceHenries, double initialCurrentAmps) implements ElectricalModel {
        public Inductor {
            ApiValidation.positive(inductanceHenries, "inductanceHenries");
            ApiValidation.finite(initialCurrentAmps, "initialCurrentAmps");
        }
    }

    /**
     * PG-native silicon PN-junction model, with temperature compensation and series
     * resistance. Positive node is the anode. This is not an ideal diode or a bare
     * Shockley equation. API 1.0 does not enable optional avalanche breakdown.
     */
    record Diode(double saturationCurrentAmps, double seriesResistanceOhms,
                 double temperatureCelsius, double idealityFactor) implements ElectricalModel {
        public Diode {
            ApiValidation.finite(saturationCurrentAmps, "saturationCurrentAmps");
            if (saturationCurrentAmps < 1e-30)
                throw new IllegalArgumentException("saturationCurrentAmps must be at least 1e-30");
            ApiValidation.positive(seriesResistanceOhms, "seriesResistanceOhms");
            ApiValidation.finite(temperatureCelsius, "temperatureCelsius");
            if (temperatureCelsius <= -273.15)
                throw new IllegalArgumentException("temperatureCelsius must be above absolute zero");
            ApiValidation.positive(idealityFactor, "idealityFactor");
        }
    }

    /**
     * Ideal transformer. Element endpoints form the primary winding. The secondary
     * endpoints use the same local node namespace. ratio = Vsecondary/Vprimary;
     * negative ratio reverses winding polarity. Primary and secondary current are
     * both oriented from their positive to negative endpoints and satisfy
     * Iprimary = -ratio*Isecondary. The primary current is the element result.
     */
    record Transformer(String secondaryPositiveNode, String secondaryNegativeNode,
                       double ratio) implements ElectricalModel {
        public Transformer {
            ApiValidation.key(secondaryPositiveNode, "secondaryPositiveNode");
            ApiValidation.key(secondaryNegativeNode, "secondaryNegativeNode");
            if (secondaryPositiveNode.equals(secondaryNegativeNode))
                throw new IllegalArgumentException("transformer secondary nodes must differ");
            ApiValidation.finite(ratio, "transformer ratio");
            if (Math.abs(ratio) < 1e-12 || Math.abs(ratio) > 1e12)
                throw new IllegalArgumentException("transformer ratio magnitude must be 1e-12..1e12");
        }
    }

    /** Ideal voltage source imposing gain*(VcontrolPositive - VcontrolNegative). */
    record ControlledVoltageSource(String controlPositiveNode, String controlNegativeNode,
                                   double gain) implements ElectricalModel {
        public ControlledVoltageSource {
            ApiValidation.key(controlPositiveNode, "controlPositiveNode");
            ApiValidation.key(controlNegativeNode, "controlNegativeNode");
            ApiValidation.finite(gain, "gain");
        }
    }

    /** Ideal current source carrying transconductanceSiemens*(VcontrolPositive - VcontrolNegative). */
    record ControlledCurrentSource(String controlPositiveNode, String controlNegativeNode,
                                   double transconductanceSiemens) implements ElectricalModel {
        public ControlledCurrentSource {
            ApiValidation.key(controlPositiveNode, "controlPositiveNode");
            ApiValidation.key(controlNegativeNode, "controlNegativeNode");
            ApiValidation.finite(transconductanceSiemens, "transconductanceSiemens");
        }
    }
}
