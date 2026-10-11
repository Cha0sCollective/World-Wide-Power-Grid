package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.modulardisplay.ModularDisplayBlockEntity;

import java.util.List;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

final class StationaryYardControls {
    final StationaryYardStations yard;
    final GameTestHelper h;
    StationaryYardControls(StationaryYardStations yard) {this.yard=yard;this.h=yard.h;}
    void build() { relay(false);relay(true);redstone(false);redstone(true);potentiometer(false);potentiometer(true);rheostat();servo();display();currentSource();indicator(); }
    void close(StationaryYardStations.Station s,double value,boolean pg) {
        yard.source(s.source(),value); ((org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
        wire(h,s.source(),pg?0:1,s.control(),0,true);
    }
    void relay(boolean pg) {
        var s=yard.station(pg?"PG contactor":"CEE relay",List.of(pg?"powergrid:contactor":"electroenergetics:relay"),"Feed runs relay coil","Relay switches lamp",pg?"30 V coil / 70 V lamp":"6 V coil / 70 V lamp");
        yard.feed(s,!pg);yard.block(s.device(),pg?ModdedBlocks.CONTACTOR.get():CEEBlocks.RELAY.get());yard.prepareMeter(s);
        var coil=pg?s.device().above():s.device(); if(pg) yard.state(coil,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.DOWN));
        var supply=s.source().south(4);var load=s.device().south(4);yard.block(supply,pg?CEEBlocks.CREATIVE_BATTERY.get():ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());yard.block(load,CEEBlocks.INDICATOR_BULB.get());
        yard.configure.add(()->{
            close(s,pg?30:6,!pg);wire(h,s.control(),1,coil,0,false);wire(h,s.source(),pg?0:1,coil,1,true);
            yard.source(supply,70);wire(h,supply,pg?1:0,s.device(),2,false);wire(h,s.device(),3,load,0,true);
            if(pg) {wire(h,supply,0,s.device(),4,false);wire(h,s.device(),5,load,1,true);}else wire(h,supply,1,load,1,false);
            yard.blockMeter(s,load,0,1,70);
        });
        yard.check(s,()->near(h,volts(h,load,0,1),70,.1,"Relay lamp feed"));
    }
    void redstone(boolean pg) {
        var s=yard.station(pg?"Redstone converter":"Redstone relay",List.of(pg?"powergrid:redstone_converter":"electroenergetics:redstone_relay"),"Lever switches lamp","Feed: 70 V supply","Lever OFF: dark");
        yard.feed(s,!pg);yard.block(s.device(),pg?ModdedBlocks.REDSTONE_CONVERTER.get():CEEBlocks.REDSTONE_RELAY.get());yard.prepareMeter(s);
        var lever=s.device().north();yard.state(lever,Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR).setValue(BlockStateProperties.POWERED,true));
        var load=s.device().south(4);yard.block(load,CEEBlocks.INDICATOR_BULB.get());
        yard.configure.add(()->{
            close(s,70,!pg);wire(h,s.control(),1,s.device(),0,false);
            if(pg) {wire(h,s.source(),0,s.device(),1,true);wire(h,s.device(),2,load,0,true);wire(h,s.device(),1,load,1,false);}
            else {wire(h,s.device(),1,load,0,true);wire(h,s.source(),1,load,1,false);}
            yard.blockMeter(s,load,0,1,70);
        });
        yard.check(s,()->{
            double expected=70;
            if(pg) {
                var be=(org.patryk3211.powergrid.electricity.base.ElectricBlockEntity)h.getLevel().getBlockEntity(s.device());
                double lower=1/(1/1000d+1/be.resistance("max"));
                expected=volts(h,s.device(),0,1)*lower/(be.resistance("min")+lower);
            }
            near(h,volts(h,load,0,1),expected,.01,"Loaded native redstone-controlled lamp");
        });
    }
    void potentiometer(boolean redstone) {
        var s=yard.station(redstone?"Redstone divider":"Shaft divider",List.of(redstone?"electroenergetics:redstone_potentiometer":"electroenergetics:potentiometer"),"10 V at initial arm",redstone?"Lever lowers voltage":"Crank lowers voltage","Return arm: reset");
        yard.feed(s,true);yard.block(s.device(),redstone?CEEBlocks.REDSTONE_POTENTIOMETER.get():CEEBlocks.POTENTIOMETER.get());yard.prepareMeter(s);
        var load=s.device().south(4);yard.block(load,ModdedBlocks.CREATIVE_RESISTOR.get());
        if(redstone) yard.state(s.device().north(),Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR));
        else yard.state(s.device().above(),AllBlocks.HAND_CRANK.getDefaultState().setValue(BlockStateProperties.FACING,Direction.DOWN));
        yard.configure.add(()->{
            close(s,10,true);resistance(h,load,100000);scroll(h,s.device(),100000);
            wire(h,s.control(),1,s.device(),0,false);wire(h,s.source(),1,s.device(),2,true);wire(h,s.device(),1,load,0,true);wire(h,s.device(),2,load,1,false);yard.blockMeter(s,load,0,1,10);
        });
        yard.check(s,()->near(h,volts(h,load,0,1),10,.05,"Initial divider arm"));
    }
    void rheostat() {
        var s=yard.station("PG rheostat",List.of("powergrid:rheostat"),"Crank changes voltage","20 V input supply","Goggles: divider arm");
        yard.feed(s,false);yard.state(s.device(),ModdedBlocks.RHEOSTAT.getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH));yard.prepareMeter(s);
        yard.state(s.device().above(),AllBlocks.HAND_CRANK.getDefaultState().setValue(BlockStateProperties.FACING,Direction.DOWN));
        var load=s.device().south(4);yard.block(load,CEEBlocks.CREATIVE_RESISTOR.get());
        yard.configure.add(()->{
            close(s,20,false);scroll(h,s.device(),27);((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setResistance(1000);
            wire(h,s.control(),1,s.device(),0,true);wire(h,s.source(),0,s.device(),2,false);wire(h,s.device(),1,load,0,true);wire(h,s.device(),2,load,1,false);yard.blockMeter(s,load,0,1,20);
        });
        yard.check(s,()->{
            var be=(org.patryk3211.powergrid.kinetics.rheostat.RheostatBlockEntity)h.getLevel().getBlockEntity(s.device());double low=1/(1/(be.getRatio()*be.resistance())+1/1000d);
            near(h,volts(h,load,0,1),20*low/((1-be.getRatio())*be.resistance()+low),.08,"Loaded rheostat divider");
        });
    }
    void servo() {
        var s=yard.station("Servo",List.of("powergrid:servo"),"Control V moves arm","2.5 V -> 180 degrees","0 V -> center");
        yard.feed(s,false);yard.block(s.device(),ModdedBlocks.SERVO.get());yard.prepareMeter(s);var control=s.source().south(4);yard.block(control,CEEBlocks.CREATIVE_BATTERY.get());
        yard.configure.add(()->{yard.switchedPair(s,s.device(),24,false);yard.source(control,2.5);wire(h,control,0,s.device(),1,false);wire(h,control,1,s.device(),2,true);});
        yard.check(s,()->h.assertTrue(h.getLevel().getBlockEntity(s.device()).saveWithoutMetadata(h.getLevel().registryAccess()).getInt("Angle")==180,"Servo arm did not follow control"));
    }
    void display() {
        var s=yard.station("Modular display",List.of("powergrid:modular_display"),"Feed OFF / ON: count","Right-click a module","Saved count persists");
        yard.feed(s,false);yard.block(s.device(),ModdedBlocks.MODULAR_DISPLAY.get());yard.prepareMeter(s);
        yard.configure.add(()->{
            var be=(ModularDisplayBlockEntity)h.getLevel().getBlockEntity(s.device());var player=h.makeMockPlayer(GameType.SURVIVAL);var item=ModdedItems.DISPLAY_MODULE.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,item);be.interact(0,player);h.assertTrue(item.isEmpty(),"Yard display module cost failed");
            close(s,24,false);wire(h,s.control(),1,s.device(),0,true);wire(h,s.source(),0,s.device(),8,false);wire(h,s.source(),0,s.device(),1,true);yard.blockMeter(s,s.device(),0,8,24);
        });
        yard.check(s,()->h.assertTrue(((ModularDisplayBlockEntity)h.getLevel().getBlockEntity(s.device())).modules[0].getIndex()>0,"Display did not count a real pulse"));
    }
    void currentSource() {
        var s=yard.station("Current source",List.of("powergrid:creative_current_source","electroenergetics:resistor"),"0.1 A -> 100 ohms","About 10 V output","Scroll current varies V");
        yard.feed(s,true);yard.block(s.source(),ModdedBlocks.CREATIVE_CURRENT_SOURCE.get());yard.block(s.device(),CEEBlocks.RESISTOR.get());yard.prepareMeter(s);
        yard.configure.add(()->{
            scroll(h,s.source(),1);((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity)h.getLevel().getBlockEntity(s.device())).setResistance(100);
            wire(h,s.source(),0,s.device(),0,true);wire(h,s.source(),1,s.device(),1,false);yard.blockMeter(s,s.device(),0,1,10);
            sign(h,s.control().south(2),"CURRENT SETTING","Source sets current","0.1 A each step","Feed switch unused");
        });
        yard.check(s,()->near(h,volts(h,s.device(),0,1),10,.03,"Native current source"));
    }
    void indicator() {
        var s=yard.station("Two-channel lamp",List.of("electroenergetics:indicator_bulb"),"Both channels lit","70 V = full / 35 = half","Feed OFF: dark");
        yard.feed(s,true);yard.state(s.device(),CEEBlocks.INDICATOR_BULB.getDefaultState().setValue(com.george_vi.electroenergetics.content.indicator_bulb.IndicatorBulbBlock.SIDE,2));yard.prepareMeter(s);
        yard.configure.add(()->{
            yard.switchedPair(s,s.device(),70,true);wire(h,s.control(),1,s.device(),2,true);wire(h,s.source(),1,s.device(),3,false);
        });
        yard.check(s,()->{
            var tag=h.getLevel().getBlockEntity(s.device()).saveWithoutMetadata(h.getLevel().registryAccess());h.assertTrue(tag.getFloat("FirstLight")>.99&&tag.getFloat("SecondLight")>.99,"Both indicator channels must light");
        });
    }
}
