package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class DimensionGameTests {
    @GameTest(template="empty",timeoutTicks=400) public static void identicalCoordinatesKeepSeparateDimensionTopology(GameTestHelper h){
        var overworld=h.getLevel();var nether=overworld.getServer().getLevel(Level.NETHER);
        var source=new BlockPos(512,64,512);var load=source.east(3);
        for(var level:new ServerLevel[]{overworld,nether}){
            level.setChunkForced(32,32,true);level.getChunk(32,32);
            for(var p:new BlockPos[]{source,load}){level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(p.below(),Blocks.STONE.defaultBlockState());}
        }
        h.runAtTickTime(5,()->{for(var level:new ServerLevel[]{overworld,nether}){level.setBlockAndUpdate(source,CEEBlocks.CREATIVE_BATTERY.getDefaultState());level.setBlockAndUpdate(load,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());}});
        // Forced chunks become ticking asynchronously. Wait for the upstream
        // scheduled placement tick rather than assuming it finished in 25 ticks.
        h.startSequence().thenIdle(10).thenWaitUntil(()->{for(var level:new ServerLevel[]{overworld,nether})
            h.assertTrue(InfrastructureSavedData.load(level).hasNode(new InWorldNode(1,source)),"Waiting for native placement in "+level.dimension().location());
        }).thenExecute(()->{for(var level:new ServerLevel[]{overworld,nether}){
            h.assertTrue(InfrastructureSavedData.load(level).hasNode(new InWorldNode(1,source)),"Native source nodes did not register in "+level.dimension().location()+": "+level.getBlockState(source)+", chunk "+level.getChunkSource().getChunkDebugData(new net.minecraft.world.level.ChunkPos(source)));
            DevicesSavedData.load(level).getDevice(source,CreativeBatteryDevice.class).voltage=level==overworld?10:30;((ResistorBlockEntity)level.getBlockEntity(load)).setValue(100);
            for(int port=0;port<2;port++){
                var endpoint=new InWorldNode(port,load);
                org.cha0scollective.wwpg.wiring.Terminals.ensurePgNode(level,endpoint);
                InfrastructureSavedData.load(level).connect(new InWorldNode(1-port,source),endpoint,CEEWireTypes.STANDARD.get());
            }}}).thenIdle(25).thenExecute(()->{near(h,overworld,load,10);near(h,nether,load,30);h.assertTrue(Bridges.get(overworld)!=Bridges.get(nether),"Dimension change reused another world's bridge");
            DevicesSavedData.load(nether).getDevice(source,CreativeBatteryDevice.class).voltage=12;overworld.setBlockAndUpdate(source,Blocks.AIR.defaultBlockState());})
            .thenIdle(25).thenExecute(()->{near(h,overworld,load,0);near(h,nether,load,12);nether.setBlockAndUpdate(source,Blocks.AIR.defaultBlockState());}).thenIdle(25).thenExecute(()->{near(h,nether,load,0);DynamicGameTests.audit(h);for(var level:new ServerLevel[]{overworld,nether}){level.setBlockAndUpdate(load,Blocks.AIR.defaultBlockState());level.setChunkForced(32,32,false);}}).thenSucceed();
    }
    private static void near(GameTestHelper h,ServerLevel level,BlockPos load,double expected){var a=new BlockWireEndpoint(load,0).getNode(level);var b=new BlockWireEndpoint(load,1).getNode(level);h.assertTrue(a!=null&&b!=null,"Dimension endpoint disappeared");BoardComponentGameTests.near(h,a.getVoltage()-b.getVoltage(),expected,.02,"Dimension "+level.dimension().location());}
}
