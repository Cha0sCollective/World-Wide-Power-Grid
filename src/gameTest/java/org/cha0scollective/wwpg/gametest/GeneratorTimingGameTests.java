package org.cha0scollective.wwpg.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.BeforeBatch;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.PgGeneratorTiming;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.transformer.TransformerBlock;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.calculation.Precalculated;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.GeneratorCoupling;
import org.patryk3211.powergrid.electricity.sim.special.IRotor;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.kinetics.generator.inductionrotor.CommutatorBlockEntity;
import org.patryk3211.powergrid.kinetics.generator.rotor.RotorBehaviour;
import org.patryk3211.powergrid.kinetics.generator.winding.WindingBlock;
import org.patryk3211.powergrid.kinetics.generator.winding.WindingBlockEntity;

/** A real native assembly: no substituted rotor, altered inductance or voltage regulator. */
@GameTestHolder("wwpg_generator_timing")
@PrefixGameTestTemplate(false)
public final class GeneratorTimingGameTests {
    private static final BlockPos BRUSH = new BlockPos(8, 3, 4);
    private static final BlockPos TRANSFORMER = new BlockPos(8, 1, 2);
    private static final BlockPos FIELD = new BlockPos(2, 2, 6);
    private static final double LOAD = 15.12214000042295;

    @GameTest(template = "empty", batch = "wwpg_generator_timing", timeoutTicks = 2850)
    public static void carbonPilePreservesNativeClutchOperatingPoints(GameTestHelper h) {
        build(h);
        var pilePos = new BlockPos(6, 1, 0);
        var sensorPos = new BlockPos(4, 1, 0);
        h.setBlock(pilePos.below(), Blocks.STONE); h.setBlock(sensorPos.below(), Blocks.STONE);
        h.setBlock(pilePos, ModdedBlocks.CARBON_PILE_COIL.getDefaultState());
        for (int n = 1; n <= 2; n++) h.setBlock(pilePos.above(n), ModdedBlocks.CARBON_PILE.getDefaultState()
                .setValue(org.patryk3211.powergrid.electricity.carbonpile.CarbonPileBlock.TOP, n == 2));
        h.setBlock(sensorPos, ModdedBlocks.RESISTOR.getDefaultState());
        var windows = new Range[] {new Range(), new Range(), new Range(), new Range()};
        ElectricWire[] load = {null};
        h.runAtTickTime(30, () -> {
            configureTransformer(h);
            ((org.patryk3211.powergrid.electricity.carbonpile.CarbonPileCoilBlockEntity) h.getBlockEntity(pilePos)).pileChanged();
            controlSpeed(h, sensorPos, 45); // Native 10,000-ohm sensing resistor.
        });
        h.runAtTickTime(65, () -> {
            connect(h, BRUSH, 0, TRANSFORMER, 3, .0015);
            connect(h, BRUSH, 1, TRANSFORMER, 2, .0015);
            connect(h, TRANSFORMER, 1, FIELD, 0, .0015);
            connect(h, TRANSFORMER, 0, pilePos, 2, .00075);
            connect(h, pilePos, 3, FIELD, 1, .00075);
            connect(h, BRUSH, 0, sensorPos, 0, .0015);
            connect(h, sensorPos, 1, pilePos, 0, .0015);
            connect(h, pilePos, 1, BRUSH, 1, .0015);
            load[0] = connect(h, BRUSH, 0, BRUSH, 1, LOAD);
        });
        h.runAtTickTime(200, () -> speed(h, 255));
        for (int tick = 205; tick <= 2600; tick++) {
            int at = tick;
            h.runAtTickTime(tick, () -> {
                assertSourceEquation(h);
                h.assertTrue(generator(h).getAssemblySource().getNetwork().isConverged(),
                        "Carbon-pile solve rejected at " + at);
                int window = at >= 450 && at <= 600 ? 0 : at >= 850 && at <= 950 ? 1
                        : at >= 1200 && at <= 1350 ? 2 : at >= 2350 ? 3 : -1;
                if (window >= 0) {
                    windows[window].add(terminalVoltage(h), generator(h).getBehaviour(RotorBehaviour.TYPE).getAngularVelocity());
                    var left = (org.patryk3211.powergrid.kinetics.generator.clutch.GeneratorClutchBlockEntity)
                            h.getBlockEntity(new BlockPos(1, 3, 4));
                    var right = (org.patryk3211.powergrid.kinetics.generator.clutch.GeneratorClutchBlockEntity)
                            h.getBlockEntity(new BlockPos(9, 3, 4));
                    if (window != 1) h.assertTrue(left.load > .999 && right.load > .999,
                            "Near-target clutch response at tick " + at + ": " + left.load + ", " + right.load
                                    + "; " + terminalVoltage(h) + " V, " + generator(h).getBehaviour(RotorBehaviour.TYPE).getAngularVelocity() + " RPM");
                    else h.assertTrue(Math.min(left.load, right.load) > .36 && Math.min(left.load, right.load) < .38
                                    && Math.max(left.load, right.load) > .999,
                            "Unsaturated native clutch response at tick " + at + ": " + left.load + ", " + right.load
                                    + "; " + terminalVoltage(h) + " V");
                }
                if (at == 650) load[0].setResistance(LOAD * .75);
                if (at == 1000) load[0].setResistance(LOAD * 1.25);
                if (at == 1400) { load[0].setResistance(LOAD); speed(h, 0); }
                if (at == 1650) speed(h, 255);
            });
        }
        h.runAtTickTime(2620, () -> {
            double[] nativeVolts = {865.2941, 596.7748, 985.0554, 865.2941};
            for (int n = 0; n < windows.length; n++) {
                var w = windows[n];
                h.assertTrue(w.count > 50 && w.maxV - w.minV < .1 && w.maxRpm - w.minRpm < .01,
                        "Carbon-pile operating point did not settle: " + w);
                BoardComponentGameTests.near(h, w.minV, nativeVolts[n], .2, "Native carbon-pile operating point " + n);
            }
            h.assertTrue(org.cha0scollective.wwpg.bridge.Bridges.get(h.getLevel()).diagnostics().isEmpty(),
                    "Carbon-pile sequence recorded electrical diagnostics");
            speed(h, 0); h.setBlock(BRUSH, Blocks.AIR); h.setBlock(TRANSFORMER, Blocks.AIR);
        });
        h.runAtTickTime(2750, h::succeed);
    }

    @BeforeBatch(batch = "wwpg_generator_timing")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @BeforeBatch(batch = "wwpg_generator_reexcitation")
    public static void configureOneStep(ServerLevel level) {
        ModdedConfigs.server().electricity.solver.multiTicks.set(1);
        ModdedConfigs.server().electricity.solver.solverBackend.set(
                CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend", "NATIVE")));
        GlobalElectricNetworks.configsReloaded();
    }

    @GameTest(template = "empty", batch = "wwpg_generator_reexcitation", timeoutTicks = 1850)
    public static void shaftVariacRestoresExcitationAtFullRotorSpeed(GameTestHelper h) {
        variacRestoresExcitation(h, false);
    }

    @GameTest(template = "empty", batch = "wwpg_generator_reexcitation", timeoutTicks = 1850)
    public static void redstoneVariacRestoresExcitationAtFullRotorSpeed(GameTestHelper h) {
        variacRestoresExcitation(h, true);
    }

    private static void variacRestoresExcitation(GameTestHelper h, boolean redstone) {
        build(h);
        var control = new BlockPos(7, 1, 0);
        var motor = control.above();
        h.setBlock(control.below(), Blocks.STONE);
        if (redstone) {
            h.setBlock(control, com.george_vi.electroenergetics.CEEBlocks.REDSTONE_VARIAC.getDefaultState());
            h.setBlock(control.north(), Blocks.REDSTONE_BLOCK);
        } else {
            h.setBlock(control, ModdedBlocks.VARIAC.getDefaultState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                    .setValue(BlockStateProperties.FACING, Direction.DOWN));
            controlSpeed(h, motor, 0);
        }
        double[] initial = {0};
        h.runAtTickTime(30, () -> {
            configureTransformer(h);
            if (redstone) controlSpeed(h, control, 8);
        });
        h.runAtTickTime(65, () -> {
            connect(h, BRUSH, 0, TRANSFORMER, 3, .0015);
            connect(h, BRUSH, 1, TRANSFORMER, 2, .0015);
            connect(h, TRANSFORMER, 1, control, 0, .00075);
            connect(h, control, 2, FIELD, 0, .00075);
            connect(h, TRANSFORMER, 0, control, 1, .00075);
            connect(h, control, 1, FIELD, 1, .00075);
            connect(h, BRUSH, 0, BRUSH, 1, LOAD);
        });
        h.runAtTickTime(200, () -> speed(h, 255));
        h.runAtTickTime(600, () -> {
            initial[0] = terminalVoltage(h);
            h.assertTrue(initial[0] > 700, "Variac excitation never built up");
            if (redstone) h.setBlock(control.north(), Blocks.AIR);
            else controlSpeed(h, motor, 256);
        });
        h.runAtTickTime(700, () -> { if (!redstone) controlSpeed(h, motor, 0); });
        h.runAtTickTime(950, () -> {
            h.assertTrue(Math.abs(terminalVoltage(h)) < 5, "Minimum variac failed to remove excitation");
            h.assertTrue(generator(h).getBehaviour(RotorBehaviour.TYPE).getAngularVelocity() > 250,
                    "Fixture did not restore excitation at full rotor speed");
            if (redstone) h.setBlock(control.north(), Blocks.REDSTONE_BLOCK);
            else controlSpeed(h, motor, -256);
        });
        h.runAtTickTime(1050, () -> { if (!redstone) controlSpeed(h, motor, 0); });
        for (int tick = 205; tick <= 1650; tick++) {
            int at = tick;
            h.runAtTickTime(tick, () -> {
                assertSourceEquation(h);
                // CEE recreates a redstone variac's circuit when power changes.
                // Its ordinary warm-up remains unavailable; the diagnostic
                // assertion below still rejects every actual solve failure.
                if (!redstone || at >= 1400) h.assertTrue(generator(h).getAssemblySource().getNetwork().isConverged(),
                        "Variac recovery rejected a one-step solve at " + at);
                if (at >= 1400) h.assertTrue(Math.abs(terminalVoltage(h) - initial[0]) < .1,
                        "Variac excitation failed to recover its original output");
            });
        }
        h.runAtTickTime(1680, () -> {
            h.assertTrue(org.cha0scollective.wwpg.bridge.Bridges.get(h.getLevel()).diagnostics().isEmpty(),
                    "Variac recovery recorded electrical diagnostics");
            speed(h, 0); if (!redstone) controlSpeed(h, motor, 0);
            h.setBlock(BRUSH, Blocks.AIR); h.setBlock(TRANSFORMER, Blocks.AIR);
        });
        h.runAtTickTime(1780, h::succeed);
    }

    private static double terminalVoltage(GameTestHelper h) {
        var source = generator(h).getAssemblySource();
        return source.getPositive().getVoltage() - source.getNegative().getVoltage();
    }

    private static void assertSourceEquation(GameTestHelper h) {
        var source = generator(h).getAssemblySource();
        // This fixture has six ohms of copper. Native fallback adds its
        // inertia companion; the coupled path must satisfy the source equation
        // directly, including after many tiny matrix coefficient changes.
        if (source.getNetwork().isConverged() && source.getResistance() == 6f)
            BoardComponentGameTests.near(h, terminalVoltage(h) - 6 * source.getCurrent(),
                    source.getVoltage(), 1e-6, "Coupled generator source equation");
    }

    private static void controlSpeed(GameTestHelper h, BlockPos motor, int speed) {
        ((SmartBlockEntity) h.getBlockEntity(motor)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(speed);
    }

    private static void configureTransformer(GameTestHelper h) {
        try {
            var entity = h.getBlockEntity(TRANSFORMER);
            var nbt = entity.saveWithoutMetadata(h.getLevel().registryAccess());
            nbt.merge(TagParser.parseTag("{Primary:{Item:\"powergrid:wire\",Turns:50,Terminal2:2,Terminal1:3},Secondary:{Item:\"powergrid:wire\",Turns:10,Terminal2:0,Terminal1:1}}"));
            entity.loadWithComponents(nbt, h.getLevel().registryAccess());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @GameTest(template = "empty", batch = "wwpg_generator_timing", timeoutTicks = 20)
    public static void directAndCustomRotorsKeepNativeIntegration(GameTestHelper h) {
        for (int steps : new int[] {1, 16}) for (int sign : new int[] {-1, 1}) {
            var network = new ElectricalNetwork(false);
            network.switchBackend(CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend", "NATIVE")));
            network.warmUp(-1);
            try {
                double[] force = {0};
                var rotor = new IRotor() {
                    public float getInertia() { return 10; }
                    public float getAngularVelocity() { return sign * 64; }
                    public void applyTickForce(float value) { force[0] += value; }
                };
                var field = new Precalculated<Float>(2f) {
                    public Float get() { return value; }
                    public int getStamp() { return ourStamp; }
                    public void invalidate() { }
                };
                var positive = new FloatingNode(); var negative = new FloatingNode();
                network.addNodes(positive, negative);
                network.addWire(new ElectricWire(1, negative, null));
                network.addWire(new ElectricWire(10, positive, negative));
                var source = new GeneratorCoupling(positive, negative, 3, rotor);
                source.setFieldStrengthProvider(field);
                network.addNode(source);
                int accepted = 0;
                for (int tick = 0; tick < 100; tick++) {
                    network.prepare(steps);
                    double expected = 0; force[0] = 0;
                    for (int step = 0; step < steps; step++) {
                        network.singleTick();
                        if (!network.isConverged()) continue;
                        accepted++;
                        expected += (float) (2 * source.getCurrent() / steps);
                        h.assertTrue(!((PgGeneratorTiming.Source) source).wwpg$coupling().managed(),
                                "Direct/custom execution entered a server rotor scope");
                        BoardComponentGameTests.near(h, source.getResistance(),
                                (float) (3 + 4 * network.getDeltaTime() / 10), 1e-6, "Native inertia companion");
                    }
                    BoardComponentGameTests.near(h, force[0], expected, 1e-6, "Native accepted torque count");
                }
                h.assertTrue(accepted > 95 * steps, "Direct generator did not establish accepted solves");
                BoardComponentGameTests.near(h, positive.getVoltage() - negative.getVoltage(),
                        2 * rotor.getAngularVelocityRadians() * 10 / 13, .001, "Native direct generator output");
            } finally { network.cleanup(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wwpg_generator_timing", timeoutTicks = 2600)
    public static void transformerFedSelfExcitationSettlesAndRecovers(GameTestHelper h) throws Exception {
        build(h);
        ElectricWire[] load = {null};
        var windows = new Range[] {new Range(), new Range(), new Range(), new Range()};
        h.runAtTickTime(30, () -> configureTransformer(h));
        h.runAtTickTime(65, () -> {
            connect(h, BRUSH, 0, TRANSFORMER, 3, .0015);
            connect(h, BRUSH, 1, TRANSFORMER, 2, .0015);
            connect(h, TRANSFORMER, 0, FIELD, 1, .0015);
            connect(h, TRANSFORMER, 1, FIELD, 0, .0015);
            load[0] = connect(h, BRUSH, 0, BRUSH, 1, LOAD);
        });
        h.runAtTickTime(200, () -> {
            var generator = generator(h);
            h.assertTrue(generator.getAssemblySource() instanceof PgGeneratorTiming.Source,
                    "Packaged generator timing integration is absent");
            h.assertTrue(Math.abs(generator.getBehaviour(RotorBehaviour.TYPE).getInertia() - 12.3) < .001,
                    "Incorrect six-rotor fixture inertia");
            speed(h, 255);
        });
        for (int tick = 205; tick <= 2450; tick++) {
            int at = tick;
            h.runAtTickTime(tick, () -> {
                var source = generator(h).getAssemblySource();
                assertSourceEquation(h);
                h.assertTrue(source.getNetwork().isConverged(), "Self-excited solve rejected at tick " + at);
                double volts = source.getPositive().getVoltage() - source.getNegative().getVoltage();
                double rpm = generator(h).getBehaviour(RotorBehaviour.TYPE).getAngularVelocity();
                h.assertTrue(Double.isFinite(volts) && Double.isFinite(rpm), "Nonfinite generator result");
                if (at >= 450 && at <= 600) windows[0].add(volts, rpm);
                if (at >= 850 && at <= 950) windows[1].add(volts, rpm);
                if (at >= 1200 && at <= 1350) windows[2].add(volts, rpm);
                if (at >= 2250) windows[3].add(volts, rpm);
                if (at == 650) load[0].setResistance(LOAD * .75);
                if (at == 1000) load[0].setResistance(LOAD * 1.25);
                if (at == 1400) { load[0].setResistance(LOAD); speed(h, 0); }
                if (at == 1600) speed(h, 255);
            });
        }
        h.runAtTickTime(2470, () -> {
            for (var range : windows) {
                h.assertTrue(range.count > 50 && range.minV > 100, "Excitation did not establish useful output");
                h.assertTrue(range.maxV - range.minV < .1 && range.maxRpm - range.minRpm < .01,
                        "Self-excited generator is still hunting: " + range);
            }
            h.assertTrue(windows[1].maxV < windows[0].minV && windows[2].minV > windows[0].maxV,
                    "Generator no longer responds to load");
            h.assertTrue(Math.abs(windows[3].minV - windows[0].minV) < .1,
                    "Generator failed to recover after drive stop/restart");
            h.assertTrue(((WindingBlockEntity) h.getBlockEntity(FIELD)).windingCurrent() > .05,
                    "Stable voltage was obtained without real winding excitation");
            speed(h, 0);
            h.setBlock(BRUSH, Blocks.AIR);
            h.setBlock(TRANSFORMER, Blocks.AIR);
        });
        h.runAtTickTime(2550, h::succeed);
    }

    private static void build(GameTestHelper h) {
        for (int x = 0; x <= 10; x++) for (int z = 1; z <= 7; z++)
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        for (int x = 2; x <= 7; x++) {
            h.setBlock(new BlockPos(x, 3, 4), ModdedBlocks.GENERATOR_LARGE_INDUCTION_ROTOR.getDefaultState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.X));
            // Twelve winding strips around the native large rotor's perimeter.
            for (int side = -2; side <= 2; side += 4) for (int offset = -1; offset <= 1; offset++) {
                winding(h, x, 3 + side, 4 + offset, true);
                winding(h, x, 3 + offset, 4 + side, false);
            }
            // The captured rig links the four winding faces through three
            // housing corners. Without these only one 18-coil face is powered.
            housing(h, x, 1, 2, Direction.SOUTH, true);
            housing(h, x, 5, 2, Direction.SOUTH, false);
            housing(h, x, 5, 6, Direction.NORTH, false);
        }
        h.setBlock(BRUSH, ModdedBlocks.GENERATOR_COMMUTATOR.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        for (int x : new int[] {1, 9}) {
            var facing = x == 1 ? Direction.WEST : Direction.EAST;
            h.setBlock(new BlockPos(x, 3, 4), ModdedBlocks.GENERATOR_CLUTCH.getDefaultState()
                    .setValue(BlockStateProperties.FACING, facing));
            h.setBlock(new BlockPos(x == 1 ? 0 : 10, 3, 4), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                    .setValue(BlockStateProperties.FACING, facing.getOpposite()));
        }
        h.setBlock(TRANSFORMER, ModdedBlocks.TRANSFORMER_SMALL.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z).setValue(TransformerBlock.COILS, 2));
        speed(h, 0);
        h.runAtTickTime(190, () -> assertFullFieldAssembly(h));
    }

    private static void housing(GameTestHelper h, int x, int y, int z, Direction facing, boolean up) {
        h.setBlock(new BlockPos(x, y, z), ModdedBlocks.GENERATOR_HOUSING.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing).setValue(BlockStateProperties.UP, up));
    }

    private static void assertFullFieldAssembly(GameTestHelper h) {
        try {
            var holderMethod = WindingBlockEntity.class.getDeclaredMethod("getSimElementHolder");
            holderMethod.setAccessible(true);
            var holder = holderMethod.invoke(h.getBlockEntity(FIELD));
            var coilField = WindingBlockEntity.class.getDeclaredField("coilWire");
            coilField.setAccessible(true);
            var wire = (org.patryk3211.powergrid.electricity.sim.special.LRSeriesWire) coilField.get(holder);
            h.assertTrue(wire != null, "Field assembly has no native winding wire");
            var resistance = wire.getClass().getDeclaredField("resistance"); resistance.setAccessible(true);
            var inductance = wire.getClass().getDeclaredField("inductance"); inductance.setAccessible(true);
            BoardComponentGameTests.near(h, resistance.getDouble(wire), 540, 1e-6, "Connected 72-coil field resistance");
            BoardComponentGameTests.near(h, inductance.getDouble(wire), 5.4, 1e-6, "Connected field inductance");
            h.assertTrue(wire.getNetwork() == generator(h).getAssemblySource().getNetwork(),
                    "Field assembly is not in the generator's actual electrical network");
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static void winding(GameTestHelper h, int x, int y, int z, boolean along) {
        h.setBlock(new BlockPos(x, y, z), ModdedBlocks.WINDING.getDefaultState()
                .setValue(WindingBlock.AXIS, Direction.Axis.X).setValue(WindingBlock.ALONG_FIRST_AXIS, along)
                .setValue(WindingBlock.PART, x == 2 ? 0 : x == 7 ? 2 : 1));
    }

    private static CommutatorBlockEntity generator(GameTestHelper h) {
        return (CommutatorBlockEntity) h.getBlockEntity(BRUSH);
    }

    private static void speed(GameTestHelper h, int rpm) {
        for (int x : new int[] {0, 10})
            ((SmartBlockEntity) h.getBlockEntity(new BlockPos(x, 3, 4)))
                    .getBehaviour(ScrollValueBehaviour.TYPE).setValue(x == 0 ? rpm : -rpm);
    }

    private static ElectricWire connect(GameTestHelper h, BlockPos a, int ai, BlockPos b, int bi, double resistance) {
        var wire = GlobalElectricNetworks.makeSimpleConnection(h.getLevel(),
                new BlockWireEndpoint(h.absolutePos(a), ai), new BlockWireEndpoint(h.absolutePos(b), bi), (float) resistance);
        wire.setResistance(resistance);
        return wire;
    }

    private static final class Range {
        int count;
        double minV = Double.POSITIVE_INFINITY, maxV = Double.NEGATIVE_INFINITY;
        double minRpm = Double.POSITIVE_INFINITY, maxRpm = Double.NEGATIVE_INFINITY;
        void add(double volts, double rpm) {
            count++;
            minV = Math.min(minV, volts); maxV = Math.max(maxV, volts);
            minRpm = Math.min(minRpm, rpm); maxRpm = Math.max(maxRpm, rpm);
        }
        public String toString() { return minV + ".." + maxV + " V, " + minRpm + ".." + maxRpm + " RPM"; }
    }
}
