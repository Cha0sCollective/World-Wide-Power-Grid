package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.fuse.FuseDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.electricswitch.HvBreakerBlock;
import org.patryk3211.powergrid.electricity.electricswitch.HvSwitchBlock;

import java.util.List;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

final class StationaryYardProtection {
    final StationaryYardStations yard;
    final GameTestHelper h;
    StationaryYardProtection(StationaryYardStations yard) {this.yard=yard;this.h=yard.h;}
    void build() {bulb();fuse(false);fuse(true);heldFuse();ceeHv();pgHv();breaker();}
    void close(StationaryYardStations.Station s,double voltage,boolean pg) {
        yard.source(s.source(),voltage);((org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
        wire(h,s.source(),pg?0:1,s.control(),0,true);
    }
    void repair(net.minecraft.core.BlockPos pos,ItemStack item) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,item);
        h.assertTrue(h.getLevel().getBlockState(pos).useItemOn(item,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false)).consumesAction(),"Native repair rejected its item");
    }
    void bulb() {
        var s=yard.station("Bulb repair",List.of("electroenergetics:broken_bulb"),"40 V lights the bulb","1000 V breaks it","40 V + copper: reset");
        yard.feed(s,true);yard.block(s.device(),CEEBlocks.BULB.get());yard.prepareMeter(s);
        yard.configure.add(()->yard.switchedPair(s,s.device(),40,true));
        if(!yard.restore) {
            h.runAtTickTime(100,()->yard.source(s.source(),1000));
            h.runAtTickTime(130,()->h.assertTrue(CEEBlocks.BROKEN_BULB.has(h.getLevel().getBlockState(s.device())),"Yard overload did not break native bulb"));
            h.runAtTickTime(140,()->{yard.source(s.source(),40);repair(s.device(),CEEItems.COPPER_WIRE.asStack());});
        }
        yard.check(s,()->h.assertTrue(CEEBlocks.BULB.has(h.getLevel().getBlockState(s.device()))&&h.getLevel().getBlockState(s.device()).getValue(com.george_vi.electroenergetics.content.bulb.BulbBlock.LIGHT)>0,"Repaired bulb has no light"));
    }
    void fuse(boolean pg) {
        var s=yard.station(pg?"PG fuse repair":"CEE fuse repair",pg?List.of("powergrid:fuse_holder"):List.of("electroenergetics:fuse","electroenergetics:broken_fuse"),
                "1 A protection","Load 1 ohm: trips",pg?"1000 ohm + iron wire":"1000 ohm + copper");
        yard.feed(s,!pg);yard.block(s.device(),pg?ModdedBlocks.FUSE_HOLDER.get():CEEBlocks.FUSE.get());yard.prepareMeter(s);var load=s.device().south(4);
        yard.block(load,pg?CEEBlocks.CREATIVE_RESISTOR.get():ModdedBlocks.CREATIVE_RESISTOR.get());
        yard.configure.add(()->{
            close(s,20,!pg);setLoad(load,pg,1000);
            if(pg) {scroll(h,s.device(),1);repair(s.device(),ModdedItems.IRON_WIRE.asStack());}
            else DevicesSavedData.load(h.getLevel()).getDevice(s.device(),FuseDevice.class).setAmperage=1;
            wire(h,s.control(),1,s.device(),0,true);wire(h,s.device(),1,load,0,false);wire(h,s.source(),pg?0:1,load,1,true);yard.blockMeter(s,load,0,1,20);
        });
        if(!yard.restore) {
            h.runAtTickTime(100,()->setLoad(load,pg,1));
            h.runAtTickTime(150,()->{
                h.assertTrue(Math.abs(volts(h,load,0,1))<.001,"Tripped yard fuse retained power");setLoad(load,pg,1000);
                if(pg) h.assertTrue(((org.patryk3211.powergrid.electricity.fuse.FuseHolderBlockEntity)h.getLevel().getBlockEntity(s.device())).removeBlown(),"Native blown fuse removal failed");
                repair(s.device(),pg?ModdedItems.IRON_WIRE.asStack():CEEItems.COPPER_WIRE.asStack());
            });
        }
        yard.check(s,()->near(h,volts(h,load,0,1),20,.1,"Repaired native fuse"));
    }
    void setLoad(net.minecraft.core.BlockPos pos,boolean cee,double resistance) {
        if(cee) ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity)h.getLevel().getBlockEntity(pos)).setResistance(resistance);
        else resistance(h,pos,resistance);
    }
    void heldFuse() {
        var s=yard.station("Held CEE fuse",List.of("electroenergetics:fuse_holder"),"Native inserted fuse","1 A rating / 20 V","Swap fuse to repair");
        yard.feed(s,true);yard.state(s.device(),CEEBlocks.FUSE_HOLDER.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));yard.prepareMeter(s);var load=s.device().south(4);yard.block(load,ModdedBlocks.CREATIVE_RESISTOR.get());
        yard.configure.add(()->{
            var item=CEEBlocks.FUSE.asStack();item.set(CEEDataComponents.FUSE_AMPERAGE,1);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,item);
            h.assertTrue(h.getLevel().getBlockState(s.device()).useItemOn(item,h.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(s.device().getCenter().add(.25,.5,0),Direction.UP,s.device(),false)).consumesAction()&&item.isEmpty(),"Yard held fuse insertion failed");
            var tag=h.getLevel().getBlockEntity(s.device()).saveWithoutMetadata(h.getLevel().registryAccess());boolean first=tag.contains("FirstID");close(s,20,true);resistance(h,load,1000);
            wire(h,s.control(),1,s.device(),first?1:0,true);wire(h,s.device(),first?3:2,load,0,false);wire(h,s.source(),1,load,1,true);yard.blockMeter(s,load,0,1,20);
        });
        yard.check(s,()->near(h,volts(h,load,0,1),20,.1,"Inserted fuse"));
    }
    void ceeHv() {
        var s=yard.station("CEE HV switch",List.of("electroenergetics:high_voltage_switch"),"Click big switch","Watch moving contact","100 V when closed");
        yard.feed(s,true);yard.state(s.device(),CEEBlocks.HV_SWITCH.getDefaultState().setValue(com.george_vi.electroenergetics.content.transmission_distribution.hv_switch.HVSwitchBlock.FACING,Direction.SOUTH));yard.prepareMeter(s);
        var target=s.device().south(2);var load=s.device().south(5);yard.state(target,CEEBlocks.CONNECTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));yard.block(load,ModdedBlocks.CREATIVE_RESISTOR.get());
        yard.configure.add(()->{
            close(s,100,true);resistance(h,load,1000);wire(h,s.control(),1,s.device(),0,true);wire(h,target,0,load,0,false);wire(h,s.source(),1,load,1,true);yard.blockMeter(s,load,0,1,100);
            repair(s.device(),ItemStack.EMPTY);
        });
        yard.check(s,()->near(h,volts(h,load,0,1),100,.1,"Native long-contact switch"));
    }
    net.minecraft.core.BlockPos drive(StationaryYardStations.Station s,net.minecraft.world.level.block.state.BlockState state,com.simibubi.create.content.kinetics.base.IRotate block) {
        Direction shaft=null;for(var d:Direction.values()) if(block.hasShaftTowards(h.getLevel(),s.device(),state,d)) {shaft=d;break;}
        h.assertTrue(shaft!=null,"Yard switch has no native shaft");var drive=s.device().relative(shaft);yard.state(drive,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,shaft.getOpposite()));return drive;
    }
    void pgHv() {
        var s=yard.station("PG HV switch",List.of("powergrid:hv_switch"),"Drive +64: close","Drive -64: open","Watch contact / gauge");
        yard.feed(s,false);var state=ModdedBlocks.HV_SWITCH.getDefaultState().setValue(HvSwitchBlock.PART,0).setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH);yard.state(s.device(),state);yard.state(s.device().south(),state.setValue(HvSwitchBlock.PART,1));yard.prepareMeter(s);
        var drive=drive(s,state,ModdedBlocks.HV_SWITCH.get());var load=s.device().south(4);yard.block(load,CEEBlocks.CREATIVE_RESISTOR.get());
        yard.configure.add(()->{
            close(s,10,false);setLoad(load,true,1000);scroll(h,drive,64);wire(h,s.control(),1,s.device(),0,true);wire(h,s.device().south(),1,load,0,false);wire(h,s.source(),0,load,1,true);yard.blockMeter(s,load,0,1,10);
        });
        if(!yard.restore) h.runAtTickTime(26,()->{if(((org.patryk3211.powergrid.electricity.electricswitch.HvSwitchBlockEntity)h.getLevel().getBlockEntity(s.device())).getSpeed()<0) scroll(h,drive,-64);});
        yard.check(s,()->near(h,volts(h,load,0,1),10,.1,"Mechanically closed switch"));
    }
    void breaker() {
        var s=yard.station("PG HV breaker",List.of("powergrid:hv_breaker"),"Drive charges spring","Lever ON: close","Load 1 ohm: trips");
        yard.feed(s,false);yard.block(s.device(),ModdedBlocks.HV_BREAKER.get());yard.prepareMeter(s);var drive=drive(s,ModdedBlocks.HV_BREAKER.getDefaultState(),ModdedBlocks.HV_BREAKER.get());
        var lever=s.device().north();yard.state(lever,Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR));var load=s.device().south(4);yard.block(load,CEEBlocks.CREATIVE_RESISTOR.get());
        yard.configure.add(()->{
            close(s,10,false);setLoad(load,true,1000);scroll(h,s.device(),1);scroll(h,drive,256);wire(h,s.control(),1,s.device(),0,true);wire(h,s.device(),1,load,0,false);wire(h,s.source(),0,load,1,true);yard.blockMeter(s,load,0,1,10);
        });
        if(!yard.restore) h.runAtTickTime(80,()->h.getLevel().setBlockAndUpdate(lever,h.getLevel().getBlockState(lever).setValue(BlockStateProperties.POWERED,true)));
        yard.check(s,()->near(h,volts(h,load,0,1),10,.1,"Charged breaker closed"));
    }
}
