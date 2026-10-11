package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.rotor.AlternatorBrushesBlock;
import com.george_vi.electroenergetics.content.rotor.AlternatorRotorBlock;
import com.george_vi.electroenergetics.content.rotor.StatorBlock;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.kinetics.generator.inductionrotor.CommutatorBlockEntity;
import org.patryk3211.powergrid.kinetics.generator.housing.GeneratorHousing;
import org.patryk3211.powergrid.kinetics.generator.winding.WindingBlockEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;

import java.util.List;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

final class StationaryYardGeneration {
    final StationaryYardStations yard;
    final GameTestHelper h;
    StationaryYardGeneration(StationaryYardStations yard) { this.yard=yard; this.h=yard.h; }
    void build() { alternator(false); alternator(true); generator(false,false); generator(false,true); generator(true,true); housing(false); housing(true); }

    void alternator(boolean three) {
        var s=yard.station(three?"Three-phase alternator":"CEE alternator",three?List.of("electroenergetics:three_phase_alternator_brushes","electroenergetics:synchroscope"):List.of("electroenergetics:alternator_brushes","electroenergetics:alternator_rotor","electroenergetics:stator"),
                "Motor speed makes V","Stop drive: no power",three?"Goggles: AC / phase":"32 RPM: about 48 V");
        var brush=s.device(); var rotor=brush.east(); var drive=rotor.east();
        yard.state(brush,(three?CEEBlocks.THREE_PHASE_ALTERNATOR_BRUSHES:CEEBlocks.ALTERNATOR_BRUSHES).getDefaultState().setValue(AlternatorBrushesBlock.FACING,Direction.WEST));
        yard.state(rotor,CEEBlocks.ALTERNATOR_ROTOR.getDefaultState().setValue(AlternatorRotorBlock.AXIS,Direction.Axis.X));
        yard.state(rotor.above(),CEEBlocks.STATOR.getDefaultState().setValue(StatorBlock.FACING,Direction.DOWN).setValue(StatorBlock.ROLL,true));
        yard.state(drive,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST)); yard.prepareMeter(s);
        var loads=new BlockPos[three?3:1];
        for(int i=0;i<loads.length;i++) {loads[i]=brush.offset(i*3,0,4);yard.block(loads[i],ModdedBlocks.CREATIVE_RESISTOR.get());}
        var phaseMeter=brush.south(6); if(three) yard.block(phaseMeter,CEEBlocks.SYNCHROSCOPE.get());
        yard.configure.add(()->{
            scroll(h,drive,three?128:32);
            for(int i=0;i<loads.length;i++) {
                resistance(h,loads[i],1000); wire(h,brush,i+1,loads[i],0,true); wire(h,brush,0,loads[i],1,false);
                if(three) {wire(h,brush,i+1,phaseMeter,i,true);wire(h,brush,i+1,phaseMeter,i+3,false);}
            }
            yard.blockMeter(s,loads[0],0,1,three?200:50);
            sign(h,s.control().south(2),"MECHANICAL DRIVE","Scroll drive speed","0 RPM = stop","No electrical feed");
        });
        yard.check(s,()->{
            if(three) h.assertTrue(((com.george_vi.electroenergetics.content.synchroscope.SynchroscopeBlockEntity)h.getLevel().getBlockEntity(phaseMeter)).validConnection,"Three-phase connection is invalid");
            else h.assertTrue(volts(h,loads[0],0,1)>40,"Alternator did not power PG load");
        });
    }

    void installCoil(BlockPos start,Direction along,Direction.Axis shaftAxis) {
        if(yard.restore) return;
        var end=start.relative(along,2);
        for(var p:new BlockPos[]{start,end}) yard.state(p,AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS,shaftAxis));
        var player=h.makeMockPlayer(GameType.SURVIVAL); var stack=ModdedItems.COPPER_COIL.asStack(3); player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        for(var p:new BlockPos[]{start,end}) h.assertTrue(stack.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(p.getCenter(),Direction.UP,p,false))).consumesAction(),"Yard winding selection failed");
        h.assertTrue(stack.isEmpty(),"Yard winding cost was not consumed");
    }

    void generator(boolean vertical,boolean large) {
        var s=yard.station(vertical?"Vertical generator":large?"Large generator":"PG generator",List.of(large?"powergrid:generator_large_induction_rotor":"powergrid:generator_induction_rotor",vertical?"powergrid:generator_vertical_commutator":"powergrid:generator_commutator","powergrid:generator_clutch","powergrid:winding","powergrid:device_connector"),
                "Feed = excitation","Drive speed changes V","OFF removes power");
        yard.feed(s,false); yard.prepareMeter(s); var brush=s.device(); var along=vertical?Direction.UP:Direction.SOUTH;
        var rotor=brush.relative(along); var clutch=rotor.relative(along); var drive=clutch.relative(along);
        yard.state(brush,(vertical?ModdedBlocks.GENERATOR_VERTICAL_COMMUTATOR:ModdedBlocks.GENERATOR_COMMUTATOR).getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));
        yard.state(rotor,(large?ModdedBlocks.GENERATOR_LARGE_INDUCTION_ROTOR:ModdedBlocks.GENERATOR_INDUCTION_ROTOR).getDefaultState().setValue(BlockStateProperties.AXIS,vertical?Direction.Axis.Y:Direction.Axis.Z));
        yard.state(clutch,ModdedBlocks.GENERATOR_CLUTCH.getDefaultState().setValue(BlockStateProperties.FACING,along));
        yard.state(drive,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,along.getOpposite()));
        var coil=vertical?brush.east(2):brush.above(large?2:1); var connector=vertical?coil.east():coil.above();
        installCoil(coil,along,vertical?Direction.Axis.X:Direction.Axis.Y);
        yard.state(connector,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,vertical?Direction.WEST:Direction.DOWN));
        var load=brush.south(6);yard.block(load,CEEBlocks.CREATIVE_RESISTOR.get());
        yard.configure.add(()->{
            yard.source(s.source(),5); ((org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true); scroll(h,drive,-64);
            ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity)h.getLevel().getBlockEntity(load)).setResistance(1000);
            wire(h,s.source(),1,s.control(),0,true); wire(h,s.control(),1,connector,0,false); wire(h,s.source(),0,connector,1,true);
            wire(h,brush,0,load,0,false); wire(h,brush,1,load,1,true); yard.blockMeter(s,load,0,1,20);
        });
        yard.check(s,()->h.assertTrue(volts(h,load,0,1)>.1&&((CommutatorBlockEntity)h.getLevel().getBlockEntity(brush)).getPower()>0,
                "Generator output: V="+volts(h,load,0,1)+", P="+((CommutatorBlockEntity)h.getLevel().getBlockEntity(brush)).getPower()+", field="+((WindingBlockEntity)h.getLevel().getBlockEntity(coil)).fieldStrengthCalc().get()));
    }

    void housing(boolean vertical) {
        var s=yard.station(vertical?"Vertical housing":"Generator housing",List.of(vertical?"powergrid:vertical_generator_housing":"powergrid:generator_housing"),
                "Feed excites 2 coils","Goggles: coil current","Remove housing: split");
        yard.feed(s,false); yard.prepareMeter(s); var first=s.device(); var direction=vertical?Direction.UP:Direction.SOUTH;
        var housing=vertical?first.north():first.west(); var second=vertical?housing.west():housing.above();
        installCoil(first,direction,vertical?Direction.Axis.X:Direction.Axis.Y);installCoil(second,direction,vertical?Direction.Axis.Z:Direction.Axis.X);
        var state=(vertical?ModdedBlocks.VERTICAL_GENERATOR_HOUSING:ModdedBlocks.GENERATOR_HOUSING).getDefaultState().setValue(GeneratorHousing.HORIZONTAL_FACING,vertical?Direction.WEST:Direction.EAST);
        if(!vertical) state=state.setValue(GeneratorHousing.UP,true); yard.state(housing,state);
        var connector=vertical?first.east():first.above();yard.state(connector,ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING,vertical?Direction.WEST:Direction.DOWN));
        yard.configure.add(()->yard.switchedPair(s,connector,5,false));
        yard.check(s,()->h.assertTrue(((WindingBlockEntity)h.getLevel().getBlockEntity(first)).fieldStrengthCalc().get()>.001&&((WindingBlockEntity)h.getLevel().getBlockEntity(second)).fieldStrengthCalc().get()>.001,"Housing did not share excitation"));
    }
}
