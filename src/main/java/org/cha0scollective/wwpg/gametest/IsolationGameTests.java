package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
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
        var healthyA=new BlockPos(1,2,4);var healthyB=new BlockPos(3,2,4);var healthyC=new BlockPos(5,2,4);
        for(var p:new BlockPos[]{a,b,c,healthyA,healthyB,healthyC})h.setBlock(p.below(),Blocks.STONE);
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(b,CEEBlocks.RESISTOR.get());h.setBlock(c,ModdedBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(healthyA,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(healthyB,CEEBlocks.RESISTOR.get());h.setBlock(healthyC,ModdedBlocks.CREATIVE_RESISTOR.get());
        var s=h.absolutePos(a);var control=h.absolutePos(b);var load=h.absolutePos(c);var level=h.getLevel();
        var healthySource=h.absolutePos(healthyA);var healthyControl=h.absolutePos(healthyB);var healthyLoad=h.absolutePos(healthyC);
        var isolatedWire=new InWorldNodeConnection(new InWorldNode(1,control),new InWorldNode(0,load));
        // Native CEE cooling floors low-power temperature at zero. Use 25 W
        // per resistor so real heating/history assertions have a nonzero baseline.
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)level.getBlockEntity(s)).setValue(100);((ResistorBlockEntity)level.getBlockEntity(load)).setValue(100);
            DevicesSavedData.load(level).getDevice(control,ResistorDevice.class).properties=ElectricalProperties.resistor(100);
            WiringGameTests.connect(h,s,0,control,0,true);WiringGameTests.connect(h,control,1,load,0,false);WiringGameTests.connect(h,s,1,load,1,true);
            ((CreativeSourceBlockEntity)level.getBlockEntity(healthySource)).setValue(100);((ResistorBlockEntity)level.getBlockEntity(healthyLoad)).setValue(100);
            DevicesSavedData.load(level).getDevice(healthyControl,ResistorDevice.class).properties=ElectricalProperties.resistor(100);
            WiringGameTests.connect(h,healthySource,0,healthyControl,0,true);WiringGameTests.connect(h,healthyControl,1,healthyLoad,0,false);WiringGameTests.connect(h,healthySource,1,healthyLoad,1,true);});
        Object[] stamps={null,null};float[] temperatures=new float[3];
        h.runAtTickTime(20,()->{near(h,voltage(h,load),50,.05,"Initial known circuit");
            near(h,loadPower(h,load),25,.05,"Initial PG gameplay power");
            h.assertTrue(Float.isFinite(DevicesSavedData.load(level).getDevice(control,ResistorDevice.class).temp)
                    &&DevicesSavedData.load(level).getDevice(control,ResistorDevice.class).temp>0,"Native CEE resistor did not heat before isolation");
            stamps[0]=branch(h,control);stamps[1]=branch(h,healthyControl);
            near(h,voltage(h,healthyLoad),50,.05,"Independent healthy circuit before isolation");
            DevicesSavedData.load(level).getDevice(control,ResistorDevice.class).properties=unknown?new UnknownProperties():ElectricalProperties.resistor(Double.NaN);
            ((CreativeSourceBlockEntity)level.getBlockEntity(healthySource)).setValue(120);});
        h.runAtTickTime(30,()->{unavailable(h,control);
            temperatures[0]=DevicesSavedData.load(level).getDevice(control,ResistorDevice.class).temp;
            var connection=InfrastructureSavedData.load(level).getConnectionData(isolatedWire);
            h.assertTrue(connection!=null&&Float.isFinite(connection.temperature()),"Isolated CEE wire was removed or acquired invalid heat");
            temperatures[1]=connection.temperature();
            temperatures[2]=DevicesSavedData.load(level).getDevice(healthyControl,ResistorDevice.class).temp;
            h.assertTrue(Float.isFinite(temperatures[0])&&temperatures[0]>0,"Isolation corrupted native CEE thermal history");
            healthy(h,healthyControl,healthyLoad,stamps[1]);});
        h.runAtTickTime(40,()->{near(h,voltage(h,load),0,.001,"Unsupported circuit is electrically isolated");
            near(h,loadPower(h,load),0,1e-8,"Isolated downstream PG load has no gameplay power");
            unavailable(h,control);
            h.assertTrue(DevicesSavedData.load(level).getDevice(control,ResistorDevice.class).temp==temperatures[0],"Unavailable CEE result advanced native heating");
            var connection=InfrastructureSavedData.load(level).getConnectionData(isolatedWire);
            h.assertTrue(connection!=null&&connection.temperature()==temperatures[1],"Unavailable CEE result advanced wire heating or removed its connection");
            healthy(h,healthyControl,healthyLoad,stamps[1]);
            h.assertTrue(DevicesSavedData.load(level).getDevice(healthyControl,ResistorDevice.class).temp>temperatures[2],"Isolation stopped healthy native gameplay heating");
            h.assertTrue(Bridges.get(level).diagnostics().stream().anyMatch(s1->s1.contains(unknown?"UnknownProperties":"Invalid electrical parameters")&&s1.contains("firstTick=")&&s1.contains("lastTick=")&&s1.contains("count=")),"Device diagnostic is missing its error history");
            var description=String.join("\n",Bridges.get(level).describe(control));
            h.assertTrue(description.contains("isolated=true")&&description.contains("isolationReason=")&&description.contains(unknown?"UnknownProperties":"Invalid electrical parameters"),"Isolated terminal did not identify its cause: "+description);
            h.assertTrue(Bridges.get(level).branchIdentity(new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(0,control),new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(1,control))==null,"Unsupported device retained its old stamp");
            DevicesSavedData.load(level).getDevice(control,ResistorDevice.class).properties=ElectricalProperties.resistor(100);});
        h.runAtTickTime(60,()->{near(h,voltage(h,load),50,.05,"Known configuration restores circuit");
            near(h,loadPower(h,load),25,.05,"Recovered PG gameplay power");
            var reading=ElectricalMeasurements.readVoltageBetween(level,control,0,control,1);
            h.assertTrue(reading.status()==MeasurementStatus.VALID&&reading.voltage().isPresent(),"Repaired CEE terminals retained unavailable readings");
            near(h,InfrastructureSavedData.load(level).ticker.lastResults.getVoltageAt(control,0,1),50,.05,"Recovered native CEE voltage");
            h.assertTrue(DevicesSavedData.load(level).getDevice(control,ResistorDevice.class).temp>temperatures[0],"Native CEE heating did not resume after repair");
            h.assertTrue(branch(h,control)!=null&&branch(h,control)!=stamps[0],"Repair did not create a fresh supported electrical stamp");
            healthy(h,healthyControl,healthyLoad,stamps[1]);
            h.assertTrue(!String.join("\n",Bridges.get(level).describe(control)).contains("isolated=true"),"Recovered device retained a stale isolation reason");DynamicGameTests.audit(h);h.succeed();});
    }
    private static Object branch(GameTestHelper h,BlockPos pos){return Bridges.get(h.getLevel()).branchIdentity(new InWorldNode(0,pos),new InWorldNode(1,pos));}
    /** Inspect the actual native branch aggregate consumed by PG gameplay. */
    private static double loadPower(GameTestHelper h,BlockPos pos){
        try{
            var field=ResistorBlockEntity.class.getDeclaredField("wire");field.setAccessible(true);
            return ((org.patryk3211.powergrid.electricity.sim.ElectricWire)field.get(h.getLevel().getBlockEntity(pos))).power();
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot inspect native resistor gameplay power",error);}
    }
    private static void unavailable(GameTestHelper h,BlockPos pos){
        var results=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
        h.assertTrue(results!=null&&Double.isNaN(results.getVoltageAt(pos,0,1))
                &&Double.isNaN(results.getCurrentThrough(new InWorldNode(0,pos),new InWorldNode(1,pos)))
                &&Double.isNaN(results.getHeatLoss(new InWorldNode(0,pos),new InWorldNode(1,pos))),"Isolated native CEE results exposed zero or stale numeric power");
        var reading=ElectricalMeasurements.readVoltageBetween(h.getLevel(),pos,0,pos,1);
        h.assertTrue(reading.status()==MeasurementStatus.ISOLATED&&reading.voltage().isEmpty(),"Isolated public voltage exposed a numeric result: "+reading.status());
    }
    private static void healthy(GameTestHelper h,BlockPos control,BlockPos load,Object stamp){
        near(h,voltage(h,load),60,.05,"Independent healthy circuit keeps solving");
        near(h,loadPower(h,load),36,.05,"Independent healthy PG gameplay power");
        near(h,InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(control,0,1),60,.05,"Independent healthy CEE results keep publishing");
        h.assertTrue(branch(h,control)==stamp,"Isolation replaced an unrelated healthy electrical stamp");
    }
    private static double voltage(GameTestHelper h,BlockPos pos){return new BlockWireEndpoint(pos,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(pos,1).getNode(h.getLevel()).getVoltage();}
    private static void near(GameTestHelper h,double value,double expected,double tolerance,String label){BoardComponentGameTests.near(h,value,expected,tolerance,label);}
}
