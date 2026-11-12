package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.equipment.HandheldMeters;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;

@GameTestHolder("wwpg_audit_meter")
@PrefixGameTestTemplate(false)
public final class AuditMeterGameTests {
    @GameTest(template="empty", timeoutTicks=60)
    public static void invalidProgressCannotChangeMeterSelection(GameTestHelper h) {
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);
        for(var p:new BlockPos[]{a,b})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());
        var first=new InWorldNode(1,h.absolutePos(a));var second=new InWorldNode(0,h.absolutePos(b));
        var player=h.makeMockPlayer(GameType.CREATIVE);player.setPos(h.absolutePos(a).getCenter());
        var meter=ModdedItems.MULTIMETER.asStack();
        h.runAtTickTime(5,()->WiringGameTests.connect(h,h.absolutePos(a),1,h.absolutePos(b),0,false));
        h.runAtTickTime(20,()->{
            h.assertTrue(HandheldMeters.attachMultimeter(new NodeConnectionPoint(first,second,.5f),h.getLevel(),player,meter),"Valid wire selection failed");
            var original=meter.copy();
            for(float progress:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-.01f,1.01f}) {
                com.george_vi.electroenergetics.CEEWireInteractionBehaviours.CLAMP_METER.get().interactWire(
                        new NodeConnectionPoint(first,second,progress),h.getLevel(),player,meter);
                h.assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(original,meter),"Native wire interaction accepted invalid meter progress");
                h.assertTrue(!HandheldMeters.attachMultimeter(new NodeConnectionPoint(first,second,progress),h.getLevel(),player,meter),"Invalid wire progress accepted: "+progress);
                h.assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(original,meter),"Rejected selection changed meter data");
            }
            for(float progress:new float[]{0,.5f,1})h.assertTrue(HandheldMeters.attachMultimeter(new NodeConnectionPoint(first,second,progress),h.getLevel(),player,meter),"Valid endpoint rejected: "+progress);
            var selected=meter.copy();player.setPos(player.position().add(1000,0,0));
            h.assertTrue(!HandheldMeters.attachMultimeter(new NodeConnectionPoint(first,second,.5f),h.getLevel(),player,meter),"Out-of-reach selection accepted");
            h.assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameComponents(selected,meter),"Out-of-reach selection changed meter data");
            h.succeed();
        });
    }
}
