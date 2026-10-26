package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class WireFailureGameTests {
    @GameTest(template="empty",timeoutTicks=500) public static void ceeWireOverloadBreaksAndRepairRejoinsPgCircuit(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);for(var p:new BlockPos[]{a,b})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());var source=h.absolutePos(a);var load=h.absolutePos(b);
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(source)).setValue(1000);((ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setValue(1);
            for(int port=0;port<2;port++)WiringGameTests.connect(h,source,port,load,port,false);});
        // CEE's native wire failure chance is 4% per overheated tick. This allows
        // its normal heating/failure path to run without replacing its RNG.
        h.runAtTickTime(430,()->{var infrastructure=InfrastructureSavedData.load(h.getLevel());
            h.assertTrue(!infrastructure.isConnected(new InWorldNode(0,source),new InWorldNode(0,load))||!infrastructure.isConnected(new InWorldNode(1,source),new InWorldNode(1,load)),"Overloaded CEE wire did not break");
            BoardComponentGameTests.near(h,voltage(h,load),0,.001,"Wire failure isolates the PG load");
            ((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(source)).setValue(10);((ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setValue(100);
            for(int port=0;port<2;port++)if(!infrastructure.isConnected(new InWorldNode(port,source),new InWorldNode(port,load)))WiringGameTests.connect(h,source,port,load,port,false);});
        h.runAtTickTime(460,()->{BoardComponentGameTests.near(h,voltage(h,load),10,.05,"Repaired CEE wires restore the PG circuit");DynamicGameTests.audit(h);h.succeed();});
    }
    private static double voltage(GameTestHelper h,BlockPos pos){return new BlockWireEndpoint(pos,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(pos,1).getNode(h.getLevel()).getVoltage();}
}
