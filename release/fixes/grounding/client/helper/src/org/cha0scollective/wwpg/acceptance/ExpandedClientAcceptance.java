package org.cha0scollective.wwpg.acceptance;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.CutOffSwitchPanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.GaugePanelAttachment;
import com.simibubi.create.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.gametest.ShowroomWorldGameTests;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.electricity.gauge.CurrentGaugeBlockEntity;
import org.patryk3211.powergrid.electricity.gauge.VoltageGaugeBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;

import java.util.*;

/** Separate, disposable helper mod. The WWPG jar under test is never modified. */
@Mod("wwpg_acceptance")
public final class ExpandedClientAcceptance {
    private record View(String name, BlockPos target, BlockPos gauge, boolean current) {}
    private final List<View> views = new ArrayList<>();
    private final long started = System.currentTimeMillis();
    private volatile GameTestInfo test;
    private volatile boolean serverPassed;
    private volatile String failure;
    private volatile long serverWires;
    private volatile String serverErrors="";
    private int serverAge, age, index, phase;
    private boolean begun, finishing;
    private final Map<String,Double> serverReadings = new java.util.concurrent.ConcurrentHashMap<>();

    public ExpandedClientAcceptance() {
        for(int i=0;i<25;i++) {
            var base=new BlockPos(32+(i%5)*16,62,8+(i/5)*16);
            views.add(new View(String.format("B%02d",i+1),base.offset(3,2,3),base.offset(8,2,1),true));
        }
        for(int i=0;i<9;i++)views.add(new View("P"+(i+1),new BlockPos(35+(i%5)*16,64,94+(i/5)*14),null,false));
        for(int i=0;i<7;i++){
            var base=new BlockPos(32+(i%5)*16,64,124+(i/5)*16);
            views.add(new View("T"+(i+1),base.east(3),base.offset(7,0,3),false));
        }
        for(int i=0;i<3;i++)views.add(new View("M"+(i+1),new BlockPos(32+i*32,64,156),null,false));
        views.add(new View("B26",new BlockPos(83,64,140),null,false));
        for(int i=0;i<22;i++){
            var base=new BlockPos(32+(i%5)*16,64,176+(i/5)*14);
            views.add(new View("D"+(i+1),base.east(3),base.offset(7,0,3),false));
        }
        views.add(new View("factory",new BlockPos(14,64,24),null,false));
        views.add(new View("relay-panel",new BlockPos(12,64,8),null,false));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,this::serverTick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,this::clientTick);
        WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_HELPER: exact saved world; backend={}; views={}",System.getProperty("wwpg.test.backend"),views.size());
    }

    private void serverTick(ServerTickEvent.Post event) {
        if(failure!=null||finishing||event.getServer().getPlayerList().getPlayers().isEmpty())return;
        var level=event.getServer().overworld();
        try {
            if(test!=null&&event.getServer().getTickCount()%40==0)diagnostic(level);
            if(test==null) {
                if(++serverAge==1)for(int x=0;x<=8;x++)for(int z=0;z<=15;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
                if(serverAge<20)return;
                for(int x=0;x<=8;x++)for(int z=0;z<=15;z++)if(!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x,z))){require(serverAge<300,"Yard chunks never became entity-ready");return;}
                var function=new TestFunction("expanded_client","expanded_client.saved_yard","wwpg_showroom:empty",Rotation.NONE,1000,0,true,ShowroomWorldGameTests::buildAndVerifyExpandedFixtureWorld);
                test=new GameTestInfo(function,Rotation.NONE,level,RetryOptions.noRetries());
                var grid=new StructureGridSpawner(new BlockPos(-512,70,-512),1,false);
                GameTestRunner.Builder.fromInfo(List.of(test),level).newStructureSpawner(grid).existingStructureSpawner(grid).haltOnError(true).build().start();
                WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_BASELINE_STARTED: downloaded yard reopened in integrated server");
            } else {
                if(test.hasFailed())throw new IllegalStateException("Saved-yard acceptance failed",test.getError());
                if(test.hasSucceeded()&&!serverPassed){
                    for(var player:event.getServer().getPlayerList().getPlayers())player.setItemSlot(EquipmentSlot.HEAD,AllItems.GOGGLES.asStack());
                    serverPassed=true;
                    WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_BASELINE_PASSED: all live exhibits and native-handler interactions");
                }
                if(serverPassed)for(var view:views)if(view.gauge!=null){
                    var be=level.getBlockEntity(view.gauge);
                    serverReadings.put(view.name,(double)(view.current?((CurrentGaugeBlockEntity)be).getValue():((VoltageGaugeBlockEntity)be).getValue()));
                }
                if(serverPassed&&event.getServer().getTickCount()%20==0){
                    serverWires=level.getEntitiesOfClass(BaseWireEntity.class,new net.minecraft.world.phys.AABB(0,-64,0,130,320,242)).size();
                    serverErrors=String.join("; ",org.cha0scollective.wwpg.bridge.Bridges.get(level).diagnostics());
                }
            }
        } catch(Throwable problem){fail(problem);}
    }

    private void clientTick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        try {
            if(System.currentTimeMillis()-started>600_000)throw new IllegalStateException("Client acceptance exceeded ten minutes");
            if(failure!=null){if(age==0&&mc.level!=null)shot(mc,"failed-world.png");if(++age>30&&!finishing){finishing=true;mc.stop();}return;}
            if(mc.level==null||mc.player==null||mc.gameMode==null||mc.getSingleplayerServer()==null)return;
            if(mc.screen!=null){mc.setScreen(null);}
            if(!serverPassed)return;
            if(!begun){begun=true;age=0;mc.options.pauseOnLostFocus=false;mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();}
            ++age;
            if(phase==0){
                if(index>=views.size()){phase=1;age=0;move(mc,new Vec3(12.2,64.2,10.5),new Vec3(12.2,64.5,8.9));return;}
                var view=views.get(index);
                if(age==1)move(mc,new Vec3(view.target.getX()+2.3,view.target.getY()+2.4,view.target.getZ()+3.2),Vec3.atCenterOf(view.target));
                if(age<45)return;
                require(mc.level.getBlockEntity(view.target)!=null||!mc.level.getBlockState(view.target).isAir(),"Client did not load "+view.name);
                if(view.name.startsWith("B")){
                    var board=(CircuitBoardBlockEntity)mc.level.getBlockEntity(view.target);
                    require(board!=null&&board.getComponentsStream().findAny().isPresent(),"Client board components missing: "+view.name);
                }
                if(view.gauge!=null){
                    var be=mc.level.getBlockEntity(view.gauge);
                    require(be!=null,"Client gauge missing: "+view.name);
                    double actual=view.current?((CurrentGaugeBlockEntity)be).getValue():((VoltageGaugeBlockEntity)be).getValue();
                    double expected=serverReadings.getOrDefault(view.name,Double.NaN);
                    require(Double.isFinite(actual)&&Double.isFinite(expected)&&Math.abs(actual-expected)<Math.max(.05,Math.abs(expected)*.05),"Client gauge differs at "+view.name+": client="+actual+", server="+expected);
                    WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_READING: {} client={} server={}",view.name,actual,expected);
                }
                if(view.name.startsWith("P")){
                    var panel=(ElectricalPanelBlockEntity)mc.level.getBlockEntity(view.target);
                    require(panel!=null&&Arrays.stream(panel.getAttachments()).anyMatch(Objects::nonNull),"Client panel attachments missing: "+view.name);
                }
                long wires=0;for(var entity:mc.level.entitiesForRendering())if(entity instanceof BaseWireEntity)++wires;
                require(wires>0,"Client has no PG wire entities at "+view.name);
                require(!com.george_vi.electroenergetics.client.WireRenderer.getAllConnections().isEmpty(),"Client has no CEE wire geometry at "+view.name);
                shot(mc,String.format("%02d-%s.png",index+1,view.name));
                WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_VIEW_PASSED: {} rendered PG wires={}",view.name,wires);
                index++;age=0;
            } else if(phase==1){
                if(age==25)clickPanel(mc,new BlockPos(12,64,8));
                if(age==50){require(!factoryControl(mc),"Client panel click did not open factory control");shot(mc,"factory-off-delay.png");}
                if(age==150){require(lamp(mc,new BlockPos(18,64,11))==0&&lamp(mc,new BlockPos(18,64,5))==15,"Client RUN/OFF lamps did not follow board off-delay");shot(mc,"factory-off.png");clickPanel(mc,new BlockPos(12,64,8));}
                if(age==215){require(factoryControl(mc)&&lamp(mc,new BlockPos(18,64,11))==15&&lamp(mc,new BlockPos(18,64,5))==0,"Client factory did not resume after panel click");shot(mc,"factory-on.png");WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_PANEL_PACKETS_PASSED: OFF -> delayed lamp change -> ON");phase=2;age=0;move(mc,new Vec3(52.2,64.2,96.5),new Vec3(51.2,64.5,94.9));}
            } else if(phase==2){
                if(age==25)clickPanel(mc,new BlockPos(51,64,94));
                if(age==50){require(lamp(mc,new BlockPos(54,64,94))==0,"Client P2 panel packet did not turn lamp off");shot(mc,"panel-P2-off.png");clickPanel(mc,new BlockPos(51,64,94));}
                if(age==85){require(lamp(mc,new BlockPos(54,64,94))==15,"Client P2 panel packet did not restore lamp");WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_P2_PACKETS_PASSED");phase=3;age=0;move(mc,new Vec3(65,69,124),new Vec3(65,64,124));}
            } else if(phase==3&&age>100){
                long wires=0,items=0;for(var entity:mc.level.entitiesForRendering()){
                    if(entity instanceof BaseWireEntity)++wires;
                    if(entity instanceof net.minecraft.world.entity.item.ItemEntity drop&&(drop.getItem().getItem() instanceof org.patryk3211.powergrid.electricity.wire.WireItem||drop.getItem().getItem() instanceof com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem))++items;
                }
                require(wires>0,"Client lost local PG wire entities");
                require(serverWires==282,"Saved yard did not retain all 282 PG wire entities: "+serverWires);
                require(items==0,"Client yard contains loose wire items: "+items);
                require(serverErrors.isEmpty(),"Simulation diagnostics present: "+serverErrors);
                shot(mc,"yard-overview.png");
                WorldWidePowerGrid.LOGGER.info("EXPANDED_REAL_CLIENT_PASSED: backend={}; views={}; synchronized gauges; panel interaction packets; server PG wires={}; locally rendered PG wires={}; loose wires={}",System.getProperty("wwpg.test.backend"),views.size(),serverWires,wires,items);
                finishing=true;mc.stop();
            }
        }catch(Throwable problem){fail(problem);age=0;}
    }
    private void move(Minecraft mc,Vec3 camera,Vec3 target){
        var delta=target.subtract(camera.add(0,mc.player.getEyeHeight(),0));
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        var uuid=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
            if(player!=null){player.getAbilities().flying=true;player.onUpdateAbilities();player.setYRot(yaw);player.setXRot(pitch);player.teleportTo(camera.x,camera.y,camera.z);}
        });
        mc.player.setYRot(yaw);mc.player.setXRot(pitch);
    }
    private void clickPanel(Minecraft mc,BlockPos pos){
        mc.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        var point=new Vec3(pos.getX()+.20,pos.getY()+.50,pos.getZ()+.999);
        var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(point,Direction.SOUTH,pos,false));
        require(result.consumesAction(),"Real client panel interaction rejected: "+pos+" "+result);
        WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_PANEL_CLICK: {} result={}",pos,result);
    }
    private boolean factoryControl(Minecraft mc){
        var be=(ElectricalPanelBlockEntity)mc.level.getBlockEntity(new BlockPos(12,64,8));
        return Arrays.stream(be.getAttachments()).filter(a->a instanceof CutOffSwitchPanelAttachment).map(a->((CutOffSwitchPanelAttachment)a).isClosed).findFirst().orElseThrow();
    }
    private int lamp(Minecraft mc,BlockPos at){return mc.level.getBlockState(at).getValue(com.george_vi.electroenergetics.content.bulb.BulbBlock.LIGHT);}
    private void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_SCREENSHOT: {}",text.getString()));}
    private void diagnostic(ServerLevel level)throws Exception {
        var pos=new BlockPos(96,64,156);
        var source=(org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity)level.getBlockEntity(pos);
        var time=source.getClass().getDeclaredField("time");time.setAccessible(true);
        var device=com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(level).getDevice(new BlockPos(100,64,156),com.george_vi.electroenergetics.content.frequency_meter.FrequencyMeterDevice.class);
        var max=device.getClass().getDeclaredField("maxVoltageLastPeriod");max.setAccessible(true);
        var first=new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(pos,0).getNode(level);
        var second=new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(pos,1).getNode(level);
        WorldWidePowerGrid.LOGGER.info("EXPANDED_AC_DIAGNOSTIC: sourceNBT={}; time={}; deltaV={}; meterTicks={}; prevV={}; period={}; cross={}; maxLast={}; status={}",source.saveWithoutMetadata(level.registryAccess()),time.get(source),first.getVoltage()-second.getVoltage(),device.ticks,device.prevV,device.prevPeriod,device.prevCross,max.get(device),org.cha0scollective.wwpg.bridge.Bridges.get(level).status());
    }
    private void require(boolean okay,String message){if(!okay)throw new IllegalStateException(message);}
    private void fail(Throwable problem){if(failure==null){failure=problem.toString();WorldWidePowerGrid.LOGGER.error("EXPANDED_REAL_CLIENT_FAILED",problem);}}
}
