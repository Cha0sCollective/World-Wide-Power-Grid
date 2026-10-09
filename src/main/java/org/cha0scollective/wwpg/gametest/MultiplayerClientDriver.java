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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;
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
        if(phase.equals("failed")){if(++age>=60){mc.stop();phase="";}return;}
        if(!phase.isEmpty()&&mc.level==null&&mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen){mc.stop();phase="";return;}
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
            }else if(phase.equals("meter-voltage")){
                if(a){
                    if(age==25||age==43){
                        int terminal=age==25?1:0;
                        // Walk around the source: its positive and negative
                        // terminals are on opposite faces of an opaque casing.
                        var point=new BlockWireEndpoint(MultiplayerGameTests.SOURCE,terminal).getExactPosition(mc.level);
                        var outward=point.subtract(Vec3.atCenterOf(MultiplayerGameTests.SOURCE)).multiply(1,0,1).normalize();
                        var position=Vec3.atBottomCenterOf(MultiplayerGameTests.SOURCE).add(outward.scale(2));
                        mc.player.setPos(position);
                        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(position.x,position.y,position.z,true));
                        lookAt(mc,point);
                    }
                    if(age==30||age==48)clickItem(mc,MultiplayerGameTests.SOURCE,age==30?1:0);
                    if(age==80){
                        var stack=mc.player.getMainHandItem();var meter=(org.patryk3211.powergrid.equipment.multimeter.MultimeterItem)stack.getItem();
                        WorldWidePowerGrid.LOGGER.info("WWPG probe readings: {}",org.patryk3211.powergrid.equipment.multimeter.MultimeterItem.getModeData(stack));
                        requireNear(meter.getMeasurement(mc.level,stack),20,"Real-client multimeter on CEE terminals");
                        ack(mc,"meter-voltage");phase="waiting";
                    }
                }else{
                    if(age==25)lookAt(mc,pgWirePoint(mc));
                    if(age==35){
                        if(!(mc.hitResult instanceof EntityHitResult hit)||!(hit.getEntity() instanceof WireEntity))throw new IllegalStateException("Clamp ray missed PG wire: "+mc.hitResult);
                        mc.options.keyUse.setDown(true);
                        if(!mc.gameMode.interact(mc.player,hit.getEntity(),InteractionHand.MAIN_HAND).consumesAction())throw new IllegalStateException("Native entity interaction rejected CEE clamp");
                    }
                    if(age==80){verifyClamp(mc,1,"Real-client CEE clamp on PG wire");mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);ack(mc,"meter-voltage");phase="waiting";}
                }
            }else if(phase.equals("meter-pg-current")){
                if(a&&age==25)lookAt(mc,pgWirePoint(mc));
                if(a&&age==35){
                    if(!(mc.hitResult instanceof EntityHitResult hit)||!(hit.getEntity() instanceof WireEntity))throw new IllegalStateException("Multimeter ray missed PG wire");
                    if(!mc.gameMode.interact(mc.player,hit.getEntity(),InteractionHand.MAIN_HAND).consumesAction())throw new IllegalStateException("Native PG multimeter rejected PG wire");
                }
                if(age==80){
                    if(a){var stack=mc.player.getMainHandItem();var meter=(org.patryk3211.powergrid.equipment.multimeter.MultimeterItem)stack.getItem();requireNear(meter.getMeasurement(mc.level,stack),1,"Real-client PG multimeter on mixed PG wire");}
                    ack(mc,"meter-pg-current");phase="waiting";
                }
            }else if(phase.equals("meter-current")){
                if(age==25)lookAt(mc,ceeWirePoint(mc,a?.4f:.65f));
                if(age==35){
                    var target=com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler.targetedPoint;
                    if(target==null)throw new IllegalStateException("Native CEE wire targeting missed with "+mc.player.getMainHandItem());
                    mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
                    if(!a)mc.options.keyUse.setDown(true);
                }
                if(age==80){
                    if(a){var stack=mc.player.getMainHandItem();var meter=(org.patryk3211.powergrid.equipment.multimeter.MultimeterItem)stack.getItem();requireNear(meter.getMeasurement(mc.level,stack),1,"Real-client PG multimeter on CEE wire");}
                    else {verifyClamp(mc,1,"Native CEE clamp regression on CEE wire");mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
                    ack(mc,"meter-current");phase="waiting";
                }
            }
        }catch(Exception e){WorldWidePowerGrid.LOGGER.error("WWPG real client failed",e);ack(mc,"failed");phase="failed";age=0;}
    }
    private static void lookAt(Minecraft mc,BlockPos pos,int terminal){
        var point=new BlockWireEndpoint(pos,terminal).getExactPosition(mc.level);
        if(point==null)throw new IllegalStateException("Client terminal has no geometry");
        lookAt(mc,point);
    }
    private static void lookAt(Minecraft mc,Vec3 point){
        var delta=point.subtract(mc.player.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        mc.player.setYRot(yaw);mc.player.setXRot(pitch);
        // PG independently raycasts on the server, just as it does for a player's
        // normal right click. Publish the view rotation before the interaction.
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(yaw,pitch,mc.player.onGround()));
    }
    private static void clickItem(Minecraft mc,BlockPos pos,int terminal){
        var hit=mc.player.pick(6,0,false);
        if(!(hit instanceof BlockHitResult block)||!block.getBlockPos().equals(pos))throw new IllegalStateException("Meter ray missed "+pos+":"+terminal);
        var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,block);
        var electric=org.patryk3211.powergrid.electricity.base.IElectric.getAt(mc.level,pos);
        int selected=electric==null?-1:electric.terminalIndexAt(mc.level.getBlockState(pos),block.getLocation().subtract(pos.getX(),pos.getY(),pos.getZ()));
        WorldWidePowerGrid.LOGGER.info("WWPG meter click {}:{} (hit ID {}) at {} with {}: {}",pos,terminal,selected,block.getLocation(),mc.player.getMainHandItem(),result);
        if(!result.consumesAction())throw new IllegalStateException("Multimeter rejected terminal: "+result);
    }
    private static Vec3 pgWirePoint(Minecraft mc){
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof org.patryk3211.powergrid.electricity.wire.HangingWireEntity wire&&wire.isConnectedTo(MultiplayerGameTests.SOURCE,1)&&wire.curveParams!=null){
            var points=new java.util.ArrayList<Vec3>();wire.curveParams.runForPoints(20,(x,y,z)->points.add(new Vec3(x,y,z).add(wire.position())));return points.get(10);
        }
        throw new IllegalStateException("Client has no PG wire curve");
    }
    private static Vec3 ceeWirePoint(Minecraft mc,float progress){
        for(var pair:com.george_vi.electroenergetics.client.WireRenderer.getAllConnections()){
            var connection=pair.getFirst();
            if(!connection.node1().sourcePos().equals(MultiplayerGameTests.SOURCE)&&!connection.node2().sourcePos().equals(MultiplayerGameTests.SOURCE))continue;
            if(!connection.node1().sourcePos().equals(MultiplayerGameTests.LOAD)&&!connection.node2().sourcePos().equals(MultiplayerGameTests.LOAD))continue;
            var first=connection.node1().getPosition(mc.level);var second=connection.node2().getPosition(mc.level);
            return com.george_vi.electroenergetics.foundation.QuadraticWireHelper.posAt(first,second,progress,pair.getSecond().getSag(first.distanceTo(second)));
        }
        throw new IllegalStateException("Client has no CEE wire geometry");
    }
    private static void verifyClamp(Minecraft mc,double expected,String label)throws ReflectiveOperationException{
        if(!mc.player.isUsingItem())throw new IllegalStateException(label+": clamp is not active");
        var overlay=com.george_vi.electroenergetics.client.ElectricPropertiesOverlay.INSTANCE;
        var field=overlay.getClass().getDeclaredField("amperage");field.setAccessible(true);
        requireNear(field.getFloat(overlay),expected,label);
    }
    private static void requireNear(double actual,double expected,String label){
        if(!Double.isFinite(actual)||Math.abs(actual-expected)>.08)throw new IllegalStateException(label+": "+actual+" instead of "+expected);
        WorldWidePowerGrid.LOGGER.info("{}: {}",label,actual);
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
