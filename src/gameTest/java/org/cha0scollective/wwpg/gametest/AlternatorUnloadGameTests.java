package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.VoltageMeasurement;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder("wwpg_unload")
@PrefixGameTestTemplate(false)
public final class AlternatorUnloadGameTests {
    @BeforeBatch(batch = "unload_alternators")
    public static void configure(ServerLevel level) {
        DynamicGameTests.substeps(level);
    }

    @GameTest(template = "empty", batch = "unload_alternators", timeoutTicks = 420)
    public static void unloadedAlternatorPowersLiveLoad(GameTestHelper h) {
        new Fixture(h, false).run();
    }

    @GameTest(template = "empty", batch = "unload_alternators", timeoutTicks = 420)
    public static void unloadedThreePhaseAlternatorPowersLiveLoads(GameTestHelper h) {
        new Fixture(h, true).run();
    }

    private record Lead(BlockPos from, int fromTerminal, BlockPos to, int toTerminal) {}

    private static final class Fixture {
        private final GameTestHelper test;
        private final ServerLevel level;
        private final boolean threePhase;
        private final BlockPos brush, drive;
        private final ChunkPos brushChunk, driveChunk;
        private final List<BlockPos> loads = new ArrayList<>();
        private final List<BlockPos> connectors = new ArrayList<>();
        private final List<Lead> leads = new ArrayList<>();
        private final Set<ChunkPos> forced = new LinkedHashSet<>();
        private final Set<ChunkPos> sourceSide = new LinkedHashSet<>();
        private LevelChunk originalBrushChunk, originalDriveChunk;
        private long started = -1, unloaded = -1, reload = -1;
        private double[] warmPower;
        private boolean restorationObserved;

        Fixture(GameTestHelper test, boolean threePhase) {
            this.test = test;
            this.level = test.getLevel();
            this.threePhase = threePhase;
            var origin = test.absolutePos(BlockPos.ZERO);
            int offset = threePhase ? 1024 : 512;
            brush = new BlockPos(((origin.getX() + offset) & ~15) + 14, origin.getY() + 3,
                    ((origin.getZ() + 512) & ~15) + 8);
            drive = brush.east(2);
            brushChunk = new ChunkPos(brush);
            driveChunk = new ChunkPos(drive);
            for (int dz : new int[]{-4, 0, 4}) {
                forced.add(new ChunkPos(brush.south(dz)));
                forced.add(new ChunkPos(drive.south(dz)));
            }
            for (int phase = 0; phase < (threePhase ? 3 : 1); phase++) {
                var load = brush.south(512).east(4 * phase);
                loads.add(load);
                forced.add(new ChunkPos(load));
            }
            var neutral = route(0, brush.south(480));
            for (int phase = 0; phase < loads.size(); phase++) {
                var load = loads.get(phase);
                var end = route(phase + 1, brush.south(480).east(4 * (phase + 1)));
                leads.add(new Lead(end, 0, load, 1));
                leads.add(new Lead(neutral, 0, load, 0));
            }
            for (var chunk : forced)
                if (chunk.getMinBlockZ() < brush.getZ() + 256) sourceSide.add(chunk);
        }

        private BlockPos route(int terminal, BlockPos end) {
            var from = brush;
            var fromTerminal = terminal;
            for (int distance = 80; distance <= 480; distance += 80) {
                var to = end.north(480 - distance);
                connectors.add(to);
                forced.add(new ChunkPos(to));
                leads.add(new Lead(from, fromTerminal, to, 0));
                from = to;
                fromTerminal = 0;
            }
            return from;
        }

        void run() {
            forced.forEach(chunk -> force(chunk, true));
            test.onEachTick(() -> {
                try {
                    tick();
                } catch (RuntimeException | Error failure) {
                    forced.forEach(chunk -> force(chunk, false));
                    throw failure;
                }
            });
        }

        private void tick() {
            long now = test.getTick();
            if (started < 0) {
                test.assertTrue(now < 80, "Fixture chunks never started ticking");
                if (!forced.stream().allMatch(this::ticking)) return;
                started = now;
                GenerationGameTests.assemble(level, brush, threePhase);
                for (var pos : connectors) place(pos, CEEBlocks.CONNECTOR.get());
                for (var pos : loads) place(pos, CEEBlocks.CREATIVE_RESISTOR.get());
            }
            long age = now - started;
            if (age == 10) {
                ((SmartBlockEntity) level.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE)
                        .setValue(threePhase ? 128 : 64);
                resistance(1000);
                for (var lead : leads)
                    WiringGameTests.connect(test, level, lead.from, lead.fromTerminal, lead.to, lead.toTerminal, false);
            }
            if (age == 95) {
                warmPower = loads.stream().mapToDouble(pos -> power(pos, 1000)).toArray();
                for (double power : warmPower) test.assertTrue(power > 5, "Alternator never powered a real load");
                test.assertTrue(level.getBlockEntity(drive) instanceof KineticBlockEntity motor
                        && Math.abs(motor.getSpeed()) == (threePhase ? 128 : 64), "Drive never reached its target speed");
                originalBrushChunk = chunk(brushChunk);
                originalDriveChunk = chunk(driveChunk);
                test.assertTrue(originalBrushChunk != null && originalDriveChunk != null
                        && originalBrushChunk != originalDriveChunk, "Drive must be in a separate chunk");
                sourceSide.forEach(chunk -> force(chunk, false));
            }
            if (age > 95 && unloaded < 0) {
                test.assertTrue(age < 145, "Both generator and drive chunks must unload");
                if (absent(brushChunk) && absent(driveChunk)) unloaded = now;
            }
            if (unloaded >= 0 && reload < 0) {
                test.assertTrue(absent(brushChunk) && absent(driveChunk), "Source or drive reloaded during the observation");
                long coldAge = now - unloaded;
                if (coldAge == 20) resistance(500);
                else if (coldAge == 40) resistance(1000);
                else expectPower(coldAge > 20 && coldAge < 40 ? 500 : 1000);
                if (coldAge == 60) {
                    reload = now;
                    sourceSide.forEach(chunk -> force(chunk, true));
                }
            }
            if (reload >= 0) {
                long reloadAge = now - reload;
                if (reloadAge < 50) {
                    for (var load : loads) {
                        var status = reading(load).status();
                        test.assertTrue(status == MeasurementStatus.VALID || status == MeasurementStatus.NOT_READY,
                                "Unexpected load status during restoration: " + status);
                        restorationObserved |= status == MeasurementStatus.NOT_READY;
                    }
                }
                if (reloadAge == 65) {
                    test.assertTrue(chunk(brushChunk) != originalBrushChunk && chunk(driveChunk) != originalDriveChunk
                            && ticking(brushChunk) && ticking(driveChunk), "Source and drive must reload into new live chunks");
                    test.assertTrue(restorationObserved, "Reload did not exercise the restoration hold");
                    expectPower(1000);
                    level.setBlockAndUpdate(drive, Blocks.AIR.defaultBlockState());
                }
                if (reloadAge == 115) {
                    for (var load : loads) test.assertTrue(power(load, 1000) < 0.0001,
                            "Alternator kept powering its load after the live drive stopped");
                    sourceSide.forEach(chunk -> force(chunk, false));
                }
                if (reloadAge == 150) {
                    test.assertTrue(absent(brushChunk) && absent(driveChunk), "Stopped generator and drive must unload again");
                    for (var load : loads) test.assertTrue(power(load, 1000) < 0.0001,
                            "Unloading a stopped alternator restored stale power");
                    DynamicGameTests.audit(test);
                    forced.forEach(chunk -> force(chunk, false));
                    test.succeed();
                }
            }
        }

        private void expectPower(int resistance) {
            for (int i = 0; i < loads.size(); i++) {
                double ratio = power(loads.get(i), resistance) / warmPower[i];
                double expected = 1000.0 / resistance;
                test.assertTrue(Math.abs(ratio - expected) < 0.15 * expected,
                        "Load " + i + " power did not track the live resistance; ratio=" + ratio + ", expected " + expected);
            }
        }

        private void resistance(int ohms) {
            var data = DevicesSavedData.load(level);
            for (var load : loads) data.getDevice(load, ResistorDevice.class).properties = ElectricalProperties.resistor(ohms);
        }

        private double power(BlockPos pos, int resistance) {
            test.assertTrue(ticking(new ChunkPos(pos)), "Remote load stopped ticking");
            var result = reading(pos);
            test.assertTrue(result.status() == MeasurementStatus.VALID, "Unexpected remote load status: " + result.status());
            double v = result.voltage().orElseThrow().rmsVoltageVolts();
            return v * v / resistance;
        }

        private VoltageMeasurement reading(BlockPos pos) {
            return ElectricalMeasurements.readVoltageBetween(level, pos, 0, pos, 1);
        }

        private void place(BlockPos pos, Block block) {
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        }

        private void force(ChunkPos pos, boolean value) { level.setChunkForced(pos.x, pos.z, value); }
        private LevelChunk chunk(ChunkPos pos) { return level.getChunkSource().getChunkNow(pos.x, pos.z); }
        private boolean absent(ChunkPos pos) { return chunk(pos) == null && !level.getChunkSource().isPositionTicking(pos.toLong()); }
        private boolean ticking(ChunkPos pos) {
            return level.getChunkSource().isPositionTicking(pos.toLong()) && level.areEntitiesLoaded(pos.toLong());
        }
    }
}
