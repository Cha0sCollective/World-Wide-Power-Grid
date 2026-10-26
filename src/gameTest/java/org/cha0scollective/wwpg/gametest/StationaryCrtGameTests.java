package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.config.ResistanceValues;
import org.patryk3211.powergrid.electricity.crt.CRTBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import static org.cha0scollective.wwpg.gametest.StationaryEquipmentGameTests.*;

/** Electrical operation; rendered beam acceptance is a separate client gate. */
@GameTestHolder("wwpg_stationary")
@PrefixGameTestTemplate(false)
public final class StationaryCrtGameTests {
    @GameTest(template = "empty", timeoutTicks = 130)
    public static void mixedPowerControlsNativeCrt(GameTestHelper h) { circuit(h, ModdedBlocks.CRT.get()); }
    @GameTest(template = "empty", timeoutTicks = 130)
    public static void mixedPowerControlsAndesiteCrt(GameTestHelper h) { circuit(h, ModdedBlocks.ANDESITE_CRT.get()); }
    @GameTest(template = "empty", timeoutTicks = 130)
    public static void mixedPowerControlsBrassCrt(GameTestHelper h) { circuit(h, ModdedBlocks.BRASS_CRT.get()); }

    private static void circuit(GameTestHelper h, Block block) {
        var crt = new BlockPos(4, 2, 3);
        var anode = new BlockPos(1, 2, 1);
        var heater = new BlockPos(1, 2, 4);
        var grid = new BlockPos(1, 2, 7);
        var x = new BlockPos(7, 2, 1);
        var y = new BlockPos(7, 2, 6);
        place(h, crt, block);
        for (var supply : new BlockPos[]{anode, heater, grid, x, y}) place(h, supply, CEEBlocks.CREATIVE_BATTERY.get());
        double heaterResistance = ResistanceValues.get(ModdedBlocks.CRT.get(), "heater");
        double coilResistance = ResistanceValues.get(ModdedBlocks.CRT.get(), "coils");
        h.runAtTickTime(5, () -> {
            source(h, anode, 1000);
            source(h, heater, heaterResistance);
            source(h, grid, 0);
            source(h, x, coilResistance * .25);
            source(h, y, -coilResistance * .25);
            var supplies = new BlockPos[]{anode, heater, grid, x, y};
            int[] terminals = {3, 1, 2, 4, 5};
            for (int i = 0; i < supplies.length; i++) {
                wire(h, supplies[i], 1, crt, terminals[i], i % 2 == 0);
                wire(h, supplies[i], 0, crt, i < 3 ? 0 : 6, i % 2 != 0);
            }
        });
        h.runAtTickTime(30, () -> {
            // The native wires have finite resistance. Use terminal voltages,
            // then compare independently measured supply currents to the model.
            h.assertTrue(current(h, heater) > .8, "Native CRT heater never reached its operating current");
            near(h, current(h, anode), gunCurrent(h, crt, heaterResistance), .0001, "Native CRT electron-gun current");
            near(h, current(h, heater), Math.abs(voltage(h, crt, 1, 0)) / heaterResistance, .001, "Native CRT heater current");
            near(h, current(h, x), Math.abs(voltage(h, crt, 4, 6)) / coilResistance, .00025, "Native CRT X deflection current");
            near(h, current(h, y), Math.abs(voltage(h, crt, 5, 6)) / coilResistance, .00025, "Native CRT Y deflection current");
            var be = (CRTBlockEntity) h.getBlockEntity(crt);
            be.setColor(DyeColor.LIME);
            var saved = be.saveWithFullMetadata(h.getLevel().registryAccess());
            var cold = (CRTBlockEntity) BlockEntity.loadStatic(h.absolutePos(crt), h.getBlockState(crt), saved, h.getLevel().registryAccess());
            h.assertTrue(cold.getColor() == DyeColor.LIME, "Cold loading lost native CRT trace color");
            source(h, grid, -10);
        });
        h.runAtTickTime(55, () -> {
            near(h, current(h, anode), 0, .00001, "Native CRT grid cutoff");
            source(h, grid, 0);
            source(h, heater, 0);
        });
        h.runAtTickTime(80, () -> {
            near(h, current(h, anode), 0, .00001, "Native CRT cold heater cutoff");
            source(h, heater, heaterResistance);
        });
        h.runAtTickTime(105, () -> {
            near(h, current(h, anode), gunCurrent(h, crt, heaterResistance), .0001, "Native CRT recovered electron-gun current");
            finish(h);
        });
    }

    private static double current(GameTestHelper h, BlockPos source) {
        return Math.abs(InfrastructureSavedData.load(h.getLevel()).ticker.lastResults
                .getCurrentThrough(h.absolutePos(source), 0, 1));
    }
    private static double voltage(GameTestHelper h, BlockPos pos, int first, int second) {
        var absolute = h.absolutePos(pos);
        return new BlockWireEndpoint(absolute, first).getNode(h.getLevel()).getVoltage()
                - new BlockWireEndpoint(absolute, second).getNode(h.getLevel()).getVoltage();
    }
    private static double gunCurrent(GameTestHelper h, BlockPos pos, double heaterResistance) {
        double heater = Math.abs(voltage(h, pos, 1, 0)) / heaterResistance;
        double emission = Math.max(0, Math.min(voltage(h, pos, 2, 0), 0) + voltage(h, pos, 3, 0) / 100);
        return emission * .01 * (heater < .8 ? 0 : Math.min(heater * heater, 1.2));
    }
}
