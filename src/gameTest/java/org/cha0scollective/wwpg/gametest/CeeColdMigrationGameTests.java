package org.cha0scollective.wwpg.gametest;
import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.inductor.InductorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.compat.cee.*;
import org.cha0scollective.wwpg.WorldWidePowerGrid;

/** Explicit cross-version serialization control, isolated from ordinary acceptance. */
@GameTestHolder("wwpg_cee_migration_cold")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="wwpg_tests")
public final class CeeColdMigrationGameTests {
    private static final BlockPos POS=new BlockPos(128,80,128);
    private static boolean verified;
    private static boolean verify(){return !System.getProperty("wwpg.test.restartPhase","SETUP").equals("SETUP");}
    @BeforeBatch(batch="wwpg_cee_migration_cold")public static void configure(ServerLevel level){DynamicGameTests.substeps(level);}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!System.getProperty("neoforge.enabledGameTestNamespaces","").contains("wwpg_cee_migration_cold")||!verify())return;
        var level=event.getServer().overworld();level.setChunkForced(POS.getX()>>4,POS.getZ()>>4,true);level.getChunkAt(POS);
        var device=DevicesSavedData.load(level).getDevice(POS,InductorDevice.class);
        if(device==null)throw new IllegalStateException("Cold migration device absent");
        double expected=CeeCompatibility.modern()?-2:2;
        if(device.lastCurrent!=expected||device.inductance!=3||CeeSaveConventions.source(level)!=CeeSavedCurrent.Convention.LEGACY)
            throw new IllegalStateException("Cold current migration mismatch: "+device.lastCurrent+", source="+CeeSaveConventions.source(level));
        WorldWidePowerGrid.LOGGER.info("CEE_COLD_MIGRATION source={} current={} energy={} runtimeConvention={}",CeeSaveConventions.source(level),device.lastCurrent,.5*device.inductance*device.lastCurrent*device.lastCurrent,CeeSaveConventions.current());
        // Hold the specimen's physical time while comparing serialized energy.
        var hold=new CompoundTag();device.write(hold);hold.putString(CeeSavedCurrent.MARKER,"unknown");device.read(hold);verified=true;
    }
    @GameTest(template="empty",batch="wwpg_cee_migration_cold",timeoutTicks=80)
    public static void currentConventionSurvivesColdVersionTransition(GameTestHelper h){
        var level=h.getLevel();level.setChunkForced(POS.getX()>>4,POS.getZ()>>4,true);level.getChunkAt(POS);
        if(!verify()){level.setBlockAndUpdate(POS.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(POS,CEEBlocks.INDUCTOR.getDefaultState());}
        h.runAtTickTime(10,()->{
            var device=DevicesSavedData.load(level).getDevice(POS,InductorDevice.class);h.assertTrue(device!=null,"Cold migration specimen not created");
            if(verify()){
                double expected=CeeCompatibility.modern()?-2:2;h.assertTrue(verified&&device.lastCurrent==expected,"Cold migration lost held current");
                var saved=new CompoundTag();device.write(saved);saved.putString(CeeSavedCurrent.MARKER,CeeSaveConventions.current().marker);device.read(saved);
            }else{
                h.assertTrue(!CeeCompatibility.modern(),"SETUP must use the frozen legacy CEE runtime");
                var saved=new CompoundTag();saved.putDouble("LastCurrent",2);saved.putDouble("Inductance",3);saved.putString(CeeSavedCurrent.MARKER,"unknown");device.read(saved);
            }
            DevicesSavedData.load(level).setDirty();
            // This isolated serialization fixture ends without advancing its 6 J specimen.
            level.getServer().tickRateManager().setFrozen(true);h.succeed();
        });
    }
}
