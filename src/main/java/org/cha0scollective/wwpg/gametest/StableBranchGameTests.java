package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Stable compatibility ownership must not hide edits to mutable native properties. */
@GameTestHolder("wwpg_loading")
@PrefixGameTestTemplate(false)
public final class StableBranchGameTests {
    @BeforeBatch(batch = "wwpg_loading_stable_branch")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template = "empty", batch = "wwpg_loading_stable_branch", timeoutTicks = 400)
    public static void retainedDescriptionAppliesStaticAndChangedParametersWithoutReplacingItsBranch(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1); var resistor = new BlockPos(4, 2, 1);
        h.setBlock(source.below(), Blocks.STONE); h.setBlock(resistor.below(), Blocks.STONE);
        h.setBlock(source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(resistor, CEEBlocks.CREATIVE_RESISTOR.get());
        var level = h.getLevel(); var sourcePos = h.absolutePos(source); var loadPos = h.absolutePos(resistor);
        var first = new InWorldNode(0, loadPos); var second = new InWorldNode(1, loadPos);
        double[][] cases = {{10, 1000}, {10, 1000}, {20, 1000}, {-20, 1000},
                {-20, 2000}, {0, 2000}, {10, 2000}, {10, 1000}};
        Object[] branch = {null}; ElectricalNetwork[] owner = {null};
        boolean[] wired = {false}; int[] healthy = {0}, elapsed = {-1}, currentCase = {-1};
        h.runAtTickTime(5, () -> {
            set(h, sourcePos, loadPos, 10, 1000);
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
                branch[0] = Bridges.get(level).branchIdentity(first, second);
                owner[0] = new BlockWireEndpoint(loadPos, 0).getNode(level).getNetwork();
                h.assertTrue(branch[0] != null && owner[0] != null, "Mixed resistor had no prepared compatibility ownership");
                elapsed[0] = 0;
            }
            if (++elapsed[0] % 8 != 1) return;
            if (currentCase[0] >= 0) {
                var expected = cases[currentCase[0]];
                h.assertTrue(reading.status() == MeasurementStatus.VALID, "Changed parameters did not produce accepted measurements");
                BoardComponentGameTests.near(h, reading.voltage().orElseThrow().meanVoltageVolts(), expected[0], .02,
                        "Retained description source voltage");
                double voltage = reading.voltage().orElseThrow().meanVoltageVolts();
                double current = InfrastructureSavedData.load(level).ticker.lastResults.getCurrentThrough(first, second);
                BoardComponentGameTests.near(h, current, voltage / expected[1], 1e-8,
                        "Retained description ignored a changed native resistance or current sign");
                h.assertTrue(Bridges.get(level).branchIdentity(first, second) == branch[0]
                                && new BlockWireEndpoint(loadPos, 0).getNode(level).getNetwork() == owner[0],
                        "A parameter-only change replaced compatibility topology or its solver owner");
            }
            if (++currentCase[0] == cases.length) { DynamicGameTests.audit(h); h.succeed(); return; }
            var next = cases[currentCase[0]];
            set(h, sourcePos, loadPos, next[0], next[1]);
        });
    }

    private static void set(GameTestHelper h, BlockPos source, BlockPos load, double voltage, double resistance) {
        ((CreativeSourceBlockEntity) h.getLevel().getBlockEntity(source)).setValue((float) voltage);
        ((ResistorBlockEntity) h.getLevel().getBlockEntity(load)).setResistance(resistance);
    }
}
