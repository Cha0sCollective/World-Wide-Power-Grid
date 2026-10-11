package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import org.cha0scollective.wwpg.equipment.HandheldMeters;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItem;

/** Actual native item hooks for an assembly with one PG and one CEE supply wire. */
final class ApiMeterFixture {
    private final GameTestHelper helper;
    private final Player meterPlayer, clampPlayer;
    private final ItemStack voltage = ModdedItems.MULTIMETER.asStack();
    private final ItemStack current = ModdedItems.MULTIMETER.asStack();
    private final ItemStack clamp = CEEItems.CLAMP_METER.asStack();

    ApiMeterFixture(GameTestHelper helper, BlockPos source, int pgSourceTerminal, BlockPos target,
                    int pgTargetTerminal, int ceeSourceTerminal, int ceeTargetTerminal) {
        this.helper = helper;
        meterPlayer = helper.makeMockPlayer(GameType.CREATIVE);
        meterPlayer.setPos(target.getCenter());
        clampPlayer = helper.makeMockPlayer(GameType.CREATIVE);
        clampPlayer.setPos(target.getCenter());
        meterPlayer.setItemInHand(InteractionHand.MAIN_HAND, voltage);
        probe(target, 0); probe(target, 1);
        meterPlayer.setItemInHand(InteractionHand.MAIN_HAND, current);
        var point = new NodeConnectionPoint(new InWorldNode(ceeSourceTerminal, source),
                new InWorldNode(ceeTargetTerminal, target), .5f);
        helper.assertTrue(HandheldMeters.attachMultimeter(point, helper.getLevel(), meterPlayer, current),
                "Native multimeter could not attach to the CEE supply wire");
        var endpoint = new BlockWireEndpoint(source, pgSourceTerminal);
        var wire = IElectric.getAt(helper.getLevel(), source)
                .getBehaviour(helper.getLevel(), source, helper.getLevel().getBlockState(source))
                .getConnections().get(endpoint).stream()
                .filter(candidate -> candidate.isConnectedTo(target, pgTargetTerminal)).findFirst().orElseThrow();
        clampPlayer.setItemInHand(InteractionHand.MAIN_HAND, clamp);
        helper.assertTrue(HandheldMeters.attachClamp(wire, clampPlayer, InteractionHand.MAIN_HAND).consumesAction(),
                "Native clamp could not attach to the PG supply wire");
    }

    void assertUnavailable(String context) {
        tick();
        for (var stack : new ItemStack[]{voltage, current}) {
            helper.assertTrue(MultimeterItem.getModeData(stack).getBoolean(HandheldMeters.UNAVAILABLE),
                    context + " did not synchronize unavailable multimeter status");
            helper.assertTrue(Float.isNaN(((MultimeterItem) stack.getItem()).getMeasurement(helper.getLevel(), stack)),
                    context + " exposed a numeric multimeter reading");
        }
        var data = clamp.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        helper.assertTrue(data.getBoolean(HandheldMeters.UNAVAILABLE)
                        && Float.isNaN(data.getFloat(HandheldMeters.CURRENT)),
                context + " exposed a numeric clamp reading");
    }

    void assertAvailable(String context) {
        tick();
        for (var stack : new ItemStack[]{voltage, current}) {
            helper.assertTrue(!MultimeterItem.getModeData(stack).getBoolean(HandheldMeters.UNAVAILABLE),
                    context + " retained unavailable multimeter status");
            helper.assertTrue(Float.isFinite(((MultimeterItem) stack.getItem()).getMeasurement(helper.getLevel(), stack)),
                    context + " did not restore the native multimeter reading");
        }
        var data = clamp.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        helper.assertTrue(!data.getBoolean(HandheldMeters.UNAVAILABLE)
                        && Float.isFinite(data.getFloat(HandheldMeters.CURRENT)),
                context + " did not restore the native clamp reading");
    }

    private void tick() {
        voltage.getItem().inventoryTick(voltage, helper.getLevel(), meterPlayer, 0, true);
        current.getItem().inventoryTick(current, helper.getLevel(), meterPlayer, 1, true);
        clamp.getItem().onUseTick(helper.getLevel(), clampPlayer, clamp, 9000);
    }

    private void probe(BlockPos target, int nativeId) {
        var endpoint = new BlockWireEndpoint(target, nativeId);
        helper.assertTrue(voltage.getItem().useOn(new UseOnContext(meterPlayer, InteractionHand.MAIN_HAND,
                new BlockHitResult(endpoint.getExactPosition(helper.getLevel()), Direction.UP, target, false))).consumesAction(),
                "Native multimeter rejected the extension terminal " + nativeId);
    }
}
