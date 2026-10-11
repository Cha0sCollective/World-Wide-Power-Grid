package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.battery.BatteryBlockEntity;
import org.patryk3211.powergrid.electricity.carbonpile.CarbonPileCoilBlockEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.febridge.FEInverterBlock;
import org.patryk3211.powergrid.electricity.sparkgap.SparkGapBlockEntity;
import org.patryk3211.powergrid.kinetics.plotter.PlotterBlock;
import org.patryk3211.powergrid.kinetics.plotter.PlotterBlockEntity;

import java.util.List;

import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Native storage, conversion and instrumentation exhibits; construction only. */
final class StationaryYardEquipment {
    final StationaryYardStations yard;
    final GameTestHelper h;
    StationaryYardEquipment(StationaryYardStations yard) { this.yard=yard; this.h=yard.h; }
    void build() { solar(false); solar(true); battery(false); battery(true); plotter(); carbonPile(); sparkGap(); conversion(); }

    void solar(boolean ceiling) {
        var s=yard.station(ceiling?"Ceiling solar":"Solar panel",List.of(ceiling?"powergrid:ceiling_tile_solar":"powergrid:solar_panel"),
                "Sun powers meter","Cover panel: OFF","Remove cover: ON");
        var panel=s.source().above(); var terminal=panel.below();
        if (ceiling) {
            yard.block(panel,ModdedBlocks.CEILING_TILE.get());
            if (!yard.restore) {
                var player=h.makeMockPlayer(GameType.SURVIVAL); var item=ModdedBlocks.SOLAR_PANEL.asStack(); player.setItemInHand(InteractionHand.MAIN_HAND,item);
                var result=h.getLevel().getBlockState(panel).useItemOn(item,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(panel.getCenter(),Direction.UP,panel,false));
                h.assertTrue(result.consumesAction()&&item.isEmpty(),"Ceiling solar assembly failed in yard");
            }
        } else yard.state(panel,ModdedBlocks.SOLAR_PANEL.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
        yard.state(terminal,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.UP));
        yard.block(s.device(),CEEBlocks.CREATIVE_RESISTOR.get()); yard.prepareMeter(s);
        yard.configure.add(()->{
            ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity)h.getLevel().getBlockEntity(s.device())).setResistance(1000);
            wire(h,terminal,0,s.device(),0,true); wire(h,terminal,1,s.device(),1,false);
            yard.blockMeter(s,s.device(),0,1,24);
        });
        yard.check(s,()->h.assertTrue(volts(h,s.device(),0,1)>5&&volts(h,s.device(),0,1)<30,"Sunlight did not reach mixed load"));
    }

    void battery(boolean potato) {
        var s=yard.station(potato?"Potato battery":"Stationary battery",List.of(potato?"powergrid:potato_battery":"powergrid:battery"),
                potato?"Battery -> meter":"Charge switch: ON",potato?"Cannot recharge":"OFF: stored power",potato?"Replace to reset":"About 12 V output");
        yard.feed(s,false); yard.block(s.device(),potato?ModdedBlocks.POTATO_BATTERY.get():ModdedBlocks.BATTERY.get());
        var terminal=potato?s.device():s.device().above();
        if (!potato) yard.state(terminal,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,Direction.DOWN));
        var load=s.device().south(4); var limit=s.control().south(4);
        yard.block(load,CEEBlocks.CREATIVE_RESISTOR.get()); yard.block(limit,ModdedBlocks.CREATIVE_RESISTOR.get()); yard.prepareMeter(s);
        yard.configure.add(()->{
            var battery=(BatteryBlockEntity)h.getLevel().getBlockEntity(s.device()); if(!potato) battery.setEnergy(battery.getCapacity()*.5);
            ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setResistance(potato?100000:1000);
            resistance(h,limit,100);
            wire(h,terminal,0,load,0,false); wire(h,terminal,1,load,1,true);
            if (!potato) {
                yard.source(s.source(),24); ((org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
                wire(h,s.source(),1,s.control(),0,true); wire(h,s.control(),1,limit,0,false); wire(h,limit,1,terminal,0,true); wire(h,s.source(),0,terminal,1,false);
            } else { yard.source(s.source(),0); sign(h,s.control().south(2),"POTATO BATTERY","No charge feed","Native limitation","Replace to reset"); }
            yard.blockMeter(s,terminal,0,1,potato?1:12);
        });
        yard.check(s,()->h.assertTrue(volts(h,load,0,1)>(potato?.1:10)&&((BatteryBlockEntity)h.getLevel().getBlockEntity(s.device())).getEnergy()>0,"Stored energy did not reach CEE load"));
    }

    void plotter() {
        var s=yard.station("Plotter",List.of("powergrid:plotter"),"Feed draws a graph","Open plotter: view","Source: 0 to 1 V");
        yard.feed(s,false); yard.state(s.device(),ModdedBlocks.PLOTTER.getDefaultState().setValue(PlotterBlock.HORIZONTAL_FACING,Direction.NORTH)); yard.prepareMeter(s);
        var motor=s.device().east(); yard.state(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST));
        yard.configure.add(()->{scroll(h,motor,128); yard.switchedPair(s,s.device(),1,false);});
        yard.check(s,()->{
            h.assertTrue(Math.abs(((PlotterBlockEntity)h.getLevel().getBlockEntity(s.device())).getAnimationSpeed())==128,"Plotter lacks shaft drive");
            var samples=h.getLevel().getBlockEntity(s.device()).saveWithoutMetadata(h.getLevel().registryAccess()).getList("Samples",net.minecraft.nbt.Tag.TAG_FLOAT);
            boolean found=false; for(int i=0;i<samples.size();i++) found|=Math.abs(samples.getFloat(i)-.5)<.001;
            h.assertTrue(found,"Plotter did not record powered waveform");
        });
    }

    void carbonPile() {
        var s=yard.station("Carbon pile",List.of("powergrid:carbon_pile","powergrid:carbon_pile_coil"),"Feed varies pile R","Goggles: resistance","Remove coal: OFF");
        yard.feed(s,false); yard.block(s.device(),ModdedBlocks.CARBON_PILE_COIL.get()); yard.block(s.device().above(),Blocks.COAL_BLOCK); yard.prepareMeter(s);
        var supply=s.source().south(4); var load=s.device().south(4); yard.block(supply,CEEBlocks.CREATIVE_BATTERY.get()); yard.block(load,ModdedBlocks.CREATIVE_RESISTOR.get());
        yard.configure.add(()->{
            var coil=(CarbonPileCoilBlockEntity)h.getLevel().getBlockEntity(s.device()); yard.switchedPair(s,s.device(),coil.resistance()*.4,false);
            yard.source(supply,10); resistance(h,load,1000);
            wire(h,supply,1,s.device(),2,false); wire(h,s.device(),3,load,0,true); wire(h,supply,0,load,1,false);
            sign(h,load.south(2),"10 V TEST LOAD","Change coil feed","Goggles: pile R","Replace coal reset");
        });
        yard.check(s,()->{
            h.assertTrue(ModdedBlocks.CARBON_PILE.has(h.getLevel().getBlockState(s.device().above())),"Coal assembly is missing");
            var coil=(CarbonPileCoilBlockEntity)h.getLevel().getBlockEntity(s.device());
            h.assertTrue(1/coil.getPileWire().conductance()>org.patryk3211.powergrid.config.ResistanceValues.get(ModdedBlocks.CARBON_PILE.get())*1.5,"Carbon pile control failed");
        });
    }

    void sparkGap() {
        var s=yard.station("Spark gap",List.of("powergrid:spark_gap"),"Feed makes an arc","1500 V / ballast","OFF stops sparks");
        yard.feed(s,false); yard.block(s.device(),ModdedBlocks.SPARK_GAP.get()); yard.prepareMeter(s);
        var ballast=s.control().south(4); yard.block(ballast,ModdedBlocks.CREATIVE_RESISTOR.get());
        yard.configure.add(()->{
            yard.source(s.source(),1500); resistance(h,ballast,1000); ((org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
            wire(h,s.source(),1,s.control(),0,true); wire(h,s.control(),1,ballast,0,false); wire(h,ballast,1,s.device(),0,true); wire(h,s.source(),0,s.device(),1,false);
            yard.blockMeter(s,s.device(),0,1,1500);
        });
        yard.check(s,()->h.assertTrue(((SparkGapBlockEntity)h.getLevel().getBlockEntity(s.device())).isSparking(),"Gap has no visible arc"));
    }

    void conversion() {
        var s=yard.station("FE conversion",List.of("electroenergetics:converter","powergrid:fe_inverter"),"Electric -> FE -> V","Goggles: both stores","Control V cuts out");
        yard.feed(s,false); var inverter=s.device(); var converter=inverter.above();
        yard.state(inverter,ModdedBlocks.FE_INVERTER.getDefaultState().setValue(FEInverterBlock.FACING,Direction.UP));
        yard.state(converter,CEEBlocks.CONVERTER.getDefaultState().setValue(com.george_vi.electroenergetics.content.converter.ConverterBlock.FACING,Direction.UP));
        var control=s.source().south(4); var load=s.device().south(4); yard.block(control,CEEBlocks.CREATIVE_BATTERY.get()); yard.block(load,CEEBlocks.CREATIVE_RESISTOR.get()); yard.prepareMeter(s);
        yard.configure.add(()->{
            yard.switchedPair(s,converter,100,false); yard.source(control,0);
            ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setResistance(1000);
            wire(h,inverter,0,load,0,false); wire(h,inverter,1,load,1,true); wire(h,control,1,inverter,2,true); wire(h,control,0,inverter,1,false);
            var outputMeter=s.meter().south(2); yard.block(outputMeter,ModdedBlocks.VOLTAGE_METER.get()); scroll(h,outputMeter,2);
            wire(h,inverter,0,outputMeter,0,false); wire(h,inverter,1,outputMeter,1,true);
            sign(h,control.south(2),"OUTPUT CONTROL","0 V = enabled","Raise V = stop","Reset to 0 V");
        });
        yard.check(s,()->h.assertTrue(volts(h,load,0,1)>10,"Native FE exchange did not power CEE load"));
    }
}
