package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.energy_meter.EnergyMeterDevice;
import com.george_vi.electroenergetics.content.gauge.ElectricGaugeBlockEntity;
import com.george_vi.electroenergetics.content.frequency_meter.FrequencyMeterBlockEntity;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.gauge.*;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class MeterGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void standaloneCeeMetersReadPgLoadAndDisconnect(GameTestHelper h) {
        var a = new BlockPos(1,2,1); var e = new BlockPos(3,2,1); var b = new BlockPos(5,2,1);
        var i = new BlockPos(5,2,3); var v = new BlockPos(3,2,4);
        for (var p : new BlockPos[] {a,e,b,i,v}) h.setBlock(p.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()); h.setBlock(e, CEEBlocks.ENERGY_METER.get());
        h.setBlock(b, ModdedBlocks.CREATIVE_RESISTOR.get()); h.setBlock(i, CEEBlocks.AMMETER.get()); h.setBlock(v, CEEBlocks.VOLTMETER.get());
        var source=h.absolutePos(a); var meter=h.absolutePos(e); var load=h.absolutePos(b); var ammeter=h.absolutePos(i); var voltmeter=h.absolutePos(v);
        double[] energy = new double[1];
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(source)).setValue(10);
            ((ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setValue(100);
            DevicesSavedData.load(h.getLevel()).getDevice(meter, EnergyMeterDevice.class).isClosed=true;
            wire(h,source,0,meter,0); wire(h,source,1,meter,1); wire(h,meter,2,ammeter,0); wire(h,ammeter,1,load,0); wire(h,meter,3,load,1);
            wire(h,load,0,voltmeter,0); wire(h,load,1,voltmeter,1);
        });
        h.runAtTickTime(35, () -> {
            var sd=DevicesSavedData.load(h.getLevel()); var device=sd.getDevice(meter,EnergyMeterDevice.class);
            BoardComponentGameTests.near(h,((ElectricGaugeBlockEntity)h.getLevel().getBlockEntity(voltmeter)).voltage,10,0.05,"Standalone CEE voltage gauge");
            BoardComponentGameTests.near(h,((ElectricGaugeBlockEntity)h.getLevel().getBlockEntity(ammeter)).voltage/0.01,0.1,0.001,"Standalone CEE current gauge");
            h.assertTrue(device.totalEnergy>0,"CEE meter accumulated no PG consumption"); energy[0]=device.totalEnergy;
        });
        h.runAtTickTime(45, () -> {
            var device=DevicesSavedData.load(h.getLevel()).getDevice(meter,EnergyMeterDevice.class);
            BoardComponentGameTests.near(h,device.totalEnergy-energy[0],10.0/72_000_000,2e-9,"Standalone CEE exactly-once energy"); device.isClosed=false;
        });
        h.runAtTickTime(65, () -> {
            BoardComponentGameTests.near(h,InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(load,0,1),0,0.00001,"Standalone meter disconnect");
            DynamicGameTests.audit(h);h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pgGaugesAndEnergyMeterReadCeeSupply(GameTestHelper h) {
        var a=new BlockPos(1,2,1);var e=new BlockPos(3,2,1);var b=new BlockPos(5,2,1);var p=new BlockPos(5,2,3);var i=new BlockPos(3,2,4);var v=new BlockPos(1,2,4);
        for(var q:new BlockPos[]{a,e,b,p,i,v})h.setBlock(q.below(),Blocks.STONE);
        h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(e,ModdedBlocks.ENERGY_METER.get());h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(p,ModdedBlocks.POWER_METER.get());h.setBlock(i,ModdedBlocks.CURRENT_METER.get());h.setBlock(v,ModdedBlocks.VOLTAGE_METER.get());
        var source=h.absolutePos(a);var meter=h.absolutePos(e);var load=h.absolutePos(b);var power=h.absolutePos(p);var current=h.absolutePos(i);var voltage=h.absolutePos(v);double[] energy=new double[1];
        h.runAtTickTime(5,()->{
            DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=10;
            ((ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setValue(100);
            wire(h,source,1,meter,0);wire(h,meter,1,power,0);wire(h,power,1,current,0);wire(h,current,1,load,0);
            for(var q:new BlockPos[]{meter,power})wire(h,source,0,q,2);wire(h,source,0,load,1);wire(h,load,0,voltage,0);wire(h,load,1,voltage,1);
        });
        h.runAtTickTime(35,()->{
            BoardComponentGameTests.near(h,((VoltageGaugeBlockEntity)h.getLevel().getBlockEntity(voltage)).getValue(),10,0.1,"Native PG voltage gauge");
            BoardComponentGameTests.near(h,((CurrentGaugeBlockEntity)h.getLevel().getBlockEntity(current)).getValue(),0.1,0.002,"Native PG current gauge");
            BoardComponentGameTests.near(h,((PowerGaugeBlockEntity)h.getLevel().getBlockEntity(power)).getValue(),1,0.04,"Native PG power gauge");
            energy[0]=h.getLevel().getBlockEntity(meter).saveWithoutMetadata(h.getLevel().registryAccess()).getDouble("Energy");
        });
        h.runAtTickTime(45,()->{
            double value=h.getLevel().getBlockEntity(meter).saveWithoutMetadata(h.getLevel().registryAccess()).getDouble("Energy");
            BoardComponentGameTests.near(h,value-energy[0],0.5/3_600_000,5e-9,"Native PG energy under CEE power");
            DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=0;
        });
        h.runAtTickTime(60,()->{BoardComponentGameTests.near(h,((PowerGaugeBlockEntity)h.getLevel().getBlockEntity(power)).getValue(),0,0.00001,"Power meter after source removal");DynamicGameTests.audit(h);h.succeed();});
    }

    @GameTest(template = "empty", timeoutTicks = 130)
    public static void frequencyMeterUsesPgSubstepClock(GameTestHelper h) {
        var a=new BlockPos(1,2,1);var b=new BlockPos(4,2,1);var f=new BlockPos(4,2,4);
        for(var q:new BlockPos[]{a,b,f})h.setBlock(q.below(),Blocks.STONE);
        h.setBlock(a,CEEBlocks.CREATIVE_BATTERY.get());h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());h.setBlock(f,CEEBlocks.FREQUENCY_METER.get());
        var source=h.absolutePos(a);var load=h.absolutePos(b);var meter=h.absolutePos(f);
        h.runAtTickTime(5,()->{
            var device=DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class);device.voltage=100;device.acFrequency=50;
            ((ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setValue(10000);
            for(var q:new BlockPos[]{load,meter}){wire(h,source,1,q,0);wire(h,source,0,q,1);}
        });
        // CEE 1.1.3's creative ACSource always generates one period per game
        // tick; its acFrequency field only selects AC rather than setting Hz.
        h.runAtTickTime(40,()->{BoardComponentGameTests.near(h,frequency(h,meter),20,0.1,"Native CEE creative AC frequency under PG's 16 substeps");DevicesSavedData.load(h.getLevel()).getDevice(source,CreativeBatteryDevice.class).voltage=200;});
        h.runAtTickTime(80,()->{BoardComponentGameTests.near(h,frequency(h,meter),20,0.1,"Frequency after amplitude change");DynamicGameTests.audit(h);h.succeed();});
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cee113DiodeRectifiesNativePgPower(GameTestHelper h) {
        var a=new BlockPos(1,2,1);var d=new BlockPos(3,2,1);var b=new BlockPos(5,2,1);
        for(var q:new BlockPos[]{a,d,b})h.setBlock(q.below(),Blocks.STONE);
        h.setBlock(a,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(d,CEEBlocks.DIODE.get());h.setBlock(b,ModdedBlocks.CREATIVE_RESISTOR.get());
        var source=h.absolutePos(a);var diode=h.absolutePos(d);var load=h.absolutePos(b);
        h.runAtTickTime(5,()->{((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(source)).setValue(10);((ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setValue(1000);wire(h,source,0,diode,1);wire(h,diode,0,load,0);wire(h,source,1,load,1);});
        h.runAtTickTime(35,()->{double v=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(load,0,1);h.assertTrue(v>9&&v<10,"CEE 1.1.3 forward diode output was "+v);((CreativeSourceBlockEntity)h.getLevel().getBlockEntity(source)).setValue(-10);});
        h.runAtTickTime(60,()->{BoardComponentGameTests.near(h,InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getVoltageAt(load,0,1),0,0.00005,"CEE diode native reverse leakage");DynamicGameTests.audit(h);h.succeed();});
    }
    private static void wire(GameTestHelper h,BlockPos a,int pa,BlockPos b,int pb){WiringGameTests.connect(h,a,pa,b,pb,false);}
    private static float frequency(GameTestHelper h,BlockPos pos){return h.getLevel().getBlockEntity(pos).saveWithoutMetadata(h.getLevel().registryAccess()).getFloat("Frequency");}
}
