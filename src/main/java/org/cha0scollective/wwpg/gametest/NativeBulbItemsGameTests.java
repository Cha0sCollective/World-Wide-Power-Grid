package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.patryk3211.powergrid.collections.*;
import org.patryk3211.powergrid.electricity.light.fixture.AbstractLightFixtureBlockEntity;

@GameTestHolder("wwpg_wires")
@PrefixGameTestTemplate(false)
public final class NativeBulbItemsGameTests {
    @GameTest(template="empty",timeoutTicks=260)
    public static void nativePgBulbItemsRetainMixedLightAndReplacement(GameTestHelper h) {
        var bulbs=new Item[]{ModdedItems.LV_LIGHT_BULB.get(),ModdedItems.LIGHT_BULB.get()};
        for(int index=0;index<bulbs.length;index++) {
            var source=h.absolutePos(new BlockPos(1,2,1+index*4));var lamp=source.east(4);
            for(var pos:new BlockPos[]{source,lamp})h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(source,CEEBlocks.CREATIVE_BATTERY.getDefaultState());h.getLevel().setBlockAndUpdate(lamp,ModdedBlocks.LIGHT_FIXTURE.getDefaultState());
            double voltage=index==0?12:120;int expectedLight=index==0?1:2;var item=bulbs[index];
            var player=h.makeMockPlayer(GameType.SURVIVAL);var identities=new Object[1];
            h.runAtTickTime(5,()->{
                var stack=new ItemStack(item);player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.setItemInHand(InteractionHand.OFF_HAND,Items.RED_DYE.getDefaultInstance());
                h.assertTrue(fixture(h,lamp).replaceBulb(player,InteractionHand.MAIN_HAND,stack)&&stack.isEmpty(),"Native bulb installation did not consume one item");
                ShowroomTools.scroll(h,source,(int)(voltage*1000));WiringGameTests.connect(h,source,1,lamp,0,true);WiringGameTests.connect(h,source,0,lamp,1,false);
            });
            h.runAtTickTime(45,()->{
                var fixture=fixture(h,lamp);h.assertTrue(fixture.getPowerLevel()==expectedLight&&!fixture.getBulbState().isBurned(),"Native rated bulb did not reach its expected light level: "+item);
                h.assertTrue(fixture.getBulbState().getItem()==item&&fixture.getBulbState().getColor()==DyeColor.RED,"Native bulb identity or dye changed");
                identities[0]=Bridges.get(h.getLevel()).branchIdentity(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(0,source),new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(1,source));
                var before=new CompoundTag();fixture.getBulbState().write(before);fixture.loadWithComponents(fixture.saveWithoutMetadata(h.getLevel().registryAccess()).copy(),h.getLevel().registryAccess());
                var after=new CompoundTag();fixture.getBulbState().write(after);h.assertTrue(after.equals(before),"Native bulb temperature, identity, dye or failure state changed during serialization");
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                h.assertTrue(fixture.replaceBulb(player,InteractionHand.MAIN_HAND,ItemStack.EMPTY)&&player.getMainHandItem().is(item)&&fixture.getBulbState()==null,"Native healthy bulb removal lost its item");
                var returned=player.getMainHandItem();h.assertTrue(fixture.replaceBulb(player,InteractionHand.MAIN_HAND,returned)&&returned.isEmpty(),"Native returned bulb could not be reinstalled");
            });
            h.runAtTickTime(80,()->{h.assertTrue(fixture(h,lamp).getPowerLevel()==expectedLight,"Reinstalled bulb stayed dark");ShowroomTools.scroll(h,source,0);});
            h.runAtTickTime(120,()->{h.assertTrue(fixture(h,lamp).getPowerLevel()==0,"Unpowered bulb failed to cool and turn off");ShowroomTools.scroll(h,source,(int)(voltage*4000));});
            h.runAtTickTime(160,()->{
                h.assertTrue(fixture(h,lamp).getBulbState().isBurned()&&fixture(h,lamp).getPowerLevel()==0,"Native bulb failed to burn out under overload");
                ShowroomTools.scroll(h,source,(int)(voltage*1000));var replacement=new ItemStack(item);player.setItemInHand(InteractionHand.MAIN_HAND,replacement);
                h.assertTrue(fixture(h,lamp).replaceBulb(player,InteractionHand.MAIN_HAND,replacement)&&fixture(h,lamp).getBulbState()==null&&replacement.getCount()==1,
                        "Native first click did not remove the burned bulb without consuming its replacement");
                h.assertTrue(fixture(h,lamp).replaceBulb(player,InteractionHand.MAIN_HAND,replacement)&&replacement.isEmpty(),"Native broken bulb replacement did not consume the healthy item");
            });
            h.runAtTickTime(210,()->{
                h.assertTrue(fixture(h,lamp).getPowerLevel()==expectedLight&&!fixture(h,lamp).getBulbState().isBurned(),"Native replacement did not restore mixed lighting");
                h.assertTrue(Bridges.get(h.getLevel()).branchIdentity(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(0,source),new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(1,source))==identities[0],"Bulb installation replaced unchanged CEE source objects");
                h.assertTrue(InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(source,1,0)>voltage*.99,"Replacement lost its CEE feed");
            });
        }
        h.runAtTickTime(215,()->{DynamicGameTests.audit(h);h.succeed();});
    }
    private static AbstractLightFixtureBlockEntity fixture(GameTestHelper h,BlockPos lamp) {return (AbstractLightFixtureBlockEntity)h.getLevel().getBlockEntity(lamp);}
}
