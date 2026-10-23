package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.railway_electrification.catenary.CatenaryHolderBlock;
import com.george_vi.electroenergetics.content.railway_electrification.catenary.CatenaryHolderBlockEntity;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.content.connector.InsulatorDevice;
import com.george_vi.electroenergetics.content.pole.ConcretePoleBlock;
import com.george_vi.electroenergetics.content.transmission_distribution.sf6_breaker.SF6BreakerBlock;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import static org.cha0scollective.wwpg.gametest.StationaryEquipmentGameTests.*;

@GameTestHolder("wwpg_distribution")
@PrefixGameTestTemplate(false)
public final class StationaryPoleGameTests {
    @GameTest(template = "empty", timeoutTicks = 120) public static void nativeTwoSegmentPoleCarriesMixedPower(GameTestHelper h) { pole(h, 2); }
    @GameTest(template = "empty", timeoutTicks = 120) public static void nativeThreeSegmentPoleCarriesMixedPower(GameTestHelper h) { pole(h, 3); }
    @GameTest(template = "empty", timeoutTicks = 120) public static void nativeFourSegmentPoleCarriesMixedPower(GameTestHelper h) { pole(h, 4); }
    @GameTest(template = "empty", timeoutTicks = 120) public static void nativeFiveSegmentPoleCarriesMixedPower(GameTestHelper h) { pole(h, 5); }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void nativeCatenarySpoolStylesAndRecoveryCarryMixedPower(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1); var load = new BlockPos(9, 2, 1);
        var first = new BlockPos(4, 4, 3); var second = new BlockPos(8, 4, 3);
        for (var pole : new BlockPos[]{first.west(2).below(2), second.east().below(2)}) {
            h.setBlock(pole.below(), Blocks.STONE);
            for (int segment = 0; segment < 3; segment++) placeItem(h, pole.above(segment).below(), Direction.UP, CEEBlocks.CONCRETE_POLE.asStack());
        }
        for (var holder : new BlockPos[]{first, second}) {
            h.setBlock(holder.below(), Blocks.STONE);
            placeItem(h, holder.below(), Direction.UP, CEEBlocks.CATENARY_HOLDER.asStack());
        }
        place(h, source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        place(h, load, CEEBlocks.CREATIVE_RESISTOR.get());
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var spool = CEEItems.COPPER_WIRE_SPOOL.asStack(2);
        Object[] identity = {null};
        h.runAtTickTime(8, () -> {
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(40);
            resistor(h, load, 1000);
            wire(h, source, 0, first, 0, true);
            wire(h, second, 0, load, 0, true);
            wire(h, source, 1, load, 1, false);
            spool(h, player, spool, first, second);
            h.assertTrue(spool.getCount() == 1 && player.getInventory().countItem(CEEItems.EMPTY_SPOOL.get()) == 1,
                    "Native catenary installation changed its spool cost");
            var sd = InfrastructureSavedData.load(h.getLevel());
            h.assertTrue(sd.isConnected(new InWorldNode(0, h.absolutePos(first)), new InWorldNode(0, h.absolutePos(second))),
                    "Native copper spool did not create a catenary connection");
            spool(h, player, spool, first, second);
            h.assertTrue(spool.getCount() == 1, "Duplicate catenary placement consumed another spool");
        });
        h.runAtTickTime(25, () -> {
            near(h, ceeVoltage(h, load), 40, .04, "Stationary native catenary power");
            h.assertTrue(((CatenaryHolderBlockEntity) h.getBlockEntity(first)).getAttachedTo() != null
                    && ((CatenaryHolderBlockEntity) h.getBlockEntity(second)).getAttachedTo() != null, "Catenary holders did not attach to native poles");
            identity[0] = org.cha0scollective.wwpg.bridge.Bridges.get(h.getLevel()).branchIdentity(
                    new InWorldNode(0, h.absolutePos(first)), new InWorldNode(0, h.absolutePos(second)));
            h.assertTrue(identity[0] != null, "Catenary electrical branch is not mapped");
        });
        for (int rotation = 0; rotation < 4; rotation++) {
            final int index = rotation;
            h.runAtTickTime(30 + rotation * 25, () -> {
                player.setItemInHand(InteractionHand.MAIN_HAND, com.simibubi.create.AllItems.WRENCH.asStack());
                var absolute = h.absolutePos(first);
                var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(absolute.getCenter(), Direction.UP, absolute, false));
                h.assertTrue(player.getMainHandItem().getItem().useOn(context).consumesAction(), "Native catenary wrench failed");
                h.assertTrue(h.getBlockState(first).getValue(CatenaryHolderBlock.STYLE)
                        == CatenaryHolderBlock.Style.values()[(index + 1) % 4], "Catenary wrench did not cycle native styles");
            });
            h.runAtTickTime(45 + rotation * 25, () -> {
                near(h, ceeVoltage(h, load), 40, .04, "Catenary style changed electrical behavior");
                h.assertTrue(org.cha0scollective.wwpg.bridge.Bridges.get(h.getLevel()).branchIdentity(
                        new InWorldNode(0, h.absolutePos(first)), new InWorldNode(0, h.absolutePos(second))) == identity[0],
                        "Catenary style change replaced its electrical branch");
            });
        }
        h.runAtTickTime(140, () -> {
            spool(h, player, CEEItems.EMPTY_SPOOL.asStack(), first, second);
            h.assertTrue(!InfrastructureSavedData.load(h.getLevel()).isConnected(new InWorldNode(0, h.absolutePos(first)),
                    new InWorldNode(0, h.absolutePos(second))) && player.getInventory().countItem(CEEItems.COPPER_WIRE_SPOOL.get()) == 1,
                    "Native catenary recovery lost its spool or left a stale connection");
        });
        h.runAtTickTime(165, () -> {
            near(h, ceeVoltage(h, load), 0, .001, "Recovered catenary left ghost power");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    private static void spool(GameTestHelper h, net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack stack,
                              BlockPos first, BlockPos second) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        for (var local : new BlockPos[]{first, second}) {
            var absolute = h.absolutePos(local);
            var click = new InWorldNode(0, absolute).getPosition(h.getLevel());
            var result = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(click, Direction.UP, absolute, false)));
            // Native duplicate rejection is expected to deny the second click.
            h.assertTrue(result.consumesAction() || result == net.minecraft.world.InteractionResult.FAIL,
                    "Native catenary spool did not handle its terminal");
        }
        stack.remove(CEEDataComponents.SELECTED_NODE);
    }

    private static void pole(GameTestHelper h, int height) {
        var bottom = new BlockPos(3, 2, 3);
        var top = bottom.above(height - 1);
        var supply = new BlockPos(1, 2, 1);
        var load = new BlockPos(6, 2, 3);
        h.setBlock(bottom.below(), Blocks.STONE);
        for (int i = 0; i < height; i++) placeItem(h, bottom.above(i).below(), Direction.UP, CEEBlocks.CONCRETE_POLE.asStack());
        place(h, supply, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        place(h, load, CEEBlocks.CREATIVE_RESISTOR.get());
        h.runAtTickTime(8, () -> {
            h.assertTrue(h.getBlockState(bottom).getValue(ConcretePoleBlock.BOTTOM)
                    && h.getBlockState(top).getValue(ConcretePoleBlock.TOP), "Native pole placement failed to establish ends");
            for (int i = 1; i < height - 1; i++) h.assertTrue(CEEBlocks.CONCRETE_POLE.get()
                    .getNodePositions(h.getLevel(), h.absolutePos(bottom.above(i)), h.getBlockState(bottom.above(i))).isEmpty(),
                    "Interior structural pole segment exposed invented terminals");
            ((CreativeSourceBlockEntity) h.getBlockEntity(supply)).setValue(100);
            resistor(h, load, 1000);
            wire(h, supply, 0, bottom, 0, true);
            wire(h, top, 0, load, 0, false);
            wire(h, supply, 1, load, 1, true);
        });
        h.runAtTickTime(35, () -> {
            near(h, ceeVoltage(h, load), 100, .1, "Native " + height + "-segment pole mixed transmission");
            h.setBlock(bottom.above(), Blocks.AIR);
        });
        h.runAtTickTime(65, () -> {
            near(h, ceeVoltage(h, load), 0, .001, "Broken native pole left ghost power");
            finish(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void nativePoleMountAndInsulatorControlMountedMixedBreaker(GameTestHelper h) {
        var pole = new BlockPos(3, 2, 3);
        h.setBlock(pole.below(), Blocks.STONE);
        placeItem(h, pole.below(), Direction.UP, CEEBlocks.CONCRETE_POLE.asStack());
        placeItem(h, pole, Direction.EAST, CEEBlocks.POLE_MOUNT.asStack());
        var mount = pole.east();
        var insulator = mount.above();
        placeItem(h, mount, Direction.UP, CEEBlocks.INSULATOR.asStack());
        var breaker = insulator.above();
        placeItem(h, insulator, Direction.UP, CEEBlocks.SF6_BREAKER.asStack());
        var source = new BlockPos(1, 2, 1);
        var load = new BlockPos(6, 2, 5);
        place(h, source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        place(h, load, ModdedBlocks.CREATIVE_RESISTOR.get());
        h.runAtTickTime(10, () -> {
            h.assertTrue(CEEBlocks.SF6_BREAKER.has(h.getBlockState(breaker.above())), "Native breaker item did not create both parts");
            h.assertTrue(CEEBlocks.INSULATOR.get().getNodePositions(h.getLevel(), h.absolutePos(insulator), h.getBlockState(insulator)).isEmpty(),
                    "Native insulator was assigned invented wire terminals");
            h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(mount)) == null, "Native mount was assigned an electrical block entity");
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(100);
            ((ResistorBlockEntity) h.getBlockEntity(load)).setValue(1000);
            wire(h, source, 0, breaker, 0, true);
            wire(h, breaker.above(), 0, load, 0, false);
            wire(h, source, 1, load, 1, true);
        });
        h.runAtTickTime(40, () -> {
            near(h, voltage(h, load), 100, .1, "Mounted native breaker closed output");
            h.setBlock(insulator.north(), Blocks.REDSTONE_BLOCK);
        });
        h.runAtTickTime(80, () -> {
            h.assertTrue(DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(insulator), InsulatorDevice.class).powered,
                    "Native redstone did not reach the structural insulator");
            near(h, voltage(h, load), 0, .001, "Insulator redstone failed to open the native breaker");
            h.setBlock(insulator.north(), Blocks.AIR);
        });
        h.runAtTickTime(120, () -> {
            near(h, voltage(h, load), 100, .1, "Released insulator control failed to restore mixed output");
            h.setBlock(pole, Blocks.AIR);
            h.assertTrue(h.getBlockState(mount).isAir(), "Native mount did not detach after its support was removed");
            finish(h);
        });
    }
    static void placeItem(GameTestHelper h, BlockPos support, Direction face, net.minecraft.world.item.ItemStack item) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        var pos = h.absolutePos(support);
        h.assertTrue(item.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(pos.getCenter().relative(face, .5), face, pos, false))).consumesAction() && item.isEmpty(),
                "Native assembly item placement failed at " + support + " on " + face);
    }
    private static double voltage(GameTestHelper h, BlockPos pos) { return StationaryNetherGameTests.voltage(h.getLevel(), h.absolutePos(pos)); }
}
