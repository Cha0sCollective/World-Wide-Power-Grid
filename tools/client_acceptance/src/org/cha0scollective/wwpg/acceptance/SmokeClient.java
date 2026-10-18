package org.cha0scollective.wwpg.acceptance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.patryk3211.powergrid.electricity.gauge.VoltageGaugeBlockEntity;
import java.util.*;

/** Short real-input integrated/TCP check, never a substitute for electrical GameTests. */
final class SmokeClient {
    private final boolean yard=System.getProperty("wwpg.ci.scenario","").startsWith("yard");
    private SmokeSession session;
    private String phase="";
    private BlockPos target;
    private double expected;
    private int age;
    private boolean acked;
    SmokeClient() {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,SmokeSession::commands);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class,SmokeSession::prepare);
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.System.class,event->{
            var message=event.getMessage().getString();if(!message.startsWith("CI_SMOKE "))return;
            var parts=message.split(" ");phase=parts[1];target=new BlockPos(Integer.parseInt(parts[2]),Integer.parseInt(parts[3]),Integer.parseInt(parts[4]));expected=Double.parseDouble(parts[5]);age=0;acked=false;
        });
        if(!Boolean.getBoolean("wwpg.ci.tcp"))NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,event->{
            if(event.getServer().getPlayerList().getPlayers().isEmpty())return;
            if(session==null) {
                if(!yard)RuntimeIdentity.verifyWorkshopSpawn(event.getServer().getPlayerList().getPlayers().getFirst());
                session=new SmokeSession(event.getServer().overworld(),event.getServer().getPlayerList().getPlayers());
            }
            session.tick();
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,this::tick);
    }
    private void tick(ClientTickEvent.Post event) {
        if(ClientShutdown.active())return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        if(mc.level==null&&mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals("Continue")){button.onPress();break;}
        }
        if(mc.level==null||mc.player==null||mc.gameMode==null||phase.isEmpty())return;
        if(phase.equals("done")){WorldWidePowerGrid.LOGGER.info("CI_SMOKE_CLIENT_COMPLETE");ClientShutdown.request();return;}
        if(acked||phase.equals("pause"))return;
        if(++age>600)throw new IllegalStateException("Client never observed "+phase);
        try{
            mc.setScreen(null);
            if(phase.equals("views")) {
                var codes=System.getProperty("wwpg.ci.workshopViews").split(",");
                int index=(age-1)/45;
                if(index>=codes.length){WorldWidePowerGrid.LOGGER.info("CI_ADDON_VISUALS_PASSED");ack(mc);return;}
                int code=Integer.parseInt(codes[index].substring(1))-1;
                var pos=org.cha0scollective.wwpg.gametest.ExtensionApiClientSession.view(code);
                if(age%45==1) {
                    // The panel faces north; photographing its back hides the
                    // custom attachment. The assembled board lies flat.
                    var target=code==10?pos.getCenter().add(0,-.45,0):pos.getCenter();
                    var feet=code==9?pos.getCenter().add(0,-.6,-2.5):
                            code==10?pos.getCenter().add(0,1,-1.5):pos.getCenter().add(2,1,3);
                    aim(mc,target,feet);
                }
                if(age%45!=35)return;
                var entity=mc.level.getBlockEntity(pos);if(entity==null)throw new IllegalStateException("Missing addon view "+codes[index]);
                if(code<9) {
                    if(!SmokeSession.flag(mc.level.getBlockState(pos),"lit"))throw new IllegalStateException("Unpowered addon view");
                    if(mc.getBlockRenderer().getBlockModel(mc.level.getBlockState(pos))==mc.getModelManager().getMissingModel())throw new IllegalStateException("Missing addon model");
                }else if(code==9) {
                    var panel=(com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity)entity;
                    var data=new net.minecraft.nbt.CompoundTag();panel.getAttachments()[com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot.THIRD_LEFT.ordinal()].write(data,true,mc.level.registryAccess());
                    if(data.getFloat("Light")<.95)throw new IllegalStateException("Panel storage indicator dark");
                    var ray=mc.player.pick(6,1,false);
                    if(!(ray instanceof BlockHitResult hit)||!hit.getBlockPos().equals(pos)||hit.getDirection()!=Direction.NORTH)
                        throw new IllegalStateException("Panel attachment view did not face its actual front");
                    WorldWidePowerGrid.LOGGER.info("CI_ADDON_PANEL_FRONT_PASSED: lit native attachment; real north-face ray");
                }else {
                    if(((org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity)entity).getComponentsStream().count()!=3)throw new IllegalStateException("Board components missing");
                    if(mc.player.getEyeY()<pos.getY()+1.5)throw new IllegalStateException("Board view did not expose its top");
                    WorldWidePowerGrid.LOGGER.info("CI_ADDON_BOARD_TOP_PASSED: three synchronized assembled components");
                }
                Screenshot.grab(mc.gameDirectory,codes[index]+".png",mc.getMainRenderTarget(),message->{});return;
            }
            if(phase.equals("tools")) {
                if(age==25){var stack=org.patryk3211.powergrid.collections.ModdedItems.MULTIMETER.asStack();mc.player.setItemInHand(InteractionHand.MAIN_HAND,stack);mc.gameMode.handleCreativeModeItemAdd(stack,36+mc.player.getInventory().selected);}
                if(age==35||age==65){int terminal=age==35?0:1;var point=new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(SmokeSession.API_LOAD,terminal).getExactPosition(mc.level);var outward=point.subtract(SmokeSession.API_LOAD.getCenter()).multiply(1,0,1).normalize();aim(mc,point,SmokeSession.API_LOAD.getCenter().add(outward.scale(2)));}
                if(age==50||age==80){var ray=mc.player.pick(6,1,false);if(!(ray instanceof BlockHitResult hit)||!hit.getBlockPos().equals(SmokeSession.API_LOAD))throw new IllegalStateException("Actual meter ray missed");if(!mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit).consumesAction())throw new IllegalStateException("Meter input rejected");}
                if(age==120){var meter=(org.patryk3211.powergrid.equipment.multimeter.MultimeterItem)mc.player.getMainHandItem().getItem();if(Math.abs(meter.getMeasurement(mc.level,mc.player.getMainHandItem())-20)>.1)throw new IllegalStateException("Meter reading differs");WorldWidePowerGrid.LOGGER.info("CI_INTEGRATED_TOOLS_PASSED");Screenshot.grab(mc.gameDirectory,"meter.png",mc.getMainRenderTarget(),m->{});ack(mc);}return;
            }
            if(age==25&&(phase.equals("off")||phase.equals("on"))&&(!Boolean.getBoolean("wwpg.ci.tcp")||mc.player.getGameProfile().getName().endsWith("A"))) {
                mc.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                boolean sneak=!yard;mc.player.setShiftKeyDown(sneak);
                mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,
                    sneak?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                var point=yard?target.getCenter().add(-.30,0,.499):target.getCenter().add(0,0,.499);
                var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(point,Direction.SOUTH,target,false));
                if(!result.consumesAction())throw new IllegalStateException("Actual control input rejected");
                mc.player.setShiftKeyDown(false);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            }
            if(age<60)return;
            if(phase.equals("audio")) {
                var device=mc.level.getBlockEntity(target);if(device==null)return;
                var field=device.getClass().getDeclaredField("soundInstance");field.setAccessible(true);
                if(field.get(device)==null)return;
            }else {
                var pos=yard?SmokeSession.YARD_LAMP:SmokeSession.API_LOAD;
                var state=mc.level.getBlockState(pos);if(state.isAir())return;
                boolean lit=yard?state.getValue(com.george_vi.electroenergetics.content.bulb.BulbBlock.LIGHT)==15:SmokeSession.flag(state,"lit");
                if(lit==phase.equals("off"))return;
                if(yard&& !phase.equals("off")) {
                    if(!(mc.level.getBlockEntity(SmokeSession.YARD_GAUGE) instanceof VoltageGaugeBlockEntity gauge)||Math.abs(gauge.getValue()-expected)>Math.max(.1,Math.abs(expected)*.01))return;
                }
            }
            Screenshot.grab(mc.gameDirectory,"smoke-"+phase+".png",mc.getMainRenderTarget(),message->{});
            WorldWidePowerGrid.LOGGER.info("CI_SMOKE_CLIENT_PASSED: {}",phase);
            ack(mc);
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private void ack(Minecraft mc){mc.player.connection.sendCommand("wwpg-ci-ack "+phase);acked=true;}
    private void aim(Minecraft mc,net.minecraft.world.phys.Vec3 target,net.minecraft.world.phys.Vec3 feet) {
        var delta=target.subtract(feet.add(0,mc.player.getEyeHeight(),0));
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        mc.player.moveTo(feet.x,feet.y,feet.z,yaw,pitch);
        if(mc.getSingleplayerServer()!=null){var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);player.teleportTo(feet.x,feet.y,feet.z);player.setYRot(yaw);player.setXRot(pitch);});}
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(yaw,pitch,false));
    }
}
