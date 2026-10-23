package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import java.util.concurrent.CompletableFuture;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class ConfigReloadGameTests {
    @GameTest(template="empty",timeoutTicks=180) public static void backgroundConfigReloadKeepsMixedNetworkSafe(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);h.setBlock(a.below(),Blocks.STONE);h.setBlock(b.below(),Blocks.STONE);h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());var s=h.absolutePos(a);var l=h.absolutePos(b);
        h.runAtTickTime(5,()->{DevicesSavedData.load(h.getLevel()).getDevice(s,CreativeBatteryDevice.class).voltage=10;((ResistorBlockEntity)h.getBlockEntity(b)).setValue(100);for(int port=0;port<2;port++)WiringGameTests.connect(h,s,1-port,l,port,true);});
        h.runAtTickTime(20,()->{var future=CompletableFuture.runAsync(()->{for(int i=0;i<100;i++)GlobalElectricNetworks.configsReloaded();});h.startSequence().thenWaitUntil(()->h.assertTrue(future.isDone(),"Background reload did not return")).thenExecute(future::join).thenIdle(30).thenExecute(()->{double v=new BlockWireEndpoint(l,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(l,1).getNode(h.getLevel()).getVoltage();BoardComponentGameTests.near(h,v,10,.03,"Network after queued background reloads");DynamicGameTests.audit(h);}).thenSucceed();});
    }
}
