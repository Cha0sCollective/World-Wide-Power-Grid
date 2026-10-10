package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.bulb.BulbBlock;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice;
import com.george_vi.electroenergetics.content.electronic_components.inductor.InductorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.electricswitch.SwitchBlock;
import org.patryk3211.powergrid.electricity.gauge.VoltageGaugeBlockEntity;
import org.patryk3211.powergrid.equipment.portablebattery.PortableBatteryBlockEntity;

import java.util.*;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Additional controls, eight connector families, motors, and stationary storage. */
final class ShowroomDeviceStations {
    private static final String[] CONTROLS={"electroenergetics:cut_off_switch","electroenergetics:double_switch","electroenergetics:emergency_stop_button","electroenergetics:momentary_switch","powergrid:lv_switch","powergrid:mv_switch","powergrid:lv_button"};
    private static final String[] CONNECTORS={"electroenergetics:connector","electroenergetics:double_connector","electroenergetics:triple_connector","electroenergetics:quad_connector","electroenergetics:duplex_wire_termination","powergrid:wire_connector","powergrid:heavy_wire_connector","powergrid:ceiling_tile_connector"};
    private static final String[] OTHER={"electroenergetics:white_electric_motor","powergrid:electric_motor","electroenergetics:capacitor","electroenergetics:high_voltage_capacitor","electroenergetics:inductor","powergrid:portable_battery","electroenergetics:accumulator"};
    private static final String[] LABELS={"CEE cut-off","CEE double","CEE e-stop","CEE momentary","PG LV switch","PG MV switch","PG LV button","CEE connector","CEE double","CEE triple","CEE quad","CEE duplex","PG connector","PG heavy","PG ceiling","CEE motor","PG motor","CEE capacitor","CEE HV cap","CEE inductor","PG battery","CEE accumulator"};
    private record Station(BlockPos base,String id,int kind) {BlockPos device(){return base.east(3);}BlockPos load(){return base.east(7);}BlockPos gauge(){return load().south(3);}boolean pg(){return id.startsWith("powergrid:");} }
    private final List<Station> stations=new ArrayList<>();
    ShowroomDeviceStations(GameTestHelper h,boolean restore) {
        int index=0;
        for(int kind=0;kind<3;kind++)for(var id:kind==0?CONTROLS:kind==1?CONNECTORS:OTHER) {
            var b=new BlockPos(32+(index%5)*16,64,176+(index/5)*14);index++;
            var s=new Station(b,id,kind);stations.add(s);
            if(restore) {
                if(id.equals("electroenergetics:capacitor"))h.assertTrue(DevicesSavedData.load(h.getLevel()).getDevice(s.device(),CapacitorDevice.class).lastVoltage>9.8,"Yard CEE capacitor lost saved voltage before recharging");
                if(id.equals("electroenergetics:high_voltage_capacitor"))h.assertTrue(DevicesSavedData.load(h.getLevel()).getDevice(s.device(),com.george_vi.electroenergetics.content.transmission_distribution.hv_capacitor.HVCapacitorDevice.class).lastVoltage>9.8,"Yard HV capacitor lost saved voltage before recharging");
                continue;
            }
            put(h,b,s.pg()?CEEBlocks.CREATIVE_BATTERY.get():ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
            put(h,s.device(),BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)));
            put(h,s.load(),kind==0?CEEBlocks.BULB.get():ModdedBlocks.CREATIVE_RESISTOR.get());
            put(h,s.gauge(),ModdedBlocks.VOLTAGE_METER.get());
            String action=kind==0?(id.contains("emergency")?"Stop; shift-reset":id.contains("momentary")||id.equals("powergrid:lv_button")?"Press: lamp flash":"Click: lamp changes"):kind==1?"Cut / rewire":id.endsWith("motor")?"V changes speed":id.contains("portable")?"Charges energy item":id.endsWith("accumulator")?"Open charger: test":"Source off: watch V";
            sign(h,s.device().south(5),"D"+index+" "+LABELS[index-1],"LIVE mixed circuit",action,"Gauge at load");
        }
        if(!restore)h.runAtTickTime(20,()->connect(h));
    }
    private void connect(GameTestHelper h) {
        for(var s:stations) {
            double voltage=s.kind==0?150:s.kind==1?10:s.id.endsWith("motor")?100:s.id.contains("battery")?24:s.id.endsWith("accumulator")?26:10;
            if(s.pg())scroll(h,s.base,(int)(voltage*1000));else pgSource(h,s.base,voltage);
            scroll(h,s.gauge(),voltage>20?2:1);
            if(s.kind!=0)resistance(h,s.load(),s.id.endsWith("inductor")?10:s.id.endsWith("accumulator")?100:1000);
            int positive=s.pg()?1:0,negative=1-positive;
            if(s.kind==0) {
                boolean two=s.id.endsWith("double_switch");wire(h,s.base,positive,s.device(),0,false);wire(h,s.device(),two?2:1,s.load(),0,true);
                if(two){wire(h,s.base,negative,s.device(),1,false);wire(h,s.device(),3,s.load(),1,true);}else wire(h,s.base,negative,s.load(),1,false);
                if(s.pg()&&!s.id.equals("powergrid:lv_button"))use(h,s,false);
            } else if(s.kind==1) {
                var state=h.getLevel().getBlockState(s.device());
                int port=state.getBlock() instanceof com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock<?> device?device.getNodePositions(h.getLevel(),s.device(),state).keySet().stream().filter(n->device.isNodeAccessible(h.getLevel(),s.device(),state,n)).mapToInt(Integer::intValue).min().orElseThrow():0;
                wire(h,s.base,positive,s.device(),port,true);wire(h,s.device(),port,s.load(),0,false);wire(h,s.base,negative,s.load(),1,false);
            } else if(s.id.endsWith("motor")||s.id.contains("portable_battery")) {
                wire(h,s.base,positive,s.device(),0,true);wire(h,s.base,negative,s.device(),1,false);
                wire(h,s.device(),0,s.load(),0,false);wire(h,s.device(),1,s.load(),1,true);
            } else if(s.id.endsWith("accumulator")) {
                var d=DevicesSavedData.load(h.getLevel()).getDevice(s.device(),com.george_vi.electroenergetics.content.accumulator.AccumulatorDevice.class);
                d.cell1Charge=com.george_vi.electroenergetics.foundation.electrical_properties.AccumulatorProperties.getNominalCharge()*.5;d.properties1.storedCharge=d.cell1Charge;
                // A series switch disconnects charging without shorting the accumulator.
                var cut=s.device().north(3);put(h,cut,CEEBlocks.CUT_OFF_SWITCH.get());
                wire(h,s.base,positive,cut,0,false);wire(h,cut,1,s.device(),0,true);wire(h,s.base,negative,s.device(),1,false);
                wire(h,s.device(),0,s.load(),0,true);wire(h,s.device(),1,s.load(),1,false);
                sign(h,cut.north(2),"CHARGER SWITCH","Click: disconnect","Load stays powered","100-ohm load drains");
            } else {
                var sd=DevicesSavedData.load(h.getLevel());
                if(s.id.endsWith("inductor"))sd.getDevice(s.device(),InductorDevice.class).inductance=1;
                else if(s.id.contains("high_voltage"))sd.getDevice(s.device(),com.george_vi.electroenergetics.content.transmission_distribution.hv_capacitor.HVCapacitorDevice.class).capacitance=.01;
                else sd.getDevice(s.device(),CapacitorDevice.class).capacitance=.01;
                // The creative resistor is the charge ballast; the gauge is across storage.
                resistance(h,s.load(),s.id.endsWith("inductor")?10:100);
                wire(h,s.base,positive,s.load(),0,true);wire(h,s.load(),1,s.device(),0,false);wire(h,s.base,negative,s.device(),1,true);
            }
            var measured=s.kind==2?s.device():s.load();wire(h,measured,0,s.gauge(),0,false);wire(h,measured,1,s.gauge(),1,true);
        }
    }
    private void use(GameTestHelper h,Station s,boolean shift) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);p.setShiftKeyDown(shift);var state=h.getLevel().getBlockState(s.device());var hit=new BlockHitResult(s.device().getCenter(),Direction.UP,s.device(),false);
        boolean accepted=s.pg()?((SwitchBlock)state.getBlock()).use(state,h.getLevel(),s.device(),p,InteractionHand.MAIN_HAND,hit).consumesAction():state.useItemOn(ItemStack.EMPTY,h.getLevel(),p,InteractionHand.MAIN_HAND,hit).consumesAction();
        h.assertTrue(accepted,"Device exhibit rejected native interaction: "+s.id);
    }
    void verify(GameTestHelper h) {
        for(var s:stations) {
            double voltage=((VoltageGaugeBlockEntity)h.getLevel().getBlockEntity(s.gauge())).getValue();
            if(s.kind==0)near(h,voltage,s.id.contains("momentary")||s.id.equals("powergrid:lv_button")?0:150,1,s.id+" control lamp");
            if(s.kind==1)near(h,voltage,10,.1,s.id+" connector output");
            if(s.kind==2) {
                if(s.id.equals("electroenergetics:white_electric_motor"))h.assertTrue(Math.abs(((com.george_vi.electroenergetics.content.electric_motor.ElectricMotorBlockEntity)h.getLevel().getBlockEntity(s.device())).getGeneratedSpeed())>0,"CEE motor has no shaft output");
                else if(s.id.equals("powergrid:electric_motor"))h.assertTrue(Math.abs(((org.patryk3211.powergrid.kinetics.motor.ElectricMotorBlockEntity)h.getLevel().getBlockEntity(s.device())).getGeneratedSpeed())>0,"PG motor has no shaft output");
                else if(s.id.contains("portable_battery"))h.assertTrue(((PortableBatteryBlockEntity)h.getLevel().getBlockEntity(s.device())).getCharge()>0,"PG portable battery is not charging");
                else if(s.id.endsWith("accumulator"))h.assertTrue(voltage>20&&DevicesSavedData.load(h.getLevel()).getDevice(s.device(),com.george_vi.electroenergetics.content.accumulator.AccumulatorDevice.class).cell1Charge>0,"Accumulator has no stored output");
                else if(s.id.endsWith("inductor"))h.assertTrue(Math.abs(volts(h,s.load(),0,1))/10>.5,"Inductor has no DC current");
                else near(h,voltage,10,.15,s.id+" charged voltage");
            }
        }
    }
    void checkInteractions(GameTestHelper h) {
        for(var s:stations)if(s.kind==0) {
            h.runAtTickTime(190,()->use(h,s,false));boolean momentary=s.id.contains("momentary")||s.id.equals("powergrid:lv_button");
            h.runAtTickTime(192,()->near(h,((VoltageGaugeBlockEntity)h.getLevel().getBlockEntity(s.gauge())).getValue(),momentary?150:0,1,s.id+" player interaction"));
            if(!momentary)h.runAtTickTime(200,()->use(h,s,s.id.contains("emergency")));
        }
    }
}
