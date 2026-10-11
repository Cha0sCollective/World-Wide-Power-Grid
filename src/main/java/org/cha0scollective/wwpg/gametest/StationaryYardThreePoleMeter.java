package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.energy_meter.TriPolarEnergyMeterDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity;
import org.patryk3211.powergrid.electricity.light.fixture.AbstractLightFixtureBlockEntity;

import java.util.List;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Split DC supply gives two signed metered lines and two obvious native loads. */
final class StationaryYardThreePoleMeter {
    static void build(StationaryYardStations yard) {
        var h=yard.h;var s=yard.station("Three-pole energy",List.of("electroenergetics:tri_polar_energy_meter"),
                "Feed: two lamps ON","About 120 V / 30 W","Meter: about 60 W",
                "The 240 V supply feeds two equal 120 V lamps in series. Relative to their shared middle line, the red lamp is +120 V and the blue lamp is -120 V. Both consume power; the native three-pole meter sums about 60 W. Energy rises slowly in kWh. Turn the feed OFF to stop both lamps, then ON to resume. This is a split DC example; the alternator stations demonstrate AC phases.");
        yard.feed(s,true);yard.block(s.device(),CEEBlocks.TRI_POLAR_ENERGY_METER.get());yard.prepareMeter(s);
        var red=s.device().south(4);var blue=red.east(2);
        yard.block(red,ModdedBlocks.LIGHT_FIXTURE.get());yard.block(blue,ModdedBlocks.LIGHT_FIXTURE.get());
        yard.configure.add(()->{
            yard.source(s.source(),240);((SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
            DevicesSavedData.load(h.getLevel()).getDevice(s.device(),TriPolarEnergyMeterDevice.class).isClosed=true;
            wire(h,s.source(),0,s.control(),0,true);wire(h,s.control(),1,s.device(),0,false);wire(h,s.source(),1,s.device(),2,true);
            wire(h,s.device(),3,red,0,true);wire(h,s.device(),5,blue,0,false);
            wire(h,red,1,s.device(),4,false);wire(h,blue,1,s.device(),4,true);
            var player=h.makeMockPlayer(GameType.SURVIVAL);
            for(var lamp:List.of(red,blue)) {
                var bulb=ModdedItems.LIGHT_BULB.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,bulb);
                player.setItemInHand(InteractionHand.OFF_HAND,(lamp.equals(red)?Items.RED_DYE:Items.BLUE_DYE).getDefaultInstance());
                h.assertTrue(((AbstractLightFixtureBlockEntity)h.getLevel().getBlockEntity(lamp)).replaceBulb(player,InteractionHand.MAIN_HAND,bulb)&&bulb.isEmpty(),"Three-pole lamp rejected its native 120 V bulb");
            }
            yard.blockMeter(s,red,0,1,120);
        });
        yard.check(s,()->{
            near(h,volts(h,red,0,1),120,.12,"Three-pole positive load");
            near(h,volts(h,blue,0,1),-120,.12,"Three-pole negative load");
            for(var lamp:List.of(red,blue))h.assertTrue(((AbstractLightFixtureBlockEntity)h.getLevel().getBlockEntity(lamp)).getPowerLevel()>0,"Three-pole signed load is dark");
            var device=DevicesSavedData.load(h.getLevel()).getDevice(s.device(),TriPolarEnergyMeterDevice.class);
            var entity=(com.george_vi.electroenergetics.content.energy_meter.EnergyMeterBlockEntity)h.getLevel().getBlockEntity(s.device());
            h.assertTrue(device.totalEnergy>0,"Three-pole meter accumulated no signed energy");
            // Native PG filaments change resistance with temperature. Compare
            // metered consumption with the two actual loads, rather than treating
            // their nominal 30 W ratings as constant during warm-up or restart.
            double expected=0;
            for(var lamp:List.of(red,blue)) {
                double voltage=volts(h,lamp,0,1);
                double resistance=((AbstractLightFixtureBlockEntity)h.getLevel().getBlockEntity(lamp)).getBulbState().resistance();
                expected+=voltage*voltage/resistance;
            }
            near(h,entity.activePower,expected,expected*.001,"Three-pole signed load consumption including small contact/wire losses");
        });
    }
}
