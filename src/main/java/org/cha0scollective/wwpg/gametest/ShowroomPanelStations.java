package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.bulb.BulbBlock;
import com.george_vi.electroenergetics.content.electrical_panel.*;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.*;
import com.george_vi.electroenergetics.content.electrical_panel.link.ElectricalPanelLink;
import com.george_vi.electroenergetics.content.electrical_panel.special_interaction.AnalogPanelAttachmentChangeStatePacket;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.link.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.collections.ModdedBlocks;

import java.util.*;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** All eleven built-in panel types, using lamps, meters, and real wireless receivers. */
final class ShowroomPanelStations {
    private record Station(BlockPos base, String name) {BlockPos source(){return base;} BlockPos panel(){return base.east(3);} BlockPos lamp(){return base.east(6);} }
    private final List<Station> stations=new ArrayList<>();
    ShowroomPanelStations(GameTestHelper h,boolean restore) {
        String[] names={"Gauges / light","Cut-off switch","Emergency stop","Momentary switch","Circuit breaker","Energy meter","3-pole meter","Analog lever","Steering wheel"};
        for(int i=0;i<names.length;i++) {
            var station=new Station(new BlockPos(32+(i%5)*16,64,94+(i/5)*14),names[i]);stations.add(station);
            if(restore)continue;
            put(h,station.source(),ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
            h.getLevel().setBlockAndUpdate(station.panel(),CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING,Direction.SOUTH));
            if(i>=7) {
                put(h,station.lamp(),Blocks.REDSTONE_LAMP);
                h.getLevel().setBlockAndUpdate(station.lamp().above(),AllBlocks.REDSTONE_LINK.getDefaultState().setValue(BlockStateProperties.FACING,Direction.UP).setValue(RedstoneLinkBlock.RECEIVER,true));
                // Initialize deferred Create behaviours before the first tick, as item placement does.
                var receiver=(RedstoneLinkBlockEntity)h.getLevel().getBlockEntity(station.lamp().above());
                var receiverTag=new CompoundTag();receiverTag.putBoolean("Transmitter",false);
                receiver.loadCustomOnly(receiverTag,h.getLevel().registryAccess());
            } else put(h,station.lamp(),CEEBlocks.BULB.get());
            if(i==4)put(h,station.lamp().north(3),ModdedBlocks.CREATIVE_RESISTOR.get());
            if(i==6)put(h,station.lamp().north(3),CEEBlocks.BULB.get());
            String action=switch(i){case 2->"Stop; shift-reset";case 3->"Press: lamp flash";case 4->"50 ohms: trips";case 7,8->"Hold / move control";case 0->"300 V / 0.60 A";case 5,6->"Energy total rises";default->"Click empty-handed";};
            sign(h,station.panel().south(3),"P"+(i+1)+" "+station.name,"LIVE CEE panel",action,i>=7?"Wireless lamp":"Watch lamp / meters");
            if(i==4)sign(h,station.lamp().north(4),"OVERLOAD LOAD","100000 ohms: safe","50 ohms: trip","Restore; then reset");
        }
        if(!restore)h.runAtTickTime(15,()->configure(h));
    }
    private PanelAttachment insert(GameTestHelper h,BlockPos at,PanelAttachmentType type,ElectricalPanelSlot slot,String label) {
        var be=(ElectricalPanelBlockEntity)h.getLevel().getBlockEntity(at);
        var attachment=type.createNew(at,type.mode.getNodesFor(at,slot),h.getLevel(),slot,Direction.SOUTH,h.getLevel().registryAccess());
        attachment.label=label;be.getAttachments()[slot.ordinal()]=attachment;be.attachmentUpdate();return attachment;
    }
    private PanelAttachment attachment(GameTestHelper h,Station s) {
        return Arrays.stream(((ElectricalPanelBlockEntity)h.getLevel().getBlockEntity(s.panel())).getAttachments()).filter(Objects::nonNull).findFirst().orElseThrow();
    }
    private void configure(GameTestHelper h) {
        for(int i=0;i<stations.size();i++) {
            var s=stations.get(i);pgSource(h,s.source(),300);
            if(i==0) {
                var amp=insert(h,s.panel(),CEEPanelAttachmentTypes.AMMETER.get(),ElectricalPanelSlot.THIRD_LEFT,"Load current");
                var volt=insert(h,s.panel(),CEEPanelAttachmentTypes.VOLTMETER.get(),ElectricalPanelSlot.THIRD_CENTERED,"Lamp voltage");
                var light=insert(h,s.panel(),CEEPanelAttachmentTypes.INDICATOR_BULB.get(),ElectricalPanelSlot.THIRD_RIGHT,"Powered");
                wire(h,s.source(),0,s.panel(),amp.nodes[0].id(),true);wire(h,s.panel(),amp.nodes[1].id(),s.lamp(),0,false);wire(h,s.source(),1,s.lamp(),1,true);
                for(var a:new PanelAttachment[]{volt,light}){wire(h,s.lamp(),0,s.panel(),a.nodes[0].id(),true);wire(h,s.lamp(),1,s.panel(),a.nodes[1].id(),false);}
            } else if(i<=4) {
                var type=switch(i){case 1->CEEPanelAttachmentTypes.CUT_OFF_SWITCH.get();case 2->CEEPanelAttachmentTypes.ESTOP.get();case 3->CEEPanelAttachmentTypes.MOMENTARY_SWITCH.get();default->CEEPanelAttachmentTypes.MINIATURE_CIRCUIT_BREAKER.get();};
                var a=insert(h,s.panel(),type,i==2?ElectricalPanelSlot.HALF_LEFT:ElectricalPanelSlot.THIRD_LEFT,s.name);
                if(a instanceof CutOffSwitchPanelAttachment cut)cut.isClosed=true;
                if(a instanceof EStopPanelAttachment stop)stop.isClosed=true;
                if(a instanceof MCBPanelAttachment breaker){breaker.setAmperage=1;breaker.isClosed=true;}
                wire(h,s.source(),0,s.panel(),a.nodes[0].id(),true);wire(h,s.panel(),a.nodes[1].id(),s.lamp(),0,false);wire(h,s.source(),1,s.lamp(),1,true);
                if(i==4){var resistor=s.lamp().north(3);resistance(h,resistor,100000);wire(h,s.lamp(),0,resistor,0,true);wire(h,s.lamp(),1,resistor,1,false);}
                ((ElectricalPanelBlockEntity)h.getLevel().getBlockEntity(s.panel())).setChanged();
            } else if(i<=6) {
                var type=i==5?CEEPanelAttachmentTypes.ENERGY_METER.get():CEEPanelAttachmentTypes.TRI_POLAR_ENERGY_METER.get();
                var a=insert(h,s.panel(),type,ElectricalPanelSlot.FULL_SLOT,s.name);
                wire(h,s.source(),0,s.panel(),a.nodes[0].id(),true);wire(h,s.source(),1,s.panel(),a.nodes[1].id(),false);
                if(i==5) {wire(h,s.panel(),a.nodes[2].id(),s.lamp(),0,true);wire(h,s.panel(),a.nodes[3].id(),s.lamp(),1,false);}
                else {wire(h,s.source(),0,s.panel(),a.nodes[2].id(),true);wire(h,s.panel(),a.nodes[3].id(),s.lamp(),0,false);wire(h,s.panel(),a.nodes[5].id(),s.lamp().north(3),0,true);for(var lamp:new BlockPos[]{s.lamp(),s.lamp().north(3)})wire(h,s.panel(),a.nodes[4].id(),lamp,1,false);}
            } else {
                var type=i==7?CEEPanelAttachmentTypes.ANALOG_LEVER.get():CEEPanelAttachmentTypes.STEERING_WHEEL.get();
                var a=insert(h,s.panel(),type,i==7?ElectricalPanelSlot.THIRD_LEFT:ElectricalPanelSlot.QUARTER_CENTER,s.name);
                var link=(ElectricalPanelLink)a;var second=i==7?Items.IRON_INGOT:Items.GOLD_INGOT;
                link.getLinkFrequencies()[0]=new ItemStack(Items.REDSTONE);link.getLinkFrequencies()[1]=new ItemStack(second);
                var receiver=(RedstoneLinkBlockEntity)h.getLevel().getBlockEntity(s.lamp().above());
                var behaviour=receiver.getBehaviour(LinkBehaviour.TYPE);
                behaviour.setFrequency(true,new ItemStack(Items.REDSTONE));behaviour.setFrequency(false,new ItemStack(second));
                analog(h,a,15);
            }
        }
    }
    private void analog(GameTestHelper h,PanelAttachment a,int value) {
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"WWPG-showroom"),ClientInformation.createDefault());
        player.setPos(a.pos.getX()+.5,a.pos.getY(),a.pos.getZ()+.5);
        new AnalogPanelAttachmentChangeStatePacket(a.pos,a.slot.ordinal(),(byte)value).handle(player);
    }
    private void use(GameTestHelper h,PanelAttachment a,boolean shift) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setShiftKeyDown(shift);
        h.assertTrue(a.onInteract(ItemStack.EMPTY,player,InteractionHand.MAIN_HAND,new BlockHitResult(a.pos.getCenter(),Direction.SOUTH,a.pos,false)).consumesAction(),"Exhibit panel control rejected use");
    }
    private void light(GameTestHelper h,Station s,boolean on) {h.assertTrue(h.getLevel().getBlockState(s.lamp()).getValue(BulbBlock.LIGHT)==(on?15:0),s.name+" lamp disagrees with control state");}
    void verify(GameTestHelper h) {
        for(int i=0;i<stations.size();i++) {
            var s=stations.get(i);var a=attachment(h,s);
            if(i==0) {
                var panel=(ElectricalPanelBlockEntity)h.getLevel().getBlockEntity(s.panel());
                near(h,((GaugePanelAttachment)panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()]).value,.6,.02,"Panel ammeter including lamp and indicator");
                near(h,((GaugePanelAttachment)panel.getAttachments()[ElectricalPanelSlot.THIRD_CENTERED.ordinal()]).value,300,1,"Panel voltmeter");
                var tag=new CompoundTag();panel.getAttachments()[ElectricalPanelSlot.THIRD_RIGHT.ordinal()].write(tag,true,h.getLevel().registryAccess());h.assertTrue(tag.getFloat("Light")>.1,"Panel indicator is dark");
            }
            if(i<=6)light(h,s,i!=3);
            if(i==5||i==6){var energy=(BaseEnergyMeterAttachment)a;h.assertTrue(energy.totalEnergy>0&&energy.activePower>80,"Panel energy meter is not accumulating consumption");}
            if(i>=7){h.assertTrue(((RedstoneLinkBlockEntity)h.getLevel().getBlockEntity(s.lamp().above())).getReceivedSignal()==15,"Analog panel did not reach real receiver");h.assertTrue(h.getLevel().getBlockState(s.lamp()).getValue(BlockStateProperties.LIT),"Analog panel receiver did not light its lamp");}
        }
    }
    void checkInteractions(GameTestHelper h) {
        for(int i:new int[]{1,2}) {
            var s=stations.get(i);
            h.runAtTickTime(190,()->use(h,attachment(h,s),false));
            h.runAtTickTime(195,()->{light(h,s,false);var a=attachment(h,s);use(h,a,a instanceof EStopPanelAttachment);});
        }
        var momentary=stations.get(3);h.runAtTickTime(190,()->use(h,attachment(h,momentary),false));
        h.runAtTickTime(192,()->light(h,momentary,true));h.runAtTickTime(205,()->light(h,momentary,false));
        var breaker=stations.get(4);h.runAtTickTime(190,()->resistance(h,breaker.lamp().north(3),50));
        h.runAtTickTime(205,()->{h.assertTrue(!((MCBPanelAttachment)attachment(h,breaker)).isClosed,"Exhibit breaker failed to trip");light(h,breaker,false);resistance(h,breaker.lamp().north(3),100000);use(h,attachment(h,breaker),false);});
        for(int i:new int[]{7,8}) {
            var s=stations.get(i);h.runAtTickTime(190,()->analog(h,attachment(h,s),0));
            h.runAtTickTime(200,()->{h.assertTrue(((RedstoneLinkBlockEntity)h.getLevel().getBlockEntity(s.lamp().above())).getReceivedSignal()==0,"Analog zero did not reach receiver");analog(h,attachment(h,s),15);});
        }
    }
}
