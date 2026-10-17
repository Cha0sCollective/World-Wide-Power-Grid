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
import org.cha0scollective.wwpg.gametest.StationaryYardGameTests;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.electricity.gauge.CurrentGaugeBlockEntity;
import org.patryk3211.powergrid.electricity.gauge.VoltageGaugeBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;

import java.util.*;

/** Separate, disposable helper mod. The WWPG jar under test is never modified. */
@Mod(value="wwpg_acceptance",dist=net.neoforged.api.distmarker.Dist.CLIENT)
public final class ExpandedClientAcceptance {
    private record View(String name, BlockPos target, BlockPos gauge, boolean current) {}
    private final List<View> views = new ArrayList<>();
    private final long started = System.currentTimeMillis();
    private volatile GameTestInfo test;
    private volatile boolean serverPassed;
    private volatile String failure;
    private volatile long serverWires, initialServerWires;
    private volatile String serverErrors="";
    private volatile BlockPos remoteLamp,remoteGauge;
    private volatile float remoteServerLight;
    private volatile double remoteServerGauge;
    private int serverAge, age, index, phase;
    private boolean begun, finishing;
    private final Map<String,Double> serverReadings = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String,Boolean> serverBuzzerSilent = new java.util.concurrent.ConcurrentHashMap<>();

    public ExpandedClientAcceptance() {
        if(Boolean.getBoolean("wwpg.ci.v2")) {
            RuntimeIdentity.record();
            ClientShutdown.install();
            if(Boolean.getBoolean("wwpg.ci.smoke")){new SmokeClient();return;}
            if(Boolean.getBoolean("wwpg.ci.tcp")){
                if(Boolean.getBoolean("wwpg.test.apiWorkshop"))new ApiWorkshopClientAcceptance();
                else new YardTcpClient();
                return;
            }
        }
        if (Boolean.getBoolean("wwpg.test.apiWorkshop")) { new ApiWorkshopClientAcceptance(); return; }
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
        // Read the exact archive's checked stations; no inventory cabinet counts
        // as a live view. Legacy views above also exercise individual gauges.
        try {
            // The Windows JVM launcher decodes -D values in its native code page,
            // whereas the saved extraction record preserves the UTF-8 folder name.
            var extraction=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("extracted-yard.json"))).getAsJsonObject();
            var save=java.nio.file.Path.of("saves",extraction.get("source_save_name").getAsString());
            var manifest=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(save.resolve("WWPG_MANIFEST.json"))).getAsJsonObject();
            for(var entry:manifest.getAsJsonObject("stations").getAsJsonArray("stations")) {
                var station=entry.getAsJsonObject();var code=station.get("code").getAsString();
                if(!code.startsWith("S"))continue;
                var base=new BlockPos(station.get("x").getAsInt(),station.get("y").getAsInt(),station.get("z").getAsInt());
                var target=base.east(7);
                if(code.equals("S03")||code.equals("S64"))target=target.above(3);
                if(code.equals("S66")||code.equals("S67"))target=target.above();
                if(code.equals("S11")||code.equals("S12"))target=base.above();
                if(code.equals("S65"))target=target.east(3).above();
                views.add(new View(code,target,base.offset(11,0,4),false));
            }
        } catch(Exception error) { throw new IllegalStateException("Cannot read exact exported yard coverage",error); }
        // Check the newly covered audio workflow before the long view tour.
        views.sort(java.util.Comparator.comparingInt(view -> view.name.equals("S07") ? 0 : 1));
        if(Boolean.getBoolean("wwpg.ci.v2") && !Boolean.getBoolean("wwpg.ci.gallery")) {
            var selected=Set.of(System.getProperty("wwpg.ci.views").split(","));
            views.removeIf(view->!selected.contains(view.name));
            if(views.size()!=selected.size())throw new IllegalStateException("Unknown selected yard view");
        }
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,this::serverTick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,this::clientTick);
        WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_HELPER: exact saved world; backend={}; views={}",System.getProperty("wwpg.test.backend"),views.size());
    }

    private void serverTick(ServerTickEvent.Post event) {
        if(failure!=null||finishing||event.getServer().getPlayerList().getPlayers().isEmpty())return;
        var level=event.getServer().overworld();
        try {
            if(Boolean.getBoolean("wwpg.ci.v2")) {
                if(++serverAge==1) {
                    org.cha0scollective.wwpg.gametest.DynamicGameTests.substeps(level);
                    for(int x=0;x<=17;x++)for(int z=0;z<=20;z++)level.setChunkForced(x,z,true);
                    for(var player:event.getServer().getPlayerList().getPlayers()) {
                        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                        player.setItemSlot(EquipmentSlot.HEAD,AllItems.GOGGLES.asStack());
                    }
                }
                if(!serverPassed) {
                    require(serverAge<600,"Selected yard stations never became ready");
                    if(serverAge<60)return;
                    for(var view:views)if(!level.isPositionEntityTicking(view.target)||level.getBlockState(view.target).isAir())return;
                    var linked=com.simibubi.create.api.contraption.train.PortalTrackProvider.getOtherSide(level,new net.createmod.catnip.math.BlockFace(new BlockPos(266,64,224),Direction.EAST));
                    if(linked==null)return;
                    remoteLamp=linked.face().getPos().south(3);remoteGauge=remoteLamp.west(2);
                    initialServerWires=level.getEntitiesOfClass(BaseWireEntity.class,new net.minecraft.world.phys.AABB(0,-64,0,284,320,314)).size();
                    require(initialServerWires>500,"Exported wire entities missing");serverPassed=true;
                    WorldWidePowerGrid.LOGGER.info("CI_YARD_READY: selected exhibits; no saved-yard GameTest rerun");
                }
                // Reuse the observation block below without running the world
                // construction/regression fixture a second time.
            }
            if(Boolean.getBoolean("wwpg.test.netherFocus")) {
                // Ordinary saved-world smoke check: no GameTestRunner and no
                // helper chunk tickets may conceal missing exported forceloads.
                if(++serverAge==1)org.cha0scollective.wwpg.gametest.DynamicGameTests.substeps(level);
                var nether=event.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
                require(level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(16,14))
                        &&nether.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(2,1)),"Exact exported save lost its native Nether tickets");
                if(!serverPassed) {
                    if(!level.isPositionEntityTicking(new BlockPos(266,65,224))||!nether.isPositionEntityTicking(new BlockPos(34,65,28))){require(serverAge<300,"Native saved tickets never became ticking");return;}
                    var linked=com.simibubi.create.api.contraption.train.PortalTrackProvider.getOtherSide(level,new net.createmod.catnip.math.BlockFace(new BlockPos(266,64,224),Direction.EAST));
                    require(linked!=null,"Ordinary exported portal has no native linked endpoint");
                    remoteLamp=linked.face().getPos().south(3);remoteGauge=remoteLamp.west(2);serverPassed=true;
                    WorldWidePowerGrid.LOGGER.info("STATIONARY_ORDINARY_PORTAL_BASELINE: native saved forceloads; no GameTest or helper tickets");
                }
                if(nether.getBlockEntity(remoteLamp)!=null)remoteServerLight=nether.getBlockEntity(remoteLamp).saveWithoutMetadata(nether.registryAccess()).getFloat("FirstLight");
                if(nether.getBlockEntity(remoteGauge) instanceof VoltageGaugeBlockEntity gauge)remoteServerGauge=gauge.getValue();
                if(serverAge%20==0)WorldWidePowerGrid.LOGGER.info("STATIONARY_ORDINARY_PORTAL_TICKING: playerLevels={}; sourceTicking={}; remoteTicking={}",event.getServer().getPlayerList().getPlayers().stream().map(p->p.level().dimension().location()).toList(),level.getChunkSource().isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(16,14)),nether.getChunkSource().isPositionTicking(net.minecraft.world.level.ChunkPos.asLong(2,1)));
                return;
            }
            if(test==null && !Boolean.getBoolean("wwpg.ci.v2")) {
                if(++serverAge==1)for(int x=0;x<=17;x++)for(int z=0;z<=20;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
                if(serverAge<20)return;
                for(int x=0;x<=17;x++)for(int z=0;z<=20;z++)if(!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x,z))){require(serverAge<300,"Yard chunks never became entity-ready");return;}
                org.cha0scollective.wwpg.gametest.DynamicGameTests.substeps(level);
                var function=new TestFunction("expanded_client","expanded_client.saved_yard","wwpg_showroom:empty",Rotation.NONE,1800,0,true,StationaryYardGameTests::stationaryYardHasWorkingInstructionsAndSurvivesRestart);
                test=new GameTestInfo(function,Rotation.NONE,level,RetryOptions.noRetries());
                var grid=new StructureGridSpawner(new BlockPos(-512,70,-512),1,false);
                GameTestRunner.Builder.fromInfo(List.of(test),level).newStructureSpawner(grid).existingStructureSpawner(grid).haltOnError(true).build().start();
                WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_BASELINE_STARTED: downloaded yard reopened in integrated server");
            } else {
                if(test!=null&&test.hasFailed())throw new IllegalStateException("Saved-yard acceptance failed",test.getError());
                if(test!=null&&test.hasSucceeded()&&!serverPassed){
                    // Native GameTestRunner removes all forced chunks at batch
                    // completion. Restore this helper's display-view tickets;
                    // the exported save also retains its native Nether tickets.
                    for(int x=0;x<=17;x++)for(int z=0;z<=20;z++)level.setChunkForced(x,z,true);
                    WorldWidePowerGrid.LOGGER.info("STATIONARY_CLIENT_BATCH_TICKETS_RESTORED: native GameTest cleanup removed the view and source tickets");
                    for(var player:event.getServer().getPlayerList().getPlayers())player.setItemSlot(EquipmentSlot.HEAD,AllItems.GOGGLES.asStack());
                    initialServerWires=level.getEntitiesOfClass(BaseWireEntity.class,new net.minecraft.world.phys.AABB(0,-64,0,284,320,314)).size();
                    require(initialServerWires>500,"Expanded stationary wires are missing");
                    var linked=com.simibubi.create.api.contraption.train.PortalTrackProvider.getOtherSide(level,new net.createmod.catnip.math.BlockFace(new BlockPos(266,64,224),Direction.EAST));
                    require(linked!=null,"Native yard portal lost its remote mapping");
                    remoteLamp=linked.face().getPos().south(3);remoteGauge=remoteLamp.west(2);
                    serverPassed=true;
                    WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_BASELINE_PASSED: all live exhibits and native-handler interactions");
                }
                if(serverPassed)for(var view:views)if(view.gauge!=null){
                    var be=level.getBlockEntity(view.gauge);
                    if(be instanceof CurrentGaugeBlockEntity current)serverReadings.put(view.name,(double)current.getValue());
                    else if(be instanceof VoltageGaugeBlockEntity voltage)serverReadings.put(view.name,(double)voltage.getValue());
                    if(level.getBlockEntity(view.target) instanceof com.george_vi.electroenergetics.content.buzzer.BuzzerBlockEntity buzzer){
                        // ServerLevel deliberately returns null for block lookups
                        // from the render thread. Inspect here and publish only
                        // the immutable observation to the client-side check.
                        var field=buzzer.getClass().getDeclaredField("soundInstance");field.setAccessible(true);
                        boolean silent=field.get(buzzer)==null;
                        require(silent,"Integrated-server buzzer created a client sound at "+view.name);
                        serverBuzzerSilent.put(view.name,silent);
                    }
                }
                if(serverPassed&&event.getServer().getTickCount()%20==0){
                    serverWires=level.getEntitiesOfClass(BaseWireEntity.class,new net.minecraft.world.phys.AABB(0,-64,0,284,320,314)).size();
                    serverErrors=String.join("; ",org.cha0scollective.wwpg.bridge.Bridges.get(level).diagnostics());
                    var nether=event.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
                    if(nether.getBlockEntity(remoteLamp)!=null)remoteServerLight=nether.getBlockEntity(remoteLamp).saveWithoutMetadata(nether.registryAccess()).getFloat("FirstLight");
                    if(nether.getBlockEntity(remoteGauge) instanceof VoltageGaugeBlockEntity gauge)remoteServerGauge=gauge.getValue();
                    if(Boolean.getBoolean("wwpg.test.netherFocus")||phase>=15) {
                        WorldWidePowerGrid.LOGGER.info("NETHER_CLIENT_TRACE: playerLevels={}; source={}; primary={}; remote={}",
                                event.getServer().getPlayerList().getPlayers().stream().map(p->p.level().dimension().location()).toList(),
                                org.cha0scollective.wwpg.bridge.Bridges.get(level).describe(new BlockPos(256,64,224)),
                                org.cha0scollective.wwpg.bridge.Bridges.get(level).describe(new BlockPos(266,65,224)),
                                org.cha0scollective.wwpg.bridge.Bridges.get(nether).describe(remoteLamp));
                    }
                }
            }
        } catch(Throwable problem){fail(problem);}
    }

    private void clientTick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(ClientShutdown.active())return;
        mc.options.pauseOnLostFocus=false;
        try {
            if(System.currentTimeMillis()-started>900_000)throw new IllegalStateException("Client acceptance exceeded fifteen minutes");
            if(failure!=null){if(age==0&&mc.level!=null)shot(mc,"failed-world.png");if(++age>30&&!finishing){finishing=true;mc.stop();}return;}
            if(mc.level==null&&mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals("Continue")) {
                    WorldWidePowerGrid.LOGGER.info("STATIONARY_CLIENT_STARTUP: continuing the fresh-instance accessibility prompt");button.onPress();break;
                }
            }
            if(mc.level==null||mc.player==null||mc.gameMode==null||mc.getSingleplayerServer()==null)return;
            if(mc.screen!=null&&phase!=4&&phase!=6&&phase!=7&&phase!=9&&phase!=11&&phase!=13){mc.setScreen(null);}
            if(!serverPassed)return;
            if(!begun){begun=true;age=0;mc.options.pauseOnLostFocus=false;mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();if(Boolean.getBoolean("wwpg.test.netherFocus"))phase=15;}
            ++age;
            if(phase==0){
                if(index>=views.size()){phase=1;age=0;move(mc,new Vec3(12.2,64.2,10.5),new Vec3(12.2,64.5,8.9));return;}
                var view=views.get(index);
                if(age==1) {
                    boolean crt=Set.of("S24","S25","S26").contains(view.name);
                    boolean plotter=view.name.equals("S15");
                    mc.options.fov().set(crt||plotter?35:70);
                    if(crt)move(mc,new Vec3(view.target.getX()+.5,view.target.getY()+.1,view.target.getZ()-2.3),new Vec3(view.target.getX()+.5,view.target.getY()+.375,view.target.getZ()+.03));
                    else if(plotter)move(mc,new Vec3(view.target.getX()+.5,view.target.getY()+.75,view.target.getZ()-2.3),Vec3.atCenterOf(view.target).add(0,.2,0));
                    else move(mc,new Vec3(view.target.getX()+2.3,view.target.getY()+2.4,view.target.getZ()+3.2),Vec3.atCenterOf(view.target));
                }
                if(age<45)return;
                require(mc.level.getBlockEntity(view.target)!=null||!mc.level.getBlockState(view.target).isAir(),"Client did not load "+view.name);
                if(view.name.matches("B[0-9]+")){
                    var board=(CircuitBoardBlockEntity)mc.level.getBlockEntity(view.target);
                    require(board!=null&&board.getComponentsStream().findAny().isPresent(),"Client board components missing: "+view.name);
                }
                if(view.gauge!=null&&serverReadings.containsKey(view.name)){
                    var be=mc.level.getBlockEntity(view.gauge);
                    require(be!=null,"Client gauge missing: "+view.name);
                    double actual=view.current?((CurrentGaugeBlockEntity)be).getValue():((VoltageGaugeBlockEntity)be).getValue();
                    double expected=serverReadings.getOrDefault(view.name,Double.NaN);
                    require(Double.isFinite(actual)&&Double.isFinite(expected)&&Math.abs(actual-expected)<Math.max(.05,Math.abs(expected)*.05),"Client gauge differs at "+view.name+": client="+actual+", server="+expected);
                    WorldWidePowerGrid.LOGGER.info("EXPANDED_CLIENT_READING: {} client={} server={}",view.name,actual,expected);
                }
                if(view.name.matches("P[0-9]+")){
                    var panel=(ElectricalPanelBlockEntity)mc.level.getBlockEntity(view.target);
                    require(panel!=null&&Arrays.stream(panel.getAttachments()).anyMatch(Objects::nonNull),"Client panel attachments missing: "+view.name);
                }
                if(view.name.startsWith("S")) {
                    if(view.name.equals("S77")) {
                        var cable=com.george_vi.electroenergetics.CEEPartialModels.DUPLEX_WIRE_SEGMENT.get();
                        require(cable!=mc.getModelManager().getMissingModel(),"Native duplex cable uses the missing model");
                        require(!cable.getQuads(null,null,net.minecraft.util.RandomSource.create(0)).isEmpty(),"Native duplex cable model has no geometry");
                        WorldWidePowerGrid.LOGGER.info("STATIONARY_CLIENT_DUPLEX_MODEL_PASSED: native bundled geometry resolves under the published duplex name");
                    }
                    var block=mc.level.getBlockEntity(view.target);
                    if(block instanceof com.george_vi.electroenergetics.content.buzzer.BuzzerBlockEntity buzzer) {
                        var field=buzzer.getClass().getDeclaredField("soundInstance");field.setAccessible(true);
                        var sound=(net.minecraft.client.resources.sounds.SoundInstance)field.get(buzzer);
                        require(sound!=null&&mc.getSoundManager().isActive(sound),"Powered client buzzer has no active native sound");
                        require(Boolean.TRUE.equals(serverBuzzerSilent.get(view.name)),"Server-thread buzzer observation unavailable at "+view.name);
                        WorldWidePowerGrid.LOGGER.info("STATIONARY_CLIENT_BUZZER_AUDIO_PASSED: client sound active; server sound absent");
                    }
                    if(block instanceof org.patryk3211.powergrid.electricity.crt.CRTBlockEntity crt) {
                        require(crt.getColor()==net.minecraft.world.item.DyeColor.LIME,"CRT client lost native color");
                        var brightness=org.patryk3211.powergrid.electricity.crt.CRTBlockEntity.class.getDeclaredField("brightness");brightness.setAccessible(true);
                        float peak=0;for(float value:(float[])brightness.get(crt))peak=Math.max(peak,value);
                        require(peak>.5,"Native CRT client has no visible beam: "+peak);
                        WorldWidePowerGrid.LOGGER.info("STATIONARY_CLIENT_CRT_BEAM: {} peak={}",view.name,peak);
                    }
                    if(block instanceof org.patryk3211.powergrid.kinetics.plotter.PlotterBlockEntity plotter) {
                        var samples=plotter.saveWithoutMetadata(mc.level.registryAccess()).getList("Samples",net.minecraft.nbt.Tag.TAG_FLOAT);
                        boolean signal=false;for(int sample=0;sample<samples.size();sample++)signal|=Math.abs(samples.getFloat(sample)-.5)<.001;
                        require(signal,"Client plotter received no powered graph samples");
                        WorldWidePowerGrid.LOGGER.info("STATIONARY_CLIENT_PLOTTER_GRAPH_PASSED: synchronized native samples");
                    }
                    if(block instanceof org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity table)
                        require(table.getSchematic().components().isEmpty(),"Native export-to-item should leave the designer empty until reloaded");
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
            } else if(phase==3){
                if(age==1)move(mc,new Vec3(163.5,65,225.5),new Vec3(159.5,64.5,224.5));
                if(age==35) {
                    var pos=new BlockPos(159,64,224);
                    var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter().add(0,.45,0),Direction.UP,pos,false));
                    require(result.consumesAction(),"Native saved-design menu click rejected");
                    phase=4;age=0;
                }
            } else if(phase==4) {
                var pos=new BlockPos(159,64,224);
                var table=(org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity)mc.level.getBlockEntity(pos);
                if(age==40) {
                    require(mc.screen instanceof org.patryk3211.powergrid.circuits.editor.CircuitDesignTableScreen,"Native design screen did not open: "+mc.screen);
                    var stack=mc.player.containerMenu.getSlot(2).getItem();
                    require(org.patryk3211.powergrid.circuits.schematic.CircuitSchematic.fromStack(mc.level.registryAccess(),stack).components().size()==5,"Native saved design item lost its components");
                    shot(mc,"native-saved-design-screen.png");
                    mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,2,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);
                    mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);
                    org.patryk3211.powergrid.collections.ModdedPackets.sendToServer(new org.patryk3211.powergrid.network.packets.SaveSchematicC2SPacket(table,true));
                }
                if(age==80) {
                    require(table.getSchematic().components().size()==5,"Real-client native load failed");
                    org.patryk3211.powergrid.collections.ModdedPackets.sendToServer(new org.patryk3211.powergrid.network.packets.ChangeScreenC2SPacket(table,1));
                    phase=6;age=0;
                }
            } else if(phase==6&&age>=40) {
                require(mc.screen instanceof org.patryk3211.powergrid.circuits.editor.CircuitDesignTableEditScreen,"Native design editor did not open: "+mc.screen);
                var editor=(org.patryk3211.powergrid.circuits.editor.CircuitDesignTableEditScreen<?>)mc.screen;
                var field=org.patryk3211.powergrid.circuits.editor.CircuitDesignTableEditScreen.class.getDeclaredField("nameField");field.setAccessible(true);
                ((net.minecraft.client.gui.components.EditBox)field.get(editor)).setValue("WWPG graphical saved design");
                shot(mc,"native-saved-design-editor.png");
                editor.save();
                phase=7;age=0;
            } else if(phase==7&&age>=40) {
                require(mc.screen instanceof org.patryk3211.powergrid.circuits.editor.CircuitDesignTableScreen,"Native save did not return to design menu");
                var table=(org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity)mc.level.getBlockEntity(new BlockPos(159,64,224));
                require(table.getSchematic().components().size()==5&&table.getSchematicName().equals("WWPG graphical saved design"),"Native client edit/save lost the loaded design");
                shot(mc,"native-design-saved.png");mc.player.closeContainer();mc.setScreen(null);
                WorldWidePowerGrid.LOGGER.info("STATIONARY_CLIENT_DESIGN_SCREEN_PASSED: native item movement, load, editor, save and synchronization");
                phase=net.neoforged.fml.ModList.get().isLoaded("pinout")?8:15;age=0;
            } else if(phase==8) {
                if(age==1)move(mc,new Vec3(262.5,64.2,281),new Vec3(262.5,64.5,278.5));
                if(age==35){openComputer(mc);phase=9;age=0;}
            } else if(phase==9) {
                if(age==40)typeCommand(mc,"wwpg stop");
                if(age==120){shot(mc,"pinout-stop-terminal.png");mc.player.closeContainer();mc.setScreen(null);phase=10;age=0;}
            } else if(phase==10&&age>=80) {
                if(!verifyPinLamps(mc,0))return;
                shot(mc,"pinout-all-off.png");openComputer(mc);phase=11;age=0;
            } else if(phase==11) {
                if(age==40)typeCommand(mc,"wwpg pattern 85");
                if(age==120){mc.player.closeContainer();mc.setScreen(null);phase=12;age=0;}
            } else if(phase==12&&age>=80) {
                if(!verifyPinLamps(mc,85))return;
                shot(mc,"pinout-odd-pins.png");openComputer(mc);phase=13;age=0;
            } else if(phase==13) {
                if(age==40)typeCommand(mc,"wwpg volts");
                if(age==200){shot(mc,"pinout-signed-voltage-terminal.png");typeCommand(mc,"wwpg start");}
                if(age==300){mc.player.closeContainer();mc.setScreen(null);phase=14;age=0;}
            } else if(phase==14&&age>=80) {
                if(!verifyPinLamps(mc,255))return;
                shot(mc,"pinout-all-restored.png");
                WorldWidePowerGrid.LOGGER.info("STATIONARY_CLIENT_PINOUT_KEYBOARD_PASSED: stop -> pattern 85 -> volts -> start");phase=15;age=0;
            } else if(phase==15) {
                if(age==1)move(mc,new Vec3(267.5,64.05,224.5),new Vec3(268.5,64.5,224.5));
                if(age>30&&mc.level.dimension()==net.minecraft.world.level.Level.NETHER&&mc.level.getBlockEntity(remoteLamp)!=null&&mc.level.getBlockEntity(remoteGauge)!=null) {
                    move(mc,new Vec3(remoteLamp.getX()+2.3,remoteLamp.getY()+1,remoteLamp.getZ()+3.2),Vec3.atCenterOf(remoteLamp));phase=16;age=0;
                }
                require(age<400,"Native yard portal did not take the real player to the Nether");
            } else if(phase==16&&age>=60) {
                var lamp=mc.level.getBlockEntity(remoteLamp);var meter=mc.level.getBlockEntity(remoteGauge);
                double light=lamp==null?Double.NaN:lamp.saveWithoutMetadata(mc.level.registryAccess()).getFloat("FirstLight");
                double volts=meter instanceof VoltageGaugeBlockEntity gauge?gauge.getValue():Double.NaN;
                WorldWidePowerGrid.LOGGER.info("STATIONARY_CLIENT_NETHER_LOAD: lamp={}; clientLight={}; serverLight={}; gauge={}; clientVoltage={}; serverVoltage={}",remoteLamp,light,remoteServerLight,remoteGauge,volts,remoteServerGauge);
                require(light>.95&&Math.abs(volts-70)<.35,"Nether client did not receive the mixed lamp and 70 V gauge");
                shot(mc,"native-nether-route.png");WorldWidePowerGrid.LOGGER.info("STATIONARY_CLIENT_NATIVE_PORTAL_PASSED: actual portal travel and synchronized remote power");phase=5;age=0;
            } else if(phase==5&&age>100){
                if(Boolean.getBoolean("wwpg.test.netherFocus")) {
                    WorldWidePowerGrid.LOGGER.info("STATIONARY_FOCUSED_PORTAL_CLIENT_PASSED: backend={}; ordinary exact exported save; actual portal travel; client/server 70 V and lit load; no helper tickets",System.getProperty("wwpg.test.backend"));
                    finishing=true;mc.stop();return;
                }
                require(index==views.size(),"Full graphical acceptance did not visit every station");
                long wires=0,items=0;for(var entity:mc.level.entitiesForRendering()){
                    if(entity instanceof BaseWireEntity)++wires;
                    if(entity instanceof net.minecraft.world.entity.item.ItemEntity drop&&(drop.getItem().getItem() instanceof org.patryk3211.powergrid.electricity.wire.WireItem||drop.getItem().getItem() instanceof com.george_vi.electroenergetics.content.wire_spool.WireSpoolItem))++items;
                }
                require(wires>0,"Client lost local PG wire entities");
                require(serverWires==initialServerWires,"Saved yard did not retain all exported PG wire entities: "+serverWires);
                require(items==0,"Client yard contains loose wire items: "+items);
                require(serverErrors.isEmpty(),"Simulation diagnostics present: "+serverErrors);
                shot(mc,"yard-overview.png");
                WorldWidePowerGrid.LOGGER.info("EXPANDED_REAL_CLIENT_PASSED: backend={}; views={}; synchronized gauges; panel interaction packets; server PG wires={}; locally rendered PG wires={}; loose wires={}",System.getProperty("wwpg.test.backend"),views.size(),serverWires,wires,items);
                finishing=true;
                if(Boolean.getBoolean("wwpg.ci.v2"))new TerminalHighlights(ClientShutdown::request);
                else mc.stop();
            }
        }catch(Throwable problem){fail(problem);age=0;}
    }
    private void openComputer(Minecraft mc) {
        var pos=new BlockPos(262,64,278);
        var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(pos.getCenter().add(0,0,.5),Direction.SOUTH,pos,false));
        require(result.consumesAction(),"Native computer right click rejected");
    }
    private void typeCommand(Minecraft mc,String command) {
        require(mc.screen!=null&&mc.screen.getClass().getName().startsWith("dan200.computercraft."),"Native computer screen did not open: "+mc.screen);
        for(char value:command.toCharArray())require(mc.screen.charTyped(value,0),"Computer rejected keyboard character");
        require(mc.screen.keyPressed(257,0,0),"Computer rejected Enter");
        WorldWidePowerGrid.LOGGER.info("STATIONARY_CLIENT_NATIVE_KEYBOARD: {}",command);
    }
    private boolean verifyPinLamps(Minecraft mc,int pattern) {
        String pending=null;
        for(int pin=1;pin<=8;pin++) {
            var pos=new BlockPos(263+5+(pin-1)%2*4,64,278+(pin-1)/2*2);
            var be=mc.level.getBlockEntity(pos);require(be!=null,"Pin lamp missing: "+pin);
            float light=be.saveWithoutMetadata(mc.level.registryAccess()).getFloat("FirstLight");
            boolean on=(pattern&(1<<(pin-1)))!=0;
            if(!(on?light>.95:light<.01))pending="pin "+pin+" light="+light;
        }
        var pins=new BlockPos(263,64,278);boolean run=(pattern&4)!=0;
        var running=(org.patryk3211.powergrid.electricity.light.fixture.AbstractLightFixtureBlockEntity)mc.level.getBlockEntity(pins.offset(15,0,10));
        if(!(run?running.getPowerLevel()>0:running.getPowerLevel()==0))pending="RUN light="+running.getPowerLevel();
        float stopped=mc.level.getBlockEntity(pins.offset(15,0,13)).saveWithoutMetadata(mc.level.registryAccess()).getFloat("FirstLight");
        if(!(run?stopped<.01:stopped>.95))pending="OFF light="+stopped;
        var panel=(com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity)mc.level.getBlockEntity(pins.south(10));
        var gauge=(com.george_vi.electroenergetics.content.electrical_panel.attachments.GaugePanelAttachment)panel.getAttachments()[com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot.THIRD_CENTERED.ordinal()];
        if(Math.abs(gauge.value-((pattern&16)!=0?70:0))>=.2)pending="panel voltage="+gauge.value;
        if(pending!=null) {
            if(age%100==0)WorldWidePowerGrid.LOGGER.info("STATIONARY_CLIENT_SETTLING: pattern={}, age={}, pending={}",pattern,age,pending);
            require(age<600,"Native computer outputs did not settle: pattern="+pattern+", "+pending);
            return false;
        }
        return true;
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
    private void require(boolean okay,String message){if(!okay)throw new IllegalStateException(message);}
    private void fail(Throwable problem){if(failure==null){failure=problem.toString();WorldWidePowerGrid.LOGGER.error("EXPANDED_REAL_CLIENT_FAILED",problem);}}
}
