package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.content.bundled_wire.BundledWireTerminationBlock;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.collections.*;
import org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity;
import org.patryk3211.powergrid.electricity.light.fixture.AbstractLightFixtureBlockEntity;
import java.util.List;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** Live family examples; color variants share the corresponding native model. */
final class StationaryYardWiring {
    private final StationaryYardStations yard;
    StationaryYardWiring(StationaryYardStations yard){this.yard=yard;}
    void build() {families();duplex();attachments();}
    private void families() {
        var h=yard.h;
        var types=List.of(CEEWireTypes.COPPER.get(),CEEWireTypes.STANDARD.get(),CEEWireTypes.HEAVILY_INSULATED.get(),CEEWireTypes.CREATIVE.get(),
                CEEWireTypes.IRON.get(),CEEWireTypes.IRON_BUS.get(),CEEWireTypes.IRON_RAIL.get(),CEEWireTypes.ELECTRUM.get(),CEEWireTypes.GLASS_INSULATOR.get());
        for(var type:types) {
            var id=CEERegistries.WIRE_TYPE.getKey(type);boolean decorative=type.isDecorative()||type.getResistance()>=1e10;
            var s=yard.station(id.getPath().replace('_',' ')+" wire",List.of(id.toString()),
                    decorative?"Insulator: lamp OFF":"10 V: lamp lights",decorative?"Carries no power":"Feed stops the lamp","Spool / cutter demo",
                    decorative?"Glass insulation passes essentially no power in CEE 1.1.3. Its dark lamp and near-zero meter reading are expected. The hanging-insulator item can also create a loose native endpoint on a PG terminal.":
                            "The native wire carries power from a PG switch into a CEE lamp. Recover it using an empty spool or cutters, then reconnect with its matching CEE spool. Colored variants use dye in the offhand.");
            yard.feed(s,true);yard.block(s.device(),CEEBlocks.INDICATOR_BULB.get());yard.prepareMeter(s);
            var a=new InWorldNode(1,s.control());var b=new InWorldNode(0,s.device());var connection=new InWorldNodeConnection(a,b);
            yard.configure.add(()->{
                yard.source(s.source(),10);((SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
                wire(h,s.source(),0,s.control(),0,true);wire(h,s.source(),1,s.device(),1,true);
                var player=h.makeMockPlayer(GameType.SURVIVAL);var spool=new ItemStack(type.getSpooledItem());player.setItemInHand(InteractionHand.MAIN_HAND,spool);
                for(var node:List.of(a,b))h.assertTrue(spool.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                        new BlockHitResult(node.getPosition(h.getLevel()),Direction.UP,node.sourcePos(),false))).consumesAction(),"Yard native spool rejected "+id);
                h.assertTrue(spool.isEmpty(),"Yard wire consumed no spool: "+id);yard.blockMeter(s,s.device(),0,1,10);
            });
            yard.check(s,()->{
                var data=InfrastructureSavedData.load(h.getLevel()).getConnectionData(connection);
                h.assertTrue(data!=null&&data.wireType()==type,"Native family cable lost identity: "+id);
                near(h,volts(h,s.device(),0,1),decorative?0:10,decorative?.00001:.01,"Native wire family "+id);
            });
        }
    }
    private void duplex() {
        var h=yard.h;var s=yard.station("Duplex and 12 V bulb",List.of("electroenergetics:duplex_wire_termination","electroenergetics:duplex","electroenergetics:bundle_conductor","powergrid:lv_light_bulb"),
                "About 12 V: lamp ON","One cable, two cores","Feed controls light","Use the duplex spool on the two terminations to reconnect the cable. Some voltage is lost in its wires. Measure current on a breakout wire; equal supply and return currents cancel around the whole cable. The spool retains CEE's native creative-only availability in 1.1.3.",
                "To replace a burned PG bulb, click once to remove it and again with a healthy bulb to install it. Use a 12 V bulb here; ordinary bulbs are rated for 120 V. Switch the feed OFF before repairs.");
        yard.feed(s,false);yard.block(s.device(),ModdedBlocks.LIGHT_FIXTURE.get());yard.prepareMeter(s);
        var a=s.control().south(4);var b=s.device().south(4);
        for(var p:List.of(a,b))yard.state(p,CEEBlocks.DUPLEX_WIRE_TERMINATION.getDefaultState().setValue(BundledWireTerminationBlock.FACING,Direction.UP)
                .setValue(BundledWireTerminationBlock.ROLL,false).setValue(BundledWireTerminationBlock.FLIP,false));
        yard.configure.add(()->{
            yard.source(s.source(),12);((SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).setState(true);
            wire(h,s.source(),1,s.control(),0,true);wire(h,s.control(),1,a,0x1001,true);wire(h,s.source(),0,a,0x1002,false);
            wire(h,b,0x1001,s.device(),0,false);wire(h,b,0x1002,s.device(),1,true);
            var player=h.makeMockPlayer(GameType.SURVIVAL);var spool=CEEItems.DUPLEX_WIRE_SPOOL.asStack();
            BundledWireGameTests.click(h,player,spool,a);BundledWireGameTests.click(h,player,spool,b);
            var bulb=ModdedItems.LV_LIGHT_BULB.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,bulb);player.setItemInHand(InteractionHand.OFF_HAND,Items.RED_DYE.getDefaultInstance());
            h.assertTrue(((AbstractLightFixtureBlockEntity)h.getLevel().getBlockEntity(s.device())).replaceBulb(player,InteractionHand.MAIN_HAND,bulb)&&bulb.isEmpty(),"Native yard LV bulb installation failed");
            yard.blockMeter(s,s.device(),0,1,12);
        });
        yard.check(s,()->{
            h.assertTrue(InfrastructureSavedData.load(h.getLevel()).isConnected(new InWorldNode(0x1000,a),new InWorldNode(0x1000,b)),"Duplex cable is missing");
            var sd=InfrastructureSavedData.load(h.getLevel());
            double series=((SwitchBlockEntity)h.getLevel().getBlockEntity(s.control())).resistance()
                    +pgResistance(s.source(),1,s.control(),0)+pgResistance(s.control(),1,a,0x1001)+pgResistance(b,0x1002,s.device(),1)
                    +sd.getConnectionData(new InWorldNodeConnection(new InWorldNode(0,s.source()),new InWorldNode(0x1002,a))).getResistance()
                    +sd.getConnectionData(new InWorldNodeConnection(new InWorldNode(0x1001,b),new InWorldNode(0,s.device()))).getResistance()
                    +2*CEEWireTypes.BUNDLE_CONDUCTOR.get().getResistance()*sd.getConnectionData(new InWorldNodeConnection(new InWorldNode(0x1000,a),new InWorldNode(0x1000,b))).length;
            double load=((AbstractLightFixtureBlockEntity)h.getLevel().getBlockEntity(s.device())).getBulbState().resistance();
            double expected=12*load/(load+series);
            near(h,volts(h,s.device(),0,1),expected,expected*.001,"Duplex lamp and native series resistance");
            h.assertTrue(((AbstractLightFixtureBlockEntity)h.getLevel().getBlockEntity(s.device())).getPowerLevel()==1,"Native red LV lamp is dark");
        });
    }
    private double pgResistance(net.minecraft.core.BlockPos a,int first,net.minecraft.core.BlockPos b,int second) {
        var level=yard.h.getLevel();
        return org.patryk3211.powergrid.electricity.base.IElectric.getAt(level,a).getBehaviour(level,a,level.getBlockState(a))
                .getConnections().get(new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(a,first)).stream()
                .filter(w->w.isConnectedTo(b,second)).findFirst().orElseThrow().getResistance();
    }
    private void attachments() {
        var h=yard.h;var s=yard.station("Lineman decorations",List.of("electroenergetics:wire_damper","electroenergetics:linemans_stick","electroenergetics:banner","electroenergetics:damper"),
                "Banner and damper","10 V: lamp stays ON","Stick edits offhand","Hold the lineman's stick in your main hand and a banner or wire damper in your offhand, then click the wire. Shift-click with an empty offhand to remove the attachment. Decorations do not change electrical power.");
        yard.feed(s,true);yard.block(s.device(),CEEBlocks.INDICATOR_BULB.get());yard.prepareMeter(s);
        var a=new InWorldNode(1,s.control());var b=new InWorldNode(0,s.device());
        yard.configure.add(()->{
            yard.switchedPair(s,s.device(),10,true);
            var player=h.makeMockPlayer(GameType.SURVIVAL);var stick=CEEItems.LINEMANS_STICK.asStack();player.setItemInHand(InteractionHand.MAIN_HAND,stick);
            player.setItemInHand(InteractionHand.OFF_HAND,Items.RED_BANNER.getDefaultInstance());CEEWireInteractionBehaviours.LINEMANS_STICK.get().interactWire(new NodeConnectionPoint(a,b,.25f),h.getLevel(),player,stick);
            player.setItemInHand(InteractionHand.OFF_HAND,CEEItems.WIRE_DAMPER.asStack());CEEWireInteractionBehaviours.LINEMANS_STICK.get().interactWire(new NodeConnectionPoint(a,b,.75f),h.getLevel(),player,stick);
        });
        yard.check(s,()->{h.assertTrue(InfrastructureSavedData.load(h.getLevel()).getConnectionData(new InWorldNodeConnection(a,b)).attachments().size()==2,"Native decorations are missing");near(h,volts(h,s.device(),0,1),10,.01,"Decorated mixed cable");});
    }
}
