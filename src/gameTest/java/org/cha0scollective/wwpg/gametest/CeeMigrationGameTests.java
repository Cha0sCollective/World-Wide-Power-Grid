package org.cha0scollective.wwpg.gametest;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.inductor.InductorDevice;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.compat.cee.*;
import org.cha0scollective.wwpg.bridge.CeeResultsAvailability;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
@GameTestHolder("wwpg_cee_migration")
@PrefixGameTestTemplate(false)
public final class CeeMigrationGameTests {
    @BeforeBatch(batch="wwpg_cee_migration")public static void configure(ServerLevel level){DynamicGameTests.substeps(level);}
    @GameTest(template="empty",batch="wwpg_cee_migration",timeoutTicks=100)
    public static void chargedInductorDirectionRoundTripAndAmbiguityIsolation(GameTestHelper h){
        var coil=new BlockPos(1,2,1);var load=new BlockPos(4,2,1);var source=new BlockPos(1,2,5);var neighbor=new BlockPos(4,2,5);
        for(var pos:new BlockPos[]{coil,load,source,neighbor})h.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE);
        h.setBlock(coil,CEEBlocks.INDUCTOR.get());h.setBlock(load,ModdedBlocks.CREATIVE_RESISTOR.get());h.setBlock(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());h.setBlock(neighbor,ModdedBlocks.CREATIVE_RESISTOR.get());
        var pos=h.absolutePos(coil);
        h.runAtTickTime(5,()->{
            var device=DevicesSavedData.load(h.getLevel()).getDevice(pos,InductorDevice.class);
            var tag=history("cee-1.1");device.read(tag);
            double expected=CeeCompatibility.modern()?-2:2;
            h.assertTrue(device.lastCurrent==expected,"Marked legacy current changed physical direction");
            var saved=new CompoundTag();device.write(saved);device.read(saved);
            h.assertTrue(device.lastCurrent==expected&&saved.getString(CeeSavedCurrent.MARKER).equals(CeeSaveConventions.current().marker),"Current converted twice on same-version round trip");
            device.read(history("unknown"));
            ((ResistorBlockEntity)h.getBlockEntity(load)).setValue(1);((ResistorBlockEntity)h.getBlockEntity(neighbor)).setValue(10);((CreativeSourceBlockEntity)h.getBlockEntity(source)).setValue(10);
            for(int terminal=0;terminal<2;terminal++){
                WiringGameTests.connect(h,pos,terminal,h.absolutePos(load),terminal,true);
                WiringGameTests.connect(h,h.absolutePos(source),terminal,h.absolutePos(neighbor),terminal,true);
            }
        });
        h.runAtTickTime(14,()->{
            var device=DevicesSavedData.load(h.getLevel()).getDevice(pos,InductorDevice.class);var r=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
            h.assertTrue(device.lastCurrent==2,"Ambiguous charged history was overwritten");
            h.assertTrue(r instanceof CeeResultsAvailability a&&!a.available(new InWorldNode(0,pos))&&Double.isNaN(r.getVoltageAt(pos,0,1)),"Ambiguous current was published as an accepted reading");
            double v=voltage(h,h.absolutePos(neighbor));BoardComponentGameTests.near(h,v,10,.01,"Healthy neighbor during migration hold including native wire losses");
            var retained=new CompoundTag();device.write(retained);h.assertTrue(retained.getString(CeeSavedCurrent.MARKER).equals("unknown"),"Ambiguity was silently relabeled");
            device.read(history("cee-1.1"));
        });
        h.runAtTickTime(24,()->{
            var device=DevicesSavedData.load(h.getLevel()).getDevice(pos,InductorDevice.class);
            double physical=CeeCompatibility.modern()?-device.lastCurrent:device.lastCurrent;
            h.assertTrue(physical>0&&physical<2,"Restored current did not decay in its original direction");
            h.assertTrue(voltage(h,h.absolutePos(load))<0,"Restored inductor drove its load backwards");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
    static CompoundTag history(String convention){var tag=new CompoundTag();tag.putDouble("LastCurrent",2);tag.putDouble("Inductance",1);tag.putString(CeeSavedCurrent.MARKER,convention);return tag;}
    static double voltage(GameTestHelper h,BlockPos pos){return new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(pos,0).getNode(h.getLevel()).getVoltage()-new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(pos,1).getNode(h.getLevel()).getVoltage();}
    @GameTest(template="empty",batch="wwpg_cee_migration",timeoutTicks=60)
    public static void capacitorCurrentAndStoredVoltageRoundTrip(GameTestHelper h){
        var at=new BlockPos(2,2,2);h.setBlock(at.below(),net.minecraft.world.level.block.Blocks.STONE);h.setBlock(at,CEEBlocks.CAPACITOR.get());
        h.runAtTickTime(5,()->{
            var device=DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(at),CapacitorDevice.class);device.lastVoltage=7;device.capacitance=.01;
            try{
                if(CeeCompatibility.modern())device.properties.getClass().getField("lastCurrent").setDouble(device.properties,.25);
                var saved=new CompoundTag();device.write(saved);device.lastVoltage=0;device.read(saved);
                h.assertTrue(device.lastVoltage==7&&device.capacitance==.01,"Saved capacitor energy changed");
                if(CeeCompatibility.modern())h.assertTrue(device.properties.getClass().getField("lastCurrent").getDouble(device.properties)==.25,"Trapezoidal current history was lost");
            }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            h.succeed();
        });
    }
}
