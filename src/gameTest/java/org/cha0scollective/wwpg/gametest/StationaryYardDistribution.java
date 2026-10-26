package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.transmission_distribution.current_transformer.CurrentTransformerBlock;
import com.george_vi.electroenergetics.content.transmission_distribution.current_transformer.CurrentTransformerBlockEntity;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.collections.ModdedBlocks;

import java.util.List;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Structural parts appear only inside their native working assemblies. */
final class StationaryYardDistribution {
    final StationaryYardStations yard;
    final GameTestHelper h;
    StationaryYardDistribution(StationaryYardStations yard) { this.yard=yard; this.h=yard.h; }
    void build() { core(); regulator(); currentTransformer(); pole(); mountedBreaker(); }

    void placeItem(BlockPos support, Direction face, ItemStack item, float yaw) {
        if (yard.restore) return;
        var player=h.makeMockPlayer(GameType.SURVIVAL); player.setYRot(yaw); player.setItemInHand(InteractionHand.MAIN_HAND,item);
        h.assertTrue(item.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(support.getCenter().relative(face,.5),face,support,false))).consumesAction()&&item.isEmpty(),"Native yard assembly failed at "+support);
    }
    void load(BlockPos p) { yard.block(p,ModdedBlocks.CREATIVE_RESISTOR.get()); }
    void close(StationaryYardStations.Station s,double voltage) {
        yard.source(s.source(),voltage); ((org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
        wire(h,s.source(),0,s.control(),0,true);
    }

    void core() {
        var s=yard.station("Core + radiator",List.of("electroenergetics:transformer_core","electroenergetics:radiator_panel"),
                "100 V -> 50 V","Scroll winding turns","Radiator adds cooling");
        yard.feed(s,true); yard.prepareMeter(s); placeItem(s.device().below(),Direction.UP,CEEBlocks.TRANSFORMER_CORE.asStack(),-90);
        yard.block(s.device().above(),CEEBlocks.RADIATOR_PANEL.get()); var output=s.device().south(4); load(output);
        yard.configure.add(()->{
            close(s,100); scroll(h,s.device(),20); scroll(h,s.device().east(),10); resistance(h,output,1000);
            wire(h,s.control(),1,s.device(),0,false); wire(h,s.source(),1,s.device(),1,true);
            wire(h,s.device().east(),0,output,0,false); wire(h,s.device().east(),1,output,1,true);
            yard.blockMeter(s,output,0,1,50);
        });
        yard.check(s,()->{
            near(h,volts(h,output,0,1),49.99375,.03,"Core 20:10 turns");
            var core=DevicesSavedData.load(h.getLevel()).getDevice(s.device(),com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerCoreDevice.class);
            h.assertTrue(core.heatDissipation>0&&CEEBlocks.RADIATOR_PANEL.has(h.getLevel().getBlockState(s.device().above())),"Core radiator is missing");
        });
    }

    void regulator() {
        var s=yard.station("Voltage regulator",List.of("electroenergetics:voltage_regulator"),
                "100 V -> 110 V","Scroll target voltage","Change supply: holds");
        yard.feed(s,true); yard.prepareMeter(s); placeItem(s.device().below(),Direction.UP,CEEBlocks.VOLTAGE_REGULATOR.asStack(),0);
        var output=s.device().south(4); load(output);
        yard.configure.add(()->{
            close(s,100); scroll(h,s.device(),110000); resistance(h,output,1000);
            wire(h,s.control(),1,s.device(),0,false); wire(h,s.source(),1,s.device(),2,true);
            wire(h,s.device(),1,output,0,false); wire(h,s.device(),3,output,1,true); yard.blockMeter(s,output,0,1,110);
        });
        yard.check(s,()->near(h,volts(h,output,0,1),110,.8,"Regulated output"));
    }

    void currentTransformer() {
        var s=yard.station("Current transformer",List.of("electroenergetics:current_transformer"),
                "20 V -> about 2 V","Scroll ratio: 10:1","1:1 = about 20 V");
        yard.feed(s,true); yard.prepareMeter(s);
        yard.state(s.device(),CEEBlocks.CURRENT_TRANSFORMER.getDefaultState().setValue(CurrentTransformerBlock.TOP,true).setValue(CurrentTransformerBlock.BOTTOM,true));
        var output=s.device().south(4); load(output);
        yard.configure.add(()->{
            close(s,20); resistance(h,output,10); ((CurrentTransformerBlockEntity)h.getLevel().getBlockEntity(s.device())).scaling.setValue(4);
            wire(h,s.control(),1,s.device(),0,false); wire(h,s.source(),1,s.device(),1,true);
            wire(h,s.device(),2,output,0,false); wire(h,s.device(),3,output,1,true); yard.blockMeter(s,output,0,1,2);
        });
        yard.check(s,()->near(h,volts(h,output,0,1),2/(1+.01/10+.01/1000),.03,"Current-transformer secondary"));
    }

    void pole() {
        var s=yard.station("Concrete pole",List.of("electroenergetics:concrete_pole"),
                "3-segment power pole","100 V at the load","Break middle: OFF");
        yard.feed(s,true); yard.prepareMeter(s);
        for(int height=0;height<3;height++) placeItem(s.device().above(height).below(),Direction.UP,CEEBlocks.CONCRETE_POLE.asStack(),0);
        var output=s.device().south(4); load(output);
        yard.configure.add(()->{
            close(s,100); resistance(h,output,1000);
            wire(h,s.control(),1,s.device(),0,true); wire(h,s.device().above(2),0,output,0,false); wire(h,s.source(),1,output,1,true); yard.blockMeter(s,output,0,1,100);
        });
        yard.check(s,()->near(h,volts(h,output,0,1),100,.1,"Power through native pole"));
    }

    void mountedBreaker() {
        var s=yard.station("Mounted breaker",List.of("electroenergetics:pole_mount","electroenergetics:insulator","electroenergetics:sulfur_hexafluoride_breaker"),
                "100 V with feed ON","Lever opens breaker","OFF lever = reset");
        yard.feed(s,true); yard.prepareMeter(s);
        var pole=s.device(); var mount=pole.east(); var insulator=mount.above(); var breaker=insulator.above();
        placeItem(pole.below(),Direction.UP,CEEBlocks.CONCRETE_POLE.asStack(),0); placeItem(pole,Direction.EAST,CEEBlocks.POLE_MOUNT.asStack(),0);
        placeItem(mount,Direction.UP,CEEBlocks.INSULATOR.asStack(),0); placeItem(insulator,Direction.UP,CEEBlocks.SF6_BREAKER.asStack(),0);
        var lever=insulator.north(); yard.block(lever.below(),Blocks.STONE); yard.block(lever,Blocks.LEVER);
        var output=pole.south(4); load(output);
        yard.configure.add(()->{
            close(s,100); resistance(h,output,1000);
            wire(h,s.control(),1,breaker,0,true); wire(h,breaker.above(),0,output,0,false); wire(h,s.source(),1,output,1,true); yard.blockMeter(s,output,0,1,100);
        });
        yard.check(s,()->near(h,volts(h,output,0,1),100,.1,"Mounted breaker output"));
    }
}
