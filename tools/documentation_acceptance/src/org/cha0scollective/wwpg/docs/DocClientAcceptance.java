package org.cha0scollective.wwpg.docs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.example.tutorialload.*;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.cha0scollective.wwpg.mixin.ElectricalNetworkAccessor;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import java.nio.file.*;
import java.util.*;

/** Test-only external client driver. Uses native client interactions and server observation. */
final class DocClientAcceptance {
    private static final BlockPos SOURCE=new BlockPos(26000,64,26000), LOAD=SOURCE.east(3);
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("WWPG documentation client");
    private final long started=System.currentTimeMillis();
    private volatile boolean ready;
    private volatile String failure;
    private int age,stopAge;
    private boolean complete,configured;
    private final List<String> messages=new ArrayList<>();
    DocClientAcceptance() {
        if(Boolean.getBoolean("wwpg.ci.v2")){
            org.cha0scollective.wwpg.acceptance.RuntimeIdentity.record();
            org.cha0scollective.wwpg.acceptance.ClientShutdown.install();
        }
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,this::tick);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,this::server);
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.System.class,event->messages.add(event.getMessage().getString()));
    }
    private void server(ServerTickEvent.Post event) {
        if(ready||failure!=null||event.getServer().getPlayerList().getPlayers().isEmpty())return;
        try {
            if(Boolean.getBoolean("wwpg.ci.v2"))org.cha0scollective.wwpg.acceptance.RuntimeIdentity.verifyWorkshopSpawn(event.getServer().getPlayerList().getPlayers().getFirst());
            var backend=CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend","NATIVE"));
            require(backend.isSupported(),"Backend unavailable");
            ModdedConfigs.server().electricity.solver.solverBackend.set(backend);
            var level=event.getServer().overworld();
            level.setChunkForced(SOURCE.getX()>>4,SOURCE.getZ()>>4,true);
            for(int x=-2;x<6;x++)for(int z=-4;z<3;z++) level.setBlockAndUpdate(SOURCE.offset(x,-1,z),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(SOURCE,BuiltInRegistries.BLOCK.get(ResourceLocation.parse("wwpg_api_example:source")).defaultBlockState());
            require(!level.getBlockState(SOURCE).isAir(),"Published SDK source block missing");
            level.setBlockAndUpdate(LOAD,TutorialLoadMod.LOAD.get().defaultBlockState());
            var player=event.getServer().getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.CREATIVE);player.teleportTo(level,26002,64,25997,0,0);
            ready=true;LOG.info("DOC_CLIENT_READY: published API source, independent load, backend={}",backend);
        } catch(Throwable error) {failure=error.toString();LOG.error("DOC_CLIENT_FAILED",error);}
    }
    private void tick(ClientTickEvent.Post event) {
        if(org.cha0scollective.wwpg.acceptance.ClientShutdown.active())return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        if(complete||failure!=null){if(++stopAge>20)mc.stop();return;}
        try {
            require(System.currentTimeMillis()-started<240000,"Client watchdog exceeded four minutes");
            if(!configured){mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(0.0);mc.options.guiScale().set(2);mc.options.save();configured=true;}
            if(mc.level==null&&mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)
                for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals("Continue"))button.onPress();
            if(!ready||mc.level==null||mc.player==null||mc.gameMode==null||mc.level.getBlockState(LOAD).isAir())return;
            mc.setScreen(null);age++;
            switch(age){
                case 30 -> hand(mc,ModdedItems.WIRE.asStack(64));
                case 35 -> look(mc,new BlockWireEndpoint(SOURCE,0).getExactPosition(mc.level));
                case 50 -> terminal(mc,SOURCE,0);
                case 60 -> look(mc,new BlockWireEndpoint(LOAD,0).getExactPosition(mc.level));
                case 75 -> terminal(mc,LOAD,0);
                case 85 -> look(mc,new BlockWireEndpoint(SOURCE,1).getExactPosition(mc.level));
                case 100 -> terminal(mc,SOURCE,1);
                case 110 -> look(mc,new BlockWireEndpoint(LOAD,1).getExactPosition(mc.level));
                case 125 -> terminal(mc,LOAD,1);
                case 155 -> mc.getSingleplayerServer().execute(()-> {
                    var level=mc.getSingleplayerServer().overworld();
                    LOG.info("DOC_CLIENT_WIRING: source={}, load={}, wires={}, diagnostics={}",org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements.read(level,SOURCE),((LoadDevice)level.getBlockEntity(LOAD)).reading(),level.getEntities(null,new net.minecraft.world.phys.AABB(SOURCE).inflate(8)).stream().map(e->e.getType()+":"+e).toList(),org.cha0scollective.wwpg.extension.ApiCircuits.get(level).diagnostics());
                });
                case 160 -> {require(lit(mc),"Wired 100 ohm load is dark");require(mc.getBlockRenderer().getBlockModel(mc.level.getBlockState(LOAD))!=mc.getModelManager().getMissingModel(),"Missing load model");hand(mc,ItemStack.EMPTY);click(mc,Direction.NORTH,false);}
                case 180 -> {message("ON / 100 ohms","0.200 A");shot(mc,"load-100-on.png");click(mc,Direction.NORTH,true);}
                case 200 -> {message("200 ohms","unavailable: waiting for next solve");click(mc,Direction.NORTH,false);}
                case 220 -> {message("ON / 200 ohms","0.100 A");click(mc,Direction.NORTH,true);}
                case 240 -> click(mc,Direction.NORTH,false);
                case 260 -> {message("ON / 400 ohms","0.050 A");click(mc,Direction.UP,false);}
                case 280 -> {require(!lit(mc),"Top-face OFF did not synchronize");click(mc,Direction.NORTH,false);}
                case 300 -> {message("OFF / 400 ohms","0.000 A");shot(mc,"load-400-off.png");click(mc,Direction.UP,false);}
                case 320 -> hand(mc,ModdedItems.MULTIMETER.asStack());
                case 330 -> look(mc,new BlockWireEndpoint(LOAD,0).getExactPosition(mc.level));
                case 340 -> terminal(mc,LOAD,0);
                case 350 -> look(mc,new BlockWireEndpoint(LOAD,1).getExactPosition(mc.level));
                case 360 -> terminal(mc,LOAD,1);
                case 400 -> {
                    var meter=(org.patryk3211.powergrid.equipment.multimeter.MultimeterItem)mc.player.getMainHandItem().getItem();
                    double volts=meter.getMeasurement(mc.level,mc.player.getMainHandItem());require(Math.abs(volts-20)<.1,"Native client meter "+volts);
                    require(lit(mc),"Held meter changed enable");
                    var server=mc.getSingleplayerServer();require(server!=null,"Expected integrated test server");
                    server.execute(()-> {
                        try {
                            var level=server.overworld();var load=(LoadDevice)level.getBlockEntity(LOAD);
                            require(load.enabled()&&load.resistanceOhms()==400&&load.reading().contains("0.050 A"),"Client controls did not reach server");
                            var expected=CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend"));
                            var actual=GlobalElectricNetworks.getWorldNetworks(level).subnetworks.stream().filter(n->!n.isEmpty()).map(n->((ElectricalNetworkAccessor)n).wwpg$solver().type()).toList();
                            require(!actual.isEmpty()&&actual.stream().allMatch(t->t==expected),"Unexpected actual backend "+actual);
                            var result=new LinkedHashMap<String,Object>();
                            result.put("passed",true);result.put("backend",expected);result.put("readout",load.reading());
                            result.put("client_meter_volts",volts);result.put("controls",true);result.put("native_tools",true);
                            result.put("read_only_click",true);result.put("model",true);result.put("published_source",true);result.put("music_volume",0);
                            result.put("runtime_sha256",DocAcceptance.artifactHash("wwpg"));
                            result.put("starter_sha256",DocAcceptance.artifactHash("tutorial_load"));
                            result.put("helper_sha256",DocAcceptance.artifactHash("wwpg_doc_acceptance"));
                            Files.writeString(Path.of("doc-client-result.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
                            LOG.info("DOC_CLIENT_PASSED: backend={}, voltage={}, readout={}",expected,volts,load.reading());complete=true;
                            if(Boolean.getBoolean("wwpg.ci.v2"))org.cha0scollective.wwpg.acceptance.ClientShutdown.request();
                        }catch(Throwable error){failure=error.toString();LOG.error("DOC_CLIENT_FAILED",error);}
                    });
                }
            }
        }catch(Throwable error){failure=error.toString();LOG.error("DOC_CLIENT_FAILED",error);}
    }
    private void message(String settings,String reading){require(messages.stream().anyMatch(m->m.contains(settings)&&m.contains(reading)),"No actual chat reading for "+settings+" / "+reading+": "+messages);}
    private static boolean lit(Minecraft mc){return mc.level.getBlockState(LOAD).getValue(LoadBlock.LIT);}
    private static void hand(Minecraft mc,ItemStack stack){mc.player.setItemInHand(InteractionHand.MAIN_HAND,stack);mc.gameMode.handleCreativeModeItemAdd(stack,36+mc.player.getInventory().selected);}
    private static void terminal(Minecraft mc,BlockPos pos,int id){
        var point=new BlockWireEndpoint(pos,id).getExactPosition(mc.level);require(point!=null,"Missing terminal geometry");
        look(mc,point);
        var picked=mc.player.pick(6,0,false);
        require(picked instanceof BlockHitResult && ((BlockHitResult)picked).getBlockPos().equals(pos),"Actual terminal ray missed: "+picked);
        mc.hitResult=picked;var hit=(BlockHitResult)picked;require(mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit).consumesAction(),"Native terminal interaction rejected "+pos+":"+id);
    }
    private static void click(Minecraft mc,Direction face,boolean sneak){
        mc.player.setShiftKeyDown(sneak);mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,sneak?net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY:net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        var hit=new BlockHitResult(LOAD.getCenter().relative(face,.5),face,LOAD,false);require(mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit).consumesAction(),"Empty-hand control rejected");
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));mc.player.setShiftKeyDown(false);
    }
    private static void look(Minecraft mc,Vec3 point){
        var delta=point.subtract(mc.player.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        mc.player.setYRot(yaw);mc.player.setXRot(pitch);
        mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(yaw,pitch,false));
    }
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->LOG.info("DOC_CLIENT_SCREENSHOT: {}",text.getString()));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
