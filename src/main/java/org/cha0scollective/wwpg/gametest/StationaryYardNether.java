package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.indicator_bulb.IndicatorBulbBlock;
import com.simibubi.create.api.contraption.train.PortalTrackProvider;
import net.createmod.catnip.math.BlockFace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity;
import org.patryk3211.powergrid.electricity.transformer.NetherTransformerBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.List;
import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** A native, walkable portal route with mixed loads on both ends. */
final class StationaryYardNether {
    private final StationaryYardStations yard;
    StationaryYardNether(StationaryYardStations yard) { this.yard=yard; }
    void build() {
        var h=yard.h;var local=h.getLevel();var remote=local.getServer().getLevel(Level.NETHER);
        var s=yard.station("Power through Nether",List.of("powergrid:nether_transformer","powergrid:transformer_core"),
                "70 V at both ends","Enter this portal","Feed OFF: lamp OFF","Follow the white path at the Nether landing. Both native transformer ends are kept loaded in this demonstration.");
        var portal=s.device().east(4);var otherPortal=new BlockPos(portal.getX()/8,64,portal.getZ()/8);var base=portal.west();var primary=base.above();
        var load=s.device().south(3);var secondary=new BlockPos[1];var ready=new boolean[]{false};
        yard.feed(s,false);yard.block(load,ModdedBlocks.CREATIVE_RESISTOR.get());yard.prepareMeter(s);
        StationaryNetherGameTests.force(remote,otherPortal,true);
        if(!yard.restore) {
            for(int x=otherPortal.getX()-7;x<=otherPortal.getX()+7;x++)for(int z=otherPortal.getZ()-7;z<=otherPortal.getZ()+7;z++) {
                remote.setBlockAndUpdate(new BlockPos(x,63,z),Blocks.SMOOTH_STONE.defaultBlockState());
                for(int y=64;y<=70;y++)remote.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            StationaryNetherGameTests.portal(h,local,portal);StationaryNetherGameTests.portal(h,remote,otherPortal);
            sign(h,portal.south(3),"NETHER ROUTE","Lamp at other end","Feed controls both","Walk through portal");
        }
        var sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(local.isPositionEntityTicking(base)&&remote.isPositionEntityTicking(otherPortal),"Waiting for yard portal chunks"));
        if(!yard.restore)sequence.thenExecute(()->StationaryNetherGameTests.assemble(h,local,base));
        sequence.thenWaitUntil(()->h.assertTrue(local.getBlockEntity(primary) instanceof NetherTransformerBlockEntity,"Waiting for native yard Nether assembly"))
                .thenExecute(()->{
                    var found=PortalTrackProvider.getOtherSide(local,new BlockFace(base,Direction.EAST));
                    h.assertTrue(found!=null&&found.level()==remote,"Yard portal found no native linked end");secondary[0]=found.face().getPos().above();
                    StationaryNetherGameTests.force(remote,secondary[0],true);
                    h.assertTrue(remote.getBlockEntity(secondary[0]) instanceof NetherTransformerBlockEntity,"Remote native yard transformer is missing");
                    if(!yard.restore) {
                        var lamp=secondary[0].south(3).below();var meter=lamp.west(2);
                        remote.setBlockAndUpdate(lamp,CEEBlocks.INDICATOR_BULB.getDefaultState().setValue(IndicatorBulbBlock.SIDE,0));remote.setBlockAndUpdate(meter,ModdedBlocks.VOLTAGE_METER.getDefaultState());
                        for(int z=otherPortal.getZ();z<=lamp.getZ()+2;z++)remote.setBlockAndUpdate(new BlockPos(lamp.getX(),63,z),Blocks.WHITE_CONCRETE.defaultBlockState());
                        var pos=lamp.south(2);remote.setBlockAndUpdate(pos,Blocks.OAK_SIGN.defaultBlockState());
                        var sign=(net.minecraft.world.level.block.entity.SignBlockEntity)remote.getBlockEntity(pos);var text=sign.getFrontText();
                        var lines=new String[]{"MIXED NETHER LOAD","70 V / about 70 mA","Overworld feed: ON","Return via portal"};for(int n=0;n<4;n++)text=text.setMessage(n,net.minecraft.network.chat.Component.literal(lines[n]));sign.setText(text,true);sign.setText(text,false);sign.setWaxed(true);sign.setChanged();
                    }
                }).thenIdle(10).thenExecute(()->{
                    if(!yard.restore) {
                        yard.source(s.source(),70);((SwitchBlockEntity)local.getBlockEntity(s.control())).setState(true);resistance(h,load,1000);
                        wire(h,s.source(),1,s.control(),0,true);wire(h,s.control(),1,primary,0,false);wire(h,s.source(),0,primary,1,true);
                        wire(h,primary,0,load,0,false);wire(h,primary,1,load,1,true);yard.blockMeter(s,load,0,1,70);
                        var lamp=secondary[0].south(3).below();var meter=lamp.west(2);
                        ((com.simibubi.create.foundation.blockEntity.SmartBlockEntity)remote.getBlockEntity(meter)).getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).setValue(2);
                        WiringGameTests.connect(h,remote,secondary[0],0,lamp,0,false);WiringGameTests.connect(h,remote,secondary[0],1,lamp,1,true);
                        WiringGameTests.connect(h,remote,lamp,0,meter,0,true);WiringGameTests.connect(h,remote,lamp,1,meter,1,false);
                    }
                    ready[0]=true;
                });
        yard.check(s,()->{
            h.assertTrue(ready[0],"Native Nether yard setup did not finish");var lamp=secondary[0].south(3).below();
            var a=local.getBlockEntity(primary).saveWithoutMetadata(local.registryAccess());var b=remote.getBlockEntity(secondary[0]).saveWithoutMetadata(remote.registryAccess());
            h.assertTrue(a.hasUUID("Link")&&a.getUUID("Link").equals(b.getUUID("Link")),"Yard native link identity changed");
            near(h,volts(h,load,0,1),70,.35,"Local native Nether load");
            near(h,new BlockWireEndpoint(lamp,0).getNode(remote).getVoltage()-new BlockWireEndpoint(lamp,1).getNode(remote).getVoltage(),70,.35,"Remote native Nether load");
            h.assertTrue(remote.getBlockEntity(lamp).saveWithoutMetadata(remote.registryAccess()).getFloat("FirstLight")>.98,"Remote Nether lamp is dark");
        });
    }
}
