package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.*;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.extension.ApiCircuits;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Uses only published example block IDs and public contracts; never imports its implementation. */
@GameTestHolder("wwpg_api")
@PrefixGameTestTemplate(false)
public final class ExtensionApiGameTests {
    @GameTest(template="empty",timeoutTicks=100) public static void pgSupplyApiLoadPgWires(GameTestHelper h){load(h,false,true);}
    @GameTest(template="empty",timeoutTicks=100) public static void pgSupplyApiLoadCeeWires(GameTestHelper h){load(h,false,false);}
    @GameTest(template="empty",timeoutTicks=100) public static void ceeSupplyApiLoadPgWires(GameTestHelper h){load(h,true,true);}
    @GameTest(template="empty",timeoutTicks=100) public static void ceeSupplyApiLoadCeeWires(GameTestHelper h){load(h,true,false);}
    private static void load(GameTestHelper h,boolean cee,boolean pgWire) {
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);
        h.setBlock(a.below(),Blocks.STONE);h.setBlock(b.below(),Blocks.STONE);
        h.setBlock(a,cee?CEEBlocks.CREATIVE_BATTERY.get():ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());example(h,b,"load");
        var source=h.absolutePos(a);var load=h.absolutePos(b);var level=h.getLevel();
        h.runAtTickTime(5,()->{
            if(cee)DevicesSavedData.load(level).getDevice(source,CreativeBatteryDevice.class).voltage=20;
            else((CreativeSourceBlockEntity)level.getBlockEntity(source)).setValue(20);
            WiringGameTests.connect(h,source,cee?1:0,load,0,pgWire);WiringGameTests.connect(h,source,cee?0:1,load,1,pgWire);
        });
        final Object[] identities=new Object[2];
        h.runAtTickTime(20,()->{
            var result=result(h,load);near(h,result.element("load_a").orElseThrow().meanCurrentAmps(),.2,.003,"First parallel load");
            near(h,result.element("load_b").orElseThrow().meanCurrentAmps(),.2,.003,"Second parallel load");
            near(h,result.element("load_a").orElseThrow().meanPowerWatts(),4,.05,"Actual gameplay power");
            identities[0]=ApiCircuits.get(level).elementIdentity(load,"load_a");identities[1]=ApiCircuits.get(level).elementIdentity(load,"load_b");
            h.assertTrue(identities[0]!=identities[1],"Parallel elements were collapsed");
        });
        h.runAtTickTime(35,()->{
            result(h,load);h.assertTrue(identities[0]==ApiCircuits.get(level).elementIdentity(load,"load_a"),"Unchanged API element was recreated");
            h.assertTrue(identities[1]==ApiCircuits.get(level).elementIdentity(load,"load_b"),"Unchanged second element was recreated");
            h.assertTrue(level.getBlockState(load).getLightEmission(level,load)>0,"Example gameplay lamp did not turn on");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100) public static void apiSupplyPgLoadPgWires(GameTestHelper h){source(h,false,true);}
    @GameTest(template="empty",timeoutTicks=100) public static void apiSupplyPgLoadCeeWires(GameTestHelper h){source(h,false,false);}
    @GameTest(template="empty",timeoutTicks=100) public static void apiSupplyCeeLoadPgWires(GameTestHelper h){source(h,true,true);}
    @GameTest(template="empty",timeoutTicks=100) public static void apiSupplyCeeLoadCeeWires(GameTestHelper h){source(h,true,false);}
    private static void source(GameTestHelper h,boolean cee,boolean pgWire) {
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);example(h,a,"source");h.setBlock(b.below(),Blocks.STONE);
        h.setBlock(b,cee?CEEBlocks.RESISTOR.get():ModdedBlocks.CREATIVE_RESISTOR.get());var source=h.absolutePos(a);var load=h.absolutePos(b);var level=h.getLevel();
        h.runAtTickTime(5,()->{
            if(cee)DevicesSavedData.load(level).getDevice(load,com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice.class).properties=com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties.resistor(100);
            else((ResistorBlockEntity)level.getBlockEntity(load)).setValue(100);
            WiringGameTests.connect(h,source,0,load,0,pgWire);WiringGameTests.connect(h,source,1,load,1,pgWire);});
        h.runAtTickTime(30,()->{var result=result(h,source);near(h,result.element("supply").orElseThrow().meanCurrentAmps(),-.2,.002,"Source passive current sign");
            near(h,new BlockWireEndpoint(load,0).getNode(level).getVoltage()-new BlockWireEndpoint(load,1).getNode(level).getVoltage(),20,.1,"Native load powered by API");
            DynamicGameTests.audit(h);h.succeed();});
    }
    @GameTest(template="empty",timeoutTicks=100) public static void apiAcSourceRmsAndNativeLoad(GameTestHelper h) {
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);example(h,a,"ac_source");h.setBlock(b.below(),Blocks.STONE);
        h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());var source=h.absolutePos(a);var load=h.absolutePos(b);var level=h.getLevel();
        h.runAtTickTime(5,()-> {((ResistorBlockEntity)level.getBlockEntity(load)).setValue(100);
            WiringGameTests.connect(h,source,0,load,0,true);WiringGameTests.connect(h,source,1,load,1,false);});
        h.runAtTickTime(30,()-> {
            var measured=result(h,source).element("supply").orElseThrow();
            var pgEndpoint=new BlockWireEndpoint(source,0);
            var pgWire=org.patryk3211.powergrid.electricity.base.IElectric.getAt(level,source)
                    .getBehaviour(level,source,level.getBlockState(source)).getConnections().get(pgEndpoint)
                    .stream().filter(w->w.isConnectedTo(load,0)).findFirst().orElseThrow();
            var ceeWire=com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData.load(level)
                    .getConnectionData(new com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection(
                            new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(1,source),
                            new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(1,load)));
            // Both native wire systems retain their finite resistance in the mixed circuit.
            double resistance=100+pgWire.getResistance()+ceeWire.getResistance();
            double rms=20/Math.sqrt(2);
            near(h,measured.rmsVoltageVolts(),rms,.001,"API 50Hz RMS voltage");
            near(h,measured.rmsCurrentAmps(),rms/resistance,.000002,"API AC RMS current including native wires");
            near(h,measured.meanPowerWatts(),-rms*rms/resistance,.00005,"API source average AC power");DynamicGameTests.audit(h);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100) public static void apiControlledVoltageDrivesNativeCeeLoad(GameTestHelper h){controlled(h,false);}
    @GameTest(template="empty",timeoutTicks=100) public static void apiControlledCurrentDrivesNativePgLoad(GameTestHelper h){controlled(h,true);}
    private static void controlled(GameTestHelper h,boolean current) {
        var a=new BlockPos(1,2,1);var b=new BlockPos(3,2,1);var c=new BlockPos(5,2,1);
        example(h,a,"source");example(h,b,current?"current_control":"voltage_control");h.setBlock(c.below(),Blocks.STONE);
        h.setBlock(c,current?ModdedBlocks.CREATIVE_RESISTOR.get():CEEBlocks.RESISTOR.get());
        var source=h.absolutePos(a);var control=h.absolutePos(b);var load=h.absolutePos(c);var level=h.getLevel();
        h.runAtTickTime(5,()-> {
            if(current)((ResistorBlockEntity)level.getBlockEntity(load)).setValue(100);
            else DevicesSavedData.load(level).getDevice(load,com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice.class).properties=com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties.resistor(100);
            WiringGameTests.connect(h,source,0,control,0,true);WiringGameTests.connect(h,source,1,control,1,false);
            WiringGameTests.connect(h,control,2,load,0,false);WiringGameTests.connect(h,control,3,load,1,true);
        });
        h.runAtTickTime(30,()-> {
            near(h,new BlockWireEndpoint(load,0).getNode(level).getVoltage()-new BlockWireEndpoint(load,1).getNode(level).getVoltage(),current?-20:40,.02,"API controlled-source output");
            var measured=result(h,control).element("controlled").orElseThrow();
            near(h,measured.meanCurrentAmps(),current?.2:-.4,.0005,"Controlled source passive sign");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100) public static void apiStorageUsesOneScheduleAndNativeNbt(GameTestHelper h) {
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);example(h,a,"source");example(h,b,"capacitor");
        var source=h.absolutePos(a);var storage=h.absolutePos(b);var level=h.getLevel();
        h.runAtTickTime(5,()->{WiringGameTests.connect(h,source,0,storage,0,true);WiringGameTests.connect(h,source,1,storage,1,true);});
        h.runAtTickTime(25,()->{
            var result=result(h,storage);double v=((ElementState.Capacitor)result.state("storage").orElseThrow()).voltageVolts();
            h.assertTrue(v>8&&v<15,"RC history advanced incorrectly: "+v);
            var saved=level.getBlockEntity(storage).saveWithoutMetadata(level.registryAccess());near(h,saved.getDouble("StoredVoltage"),v,.1,"Owning add-on saved committed voltage");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100) public static void apiTransformerNativeLoadAndSignedCurrent(GameTestHelper h) {
        var a=new BlockPos(1,2,1);var b=new BlockPos(3,2,1);var c=new BlockPos(5,2,1);example(h,a,"source");example(h,b,"transformer");
        h.setBlock(c.below(),Blocks.STONE);h.setBlock(c,ModdedBlocks.CREATIVE_RESISTOR.get());
        var source=h.absolutePos(a);var transformer=h.absolutePos(b);var load=h.absolutePos(c);var level=h.getLevel();
        h.runAtTickTime(5,()->{((ResistorBlockEntity)level.getBlockEntity(load)).setValue(100);
            WiringGameTests.connect(h,source,0,transformer,0,true);WiringGameTests.connect(h,source,1,transformer,1,true);
            WiringGameTests.connect(h,transformer,2,load,0,true);WiringGameTests.connect(h,transformer,3,load,1,true);});
        h.runAtTickTime(30,()->{near(h,new BlockWireEndpoint(load,0).getNode(level).getVoltage()-new BlockWireEndpoint(load,1).getNode(level).getVoltage(),40,.1,"API transformer secondary");
            near(h,result(h,transformer).element("coupling").orElseThrow().meanCurrentAmps(),.8,.004,"API primary winding current");DynamicGameTests.audit(h);h.succeed();});
    }
    private static void example(GameTestHelper h,BlockPos pos,String name) {
        var id=ResourceLocation.fromNamespaceAndPath("wwpg_api_example",name);
        h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id),"Independent API example add-on is not installed: "+id);
        h.setBlock(pos.below(),Blocks.STONE);h.setBlock(pos,BuiltInRegistries.BLOCK.get(id));
    }
    private static DeviceResults result(GameTestHelper h,BlockPos pos) {
        var result=ElectricalMeasurements.read(h.getLevel(),pos);
        h.assertTrue(result.status()==MeasurementStatus.VALID,"API result is "+result.status()+": "+result.detail());return result;
    }
    private static void near(GameTestHelper h,double value,double expected,double tolerance,String message) {
        h.assertTrue(Double.isFinite(value)&&Math.abs(value-expected)<=tolerance,message+": "+value+" expected "+expected);
    }
}

