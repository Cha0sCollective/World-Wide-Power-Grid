package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.transmission_distribution.voltage_regulator.VoltageRegulatorBlock;
import com.george_vi.electroenergetics.foundation.base.DirectionalRolledDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedDataComponents;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireItem;

/** Actual player rays and native PG wire dispatch, rather than synthetic terminal-ID hits. */
@GameTestHolder("wwpg_wires")
@PrefixGameTestTemplate(false)
public final class CeeTerminalPickingGameTests {
    private static final BlockPos REGULATOR = new BlockPos(5, 2, 5);

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void allPgWiresSelectUpperRegulatorPinsAcrossRotations(GameTestHelper h) {
        regulator(h);
        h.runAtTickTime(5, () -> {
            var pos = h.absolutePos(REGULATOR);
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            var wires = new ItemStack[]{ModdedItems.WIRE.asStack(64), ModdedItems.IRON_WIRE.asStack(64),
                    ModdedItems.GOLDEN_WIRE.asStack(64), ModdedItems.INSULATED_COPPER_WIRE.asStack(64)};
            for (var facing : Direction.Plane.HORIZONTAL) {
                h.getLevel().setBlockAndUpdate(pos, h.getBlockState(REGULATOR).setValue(VoltageRegulatorBlock.FACING, facing));
                for (var wire : wires) for (int id = 0; id < 2; ++id) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, wire.copy());
                    var point = new BlockWireEndpoint(pos, id).getExactPosition(h.getLevel());
                    aim(player, point.add(0, 2, 0), point);
                    var original = outline(h, player, 4);
                    h.assertTrue(selected(h, original, pos) == -1, "Fixture must reproduce the ordinary outline miss");
                    var hit = player.pick(4, 1, false);
                    h.assertTrue(selected(h, hit, pos) == id, "Player aim missed upper pin " + id + " / " + facing + " / " + wire);
                    click(player, (BlockHitResult) hit);
                    var connection = player.getMainHandItem().get(ModdedDataComponents.CONNECTION_DATA.get());
                    h.assertTrue(connection != null && new BlockWireEndpoint(pos, id).equals(connection.endpoint()),
                            "Native PG wire handler did not select the aimed CEE endpoint");
                    h.assertTrue(player.getMainHandItem().getCount() == 64, "Selecting an endpoint consumed wire");
                }
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void pickingRespectsOcclusionReachAndOtherTools(GameTestHelper h) {
        regulator(h);
        h.runAtTickTime(5, () -> {
            var pos = h.absolutePos(REGULATOR);
            var point = new BlockWireEndpoint(pos, 0).getExactPosition(h.getLevel());
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            aim(player, point.add(0, 3, 0), point);
            player.setItemInHand(InteractionHand.MAIN_HAND, ModdedItems.GOLDEN_WIRE.asStack());
            h.assertTrue(selected(h, player.pick(4, 1, false), pos) == 0, "Exposed control pin was not selectable");
            h.assertTrue(player.pick(1, 1, false).getType() == HitResult.Type.MISS, "Picking exceeded the supplied reach");
            var obstruction = BlockPos.containing(point.add(0, 1.5, 0));
            h.getLevel().setBlockAndUpdate(obstruction, Blocks.STONE.defaultBlockState());
            var blocked = player.pick(4, 1, false);
            h.assertTrue(blocked instanceof BlockHitResult block && block.getBlockPos().equals(obstruction), "Picked through a solid obstruction");
            h.getLevel().removeBlock(obstruction, false);
            for (var stack : new ItemStack[]{ItemStack.EMPTY, new ItemStack(Items.STICK), CEEItems.WIRE_SPOOL.asStack()}) {
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var vanilla = outline(h, player, 4);
                var actual = player.pick(4, 1, false);
                h.assertTrue(actual.getType() == vanilla.getType() && actual.getLocation().equals(vanilla.getLocation()),
                        "Changed ordinary targeting for a non-PG tool");
            }
            player.setItemInHand(InteractionHand.OFF_HAND, ModdedItems.GOLDEN_WIRE.asStack());
            h.assertTrue(selected(h, player.pick(4, 1, false), pos) == 0, "Offhand wire highlight uses different picking");
            h.getLevel().removeBlock(pos, false);
            h.assertTrue(selected(h, player.pick(4, 1, false), pos) == -1, "Removed owner retained a targeting box");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void capacitorPinsRemainSelectableAcrossNativeOrientations(GameTestHelper h) {
        var pos = h.absolutePos(REGULATOR);
        h.setBlock(REGULATOR.below(), Blocks.STONE);
        h.setBlock(REGULATOR, CEEBlocks.CAPACITOR.get());
        h.runAtTickTime(5, () -> {
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, ModdedItems.GOLDEN_WIRE.asStack(64));
            for (var facing : Direction.values()) for (boolean roll : new boolean[]{false, true}) {
                var state = CEEBlocks.CAPACITOR.getDefaultState().setValue(DirectionalRolledDeviceBlock.FACING, facing)
                        .setValue(DirectionalRolledDeviceBlock.ROLL, roll);
                h.getLevel().setBlockAndUpdate(pos, state);
                var adapter = Terminals.cee(h.getLevel(), pos);
                for (int id = 0; id < adapter.terminalCount(); ++id) {
                    var point = new BlockWireEndpoint(pos, id).getExactPosition(h.getLevel());
                    boolean found = false;
                    var rays = new StringBuilder();
                    for (var side : Direction.values()) {
                        aim(player, point.add(Vec3.atLowerCornerOf(side.getNormal()).scale(2)), point);
                        var hit = player.pick(4, 1, false);
                        rays.append(side).append('=').append(selected(h, hit, pos)).append('@').append(hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ())).append(' ');
                        if (selected(h, hit, pos) == id) { found = true; break; }
                    }
                    h.assertTrue(found, "Capacitor pin cannot be aimed at: " + facing + "/" + roll + "/" + id + " center=" + point.subtract(pos.getX(), pos.getY(), pos.getZ()) + " rays=" + rays);
                }
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void stackedRegulatorPickingObservesLiveAssemblyEnds(GameTestHelper h) {
        regulator(h);
        var upper = REGULATOR.above();
        h.setBlock(upper, CEEBlocks.VOLTAGE_REGULATOR.getDefaultState());
        h.runAtTickTime(5, () -> {
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, ModdedItems.GOLDEN_WIRE.asStack());
            var pos = h.absolutePos(upper);
            h.assertTrue(h.getBlockState(upper).getValue(VoltageRegulatorBlock.TOP)
                    && !h.getBlockState(REGULATOR).getValue(VoltageRegulatorBlock.TOP), "Native stack did not establish its top");
            var adapter = Terminals.cee(h.getLevel(), pos);
            for (int id = 0; id < adapter.terminalCount(); ++id) {
                if (adapter.terminal(h.getBlockState(upper), id) == null) continue;
                var point = new BlockWireEndpoint(pos, id).getExactPosition(h.getLevel());
                aim(player, point.add(0, 2, 0), point);
                h.assertTrue(selected(h, player.pick(4, 1, false), pos) == id, "Stacked top pin missed");
            }
            h.getLevel().removeBlock(pos, false);
            var bottom = h.absolutePos(REGULATOR);
            var point = new BlockWireEndpoint(bottom, 0).getExactPosition(h.getLevel());
            aim(player, point.add(0, 2, 0), point);
            h.assertTrue(selected(h, player.pick(4, 1, false), bottom) == 0, "Rebuilt single regulator kept stale stack geometry");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void realAimGoldenWireConnectionsCarryRegulatedPower(GameTestHelper h) {
        regulator(h);
        var sourceRelative = new BlockPos(1, 2, 5);
        var loadRelative = new BlockPos(9, 2, 5);
        for (var p : new BlockPos[]{sourceRelative, loadRelative}) h.setBlock(p.below(), Blocks.STONE);
        h.setBlock(sourceRelative, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(loadRelative, ModdedBlocks.CREATIVE_RESISTOR.get());
        var pos = h.absolutePos(REGULATOR);
        var source = h.absolutePos(sourceRelative);
        var load = h.absolutePos(loadRelative);
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) h.getBlockEntity(sourceRelative)).setValue(100);
            ((ResistorBlockEntity) h.getBlockEntity(loadRelative)).setValue(1000);
            ((com.simibubi.create.foundation.blockEntity.SmartBlockEntity) h.getBlockEntity(REGULATOR))
                    .getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(100_000);
            WiringGameTests.connect(h, source, 1, pos, 2, false);
            WiringGameTests.connect(h, pos, 3, load, 1, false);
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            for (int id = 0; id < 2; ++id) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ModdedItems.GOLDEN_WIRE.asStack(64));
                var target = new BlockWireEndpoint(id == 0 ? source : load, 0);
                var targetPoint = target.getExactPosition(h.getLevel());
                var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(targetPoint, Direction.UP, target.getPos(), false));
                h.assertTrue(IElectric.getAt(h.getLevel(), target.getPos()).onWire(h.getLevel().getBlockState(target.getPos()), context).consumesAction(), "PG source/load selection failed");
                var point = new BlockWireEndpoint(pos, id).getExactPosition(h.getLevel());
                aim(player, point.add(0, 2, 0), point);
                var hit = player.pick(4, 1, false);
                h.assertTrue(selected(h, hit, pos) == id, "Powered regulator terminal aim failed");
                click(player, (BlockHitResult) hit);
                h.assertTrue(player.getMainHandItem().getCount() < 64, "Finishing native connection did not consume wire");
            }
        });
        h.runAtTickTime(25, () -> {
            var reading = org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements.readVoltageBetween(h.getLevel(), load, 0, load, 1);
            h.assertTrue(reading.status() == org.cha0scollective.wwpg.api.MeasurementStatus.VALID, "Aimed connections did not produce accepted powered readings: " + reading);
            BoardComponentGameTests.near(h, reading.voltage().orElseThrow().meanVoltageVolts(), 100, .5, "Regulated load through aimed golden wires");
            DynamicGameTests.audit(h);
            h.succeed();
        });
    }

    private static void regulator(GameTestHelper h) {
        h.setBlock(REGULATOR.below(), Blocks.STONE);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var stack = CEEBlocks.VOLTAGE_REGULATOR.asStack();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var below = h.absolutePos(REGULATOR.below());
        h.assertTrue(stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(below.getCenter().add(0, .5, 0), Direction.UP, below, false))).consumesAction(), "Native regulator placement failed");
    }

    private static void aim(Player player, Vec3 from, Vec3 to) {
        player.setPos(from.x, from.y - player.getEyeHeight(), from.z);
        var delta = to.subtract(from);
        player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        player.setYHeadRot(player.getYRot());
        player.yHeadRotO = player.getYRot();
        // Native PG deliberately repicks with partialTick=0; make both ends of
        // this mock player's interpolation represent the same completed aim.
        player.setOldPosAndRot();
    }

    private static HitResult outline(GameTestHelper h, Player player, double reach) {
        var from = player.getEyePosition(1);
        return h.getLevel().clip(new ClipContext(from, from.add(player.getViewVector(1).scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    private static int selected(GameTestHelper h, HitResult hit, BlockPos owner) {
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK || !block.getBlockPos().equals(owner)) return -1;
        var adapter = Terminals.cee(h.getLevel(), owner);
        return adapter == null ? -1 : adapter.terminalIndexAt(h.getLevel().getBlockState(owner), hit.getLocation().subtract(owner.getX(), owner.getY(), owner.getZ()));
    }

    private static void click(Player player, BlockHitResult hit) {
        WireItem.useOn(player, InteractionHand.MAIN_HAND, hit.getBlockPos(), hit.getDirection());
    }
}
