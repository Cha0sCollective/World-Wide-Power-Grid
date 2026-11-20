package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.light.factorylight.FactoryLightBlock;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg_wires")
@PrefixGameTestTemplate(false)
public final class FactoryLightWireGameTests {
    private static final String BATCH = "wwpg_factory_light_wire";
    private static final BlockPos ROW = new BlockPos(1, 3, 1);

    @BeforeBatch(batch = BATCH)
    public static void configure(ServerLevel level) {
        DynamicGameTests.substeps(level);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void copperSpoolRejectsTheSameTerminalOnAJoinedFactoryLight(GameTestHelper h) {
        var endpoints = joinedLight(h);
        var level = h.getLevel();
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var spool = CEEItems.COPPER_WIRE_SPOOL.asStack(2);
        player.setItemInHand(InteractionHand.MAIN_HAND, spool);

        h.runAtTickTime(20, () -> {
            var saved = InfrastructureSavedData.load(level);
            assertSharedTerminal(h, endpoints);
            useSpool(player, endpoints.first);
            h.assertTrue(endpoints.first.equals(spool.get(CEEDataComponents.SELECTED_NODE)),
                    "First connector was not selected");
            useSpool(player, endpoints.second);
            h.assertTrue(!saved.isConnected(endpoints.first, endpoints.second), "Spool saved a wire to the same electrical node");
            h.assertTrue(spool.getCount() == 2 && endpoints.first.equals(spool.get(CEEDataComponents.SELECTED_NODE)),
                    "Rejected placement consumed the spool or lost its first selection");

            var otherTerminal = new InWorldNode(1, endpoints.second.sourcePos());
            useSpool(player, otherTerminal);
            h.assertTrue(saved.isConnected(endpoints.first, otherTerminal) && spool.getCount() == 1,
                    "Spool did not retain normal placement to a different terminal");
        });
        h.runAtTickTime(40, h::succeed);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 110)
    public static void savedCopperSelfLinkCanReconnectAfterASplit(GameTestHelper h) {
        var endpoints = joinedLight(h);
        var level = h.getLevel();
        var source = h.absolutePos(ROW.south(4));
        var load = source.east(4);
        for (var pos : new BlockPos[]{source, load}) level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
        level.setBlockAndUpdate(load, ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());

        h.runAtTickTime(20, () -> {
            assertSharedTerminal(h, endpoints);
            var saved = InfrastructureSavedData.load(level);
            Terminals.ensurePgNode(level, endpoints.first);
            Terminals.ensurePgNode(level, endpoints.second);
            // Place legacy data through CEE's saved infrastructure without the new item guard.
            saved.connect(endpoints.first, endpoints.second, CEEWireTypes.COPPER.get());
            ShowroomTools.pgSource(h, source, 20);
            ShowroomTools.resistance(h, load, 1000);
            for (int pin = 0; pin < 2; ++pin) WiringGameTests.connect(h, source, pin, load, pin, false);
        });
        h.runAtTickTime(40, () -> {
            var bridge = Bridges.get(level);
            var saved = InfrastructureSavedData.load(level);
            h.assertTrue(saved.isConnected(endpoints.first, endpoints.second), "A legacy wire must remain in the save");
            h.assertTrue(bridge.branchIdentity(endpoints.first, endpoints.second) == null, "Same-node span acquired a solver stamp");
            assertPowered(h, load);
            h.setBlock(ROW.east(), Blocks.AIR);
        });
        h.runAtTickTime(72, () -> {
            var first = pgNode(level, endpoints.first);
            var second = pgNode(level, endpoints.second);
            h.assertTrue(first != null && second != null && first != second,
                    "Splitting the factory-light row did not separate its terminals");
            h.assertTrue(Bridges.get(level).branchIdentity(endpoints.first, endpoints.second) != null,
                    "A saved wire did not resume solving after the joined row split");
            assertPowered(h, load);
            var saved = InfrastructureSavedData.load(level);
            saved.removeConnection(new InWorldNodeConnection(endpoints.first, endpoints.second));
            h.assertTrue(!saved.isConnected(endpoints.first, endpoints.second), "Could not remove the legacy span");
        });
        h.runAtTickTime(87, () -> {
            h.assertTrue(Bridges.get(level).branchIdentity(endpoints.first, endpoints.second) == null,
                    "Removing the saved wire left a solver branch");
            assertPowered(h, load);
            h.succeed();
        });
    }

    private static Endpoints joinedLight(GameTestHelper h) {
        for (int x = 0; x < 3; ++x) {
            var part = ROW.east(x);
            h.setBlock(part, ModdedBlocks.FACTORY_LIGHT.getDefaultState()
                    .setValue(FactoryLightBlock.HORIZONTAL_AXIS, Direction.Axis.X)
                    .setValue(FactoryLightBlock.PART, 4 + x));
        }
        var connector = ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING, Direction.DOWN);
        h.setBlock(ROW.above(), connector);
        h.setBlock(ROW.east(2).above(), connector);
        return new Endpoints(new InWorldNode(0, h.absolutePos(ROW.above())),
                new InWorldNode(0, h.absolutePos(ROW.east(2).above())));
    }

    private static void useSpool(Player player, InWorldNode node) {
        var position = node.getPosition(player.level());
        if (position == null) throw new IllegalStateException("Missing connector geometry for " + node);
        player.getMainHandItem().getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(position, Direction.UP, node.sourcePos(), false)));
    }

    private static OwnedFloatingNode pgNode(ServerLevel level, InWorldNode node) {
        return new BlockWireEndpoint(node.sourcePos(), node.id()).getNode(level);
    }

    private static void assertSharedTerminal(GameTestHelper h, Endpoints endpoints) {
        var level = h.getLevel();
        var first = pgNode(level, endpoints.first);
        h.assertTrue(first != null && first == pgNode(level, endpoints.second), "Fixture connectors must resolve to one PG node");
    }

    private static void assertPowered(GameTestHelper h, BlockPos load) {
        var positive = new BlockWireEndpoint(load, 0).getNode(h.getLevel());
        var negative = new BlockWireEndpoint(load, 1).getNode(h.getLevel());
        BoardComponentGameTests.near(h, Math.abs(positive.getVoltage() - negative.getVoltage()), 20, .05,
                "Healthy circuit next to the factory-light row");
    }

    private record Endpoints(InWorldNode first, InWorldNode second) {}
}
