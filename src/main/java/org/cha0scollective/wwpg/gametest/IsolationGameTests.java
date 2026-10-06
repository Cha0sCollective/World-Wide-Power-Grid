package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class IsolationGameTests {
    private static final class UnknownProperties extends ElectricalProperties {
        UnknownProperties(){super(100,0,0);}
    }
    @GameTest(template="empty",timeoutTicks=110) public static void unknownPropertyIsolatesAndRecoversWithoutStalePower(GameTestHelper h){isolation(h,true);}
    @GameTest(template="empty",timeoutTicks=110) public static void nonfinitePropertyIsolatesAndRecoversWithoutStalePower(GameTestHelper h){isolation(h,false);}
    private static void isolation(GameTestHelper h,boolean unknown){
        var a=new BlockPos(1,2,1);var b=new BlockPos(3,2,1);var c=new BlockPos(5,2,1);
        for(var p:new BlockPos[]{a,b,c})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(b,CEEBlocks.CREATIVE_RESISTOR.get());h.setBlock(c,ModdedBlocks.CREATIVE_RESISTOR.get());
        var s=h.absolutePos(a);var control=h.absolutePos(b);var load=h.absolutePos(c);var level=h.getLevel();
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(10);((ResistorBlockEntity)level.getBlockEntity(load)).setValue(100);
            DevicesSavedData.load(level).getDevice(control,ResistorDevice.class).properties=ElectricalProperties.resistor(100);
            WiringGameTests.connect(h,s,0,control,0,true);WiringGameTests.connect(h,control,1,load,0,true);WiringGameTests.connect(h,s,1,load,1,true);});
        Object[] stamp={null};
        h.runAtTickTime(20,()->{near(h,voltage(h,load),5,.05,"Initial known circuit");stamp[0]=Bridges.get(level).branchIdentity(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(0,control),new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(1,control));
            DevicesSavedData.load(level).getDevice(control,ResistorDevice.class).properties=unknown?new UnknownProperties():ElectricalProperties.resistor(Double.NaN);});
        h.runAtTickTime(40,()->{near(h,voltage(h,load),0,.001,"Unsupported circuit is electrically isolated");
            near(h,InfrastructureSavedData.load(level).ticker.lastResults.getVoltageAt(control,0,1),0,.001,"Isolated CEE device cannot reuse its previous result");
            h.assertTrue(Bridges.get(level).diagnostics().stream().anyMatch(s1->s1.contains(unknown?"UnknownProperties":"Invalid electrical parameters")),"Device diagnostic is missing");
            h.assertTrue(Bridges.get(level).branchIdentity(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(0,control),new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(1,control))==null,"Unsupported device retained its old stamp");
            DevicesSavedData.load(level).getDevice(control,ResistorDevice.class).properties=ElectricalProperties.resistor(100);});
        h.runAtTickTime(60,()->{near(h,voltage(h,load),5,.05,"Known configuration restores circuit");DynamicGameTests.audit(h);h.succeed();});
    }
    private static double voltage(GameTestHelper h,BlockPos pos){return new BlockWireEndpoint(pos,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(pos,1).getNode(h.getLevel()).getVoltage();}
    private static void near(GameTestHelper h,double value,double expected,double tolerance,String label){BoardComponentGameTests.near(h,value,expected,tolerance,label);}
}
