package org.cha0scollective.wwpg.gametest;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.mixin.*;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Real device teardown retains external junctions while its internal wire disappears. */
@GameTestHolder("wwpg_unload")
@PrefixGameTestTemplate(false)
public final class UnloadPartitionGameTests {
    @BeforeBatch(batch="unload_powered") public static void powered(ServerLevel level){DynamicGameTests.substeps(level);}
    @BeforeBatch(batch="unload_passive") public static void passive(ServerLevel level){DynamicGameTests.substeps(level);}
    @BeforeBatch(batch="unload_rebuild") public static void rebuild(ServerLevel level){DynamicGameTests.substeps(level);}

    @GameTest(template="empty",batch="unload_powered",timeoutTicks=110)
    public static void unloadRepartitionsTwoPoweredCircuitsAndReloadMergesThem(GameTestHelper h){scenario(h,true);}
    @GameTest(template="empty",batch="unload_passive",timeoutTicks=110)
    public static void unloadRepartitionsPoweredAndPassiveCircuitsWithoutFalseFailures(GameTestHelper h){scenario(h,false);}

    @GameTest(template="empty",batch="unload_rebuild",timeoutTicks=110)
    public static void rebuildingPausedDeviceRetainsExternalOwnersUntilReload(GameTestHelper h){scenario(h,true,true);}

    private static void scenario(GameTestHelper h,boolean poweredSecond){
        scenario(h,poweredSecond,false);
    }
    private static void scenario(GameTestHelper h,boolean poweredSecond,boolean rebuildPaused){
        BlockPos a=new BlockPos(1,2,1),loadA=new BlockPos(4,2,1),bridge=new BlockPos(4,2,4),b=new BlockPos(1,2,7),loadB=new BlockPos(4,2,7);
        for(var p:List.of(a,loadA,bridge,b,loadB)){h.setBlock(p.below(),Blocks.STONE);h.setBlock(p,ModdedBlocks.CREATIVE_RESISTOR.get());}
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        if(poweredSecond)h.setBlock(b,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        var level=h.getLevel();var pa=h.absolutePos(a);var pla=h.absolutePos(loadA);var pb=h.absolutePos(b);var plb=h.absolutePos(loadB);var pbridge=h.absolutePos(bridge);
        ElectricalNetwork[] old={null};
        h.runAtTickTime(5,()->{
            ((CreativeSourceBlockEntity)level.getBlockEntity(pa)).setValue(10);
            if(poweredSecond)((CreativeSourceBlockEntity)level.getBlockEntity(pb)).setValue(20);
            ((ResistorBlockEntity)level.getBlockEntity(pla)).setValue(100);
            ((ResistorBlockEntity)level.getBlockEntity(plb)).setValue(200);
            ((ResistorBlockEntity)level.getBlockEntity(pbridge)).setValue(1000);
            for(int terminal=0;terminal<2;terminal++){
                WiringGameTests.connect(h,pa,terminal,pla,terminal,true);
                WiringGameTests.connect(h,pb,terminal,plb,terminal,true);
            }
            // Two physical wires per bridge terminal force PG to retain these
            // junctions on unload. Removing a leaf line alone already queues a split.
            WiringGameTests.connect(h,pa,0,pbridge,0,true);
            WiringGameTests.connect(h,pla,0,pbridge,0,true);
            WiringGameTests.connect(h,pb,0,pbridge,1,true);
            WiringGameTests.connect(h,plb,0,pbridge,1,true);
        });
        h.runAtTickTime(25,()->{
            voltage(h,pla,10);voltage(h,plb,poweredSecond?20:0);
            old[0]=owner(level,pa);h.assertTrue(old[0]==owner(level,pb),"Fixture was not initially joined through the device");
            var behaviour=((ElectricBlockEntity)level.getBlockEntity(pbridge)).getElectricBehaviour();
            // The test chunk remains ticking. Suppress its automatic lazy wake
            // until the explicit reload, modeling the actual non-ticking device.
            behaviour.setLazyTickRate(1000);
            behaviour.unload();
            var world=GlobalElectricNetworks.getWorldNetworks(level);
            h.assertTrue(((WorldNetworksAccessor)world).wwpg$islandQueue().contains(old[0]),"Internal teardown did not queue the old owner for repartitioning");
            h.assertTrue(new BlockWireEndpoint(pbridge,0).getNode(level)!=null && new BlockWireEndpoint(pbridge,1).getNode(level)!=null,"Fixture lost its retained external junctions");
            int queued=((WorldNetworksAccessor)world).wwpg$islandQueue().size();
            behaviour.unload();
            h.assertTrue(((WorldNetworksAccessor)world).wwpg$islandQueue().size()==queued,"Repeated unload duplicated discovery work");
        });
        h.runAtTickTime(45,()->{
            h.assertTrue(((ElectricBlockEntity)level.getBlockEntity(pbridge)).getElectricBehaviour().isPaused(),"Fixture woke the unloaded device before its explicit reload");
            h.assertTrue(owner(level,pa)!=owner(level,pb),"Disconnected circuits retained one solver owner");
            voltage(h,pla,10);voltage(h,plb,poweredSecond?20:0);
            verifyOwner(h,owner(level,pa));verifyOwner(h,owner(level,pb));
            var behaviour=((ElectricBlockEntity)level.getBlockEntity(pbridge)).getElectricBehaviour();
            if(rebuildPaused){
                behaviour.rebuildCircuit(false);
                h.assertTrue(behaviour.isPaused(),"Internal rebuild woke an unloaded device");
                h.assertTrue(owner(level,pa)!=owner(level,pb),"Paused rebuild prematurely merged detached circuits");
            }
            behaviour.unpause();behaviour.setLazyTickRate(10);
        });
        h.runAtTickTime(70,()->{
            h.assertTrue(owner(level,pa)==owner(level,pb),"Reload did not merge the reconnected circuits");
            voltage(h,pla,10);voltage(h,plb,poweredSecond?20:0);verifyOwner(h,owner(level,pa));
            DynamicGameTests.audit(h);h.succeed();
        });
    }
    static ElectricalNetwork owner(ServerLevel level,BlockPos p){return new BlockWireEndpoint(p,0).getNode(level).getNetwork();}
    static void verifyOwner(GameTestHelper h,ElectricalNetwork n){
        var a=(ElectricalNetworkAccessor)n;
        long actual=n.getNodes().stream().filter(node->node.isSource()).count()+a.wwpg$wires().stream().filter(wire->wire.isSource()).count();
        h.assertTrue(a.wwpg$sourceCount()==actual,"Source membership reported="+a.wwpg$sourceCount()+", actual="+actual+", tick="+h.getTick());
        var solver=a.wwpg$solver();
        for(int row=0;row<n.size();row++)h.assertTrue(Double.isFinite(solver.stateVector().safe_get(row,0)),"Unloaded circuit retained nonfinite native/Java state");
        h.assertTrue(n.isConverged(),"Repartitioned circuit was not healthy");
    }
    static void voltage(GameTestHelper h,BlockPos p,double expected){
        var value=ElectricalMeasurements.readVoltageBetween(h.getLevel(),p,0,p,1);
        h.assertTrue(value.status()==MeasurementStatus.VALID,"Expected valid native measurement: "+value.status());
        BoardComponentGameTests.near(h,value.voltage().orElseThrow().meanVoltageVolts(),expected,.02,"Powered/passive load voltage");
    }
}
