package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Real powered optional devices; registration alone is insufficient. */
@GameTestHolder("wwpg_loader_addons")
@PrefixGameTestTemplate(false)
public final class LoaderAddonGameTests {
    @GameTest(template="empty", timeoutTicks=100)
    public static void gaugedWirePowersCeeLoadThroughNativeItemInput(GameTestHelper h) {
        var source = new BlockPos(1,2,1); var load = new BlockPos(5,2,1);
        h.setBlock(source.below(), Blocks.STONE); h.setBlock(source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(load.below(), Blocks.STONE); h.setBlock(load, CEEBlocks.CREATIVE_RESISTOR.get());
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity)h.getBlockEntity(source)).setValue(10);
            ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity)h.getBlockEntity(load)).setResistance(100);
            wire(h, h.absolutePos(source), 0, h.absolutePos(load), 0);
            wire(h, h.absolutePos(source), 1, h.absolutePos(load), 1);
        });
        h.runAtTickTime(30, () -> {
            var a = new BlockWireEndpoint(h.absolutePos(load),0).getNode(h.getLevel());
            var b = new BlockWireEndpoint(h.absolutePos(load),1).getNode(h.getLevel());
            BoardComponentGameTests.near(h, Math.abs(a.getVoltage()-b.getVoltage()), 10, .02, "Powered CEE load through real Gauged wire");
            var wires = GlobalElectricNetworks.getWorldNetworks(h.getLevel()).findConnectedWires(new BlockWireEndpoint(h.absolutePos(load),0));
            h.assertTrue(wires != null && !wires.isEmpty(), "No persistent Gauged wire");
            for (var wire : wires) {
                var entity = wire.persistentOwnerId.getEntity(h.getLevel());
                h.assertTrue(entity != null && BuiltInRegistries.ITEM.getKey(entity.getItem()).toString().equals("gauged_wires:cu_wire_14awg"), "Wrong retained wire material");
            }
            DynamicGameTests.audit(h); h.succeed();
        });
    }
    @GameTest(template="empty", timeoutTicks=100)
    public static void powerComponentsFuseConductsCeePowerAfterBoardRebuild(GameTestHelper h) {
        var type = ComponentRegistry.get(ResourceLocation.parse("moregrid:fuse"));
        h.assertTrue(type != null, "Power Components fuse is missing");
        var f = new BoardFixture(h, type, p -> {});
        h.runAtTickTime(5, () -> { f.connect(); f.resistance(0,100); f.resistance(1,100); f.voltage(0,10); });
        h.runAtTickTime(30, () -> {
            BoardComponentGameTests.near(h, f.current(), .05, .001, "Power Components powered fuse");
            var saved=f.board.getSchematic().serializeNbt(h.getLevel().registryAccess());
            f.board.setSchematic(org.patryk3211.powergrid.circuits.schematic.CircuitSchematic.fromNbt(h.getLevel().registryAccess(),saved));
        });
        h.runAtTickTime(40, () -> {
            BoardComponentGameTests.near(h, Math.abs(f.feedCurrent(0)), .05, .001, "Powered fuse after real board rebuild");
            h.assertTrue(f.board.getComponentsStream().anyMatch(p -> p.component==type && !p.destroyed), "Rebuilt addon component missing");
            DynamicGameTests.audit(h); h.succeed();
        });
    }
    private static void wire(GameTestHelper h, BlockPos a, int ai, BlockPos b, int bi) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("gauged_wires:cu_wire_14awg"));
        h.assertTrue(item != Items.AIR, "Gauged wire item is missing");
        var player = h.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item,64));
        for (var endpoint : new BlockWireEndpoint[]{new BlockWireEndpoint(a,ai), new BlockWireEndpoint(b,bi)}) {
            var point = endpoint.getExactPosition(h.getLevel());
            h.assertTrue(point != null, "Missing wire target");
            var context = new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(point,Direction.UP,endpoint.getPos(),false));
            h.assertTrue(IElectric.getAt(h.getLevel(),endpoint.getPos()).onWire(h.getLevel().getBlockState(endpoint.getPos()),context).consumesAction(), "Gauged wire connection rejected");
        }
    }
}
