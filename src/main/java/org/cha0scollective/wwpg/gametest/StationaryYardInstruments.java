package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.config.ResistanceValues;
import org.patryk3211.powergrid.electricity.crt.CRTBlockEntity;
import org.patryk3211.powergrid.kinetics.punchcard.PunchCardReaderBlock;
import org.patryk3211.powergrid.kinetics.punchcard.PunchCardReaderBlockEntity;

import java.util.List;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Native screens and mechanical card control, with obvious lamp outputs. */
final class StationaryYardInstruments {
    final StationaryYardStations yard;
    final GameTestHelper h;
    StationaryYardInstruments(StationaryYardStations yard) { this.yard=yard; this.h=yard.h; }
    void build() { for(var block:new Block[]{ModdedBlocks.CRT.get(),ModdedBlocks.ANDESITE_CRT.get(),ModdedBlocks.BRASS_CRT.get()}) crt(block); reader(); }

    void crt(Block block) {
        var s=yard.station(block.getName().getString(),List.of("powergrid:crt"),"Heater feed: beam","Scroll X/Y: move dot","Grid: 0 V to reset");
        yard.feed(s,false); yard.block(s.device(),block); yard.prepareMeter(s);
        var anode=s.source().south(4); var grid=s.source().south(6); var x=s.device().south(4); var y=s.device().south(6);
        for(var pos:new BlockPos[]{anode,x,y}) yard.block(pos,CEEBlocks.CREATIVE_BATTERY.get());
        yard.block(grid,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        yard.configure.add(()->{
            double heater=ResistanceValues.get(ModdedBlocks.CRT.get(),"heater"); double coil=ResistanceValues.get(ModdedBlocks.CRT.get(),"coils");
            yard.source(s.source(),heater); ((org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
            wire(h,s.source(),1,s.control(),0,true); wire(h,s.control(),1,s.device(),1,false); wire(h,s.source(),0,s.device(),0,true);
            yard.source(anode,1000); wire(h,anode,1,s.device(),3,true); wire(h,anode,0,s.device(),0,false);
            yard.source(grid,0); wire(h,grid,1,s.device(),2,true); wire(h,grid,0,s.device(),0,false);
            yard.source(x,coil*.25); yard.source(y,coil*.25);
            wire(h,x,1,s.device(),4,true); wire(h,x,0,s.device(),6,false);
            wire(h,y,0,s.device(),5,false); wire(h,y,1,s.device(),6,true);
            ((CRTBlockEntity)h.getLevel().getBlockEntity(s.device())).setColor(DyeColor.LIME);
            yard.blockMeter(s,s.device(),3,0,1000);
            sign(h,grid.south(),"GRID CUTOFF","0 V = beam ON","Raise V = beam OFF","Polarity reversed");
        });
        yard.check(s,()->{
            h.assertTrue(Math.abs(volts(h,s.device(),1,0))/ResistanceValues.get(ModdedBlocks.CRT.get(),"heater")>.8,"CRT heater is off");
            h.assertTrue(volts(h,s.device(),3,0)>900,"CRT anode is off");
            h.assertTrue(((CRTBlockEntity)h.getLevel().getBlockEntity(s.device())).getColor()==DyeColor.LIME,"CRT color was lost");
        });
    }

    void reader() {
        var s=yard.station("Punch-card reader",List.of("powergrid:punch_card_reader","electroenergetics:indicator_bulb"),
                "Card runs 8 lamps","Reverse motor: reset","Remove / edit card");
        yard.feed(s,false); yard.prepareMeter(s); yard.state(s.device(),ModdedBlocks.PUNCH_CARD_READER.getDefaultState().setValue(PunchCardReaderBlock.HORIZONTAL_FACING,Direction.SOUTH));
        var motor=s.device().east(); yard.state(motor,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.WEST));
        var outputs=new BlockPos[8];
        for(int i=0;i<8;i++) {
            outputs[i]=s.device().offset((i%4)*3,0,3+(i/4)*2);
            yard.state(outputs[i],CEEBlocks.INDICATOR_BULB.getDefaultState().setValue(com.george_vi.electroenergetics.content.indicator_bulb.IndicatorBulbBlock.SIDE,0));
        }
        yard.configure.add(()->{
            yard.source(s.source(),70); scroll(h,motor,-16); ((org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
            wire(h,s.source(),1,s.control(),0,true); wire(h,s.control(),1,s.device(),0,false);
            for(int i=0;i<8;i++) { wire(h,s.device(),i+1,outputs[i],0,i%2==0); wire(h,s.source(),0,outputs[i],1,i%2!=0); }
            var card=ModdedItems.PUNCH_CARD.asStack(); var data=new CompoundTag();
            data.putByteArray("Data",new byte[]{1,2,4,8,16,32,64,(byte)128,85,(byte)170,85,(byte)170,0,85,(byte)170,(byte)255}); card.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            var player=h.makeMockPlayer(GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND,card);
            h.assertTrue(h.getLevel().getBlockState(s.device()).useItemOn(card,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(s.device().getCenter(),Direction.UP,s.device(),false)).consumesAction()&&card.isEmpty(),"Yard card insertion failed");
            yard.blockMeter(s,outputs[0],0,1,70);
            sign(h,motor.south(),"SCAN SPEED","-16 = forward","+16 = rewind","Stop at first row");
        });
        yard.check(s,()->{
            var reader=(PunchCardReaderBlockEntity)h.getLevel().getBlockEntity(s.device()); h.assertTrue(reader.getRedstoneOutput()==15&&!reader.currentItem().isEmpty(),"Punch card did not finish its native scan");
            for(var output:outputs) h.assertTrue(h.getLevel().getBlockEntity(output).saveWithoutMetadata(h.getLevel().registryAccess()).getFloat("FirstLight")>.9,"Punch card lamp is dark");
        });
    }
}
