package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.foundation.electrical_properties.CapacitorProperties;
import com.george_vi.electroenergetics.foundation.electrical_properties.InductorProperties;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.LinearBranch;
import org.cha0scollective.wwpg.bridge.TransformerStamp;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.nio.file.Files;
import java.nio.file.Path;

/** Analytical references use the pinned equations and explicit circuit values. */
@GameTestHolder("wwpg_stationary")
@PrefixGameTestTemplate(false)
public final class StationaryAccuracyGameTests {
    // PG's configured absolute voltage convergence tolerance applies near zero.
    private static final double ZERO = 1e-6;

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void ceeSourcePgLoadPgWiresMeetPointOnePercent(GameTestHelper h) { mixedDc(h, true, true); }
    @GameTest(template = "empty", timeoutTicks = 90)
    public static void ceeSourcePgLoadCeeWiresMeetPointOnePercent(GameTestHelper h) { mixedDc(h, true, false); }
    @GameTest(template = "empty", timeoutTicks = 90)
    public static void pgSourceCeeLoadPgWiresMeetPointOnePercent(GameTestHelper h) { mixedDc(h, false, true); }
    @GameTest(template = "empty", timeoutTicks = 90)
    public static void pgSourceCeeLoadCeeWiresMeetPointOnePercent(GameTestHelper h) { mixedDc(h, false, false); }

    private static void mixedDc(GameTestHelper h, boolean ceeSource, boolean pgWire) {
        var source = new BlockPos(1, 2, 1); var load = new BlockPos(4, 2, 1);
        StationaryEquipmentGameTests.place(h, source, ceeSource ? CEEBlocks.CREATIVE_BATTERY.get() : ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        StationaryEquipmentGameTests.place(h, load, ceeSource ? ModdedBlocks.CREATIVE_RESISTOR.get() : CEEBlocks.CREATIVE_RESISTOR.get());
        int positive = ceeSource ? 1 : 0;
        h.runAtTickTime(5, () -> {
            supply(h, source, ceeSource, 10); load(h, load, ceeSource, 10);
            StationaryEquipmentGameTests.wire(h, source, positive, load, 0, pgWire);
            StationaryEquipmentGameTests.wire(h, source, 1-positive, load, 1, pgWire);
        });
        h.runAtTickTime(25, () -> {
            dc(h, source, load, ceeSource, pgWire, 10, 10);
            supply(h, source, ceeSource, -10);
        });
        h.runAtTickTime(40, () -> {
            dc(h, source, load, ceeSource, pgWire, -10, 10);
            load(h, load, ceeSource, 100);
        });
        h.runAtTickTime(55, () -> {
            dc(h, source, load, ceeSource, pgWire, -10, 100);
            supply(h, source, ceeSource, 0);
        });
        h.runAtTickTime(70, () -> {
            dc(h, source, load, ceeSource, pgWire, 0, 100);
            DynamicGameTests.audit(h); h.succeed();
        });
    }
    private static void dc(GameTestHelper h, BlockPos source, BlockPos load, boolean ceeSource, boolean pgWire,
                           double supply, double resistance) {
        var absoluteSource = h.absolutePos(source); var absoluteLoad = h.absolutePos(load);
        double series = ceeSource ? .001 : 1e-4;
        for (int port = 0; port < 2; port++) {
            int sourcePort = ceeSource ? 1-port : port;
            if (pgWire) {
                var endpoint = new BlockWireEndpoint(absoluteSource, sourcePort);
                int target = port;
                var wires = IElectric.getAt(h.getLevel(), absoluteSource).getBehaviour(h.getLevel(), absoluteSource,
                        h.getLevel().getBlockState(absoluteSource)).getConnections().get(endpoint);
                series += wires.stream().filter(w -> w.isConnectedTo(absoluteLoad, target)).findFirst().orElseThrow().getResistance();
            } else {
                series += InfrastructureSavedData.load(h.getLevel()).getConnectionData(new InWorldNodeConnection(
                        new InWorldNode(sourcePort, absoluteSource), new InWorldNode(port, absoluteLoad))).getResistance();
            }
        }
        double current = supply/(resistance+series);
        double voltage = new BlockWireEndpoint(absoluteLoad, 0).getNode(h.getLevel()).getVoltage()
                - new BlockWireEndpoint(absoluteLoad, 1).getNode(h.getLevel()).getVoltage();
        relative(h, voltage, current*resistance, .001, "Mixed DC voltage including native source and wire losses");
        var results = InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
        relative(h, results.getCurrentThrough(ceeSource ? absoluteSource : absoluteLoad, 0, 1), current, .001,
                "Mixed DC signed CEE result current");
    }
    private static void supply(GameTestHelper h, BlockPos pos, boolean cee, double voltage) {
        if (cee) StationaryEquipmentGameTests.source(h, pos, voltage);
        else ((CreativeSourceBlockEntity) h.getBlockEntity(pos)).setValue((float) voltage);
    }
    private static void load(GameTestHelper h, BlockPos pos, boolean pg, double resistance) {
        if (pg) ((ResistorBlockEntity) h.getBlockEntity(pos)).setValue((float) resistance);
        else StationaryEquipmentGameTests.resistor(h, pos, resistance);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void dcSourceDividerAndSignedCurrentMeetPointOnePercent(GameTestHelper h) {
        var n = network();
        try {
            var ground = node(n); var supply = node(n); var output = node(n);
            n.addWire(new ElectricWire(.001, ground, null));
            var source = new LinearBranch(n, ground, supply, ElectricalProperties.fromThevenin(1, 10), false);
            n.addWire(new ElectricWire(9, supply, output));
            var firstLoad = new ElectricWire(120, output, ground);
            var secondLoad = new ElectricWire(240, output, ground);
            n.addWire(firstLoad); n.addWire(secondLoad);
            for (double voltage : new double[]{10, -10, 100, .001, 0}) {
                source.update(ElectricalProperties.fromThevenin(1, voltage));
                n.calculate(16);
                // 120 || 240 = 80 ohms; source + series resistance = 10 ohms.
                double current = voltage / 90;
                relative(h, output.getVoltage()-ground.getVoltage(), current*80, .001, "DC divider voltage");
                relative(h, source.current(), current, .001, "CEE source signed current");
                relative(h, firstLoad.current(), current*80/120, .001, "First parallel load current");
                relative(h, secondLoad.current(), current*80/240, .001, "Second parallel load current");
                near(h, firstLoad.current()+secondLoad.current(), source.current(), ZERO, "Kirchhoff current balance");
            }
        } finally { n.cleanup(); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void transformerReflectedImpedanceAndCurrentMeetPointOnePercent(GameTestHelper h) {
        var n = network();
        try {
            var ground = node(n); var supply = node(n); var primary = node(n);
            var secondary = node(n); var secondaryGround = node(n);
            n.addWire(new ElectricWire(.001, ground, null));
            n.addWire(new ElectricWire(.001, secondaryGround, null));
            var source = new VoltageSourceCoupling(supply, ground, 0);
            source.setVoltage(20); n.addNode(source);
            var feed = new ElectricWire(1, supply, primary); n.addWire(feed);
            var load = new ElectricWire(100, secondary, secondaryGround); n.addWire(load);
            var transformer = new TransformerStamp(n, primary, ground, secondary, secondaryGround, 2);
            for (double ratio : new double[]{.25, .5, 1, 2, -2, 10}) {
                transformer.update(ratio); n.calculate(16);
                double reflected = ratio*ratio*100;
                double primaryVoltage = 20*reflected/(1+reflected);
                relative(h, primary.getVoltage()-ground.getVoltage(), primaryVoltage, .001, "Reflected input impedance");
                relative(h, secondary.getVoltage()-secondaryGround.getVoltage(), primaryVoltage/ratio, .001, "Winding polarity and ratio");
                relative(h, transformer.primaryCurrent(), 20/(1+reflected), .001, "Transformer primary current");
                relative(h, transformer.secondaryCurrent(), -primaryVoltage/ratio/100, .001, "Transformer secondary current");
                near(h, transformer.primaryCurrent()+transformer.secondaryCurrent()/ratio, 0, ZERO, "Ampere-turn relationship");
                relative(h, load.current()*(secondary.getVoltage()-secondaryGround.getVoltage()),
                        feed.current()*primaryVoltage, .001, "Ideal transformer power balance");
            }
        } finally { n.cleanup(); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pinnedCapacitorChargeDischargeWindowsMeetOnePercent(GameTestHelper h) {
        var samples = new JsonArray();
        for (int steps : new int[]{1, 2, 16}) {
            var n = network();
            try {
                var ground = node(n); var supply = node(n); var output = node(n);
                n.addWire(new ElectricWire(.001, ground, null));
                var source = new LinearBranch(n, ground, supply, ElectricalProperties.fromThevenin(1, 10), false);
                n.addWire(new ElectricWire(9, supply, output));
                var properties = new CapacitorProperties(); properties.capacitance = .1; properties.lastVoltage = -2;
                properties.tick(new double[2*steps], 0, steps, 0, 1);
                var capacitor = new LinearBranch(n, output, ground, properties, false);
                var values = new double[2*steps];
                for (double voltage : new double[]{10, 0, -10}) {
                    source.update(ElectricalProperties.fromThevenin(1, voltage));
                    double start = properties.lastVoltage;
                    // Backward Euler RC reference: tau = R*C = 10*.1 = 1 second.
                    double decay = 1/(1+.05/steps);
                    for (int tick = 1; tick <= 30; tick++) {
                        n.prepare(steps);
                        int stamp = n.getStamp();
                        for (int step = 0; step < steps; step++) {
                            properties.tick(values, step, steps, 0, 1); capacitor.update(properties);
                            n.singleTick();
                            values[step] = output.getVoltage(); values[steps+step] = ground.getVoltage();
                            properties.afterTick(values, 0, 1, step, steps);
                        }
                        h.assertTrue(n.getStamp()-stamp == steps, "RC advanced outside its declared substeps");
                        double expected = voltage+(start-voltage)*Math.pow(decay, tick*steps);
                        sample(samples, steps, tick, voltage, expected, properties.lastVoltage);
                        relative(h, properties.lastVoltage, expected, .01, "Recorded RC window at " + steps + " substeps, tick " + tick);
                    }
                }
            } finally { n.cleanup(); }
        }
        record("rc", samples);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pinnedInductorChargeDischargeWindowsMeetOnePercent(GameTestHelper h) {
        var samples = new JsonArray();
        for (int steps : new int[]{1, 2, 16}) {
            var n = network();
            try {
                var ground = node(n); var supply = node(n); var output = node(n);
                n.addWire(new ElectricWire(.001, ground, null));
                var source = new LinearBranch(n, ground, supply, ElectricalProperties.fromThevenin(1, 10), false);
                n.addWire(new ElectricWire(9, supply, output));
                var properties = new InductorProperties(); properties.inductance = 1; properties.lastCurrent = -.2;
                properties.tick(new double[2*steps], 0, steps, 0, 1);
                var inductor = new LinearBranch(n, output, ground, properties, false);
                var values = new double[2*steps];
                for (double voltage : new double[]{10, 0, -10}) {
                    source.update(ElectricalProperties.fromThevenin(1, voltage));
                    double start = properties.lastCurrent;
                    // Backward Euler RL reference: tau = L/R = 1/10 second.
                    double decay = 1/(1+.05/steps*10);
                    for (int tick = 1; tick <= 30; tick++) {
                        n.prepare(steps);
                        int stamp = n.getStamp();
                        for (int step = 0; step < steps; step++) {
                            properties.tick(values, step, steps, 0, 1); inductor.update(properties);
                            n.singleTick();
                            values[step] = output.getVoltage(); values[steps+step] = ground.getVoltage();
                            properties.afterTick(values, 0, 1, step, steps);
                        }
                        h.assertTrue(n.getStamp()-stamp == steps, "RL advanced outside its declared substeps");
                        double expected = voltage/10+(start-voltage/10)*Math.pow(decay, tick*steps);
                        sample(samples, steps, tick, voltage, expected, properties.lastCurrent);
                        relative(h, properties.lastCurrent, expected, .01, "Recorded RL window at " + steps + " substeps, tick " + tick);
                    }
                }
            } finally { n.cleanup(); }
        }
        record("rl", samples);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pinnedAcSamplesRmsAndQuarterPhaseMatchAnalyticalReference(GameTestHelper h) {
        var n = network();
        try {
            int steps = 16;
            var ground = node(n); var output = node(n);
            n.addWire(new ElectricWire(.001, ground, null));
            n.addWire(new ElectricWire(9, output, ground));
            var properties = new CreativeBatteryDevice.ACSource(10, 0, 1);
            properties.tick(new double[steps*2], 0, steps, 0, 1);
            var source = new LinearBranch(n, ground, output, properties, false);
            double[] phases = new double[steps];
            double squares = 0;
            for (int step = 0; step < steps; step++) {
                properties.tick(new double[steps*2], step, steps, 0, 1); source.update(properties);
                n.calculate(1);
                phases[step] = output.getVoltage()-ground.getVoltage(); squares += phases[step]*phases[step];
                // CEE 1.1.3's creative AC source emits one cosine cycle per game tick.
                // Its stored float TWO_PI differs slightly from Math.PI. Keep
                // that upstream waveform precision in the near-zero reference.
                relative(h, phases[step], 9*Math.cos(6.283185482025146d*step/steps), .001, "Pinned CEE AC sample " + step);
                relative(h, source.current(), phases[step]/9, .001, "AC signed current " + step);
            }
            relative(h, Math.sqrt(squares/steps), 9/Math.sqrt(2), .001, "AC RMS");
            double correlation = 0;
            for (int step = 0; step < steps; step++) correlation += phases[step]*phases[(step+steps/4)%steps];
            near(h, correlation, 0, .0001, "Quarter-period phase correlation");
        } finally { n.cleanup(); }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void reactiveTransformerSamplesPhaseRmsAndWindingCurrentsMatchDiscreteReference(GameTestHelper h) {
        var n = network();
        try {
            int steps = 16;
            double ratio = 2, resistance = 100, capacitance = .001, dt = .05/steps;
            var ground = node(n); var primary = node(n); var secondary = node(n); var returnNode = node(n);
            n.addWire(new ElectricWire(.001, ground, null));
            n.addWire(new ElectricWire(.001, returnNode, null));
            var sourceProperties = new CreativeBatteryDevice.ACSource(10, 0, 1);
            sourceProperties.tick(new double[steps*2], 0, steps, 0, 1);
            var source = new LinearBranch(n, ground, primary, sourceProperties, false);
            var transformer = new TransformerStamp(n, primary, ground, secondary, returnNode, ratio);
            var resistor = new ElectricWire(resistance, secondary, returnNode); n.addWire(resistor);
            var properties = new CapacitorProperties(); properties.capacitance = capacitance;
            properties.tick(new double[steps*2], 0, steps, 0, 1);
            var capacitor = new LinearBranch(n, secondary, returnNode, properties, false);
            var values = new double[steps*2];
            double theta = 2*Math.PI/steps;
            // Discrete RC admittance: Y = 1/R + C/dt * (1 - exp(-j*theta)).
            double realY = 1/resistance+capacitance/dt*(1-Math.cos(theta));
            double imaginaryY = capacitance/dt*Math.sin(theta);
            double realDenominator = 1+realY/(ratio*ratio), imaginaryDenominator = imaginaryY/(ratio*ratio);
            double denominator = realDenominator*realDenominator+imaginaryDenominator*imaginaryDenominator;
            double realVoltage = 10/ratio*realDenominator/denominator;
            double imaginaryVoltage = -10/ratio*imaginaryDenominator/denominator;
            double realCurrent = realY*realVoltage-imaginaryY*imaginaryVoltage;
            double imaginaryCurrent = realY*imaginaryVoltage+imaginaryY*realVoltage;
            double squares = 0, cosine = 0, sine = 0;
            for (int cycle = 0; cycle < 10; cycle++) {
                n.prepare(steps);
                for (int step = 0; step < steps; step++) {
                    sourceProperties.tick(values, step, steps, 0, 1); source.update(sourceProperties);
                    properties.tick(values, step, steps, 0, 1); capacitor.update(properties);
                    n.singleTick();
                    values[step] = secondary.getVoltage(); values[steps+step] = returnNode.getVoltage();
                    properties.afterTick(values, 0, 1, step, steps);
                    if (cycle != 9) continue;
                    double voltage = values[step]-values[steps+step];
                    double expectedVoltage = realVoltage*Math.cos(theta*step)-imaginaryVoltage*Math.sin(theta*step);
                    double expectedCurrent = realCurrent*Math.cos(theta*step)-imaginaryCurrent*Math.sin(theta*step);
                    relative(h, voltage, expectedVoltage, .001, "Reactive transformer voltage sample " + step);
                    relative(h, transformer.primaryCurrent(), expectedCurrent/ratio, .001, "Reactive primary current sample " + step);
                    relative(h, transformer.secondaryCurrent(), -expectedCurrent, .001, "Reactive secondary current sample " + step);
                    near(h, resistor.current()+capacitor.current()+transformer.secondaryCurrent(), 0, ZERO, "Reactive winding Kirchhoff balance");
                    squares += voltage*voltage; cosine += voltage*Math.cos(theta*step); sine += voltage*Math.sin(theta*step);
                }
            }
            relative(h, Math.sqrt(squares/steps), Math.hypot(realVoltage, imaginaryVoltage)/Math.sqrt(2), .001, "Reactive winding RMS");
            near(h, Math.atan2(-sine, cosine), Math.atan2(imaginaryVoltage, realVoltage), .001, "Reactive reflected-impedance phase");
        } finally { n.cleanup(); }
        h.succeed();
    }

    private static ElectricalNetwork network() {
        var n = new ElectricalNetwork(false);
        n.switchBackend(CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend", "NATIVE")));
        n.warmUp(-1);
        return n;
    }
    private static void sample(JsonArray samples, int steps, int tick, double supply, double expected, double actual) {
        var row = new JsonObject();
        row.addProperty("substeps", steps); row.addProperty("tick", tick);
        row.addProperty("seconds", tick*.05); row.addProperty("supply_voltage", supply);
        row.addProperty("expected", expected); row.addProperty("actual", actual);
        row.addProperty("tolerance", Math.max(ZERO, Math.abs(expected)*.01));
        samples.add(row);
    }
    private static void record(String kind, JsonArray samples) {
        var report = new JsonObject();
        report.addProperty("schema", 1);
        report.addProperty("backend", System.getProperty("wwpg.test.backend", "NATIVE"));
        report.addProperty("model", "CEE 1.1.3 backward Euler " + kind.toUpperCase());
        report.addProperty("resistance_ohms", 10);
        report.addProperty(kind.equals("rc") ? "capacitance_farads" : "inductance_henries", kind.equals("rc") ? .1 : 1);
        report.addProperty("relative_tolerance", .01);
        report.addProperty("near_zero_tolerance", ZERO);
        report.add("samples", samples);
        try { Files.writeString(Path.of("accuracy-" + kind + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
        catch (java.io.IOException e) { throw new IllegalStateException("Could not record electrical accuracy evidence", e); }
    }
    private static FloatingNode node(ElectricalNetwork n) { var node = new FloatingNode(); n.addNode(node); return node; }
    private static void relative(GameTestHelper h, double actual, double expected, double fraction, String label) {
        near(h, actual, expected, Math.max(ZERO, Math.abs(expected)*fraction), label);
    }
    private static void near(GameTestHelper h, double actual, double expected, double tolerance, String label) {
        BoardComponentGameTests.near(h, actual, expected, tolerance, label);
    }
}
