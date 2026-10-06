package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.modulardisplay.ModularDisplayBlockEntity;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class DisplayGameTests {
    @GameTest(template="empty",timeoutTicks=115) public static void ceePulsesPgModularDisplayWithNativeModules(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);for(var p:new BlockPos[]{a,b})h.setBlock(p.below(),Blocks.STONE);h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(b,ModdedBlocks.MODULAR_DISPLAY.get());var source=h.absolutePos(a);var display=h.absolutePos(b);
        h.runAtTickTime(5,()->{var be=(ModularDisplayBlockEntity)h.getBlockEntity(b);var player=h.makeMockPlayer(GameType.SURVIVAL);var item=ModdedItems.DISPLAY_MODULE.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,item);be.interact(0,player);h.assertTrue(item.isEmpty()&&be.modules[0]!=null,"Native PG display module installation failed");
            WiringGameTests.connect(h,source,1,display,0,true);WiringGameTests.connect(h,source,0,display,8,false);WiringGameTests.connect(h,source,0,display,1,true);DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=24;});
        h.runAtTickTime(25,()->{var be=(ModularDisplayBlockEntity)h.getBlockEntity(b);h.assertTrue(be.modules[0].getIndex()==1,"PG display did not consume CEE pulse");DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=0;});
        h.runAtTickTime(40,()->{DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=24;});
        h.runAtTickTime(60,()->{var be=(ModularDisplayBlockEntity)h.getBlockEntity(b);h.assertTrue(be.modules[0].getIndex()==2,"PG display did not count second CEE pulse");DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=0;be.loadWithComponents(be.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());});
        h.runAtTickTime(90,()->{h.assertTrue(((ModularDisplayBlockEntity)h.getBlockEntity(b)).modules[0].getIndex()==2,"Native display reconfiguration lost its saved count");DynamicGameTests.audit(h);h.succeed();});
    }
}
