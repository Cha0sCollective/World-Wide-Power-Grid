package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.content.gauge.ElectricGaugeBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.WireEntity;

@EventBusSubscriber(modid="wwpg",value=Dist.CLIENT)
public final class MultiplayerClientDriver {
    private static String phase="";private static int age,bootAge;private static boolean first,connecting;
    @SubscribeEvent public static void chat(ClientChatReceivedEvent.System event){
        if(!Boolean.getBoolean("wwpg.test.multiplayer"))return;
        var text=event.getMessage().getString();if(text.startsWith("WWPG_TEST ")){phase=text.substring(10);age=0;first=false;WorldWidePowerGrid.LOGGER.info("WWPG real client phase: {}",phase);}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("wwpg.test.multiplayer"))return;
        var mc=Minecraft.getInstance();
        if(connecting&&mc.level==null&&phase.isEmpty()&&mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen&&++bootAge>=100){connecting=false;bootAge=39;}
        if(!connecting&&mc.level==null&&mc.getOverlay()==null&&++bootAge>=40){
            connecting=true;
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(),mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25575"),
                    new net.minecraft.client.multiplayer.ServerData("WWPG acceptance","127.0.0.1:25575",net.minecraft.client.multiplayer.ServerData.Type.LAN),false,null);
        }
        if(phase.equals("done")){if(++age==100){WorldWidePowerGrid.LOGGER.info("WWPG_REAL_CLIENT_PASSED");mc.stop();phase="";}return;}
        if(mc.player==null||mc.level==null||mc.gameMode==null||phase.isEmpty())return;
        ++age;boolean a=mc.player.getGameProfile().getName().endsWith("A");
        try{
            if(phase.equals("wire")&&(age==28||age==43))lookAt(mc,age==28?MultiplayerGameTests.SOURCE:MultiplayerGameTests.LOAD,age==28?(a?1:0):(a?0:1));
            if(phase.equals("wire")&&age>=30&&!first&&mc.level.getBlockEntity(MultiplayerGameTests.LOAD)!=null){click(mc,MultiplayerGameTests.SOURCE,a?1:0);first=true;age=30;}
            else if(phase.equals("wire")&&first&&age==45){click(mc,MultiplayerGameTests.LOAD,a?0:1);ack(mc,"wired");phase="waiting";}
            else if(phase.equals("configure")&&age==25){
                var pos=a?MultiplayerGameTests.SOURCE:MultiplayerGameTests.LOAD;
                var be=(SmartBlockEntity)mc.level.getBlockEntity(pos);var behaviour=(ValueSettingsBehaviour)be.getBehaviour(ScrollValueBehaviour.TYPE);
                PacketDistributor.sendToServer(new ValueSettingsPacket(pos,0,a?20:37,null,null,Direction.SOUTH,false,behaviour.netId()));ack(mc,"configured");phase="waiting";
            }else if(phase.equals("verify")&&age==50){
                var gauge=(ElectricGaugeBlockEntity)mc.level.getBlockEntity(MultiplayerGameTests.METER);
                var load=(ResistorBlockEntity)mc.level.getBlockEntity(MultiplayerGameTests.LOAD);
                int wires=0;for(var entity:mc.level.entitiesForRendering())if(entity instanceof WireEntity)++wires;
                if(gauge==null||Math.abs(gauge.voltage-20)>0.15||load==null||Math.abs(load.getValue()-20)>0.01||wires==0)
                    throw new IllegalStateException("Client state: meter="+(gauge==null?null:gauge.voltage)+", resistor="+(load==null?null:load.getValue())+", PG wires="+wires);
                WorldWidePowerGrid.LOGGER.info("WWPG real client verified: voltage={}, resistance={}, rendered PG wire entities={}",gauge.voltage,load.getValue(),wires);ack(mc,"verified");phase="waiting";
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),message->WorldWidePowerGrid.LOGGER.info("WWPG acceptance screenshot: {}",message.getString()));
            }
        }catch(Exception e){WorldWidePowerGrid.LOGGER.error("WWPG real client failed",e);ack(mc,"failed");phase="waiting";}
    }
    private static void lookAt(Minecraft mc,BlockPos pos,int terminal){
        var point=new BlockWireEndpoint(pos,terminal).getExactPosition(mc.level);
        if(point==null)throw new IllegalStateException("Client terminal has no geometry");
        var delta=point.subtract(mc.player.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        mc.player.setYRot(yaw);mc.player.setXRot(pitch);
        // PG independently raycasts on the server, just as it does for a player's
        // normal right click. Publish the view rotation before the interaction.
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(yaw,pitch,mc.player.onGround()));
    }
    private static void click(Minecraft mc,BlockPos pos,int terminal){
        var hit=mc.player.pick(6,0,false);
        if(!(hit instanceof BlockHitResult block)||!block.getBlockPos().equals(pos))throw new IllegalStateException("Client ray missed terminal "+pos+":"+terminal+", hit="+hit);
        var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,block);
        var electric=org.patryk3211.powergrid.electricity.base.IElectric.getAt(mc.level,pos);
        int selected=electric.terminalIndexAt(mc.level.getBlockState(pos),block.getLocation().subtract(pos.getX(),pos.getY(),pos.getZ()));
        WorldWidePowerGrid.LOGGER.info("WWPG real client click {}:{} (PG hit ID {}) at {} using {}: {}",pos,terminal,selected,block.getLocation(),mc.player.getMainHandItem(),result);
        if(selected!=terminal)throw new IllegalStateException("Ray selected terminal "+selected+" instead of "+terminal);
        if(!result.consumesAction())throw new IllegalStateException("Native wire interaction rejected "+pos+":"+terminal+": "+result);
    }
    private static void ack(Minecraft mc,String checkpoint){mc.player.connection.sendCommand("wwpg-test "+checkpoint);}
}
