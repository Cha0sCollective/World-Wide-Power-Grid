package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEETags;
import com.george_vi.electroenergetics.content.indicator_bulb.IndicatorBulbBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Actual installed holder parts; the separate protection station covers the fuse. */
final class StationaryYardHeldParts {
    private final StationaryYardStations yard;
    StationaryYardHeldParts(StationaryYardStations yard){this.yard=yard;}
    void build(){switchAndIndicator();copperBypass();}
    private void switchAndIndicator(){
        var h=yard.h;
        var s=yard.station("Held switch + lamp",List.of("electroenergetics:fuse_holder"),
                "Click left insert", "Held lamp ON/OFF", "70 V ON / 0 V OFF", "Dye right insert; use a wrench to remove either part.");
        yard.feed(s,true);yard.state(s.device(),CEEBlocks.FUSE_HOLDER.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
        var link=s.device().east(3);yard.state(link,CEEBlocks.CONNECTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));yard.prepareMeter(s);
        yard.configure.add(()->{
            insert(s.device(),true,CEEBlocks.CUT_OFF_SWITCH.asStack());insert(s.device(),false,CEEBlocks.INDICATOR_BULB.asStack());
            use(s.device(),false,Items.LIME_DYE.getDefaultInstance());use(s.device(),true,ItemStack.EMPTY);
            yard.source(s.source(),70);((org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
            wire(h,s.source(),0,s.control(),0,true);wire(h,s.control(),1,s.device(),1,false);
            wire(h,s.device(),3,link,0,true);wire(h,link,0,s.device(),0,false);wire(h,s.source(),1,s.device(),2,true);
            yard.blockMeter(s,s.device(),0,2,70);
        });
        yard.check(s,()->{
            var tag=h.getLevel().getBlockEntity(s.device()).saveWithoutMetadata(h.getLevel().registryAccess());
            h.assertTrue(tag.getString("FirstID").equals("electroenergetics:cut_off_switch")&&tag.getCompound("FirstData").getBoolean("Closed"),"Native held switch is open or missing");
            h.assertTrue(tag.getString("SecondID").equals("electroenergetics:indicator_bulb")&&tag.getCompound("SecondData").getFloat("Light")>.99&&tag.getCompound("SecondData").getString("Color").equals("lime"),"Native held indicator is dark or lost its dye");
            near(h,volts(h,s.device(),0,2),70,.1,"Held switch lamp voltage");
        });
    }
    private void copperBypass(){
        var h=yard.h;
        var s=yard.station("Held copper bypass",List.of("electroenergetics:fuse_holder"),
                "70 V / about 70 mA", "Click copper: OFF", "Copper ingot: repair", "This bypass has no fuse protection; use only the supplied lamp load.");
        yard.feed(s,true);yard.state(s.device(),CEEBlocks.FUSE_HOLDER.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP));
        var lamp=s.device().south(4);yard.state(lamp,CEEBlocks.INDICATOR_BULB.getDefaultState().setValue(IndicatorBulbBlock.SIDE,0));yard.prepareMeter(s);
        yard.configure.add(()->{
            insert(s.device(),true,CEETags.itemFromTag(CEETags.FUSE_BYPASS_ITEM).getDefaultInstance());yard.source(s.source(),70);
            ((org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
            wire(h,s.source(),0,s.control(),0,true);wire(h,s.control(),1,s.device(),1,true);wire(h,s.device(),3,lamp,0,false);wire(h,s.source(),1,lamp,1,true);yard.blockMeter(s,lamp,0,1,70);
        });
        yard.check(s,()->{
            var tag=h.getLevel().getBlockEntity(s.device()).saveWithoutMetadata(h.getLevel().registryAccess());
            h.assertTrue(tag.getString("FirstID").equals("electroenergetics:copper_conductor"),"Native copper bypass is missing");
            h.assertTrue(h.getLevel().getBlockEntity(lamp).saveWithoutMetadata(h.getLevel().registryAccess()).getFloat("FirstLight")>.99,"Copper bypass lamp is dark");
            near(h,volts(h,lamp,0,1),70,.1,"Native copper bypass load");
        });
    }
    private void insert(BlockPos pos,boolean first,ItemStack item){use(pos,first,item);yard.h.assertTrue(item.isEmpty(),"Native yard insert consumed no item");}
    private void use(BlockPos pos,boolean first,ItemStack item){
        var h=yard.h;var player=h.makeMockPlayer(GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,item);
        h.assertTrue(h.getLevel().getBlockState(pos).useItemOn(item,h.getLevel(),player,InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(pos.getCenter().add(first?.25:-.25,.5,0),Direction.UP,pos,false)).consumesAction(),"Native yard held-part interaction failed");
    }
}
