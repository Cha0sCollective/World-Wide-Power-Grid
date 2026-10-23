package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.grounding.GroundingRodBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class GroundingGameTests {
    @GameTest(template="empty",timeoutTicks=110) public static void bothNativeGroundingSystemsSharePhysicalReference(GameTestHelper h){
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);var ceeGround=new BlockPos(1,2,4);var pgGround=new BlockPos(5,2,5);
        for(int x=-1;x<=1;++x)for(int z=-1;z<=1;++z)for(int depth=1;depth<=2;++depth)h.setBlock(pgGround.offset(x,-depth,z),Blocks.STONE);
        for(var p:new BlockPos[]{a,b,ceeGround})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(b,CEEBlocks.CREATIVE_RESISTOR.get());h.setBlock(ceeGround,CEEBlocks.GROUND_ROD.get());h.setBlock(pgGround,ModdedBlocks.GROUNDING_ROD.get());
        var s=h.absolutePos(a);var load=h.absolutePos(b);var cg=h.absolutePos(ceeGround);var pg=h.absolutePos(pgGround);var level=h.getLevel();
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(10);DevicesSavedData.load(level).getDevice(load,ResistorDevice.class).properties=ElectricalProperties.resistor(100);
            WiringGameTests.connect(h,s,0,load,0,false);WiringGameTests.connect(h,s,1,load,1,false);
            WiringGameTests.connect(h,s,1,cg,0,true);WiringGameTests.connect(h,s,0,pg,0,false);});
        h.runAtTickTime(30,()->{
            var nbt=((GroundingRodBlockEntity)level.getBlockEntity(pg)).saveWithoutMetadata(level.registryAccess());double resistance=nbt.getFloat("Resistance");
            h.assertTrue(resistance>0,"PG native soil scan did not enable grounding");
            double positive=new BlockWireEndpoint(s,0).getNode(level).getVoltage(),negative=new BlockWireEndpoint(s,1).getNode(level).getVoltage();
            BoardComponentGameTests.near(h,positive-negative,10,.02,"Grounded source polarity");
            BoardComponentGameTests.near(h,positive,10*resistance/(resistance+1),.03,"Two physical grounds retain native resistance");
            h.assertTrue(negative<=0&&negative>-.1,"Synthetic reference overrode the physical return: "+negative);
            level.destroyBlock(pg,false);
        });
        h.runAtTickTime(50,()->{BoardComponentGameTests.near(h,new BlockWireEndpoint(s,1).getNode(level).getVoltage(),0,.02,"Removing PG ground preserves the CEE physical reference");DynamicGameTests.audit(h);h.succeed();});
    }
}
