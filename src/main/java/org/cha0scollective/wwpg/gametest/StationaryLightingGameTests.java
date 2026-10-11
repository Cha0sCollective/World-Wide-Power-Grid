package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEETags;
import com.george_vi.electroenergetics.content.bulb.BulbBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.light.fixture.AbstractLightFixtureBlockEntity;
import org.patryk3211.powergrid.electricity.light.fixture.LightFixtureBlock;
import org.patryk3211.powergrid.general.ceilingtile.solar.CeilingTileSolarBlock;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import java.util.HashSet;
import java.util.Set;

import static org.cha0scollective.wwpg.gametest.StationaryEquipmentGameTests.*;

@GameTestHolder("wwpg_stationary")
@PrefixGameTestTemplate(false)
public final class StationaryLightingGameTests {
    @GameTest(template = "empty", timeoutTicks = 140)
    public static void nativeBrokenBulbRepairRestoresMixedPowerWithoutRewiring(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1);
        var bulb = new BlockPos(4, 2, 1);
        place(h, source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        place(h, bulb, CEEBlocks.BROKEN_BULB.get());
        var owners = new HashSet<WorldNetworks.PartId>();
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(40);
            wire(h, source, 0, bulb, 0, true);
            wire(h, source, 1, bulb, 1, false);
        });
        h.runAtTickTime(25, () -> {
            h.assertTrue(h.getBlockState(bulb).getValue(BulbBlock.LIGHT) == 0, "Broken native bulb emitted light");
            owners.addAll(pgWireOwners(h, bulb));
            h.assertTrue(owners.size() == 1, "Fixture must retain one PG wire and one CEE wire");
            repair(h, bulb);
        });
        h.runAtTickTime(50, () -> {
            var device = com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(h.getLevel())
                    .getDevice(h.absolutePos(bulb), com.george_vi.electroenergetics.content.bulb.BulbDevice.class);
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("BULB_REPAIR: state={}, destroyed={}, temp={}, voltage={}",
                    h.getBlockState(bulb), device.destroyed, device.temp, ceeVoltage(h, bulb));
            h.assertTrue(CEEBlocks.BULB.has(h.getBlockState(bulb)) && h.getBlockState(bulb).getValue(BulbBlock.LIGHT) > 0,
                    "Native repair did not restore mixed-powered lighting without rewiring");
            verifyBulbWires(h, bulb, owners);
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(1000);
        });
        h.runAtTickTime(75, () -> {
            h.assertTrue(CEEBlocks.BROKEN_BULB.has(h.getBlockState(bulb)), "Native overload did not break the bulb");
            verifyBulbWires(h, bulb, owners);
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(40);
            repair(h, bulb);
        });
        h.runAtTickTime(95, () -> {
            h.assertTrue(h.getBlockState(bulb).getValue(BulbBlock.LIGHT) > 0, "Second native repair did not recover lighting");
            verifyBulbWires(h, bulb, owners);
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(0);
        });
        h.runAtTickTime(115, () -> {
            h.assertTrue(h.getBlockState(bulb).getValue(BulbBlock.LIGHT) == 0, "Repaired bulb retained ghost light");
            finish(h);
        });
    }

    private static Set<WorldNetworks.PartId> pgWireOwners(GameTestHelper h, BlockPos pos) {
        var result = new HashSet<WorldNetworks.PartId>();
        for (var part : GlobalElectricNetworks.getWorldNetworks(h.getLevel()).findConnectedWires(
                new BlockWireEndpoint(h.absolutePos(pos), 0))) result.add(part.persistentOwnerId);
        return result;
    }
    private static void verifyBulbWires(GameTestHelper h, BlockPos pos, Set<WorldNetworks.PartId> expected) {
        h.assertTrue(expected.equals(pgWireOwners(h, pos)), "Native bulb transition lost or replaced PG wire identities");
        h.assertTrue(InfrastructureSavedData.load(h.getLevel()).getConnections(new InWorldNode(1, h.absolutePos(pos))).size() == 1,
                "Native bulb transition lost or duplicated its CEE wire");
    }
    private static void repair(GameTestHelper h, BlockPos pos) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var ingredient = CEEItems.COPPER_WIRE.asStack();
        h.assertTrue(ingredient.is(CEETags.BULB_REPAIR_ITEM), "Pinned repair ingredient differs from the native tag");
        player.setItemInHand(InteractionHand.MAIN_HAND, ingredient);
        var absolute = h.absolutePos(pos);
        var result = h.getBlockState(pos).useItemOn(ingredient, h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(absolute.getCenter(), Direction.UP, absolute, false));
        h.assertTrue(result.consumesAction() && ingredient.isEmpty(), "Native repair did not consume its ingredient");
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void nativeCeilingSolarAssemblyPowersCeeAndRespondsToShade(GameTestHelper h) {
        var panel = new BlockPos(3, 3, 2);
        var connector = panel.below();
        var load = new BlockPos(6, 2, 2);
        h.setBlock(panel, ModdedBlocks.CEILING_TILE.get());
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var item = ModdedBlocks.SOLAR_PANEL.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        var absolute = h.absolutePos(panel);
        var installed = h.getBlockState(panel).useItemOn(item, h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(absolute.getCenter(), Direction.UP, absolute, false));
        h.assertTrue(installed.consumesAction() && item.isEmpty() && ModdedBlocks.CEILING_TILE_SOLAR.has(h.getBlockState(panel)),
                "Native ceiling solar installation did not retain its assembly and item cost");
        h.setBlock(connector, ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING, Direction.UP));
        place(h, load, CEEBlocks.CREATIVE_RESISTOR.get());
        double[] lit = {0};
        h.runAtTickTime(5, () -> {
            h.getLevel().setDayTime(6000);
            h.getLevel().setWeatherParameters(0, 10000, false, false);
            resistor(h, load, 1000);
            wire(h, connector, 0, load, 0, true);
            wire(h, connector, 1, load, 1, false);
        });
        h.runAtTickTime(60, () -> {
            lit[0] = ceeVoltage(h, load);
            h.assertTrue(lit[0] > 5 && lit[0] < 30, "Native ceiling solar failed to supply CEE: " + lit[0]);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) h.setBlock(panel.offset(x, 3, z), Blocks.STONE);
        });
        h.runAtTickTime(150, () -> {
            h.assertTrue(Math.abs(ceeVoltage(h, load)) < lit[0] * .1, "Native ceiling solar ignored shade");
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(absolute.getCenter(), Direction.EAST, absolute, false));
            h.assertTrue(((CeilingTileSolarBlock) h.getBlockState(panel).getBlock()).onWrenched(h.getBlockState(panel), context).consumesAction(),
                    "Native ceiling solar removal failed");
        });
        h.runAtTickTime(170, () -> {
            h.assertTrue(ModdedBlocks.CEILING_TILE.has(h.getBlockState(panel)), "Native solar removal lost the underlying ceiling tile");
            near(h, ceeVoltage(h, load), 0, .001, "Removed ceiling solar left ghost output");
            finish(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ceePowerRunsNativeGrowthLampAndCropEffect(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1);
        var lamp = new BlockPos(4, 4, 4);
        var crop = lamp.below(2);
        place(h, source, CEEBlocks.CREATIVE_BATTERY.get());
        h.setBlock(lamp, ModdedBlocks.LIGHT_FIXTURE.getDefaultState().setValue(LightFixtureBlock.FACING, Direction.DOWN));
        h.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.setBlock(crop, Blocks.WHEAT);
        h.runAtTickTime(5, () -> {
            source(h, source, 240);
            var item = ModdedItems.GROWTH_LAMP.asStack();
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, item);
            h.assertTrue(((AbstractLightFixtureBlockEntity) h.getBlockEntity(lamp)).replaceBulb(player, InteractionHand.MAIN_HAND, item)
                    && item.isEmpty(), "Native growth lamp installation failed");
            wire(h, source, 1, lamp, 0, true);
            wire(h, source, 0, lamp, 1, false);
        });
        h.runAtTickTime(80, () -> {
            var fixture = (AbstractLightFixtureBlockEntity) h.getBlockEntity(lamp);
            h.assertTrue(fixture.getPowerLevel() > 0 && !fixture.getBulbState().isBurned(), "Mixed power did not activate the growth lamp");
            // Exercise the public native effect with a repeatable random sequence,
            // independently of ordinary random crop ticks in the acceptance world.
            h.setBlock(crop, Blocks.WHEAT);
            h.getLevel().random.setSeed(231);
            for (int i = 0; i < 2000 && h.getBlockState(crop).getValue(CropBlock.AGE) == 0; i++)
                fixture.getBulbState().runSpecialEffects(h.getLevel(), h.absolutePos(lamp), Direction.DOWN);
            h.assertTrue(h.getBlockState(crop).getValue(CropBlock.AGE) > 0, "Powered native growth effect did not grow the crop");
            source(h, source, 0);
        });
        h.runAtTickTime(160, () -> {
            var fixture = (AbstractLightFixtureBlockEntity) h.getBlockEntity(lamp);
            h.assertTrue(fixture.getPowerLevel() == 0, "Unpowered growth lamp did not cool to its native off state");
            h.setBlock(crop, Blocks.WHEAT);
            for (int i = 0; i < 100; i++) fixture.getBulbState().runSpecialEffects(h.getLevel(), h.absolutePos(lamp), Direction.DOWN);
            h.assertTrue(h.getBlockState(crop).getValue(CropBlock.AGE) == 0, "Off growth lamp continued its native crop effect");
            finish(h);
        });
    }
}
