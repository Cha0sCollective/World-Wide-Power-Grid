package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.cha0scollective.wwpg.bridge.PropertyCurrentSourceWire;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;
import org.patryk3211.powergrid.electricity.sim.special.CRSeriesWire;
import org.patryk3211.powergrid.electricity.sim.special.SamplingWire;
import org.patryk3211.powergrid.electricity.sim.special.TransmissionLine;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEndpointType;

/** Applied-mixin and powered-circuit checks; the launcher selects the actual backend. */
@GameTestHolder("wwpg_wire_power")
@PrefixGameTestTemplate(false)
public final class PlainWirePowerGameTests {
    @BeforeBatch(batch = "wwpg_wire_power_core")
    public static void configureCore(ServerLevel level) { DynamicGameTests.substeps(level); }

    @BeforeBatch(batch = "wwpg_wire_power_world")
    public static void configureWorld(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template = "empty", batch = "wwpg_wire_power_core", timeoutTicks = 40)
    public static void appliedMixinUsesOneDifferentialAndPreservesFallbacks(GameTestHelper h) {
        var network = new ElectricalNetwork(false);
        network.switchBackend(ModdedConfigs.server().electricity.solver.solverBackend.get());
        var world = GlobalElectricNetworks.getWorldNetworks((LevelAccessor) h.getLevel());
        try {
            for (boolean line : new boolean[]{false, true}) {
                for (int grounding = 0; grounding < (line ? 1 : 3); grounding++) {
                    for (boolean attached : new boolean[]{false, true}) {
                        for (double voltage : new double[]{10, -10, 0.0, -0.0, Double.MIN_VALUE,
                                1e150, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                            for (double resistance : new double[]{4, Double.MIN_VALUE, Double.MAX_VALUE}) {
                                var first = grounding == 2 ? null : new CountingNode(voltage);
                                var second = grounding == 1 ? null : new CountingNode(grounding == 2 ? voltage : -0.0);
                                var referenceFirst = grounding == 2 ? null : new CountingNode(voltage);
                                var referenceSecond = grounding == 1 ? null : new CountingNode(grounding == 2 ? voltage : -0.0);
                                ElectricWire actual = line
                                        ? new TransmissionLine(resistance, new TestEndpoint(first), new TestEndpoint(second), world)
                                        : new ElectricWire(resistance, first, second);
                                var reference = new ReferenceWire(resistance, referenceFirst, referenceSecond);
                                if (attached) { actual.setNetwork(network); reference.setNetwork(network); }
                                sameBits(h, reference.internalPower(), actual.internalPower(), "Applied plain/line power");
                                int expectedReads = !attached ? 1 : Double.isFinite(actual.conductance()) ? 1 : 2;
                                h.assertTrue((first == null || first.reads == expectedReads) && (second == null || second.reads == expectedReads),
                                        "Applied plain/line mixin did not retain its one-read or fallback path");
                                h.assertTrue((referenceFirst == null || referenceFirst.reads == (attached ? 2 : 1))
                                                && (referenceSecond == null || referenceSecond.reads == (attached ? 2 : 1)),
                                        "Native reference was optimized unexpectedly");
                            }
                        }
                    }
                }
            }
            var first = new CountingNode(-10); var second = new CountingNode(0);
            var switched = new SwitchedWire(4, first, second, false); switched.setNetwork(network);
            sameBits(h, 0.0 * -10, switched.internalPower(), "Off switch kept suppressed current");
            var custom = new ElectricWire(4, first, second) { @Override public double current() { return 7; } };
            custom.setNetwork(network);
            sameBits(h, -70, custom.internalPower(), "Unknown subclass kept virtual current");
            var reactive = new CRSeriesWire(.002, 1, first, second); reactive.setNetwork(network);
            sameBits(h, reactive.current() * reactive.potentialDifference(), reactive.internalPower(), "Reactive virtual current");
            var sampling = new SamplingWire(4, first, second); sampling.setNetwork(network); sampling.prepare(16);
            first.reads = second.reads = 0;
            for (int step = 0; step < 16; step++) sampling.postMicroTick();
            sameBits(h, 25, sampling.power(), "Sampling wire kept native power aggregation");
            for (double value : sampling.samples) sameBits(h, -10, value, "Sampling wire retained its waveform");
            h.assertTrue(first.reads == 48 && second.reads == 48, "Sampling subclass skipped native power or waveform reads");
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template = "empty", batch = "wwpg_wire_power_core", timeoutTicks = 40)
    public static void appliedPropertyPowerPreservesValuesReadsAndAccumulation(GameTestHelper h) {
        var network = new ElectricalNetwork(false);
        try {
            for (boolean attached : new boolean[]{false, true}) {
                for (double voltage : new double[]{10, -10, 0.0, -0.0, Double.MIN_VALUE, 1e-308,
                        1e150, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                    for (double conductance : new double[]{.25, 0.0, -0.0, Double.MIN_VALUE,
                            1e150, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                        var first = new CountingNode(voltage); var expectedFirst = new CountingNode(voltage);
                        var actual = new PropertyCurrentSourceWire(first, null, conductance);
                        var reference = new CurrentSourceWire(expectedFirst, null, conductance);
                        if (attached) { actual.setNetwork(network); reference.setNetwork(network); }
                        for (double sourceCurrent : new double[]{100, 0, -100}) {
                            actual.setCurrent(sourceCurrent); reference.setCurrent(sourceCurrent);
                            first.reads = expectedFirst.reads = 0;
                            sameBits(h, reference.internalPower(), actual.internalPower(), "Applied property power");
                            int expectedReads = !attached ? 1 : Double.isFinite(conductance) && conductance > 0 ? 1 : 2;
                            h.assertTrue(first.reads == expectedReads, "Property fast path or exceptional fallback changed");
                            h.assertTrue(expectedFirst.reads == (attached ? 2 : 1), "Property reference callbacks changed");
                        }
                    }
                }
            }
            var first = new CountingNode(0); var second = new CountingNode(0);
            var actual = new PropertyCurrentSourceWire(first, second, .25);
            var reference = new CurrentSourceWire(first, second, .25);
            actual.setNetwork(network); reference.setNetwork(network);
            for (int count : new int[]{1, 3, 16, 32}) {
                actual.prepare(count); reference.prepare(count);
                for (int step = 0; step < count; step++) {
                    first.value = step % 2 == 0 ? step : -step;
                    second.value = step % 3;
                    actual.setConductance(step % 4 == 0 ? .5 : .25);
                    reference.setConductance(actual.conductance());
                    actual.setCurrent(step - 8); reference.setCurrent(step - 8);
                    actual.postMicroTick(); reference.postMicroTick();
                    sameBits(h, reference.power(), actual.power(), "Property accumulation order or changing source");
                }
            }
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template = "empty", batch = "wwpg_wire_power_core", timeoutTicks = 40)
    public static void poweredPlainAndLinePowerKeepSixteenSubstepsAndEdits(GameTestHelper h) {
        var network = new ElectricalNetwork(false);
        network.switchBackend(ModdedConfigs.server().electricity.solver.solverBackend.get());
        var world = GlobalElectricNetworks.getWorldNetworks((LevelAccessor) h.getLevel());
        try {
            var first = new OwnedFloatingNode(new BlockWireEndpoint(BlockPos.ZERO, 0));
            var second = new OwnedFloatingNode(new BlockWireEndpoint(BlockPos.ZERO, 1));
            network.addNode(first); network.addNode(second);
            var plain = new ElectricWire(8, first, second);
            var line = new TransmissionLine(12, new TestEndpoint(first), new TestEndpoint(second), world);
            var property = new PropertyCurrentSourceWire(first, second, .25);
            network.addWire(plain); network.addWire(line); network.addWire(property);
            network.addWire(new CurrentSourceWire(second, null, 1000));
            var source = new VoltageSourceCoupling(first, second, 0); network.addNode(source);
            int acceptedFrames = 0;
            for (int frame = 0; frame < 16; frame++) {
                double voltage = switch ((frame / 4) % 4) { case 0 -> 10; case 1 -> -10; case 2 -> 0; default -> 20; };
                double resistance = frame < 8 ? 8 : 16;
                plain.setResistance(resistance); source.setVoltage((float) voltage);
                network.prepare(16);
                boolean accepted = true;
                double plainPower = 0, linePower = 0, propertyPower = 0;
                for (int step = 0; step < 16; step++) {
                    property.setCurrent(step - 8);
                    network.singleTick(); accepted &= network.isConverged();
                    plainPower += nativePower(plain) / 16; linePower += nativePower(line) / 16;
                    propertyPower += nativePower(property) / 16;
                }
                if (!accepted) continue;
                acceptedFrames++;
                BoardComponentGameTests.near(h, first.getVoltage() - second.getVoltage(), voltage, 1e-6, "Powered differential");
                BoardComponentGameTests.near(h, plain.current(), voltage / resistance, 1e-6, "Signed plain current");
                BoardComponentGameTests.near(h, line.current(), voltage / 12, 1e-6, "Signed line current");
                BoardComponentGameTests.near(h, plain.power(), voltage * voltage / resistance, 1e-6, "Plain sixteen-step power");
                BoardComponentGameTests.near(h, line.power(), voltage * voltage / 12, 1e-6, "Line sixteen-step power");
                sameBits(h, plainPower, plain.power(), "Plain native aggregation order");
                sameBits(h, linePower, line.power(), "Line native aggregation order");
                sameBits(h, propertyPower, property.power(), "Powered property aggregation order");
            }
            h.assertTrue(acceptedFrames >= 12, "Powered fixture did not obtain enough accepted frames");
            h.assertTrue(((ElectricalNetworkAccessor) network).wwpg$solver().type()
                            == ModdedConfigs.server().electricity.solver.solverBackend.get(),
                    "Powered fixture ran the wrong actual backend");
            h.succeed();
        } finally { network.cleanup(); }
    }

    @GameTest(template = "empty", batch = "wwpg_wire_power_world", timeoutTicks = 400)
    public static void physicalPlainAndLinePowerAreMaskedWhileLoadingAndResume(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1); var resistor = new BlockPos(4, 2, 1);
        h.setBlock(source.below(), Blocks.STONE); h.setBlock(resistor.below(), Blocks.STONE);
        h.setBlock(source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(resistor, ModdedBlocks.CREATIVE_RESISTOR.get());
        // The return is a CEE physical wire so restoration includes both wiring systems.
        var level = h.getLevel(); var sourcePos = h.absolutePos(source); var loadPos = h.absolutePos(resistor);
        ElectricWire[] plain = {null}; TransmissionLine[] line = {null}; ElectricalNetwork[] network = {null};
        ElectricWire[] infiniteConductanceProbe = {null};
        boolean[] wired = {false}; int[] healthy = {0}, elapsed = {-1}, held = {0};
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(sourcePos)).setValue(10);
            ((org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity) level.getBlockEntity(loadPos)).setValue(1000);
            WiringGameTests.connect(h, sourcePos, 0, loadPos, 0, true);
            WiringGameTests.connect(h, sourcePos, 1, loadPos, 1, false);
            wired[0] = true;
        });
        h.onEachTick(() -> {
            if (!wired[0]) return;
            var reading = ElectricalMeasurements.readVoltageBetween(level, loadPos, 0, loadPos, 1);
            if (elapsed[0] < 0) {
                if (reading.status() != MeasurementStatus.VALID
                        || Math.abs(reading.voltage().orElseThrow().meanVoltageVolts()) < 9) { healthy[0] = 0; return; }
                if (++healthy[0] < 10) return;
                var first = new BlockWireEndpoint(loadPos, 0).getNode(level);
                var second = new BlockWireEndpoint(loadPos, 1).getNode(level);
                network[0] = first.getNetwork();
                var wires = ((ElectricalNetworkAccessor) network[0]).wwpg$wires();
                plain[0] = (ElectricWire) wires.stream().filter(wire -> wire.getClass() == ElectricWire.class
                                && (wire.getNode1() == first && wire.getNode2() == second
                                || wire.getNode1() == second && wire.getNode2() == first)).findFirst().orElseThrow();
                line[0] = (TransmissionLine) wires.stream().filter(wire -> wire.getClass() == TransmissionLine.class
                                && (wire.getNode1() == first || wire.getNode2() == first)).findFirst().orElseThrow();
                sameBits(h, nativePower(plain[0]), plain[0].internalPower(), "Physical plain power");
                sameBits(h, nativePower(line[0]), line[0].internalPower(), "Physical line power");
                // This probe is never stamped or solved: no infinite value reaches a matrix.
                infiniteConductanceProbe[0] = new ElectricWire(Double.MIN_VALUE, new CountingNode(10), null);
                infiniteConductanceProbe[0].setNetwork(network[0]);
                elapsed[0] = 0;
            }
            int step = ++elapsed[0];
            if (step >= 2 && step <= 12 && RestorationCoordinator.paused(network[0])) {
                held[0]++;
                h.assertTrue(reading.status() == MeasurementStatus.NOT_READY, "Paused physical circuit exposed stale measurements");
                for (var wire : new AbstractElectricWire[]{plain[0], line[0], infiniteConductanceProbe[0]}) {
                    h.assertTrue(wire.current() == 0 && wire.potentialDifference() == 0
                                    && wire.internalPower() == 0 && wire.power() == 0,
                            "Plain/line power or exceptional fallback bypassed restoration masking");
                }
            }
            if (step <= 12) RestorationCoordinator.restoring(level, new ChunkPos(sourcePos));
            if (step == 45) {
                h.assertTrue(held[0] > 0, "Fixture never observed an actual loading pause");
                h.assertTrue(reading.status() == MeasurementStatus.VALID && plain[0].internalPower() > .09,
                        "Physical plain power failed to resume");
                h.assertTrue(plain[0].getNetwork() == network[0] && line[0].getNetwork() == network[0],
                        "Restoration replaced a retained physical wire owner");
                ((CreativeSourceBlockEntity) level.getBlockEntity(sourcePos)).setValue(-10);
            }
            if (step == 60) {
                h.assertTrue(reading.status() == MeasurementStatus.VALID, "Reversed source lost accepted readings");
                BoardComponentGameTests.near(h, reading.voltage().orElseThrow().meanVoltageVolts(), -10, .02, "Reversed physical supply");
                BoardComponentGameTests.near(h, plain[0].internalPower(), .1, .0001, "Physical power retained resistive sign");
                sameBits(h, nativePower(line[0]), line[0].internalPower(), "Reversed line power");
                ((CreativeSourceBlockEntity) level.getBlockEntity(sourcePos)).setValue(0);
            }
            if (step == 75) {
                h.assertTrue(reading.status() == MeasurementStatus.VALID, "Zero source lost accepted readings");
                for (var wire : new AbstractElectricWire[]{plain[0], line[0]}) {
                    BoardComponentGameTests.near(h, wire.internalPower(), 0, 1e-9, "Zero source retained instantaneous power");
                    BoardComponentGameTests.near(h, wire.power(), 0, 1e-9, "Zero source retained aggregate power");
                }
                infiniteConductanceProbe[0].setNetwork(null);
                DynamicGameTests.audit(h); h.succeed();
            }
        });
    }

    private static double nativePower(AbstractElectricWire wire) {
        return wire.current() * wire.potentialDifference();
    }

    private static void sameBits(GameTestHelper h, double expected, double actual, String message) {
        h.assertTrue(Double.doubleToRawLongBits(expected) == Double.doubleToRawLongBits(actual), message);
    }

    /** ElectricWire's native superclass formula remains reachable through an unrecognized subclass. */
    private static final class ReferenceWire extends ElectricWire {
        ReferenceWire(double resistance, IElectricNode first, IElectricNode second) { super(resistance, first, second); }
    }

    private static final class CountingNode extends OwnedFloatingNode {
        double value;
        int reads;
        CountingNode(double value) { super(new BlockWireEndpoint(BlockPos.ZERO, 0)); this.value = value; }
        @Override public double getVoltage() { reads++; return value; }
    }

    private static final class TestEndpoint implements IWireEndpoint {
        private final OwnedFloatingNode node;
        TestEndpoint(OwnedFloatingNode node) { this.node = node; }
        @Override public WireEndpointType type() { return WireEndpointType.BLOCK; }
        @Override public void read(CompoundTag tag) { throw new UnsupportedOperationException(); }
        @Override public void write(CompoundTag tag) { throw new UnsupportedOperationException(); }
        @Override public Vec3 getExactPosition(Level level) { return Vec3.ZERO; }
        @Override public OwnedFloatingNode getNode(Level level) { return node; }
    }
}
