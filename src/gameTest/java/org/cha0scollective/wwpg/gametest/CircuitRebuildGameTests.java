package org.cha0scollective.wwpg.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.mixin.WorldNetworksAccessor;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.contactor.ContactorBlockEntity;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import static org.cha0scollective.wwpg.gametest.UnloadPartitionGameTests.*;

/** Rebuilds detach graph edges even when every device and wire entity stays loaded. */
@GameTestHolder("wwpg_unload")
@PrefixGameTestTemplate(false)
public final class CircuitRebuildGameTests {
    @BeforeBatch(batch="contactor_rebuild") public static void contactor(ServerLevel level){DynamicGameTests.substeps(level);}
    @BeforeBatch(batch="external_rebuild") public static void external(ServerLevel level){DynamicGameTests.substeps(level);}
    @BeforeBatch(batch="membership_reuse") public static void membership(ServerLevel level){DynamicGameTests.substeps(level);}

    @GameTest(template="empty",batch="membership_reuse",timeoutTicks=40)
    public static void clearedOwnerCanBeReusedWithoutStaleSourceOrGroundCounts(GameTestHelper h){
        var backend=org.patryk3211.powergrid.collections.ModdedConfigs.server().electricity.solver.solverBackend.get();
        var n=new org.patryk3211.powergrid.electricity.sim.ElectricalNetwork(true,backend::create);
        var access=(org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor)n;
        try{
            for(int pass=0;pass<3;pass++){
                var node=new org.patryk3211.powergrid.electricity.sim.node.FloatingNode();n.addNode(node);
                var source=new org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire(node,null,2);source.setCurrent(4);n.addWire(source);
                h.assertTrue(access.wwpg$sourceCount()==1&&access.wwpg$groundCount()==1,"Reused owner counted retired sources or grounds");
                n.prepare(16);for(int i=0;i<32;i++)n.singleTick();
                h.assertTrue(n.isConverged(),"Reused owner failed to solve");BoardComponentGameTests.near(h,node.getVoltage(),2,.0001,"Reused grounded source voltage");
                n.clear();h.assertTrue(access.wwpg$sourceCount()==0&&access.wwpg$groundCount()==0,"Cleared owner retained source/ground counts");
            }
            var node=new org.patryk3211.powergrid.electricity.sim.node.FloatingNode();n.addNode(node);
            n.addWire(new org.patryk3211.powergrid.electricity.sim.ElectricWire(1,node,null));
            h.assertTrue(access.wwpg$sourceCount()==0&&access.wwpg$groundCount()==1,"Passive reuse retained a retired source");
        }finally{n.clear();n.cleanup();}
        h.succeed();
    }

    @GameTest(template="empty",batch="contactor_rebuild",timeoutTicks=400)
    public static void contactorDelayedRebuildSplitsAndReclosesBothPolesTwice(GameTestHelper h){
        var a=new BlockPos(1,2,1);var loadA=new BlockPos(4,2,1);
        var b=new BlockPos(1,2,7);var loadB=new BlockPos(4,2,7);
        var control=new BlockPos(4,2,4);var coil=new BlockPos(1,2,4);var socket=control.above();
        for(var p:List.of(a,loadA,b,loadB,control,coil)){h.setBlock(p.below(),Blocks.STONE);h.setBlock(p,ModdedBlocks.CREATIVE_RESISTOR.get());}
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(coil,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(control,ModdedBlocks.CONTACTOR.get());
        h.setBlock(socket,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.DOWN));
        var level=h.getLevel();var pa=h.absolutePos(a);var pla=h.absolutePos(loadA);var pb=h.absolutePos(b);var plb=h.absolutePos(loadB);
        var pc=h.absolutePos(control);var drive=h.absolutePos(coil);var connector=h.absolutePos(socket);
        h.runAtTickTime(5,()->{
            ((CreativeSourceBlockEntity)level.getBlockEntity(pa)).setValue(10);
            ((CreativeSourceBlockEntity)level.getBlockEntity(drive)).setValue(30);
            for(var p:List.of(pla,pb,plb))((ResistorBlockEntity)level.getBlockEntity(p)).setValue(100);
            for(int t=0;t<2;t++){
                WiringGameTests.connect(h,pa,t,pla,t,true);
                WiringGameTests.connect(h,pb,t,plb,t,true);
                // Parallel physical routes retain the contact terminals as junctions.
                WiringGameTests.connect(h,pa,t,pc,2+2*t,true);
                WiringGameTests.connect(h,pla,t,pc,2+2*t,true);
                WiringGameTests.connect(h,pb,t,pc,3+2*t,true);
                WiringGameTests.connect(h,plb,t,pc,3+2*t,true);
                WiringGameTests.connect(h,drive,t,connector,t,true);
            }
        });
        h.runAtTickTime(40,()->{
            h.assertTrue(((ContactorBlockEntity)level.getBlockEntity(pc)).getSignal()==15,"Coil did not close contactor");
            h.assertTrue(owner(level,pa)==owner(level,pb),"Closed contacts did not join load and supply");
            closedVoltage(h,pc,plb);((CreativeSourceBlockEntity)level.getBlockEntity(drive)).setValue(0);
        });
        h.runAtTickTime(170,()->{
            split(h,pc,pa,pb,pla,plb);
            ((CreativeSourceBlockEntity)level.getBlockEntity(drive)).setValue(30);
        });
        h.runAtTickTime(205,()->{
            h.assertTrue(((ContactorBlockEntity)level.getBlockEntity(pc)).getSignal()==15,"Contactor failed to reclose after its internal wires were retired");
            h.assertTrue(owner(level,pa)==owner(level,pb),"Rebuilt closed contacts did not merge owners");
            closedVoltage(h,pc,plb);verifyOwner(h,owner(level,pa));
            ((CreativeSourceBlockEntity)level.getBlockEntity(drive)).setValue(0);
        });
        h.runAtTickTime(340,()->{split(h,pc,pa,pb,pla,plb);DynamicGameTests.audit(h);h.succeed();});
    }

    @GameTest(template="empty",batch="membership_reuse",timeoutTicks=40)
    public static void clearedOwnerCannotReactivateRetiredLeafSolverHooks(GameTestHelper h){
        var backend=org.patryk3211.powergrid.collections.ModdedConfigs.server().electricity.solver.solverBackend.get();
        var n=new org.patryk3211.powergrid.electricity.sim.ElectricalNetwork(true,backend::create);
        try{
            var a=new org.patryk3211.powergrid.electricity.sim.node.FloatingNode();var b=new org.patryk3211.powergrid.electricity.sim.node.FloatingNode();n.addNodes(a,b);
            var diode=new org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire(1e-12,1,22,1,a,b);n.addWire(diode);
            h.assertTrue(n.innerHooks.contains(diode),"Fixture diode did not register its nonlinear callback");
            n.makeLeaf(a,b);h.assertTrue(!n.innerHooks.contains(diode),"Leaf elimination did not defer the callback");
            n.clear();
            var c=new org.patryk3211.powergrid.electricity.sim.node.FloatingNode();var d=new org.patryk3211.powergrid.electricity.sim.node.FloatingNode();n.addNodes(c,d);
            n.makeLeaf(c,d);n.removeLeaf(c);
            h.assertTrue(n.innerHooks.isEmpty(),"Reusing a cleared owner revived a retired wire's solver callback");
        }finally{n.clear();n.cleanup();}
        h.succeed();
    }
    private static void closedVoltage(GameTestHelper h,BlockPos contactor,BlockPos load){
        // Two100-ohm loads in parallel, fed through both native contact resistances.
        var device=(ContactorBlockEntity)h.getLevel().getBlockEntity(contactor);
        double expected=10*50/(50+2*device.resistance("switch"));
        var value=org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements.readVoltageBetween(h.getLevel(),load,0,load,1);
        h.assertTrue(value.status()==org.cha0scollective.wwpg.api.MeasurementStatus.VALID,"Closed contactor voltage was unavailable");
        BoardComponentGameTests.near(h,value.voltage().orElseThrow().meanVoltageVolts(),expected,.01,"Loaded contactor voltage including native resistance");
    }
    private static void split(GameTestHelper h,BlockPos control,BlockPos a,BlockPos b,BlockPos loadA,BlockPos loadB){
        var level=h.getLevel();h.assertTrue(((ContactorBlockEntity)level.getBlockEntity(control)).getSignal()==0,"Unpowered contactor remained closed");
        h.assertTrue(owner(level,a)!=owner(level,b),"Delayed internal contact removal retained one solver for disconnected circuits");
        voltage(h,loadA,10);voltage(h,loadB,0);verifyOwner(h,owner(level,a));verifyOwner(h,owner(level,b));
    }

    @GameTest(template="empty",batch="external_rebuild",timeoutTicks=130)
    public static void internalAndExternalRebuildsQueueOwnersAndLeaveOtherCircuitsHealthy(GameTestHelper h){
        var a=new BlockPos(1,2,1);var loadA=new BlockPos(4,2,1);var bridge=new BlockPos(4,2,4);
        var b=new BlockPos(1,2,7);var loadB=new BlockPos(4,2,7);
        for(var p:List.of(a,loadA,bridge,b,loadB)){h.setBlock(p.below(),Blocks.STONE);h.setBlock(p,ModdedBlocks.CREATIVE_RESISTOR.get());}
        for(var p:List.of(a,b))h.setBlock(p,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        var level=h.getLevel();var pa=h.absolutePos(a);var pla=h.absolutePos(loadA);var pb=h.absolutePos(b);var plb=h.absolutePos(loadB);var pbridge=h.absolutePos(bridge);
        var oldTerminal=new org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode[1];
        h.runAtTickTime(5,()->{
            ((CreativeSourceBlockEntity)level.getBlockEntity(pa)).setValue(10);((CreativeSourceBlockEntity)level.getBlockEntity(pb)).setValue(20);
            for(var p:List.of(pla,plb,pbridge))((ResistorBlockEntity)level.getBlockEntity(p)).setValue(100);
            for(int t=0;t<2;t++){WiringGameTests.connect(h,pa,t,pla,t,true);WiringGameTests.connect(h,pb,t,plb,t,true);}
            WiringGameTests.connect(h,pa,0,pbridge,0,true);WiringGameTests.connect(h,pla,0,pbridge,0,true);
            WiringGameTests.connect(h,pb,0,pbridge,1,true);WiringGameTests.connect(h,plb,0,pbridge,1,true);
        });
        h.runAtTickTime(25,()->{
            voltage(h,pla,10);voltage(h,plb,20);var old=owner(level,pa);h.assertTrue(old==owner(level,pb),"Rebuild fixture was not initially joined");
            var behaviour=((ElectricBlockEntity)level.getBlockEntity(pbridge)).getElectricBehaviour();
            var queue=((WorldNetworksAccessor)GlobalElectricNetworks.getWorldNetworks(level)).wwpg$islandQueue();
            behaviour.rebuildCircuit(false);h.assertTrue(queue.contains(old),"Internal rebuild failed to retain old solver owner for discovery");
            int size=queue.size();behaviour.rebuildCircuit(false);h.assertTrue(queue.size()==size,"Repeated rebuild duplicated queued discovery");
        });
        h.runAtTickTime(50,()->{
            voltage(h,pla,10);voltage(h,plb,20);verifyOwner(h,owner(level,pa));
            oldTerminal[0]=((ElectricBlockEntity)level.getBlockEntity(pbridge)).getElectricBehaviour().getExternalNodes().getFirst();
            ((ElectricBlockEntity)level.getBlockEntity(pbridge)).getElectricBehaviour().rebuildCircuit(true);
        });
        h.runAtTickTime(80,()->{
            h.assertTrue(((ElectricBlockEntity)level.getBlockEntity(pbridge)).getElectricBehaviour().getExternalNodes().getFirst()!=oldTerminal[0],"External rebuild did not replace its terminal objects");
            h.assertTrue(owner(level,pa)==owner(level,pb),"External rebuild failed to reattach the retained physical wires");
            voltage(h,pla,10);voltage(h,plb,20);verifyOwner(h,owner(level,pa));verifyOwner(h,owner(level,pb));
            DynamicGameTests.audit(h);h.succeed();
        });
    }
}
