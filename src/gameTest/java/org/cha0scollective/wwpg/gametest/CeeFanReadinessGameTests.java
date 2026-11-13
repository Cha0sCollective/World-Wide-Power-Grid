package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.electrical_panel.*;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;
import org.cha0scollective.wwpg.bridge.CeeResultsAvailability;
import org.cha0scollective.wwpg.compat.cee.CeeCompatibility;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;

/** Modern resistive fan devices driven by real PG wires. No client visual claim. */
@GameTestHolder("wwpg_cee_fans")
@PrefixGameTestTemplate(false)
public final class CeeFanReadinessGameTests {
    @GameTest(template="empty",timeoutTicks=80)
    public static void standaloneFanPowerDirectionAndStop(GameTestHelper h){fan(h,false);}
    @GameTest(template="empty",timeoutTicks=80)
    public static void panelFanPowerDirectionAndStop(GameTestHelper h){fan(h,true);}
    private static void fan(GameTestHelper h,boolean panel){
        h.assertTrue(CeeCompatibility.modern(),"Select CEE 1.2 for the new fan fixtures");DynamicGameTests.substeps(h.getLevel());
        var source=new BlockPos(1,2,1);var fan=new BlockPos(4,2,1);
        for(var pos:new BlockPos[]{source,fan})h.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.STONE);
        h.setBlock(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        if(panel)h.setBlock(fan,CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING,Direction.NORTH));
        else try{h.setBlock(fan,(Block)((java.util.function.Supplier<?>)CEEBlocks.class.getField("ELECTRIC_FAN").get(null)).get());}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        int[] ids={0,1};
        h.runAtTickTime(5,()->{
            drive(h,source,100);
            if(panel){var attachment=PanelGameTests.insert(h,h.absolutePos(fan),CeeTestAttachments.type("FAN"),ElectricalPanelSlot.HALF_UPPER);
                h.assertTrue(attachment.nodes.length==2,"Panel fan terminal count");ids[0]=attachment.nodes[0].id();ids[1]=attachment.nodes[1].id();}
            for(int port=0;port<2;port++)WiringGameTests.connect(h,h.absolutePos(source),port,h.absolutePos(fan),ids[port],true);
        });
        h.runAtTickTime(20,()->{check(h,fan,ids,panel,100);drive(h,source,-100);});
        h.runAtTickTime(28,()->{check(h,fan,ids,panel,-100);drive(h,source,0);});
        h.runAtTickTime(36,()->{check(h,fan,ids,panel,0);DynamicGameTests.audit(h);h.succeed();});
    }
    private static void drive(GameTestHelper h,BlockPos source,float voltage){((CreativeSourceBlockEntity)h.getBlockEntity(source)).setValue(voltage);}
    private static double resistance(){try{
        Object value=CEEConfigs.server().resistanceValues.getClass().getField("electricFanResistance").get(CEEConfigs.server().resistanceValues);
        return ((Number)value.getClass().getMethod("get").invoke(value)).doubleValue();
    }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static void check(GameTestHelper h,BlockPos fan,int[] ids,boolean panel,double voltage){
        var pos=h.absolutePos(fan);var a=new InWorldNode(ids[0],pos);var b=new InWorldNode(ids[1],pos);
        var results=InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
        h.assertTrue(results instanceof CeeResultsAvailability available&&available.available(a,b),"Fan reading unavailable");
        double resistance=resistance();
        double[] av=results.getVoltages(a),bv=results.getVoltages(b);
        h.assertTrue(av.length==16&&bv.length==16,"Fan waveform sample count");
        BoardComponentGameTests.near(h,av[15]-bv[15],voltage,.05,"Fan signed voltage");
        BoardComponentGameTests.near(h,results.getCurrentThrough(a,b),voltage/resistance,.001,"Fan signed current");
        BoardComponentGameTests.near(h,results.getHeatLoss(a,b),voltage*voltage/resistance,.05,"Fan electrical power");
        if(!panel){var device=DevicesSavedData.load(h.getLevel()).getDevice(pos);h.assertTrue(device!=null,"Missing fan device");
            var tag=new CompoundTag();device.write(tag);
            BoardComponentGameTests.near(h,tag.getFloat("TargetSpeed"),Math.signum(voltage)*voltage*voltage/resistance/50,.002,"Fan signed speed target");
            h.assertTrue(Float.isFinite(tag.getFloat("Speed")),"Fan physical speed became non-finite");}
    }
}
