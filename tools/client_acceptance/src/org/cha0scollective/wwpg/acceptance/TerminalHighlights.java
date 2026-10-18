package org.cha0scollective.wwpg.acceptance;

import com.george_vi.electroenergetics.CEEBlocks;
import com.simibubi.create.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.info.TerminalHandler;
import java.nio.file.Files;
import java.util.*;

/** Disposable client probe, never packaged in the candidate. Uses real player rays. */
final class TerminalHighlights {
    private record View(String name,BlockPos pos,int terminal,String equipment,boolean highlighted) { }
    private static final BlockPos PG=new BlockPos(-32,66,-32), BATTERY=new BlockPos(-28,66,-32);
    private final List<View> views=List.of(
            new View("pg-wire",PG,0,"main",true),
            new View("cee-battery-wire-0",BATTERY,0,"main",true),
            new View("cee-battery-offhand-1",BATTERY,1,"off",true),
            new View("cee-battery-goggles-0",BATTERY,0,"goggles",true),
            new View("cee-battery-goggles-1",BATTERY,1,"goggles",true),
            new View("cee-empty-hands",BATTERY,0,"none",false));
    private final List<Map<String,Object>> results=new ArrayList<>();
    private final Direction[] directions={Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.DOWN};
    private volatile boolean ready;
    private int serverAge,age,index,direction;
    private boolean finishing;
    private final long started=System.currentTimeMillis();
    private final Runnable done;
    TerminalHighlights(Runnable done) {
        this.done=done;
        NeoForge.EVENT_BUS.addListener(this::server);
        NeoForge.EVENT_BUS.addListener(this::client);
    }
    private void server(ServerTickEvent.Post event) {
        if(ready || event.getServer().getPlayerList().getPlayers().isEmpty())return;
        if(++serverAge<5)return;
        var level=event.getServer().overworld();
        for(int x=-36;x<-20;x++)for(int z=-36;z<-26;z++)level.setBlockAndUpdate(new BlockPos(x,65,z),Blocks.STONE.defaultBlockState());
        level.setDayTime(6000);
        level.setBlockAndUpdate(PG,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
        level.setBlockAndUpdate(BATTERY,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        for(var player:event.getServer().getPlayerList().getPlayers()) {
            player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
            player.teleportTo(level,-28,68,-29,player.getYRot(),player.getXRot());
        }
        ready=true;
    }
    private void client(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(ClientShutdown.active() || finishing)return;
        if(System.currentTimeMillis()-started>240_000){fail(mc,new IllegalStateException("Client check did not finish"));return;}
        if(!ready || mc.level==null || mc.player==null || mc.getSingleplayerServer()==null)return;
        if(!mc.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                || IElectric.getAt(mc.level,PG)==null || IElectric.getAt(mc.level,BATTERY)==null)return;
        try {
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.setScreen(null);
            if(++age<35)return;
            var view=views.get(index);
            var electric=IElectric.getAt(mc.level,view.pos());
            require(electric!=null,"Missing client terminal facade "+view.name());
            var placement=electric.terminal(mc.level.getBlockState(view.pos()),view.terminal());
            require(placement instanceof IDecoratedTerminal,"Undecorated terminal "+view.name());
            var decorated=(IDecoratedTerminal)placement;
            if(age==35) {
                equipment(mc,view.equipment());
                aim(mc,view.pos(),placement.getOrigin(),directions[direction]);
                return;
            }
            if(age<45)return;
            var ray=mc.player.pick(6,1,false);
            boolean matched=ray instanceof BlockHitResult block && block.getBlockPos().equals(view.pos())
                    && electric.terminalIndexAt(mc.level.getBlockState(view.pos()),block.getLocation().subtract(Vec3.atLowerCornerOf(view.pos())))==view.terminal();
            if(!matched) {
                WorldWidePowerGrid.LOGGER.info("HIGHLIGHT_RAY_MISS: view={}, direction={}, state={}, origin={}, outline={}, eye={}, hitBlock={}, hitPoint={}",
                        view.name(),directions[direction],mc.level.getBlockState(view.pos()),placement.getOrigin(),decorated.getOutline(),mc.player.getEyePosition(),
                        ray instanceof BlockHitResult b?b.getBlockPos():null,ray.getLocation());
                if(++direction>=directions.length)throw new IllegalStateException("No real player ray reached "+view.name()+", last="+ray);
                age=34;return;
            }
            mc.hitResult=ray; // The native highlighter consumes the same actual pick.
            TerminalHandler.tick(mc.level);
            var label=TerminalHandler.overlayText(mc.player);
            require((label!=null)==view.highlighted(),"Native PG highlighter visibility disagreed for "+view.name());
            if(label!=null)require(label.equals(decorated.getName()),"Native overlay label differs from CEE's label");
            if(age==55) {
                Screenshot.grab(mc.gameDirectory,view.name()+".png",mc.getMainRenderTarget(),message->WorldWidePowerGrid.LOGGER.info("HIGHLIGHT_SCREENSHOT: {}",message.getString()));
                var result=new LinkedHashMap<String,Object>();
                result.put("view",view.name());result.put("terminal",view.terminal());result.put("equipment",view.equipment());
                result.put("highlighted",label!=null);result.put("label",label==null?"":label.getString());
                result.put("outline",decorated.getOutline().toString());result.put("real_player_ray",ray.toString());results.add(result);
                WorldWidePowerGrid.LOGGER.info("HIGHLIGHT_VIEW_PASSED: {}",result);
                index++;age=0;direction=0;
                if(index==views.size()) {
                    Files.writeString(mc.gameDirectory.toPath().resolve("highlight-proof.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));
                    WorldWidePowerGrid.LOGGER.info("CEE_TERMINAL_HIGHLIGHT_CLIENT_PASSED: {} views",results.size());finishing=true;done.run();
                }
            }
        }catch(Throwable error){fail(mc,error);}
    }
    private void equipment(Minecraft mc,String kind) {
        var main=kind.equals("main")?ModdedItems.IRON_WIRE.asStack():ItemStack.EMPTY;
        var off=kind.equals("off")?ModdedItems.IRON_WIRE.asStack():ItemStack.EMPTY;
        var head=kind.equals("goggles")?AllItems.GOGGLES.asStack():ItemStack.EMPTY;
        mc.player.setItemInHand(InteractionHand.MAIN_HAND,main);mc.player.setItemInHand(InteractionHand.OFF_HAND,off);mc.player.setItemSlot(EquipmentSlot.HEAD,head);
        var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            if(player!=null){player.setItemInHand(InteractionHand.MAIN_HAND,main.copy());player.setItemInHand(InteractionHand.OFF_HAND,off.copy());player.setItemSlot(EquipmentSlot.HEAD,head.copy());}
        });
    }
    private void aim(Minecraft mc,BlockPos pos,Vec3 origin,Direction direction) {
        var target=origin.add(Vec3.atLowerCornerOf(pos));
        var eye=target.add(direction.getStepX()*2.2,direction.getStepY()*2.2,direction.getStepZ()*2.2);
        var feet=eye.add(0,-mc.player.getEyeHeight(),0);var delta=target.subtract(eye);
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));
        mc.player.moveTo(feet.x,feet.y,feet.z,yaw,pitch);
        mc.player.getAbilities().flying=true;
        var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            if(player!=null){player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(feet.x,feet.y,feet.z);player.setYRot(yaw);player.setXRot(pitch);}
        });
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private void fail(Minecraft mc,Throwable error){
        if(finishing)return;finishing=true;age=0;
        WorldWidePowerGrid.LOGGER.error("CEE_TERMINAL_HIGHLIGHT_CLIENT_FAILED",error);
        try{Files.writeString(mc.gameDirectory.toPath().resolve("FAILED.txt"),error.toString());}catch(Exception ignored){}
        ClientShutdown.request();
    }
}
