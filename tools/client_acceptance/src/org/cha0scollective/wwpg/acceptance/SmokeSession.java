package org.cha0scollective.wwpg.acceptance;

import com.google.gson.JsonParser;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.cha0scollective.wwpg.gametest.DynamicGameTests;
import org.patryk3211.powergrid.electricity.gauge.VoltageGaugeBlockEntity;
import java.nio.file.*;
import java.util.*;

/** Small observer on the server thread; only clients operate the tested controls. */
final class SmokeSession {
    static final BlockPos YARD_CONTROL=new BlockPos(51,64,94), YARD_LAMP=new BlockPos(54,64,94), YARD_GAUGE=new BlockPos(55,64,127);
    static final BlockPos API_LOAD=new BlockPos(9006,64,9000);
    static final Map<String,Set<UUID>> ACKS=new HashMap<>();
    private final ServerLevel level;
    private final List<ServerPlayer> players;
    private final boolean yard=System.getProperty("wwpg.ci.scenario","").startsWith("yard");
    private final String[] phases;
    private int stage=-1, age, unavailableTicks;
    private boolean held;
    private BlockPos buzzer;
    private final long started;
    SmokeSession(ServerLevel level,List<ServerPlayer> players) {
        this.level=level;this.players=List.copyOf(players);started=level.getGameTime();ACKS.clear();
        DynamicGameTests.substeps(level);
        boolean nativeWorkshop=!yard&&System.getProperty("wwpg.test.backend").equals("NATIVE")&&System.getProperty("wwpg.test.profile").equals("PINOUT");
        phases=yard&&!Boolean.getBoolean("wwpg.ci.tcp")?new String[]{"audio","base","off","on","pause","recovered"}:nativeWorkshop?
            new String[]{"views","base","off","on","pause","recovered","tools"}:new String[]{"base","off","on","pause","recovered"};
        for(var player:players){player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();}
        if(yard)try {
            var manifest=JsonParser.parseString(Files.readString(Path.of(System.getProperty("wwpg.ci.save"),"WWPG_MANIFEST.json"))).getAsJsonObject();
            for(var item:manifest.getAsJsonObject("stations").getAsJsonArray("stations")) {
                var station=item.getAsJsonObject();if(station.get("code").getAsString().equals("S07"))
                    buzzer=new BlockPos(station.get("x").getAsInt()+7,station.get("y").getAsInt(),station.get("z").getAsInt());
            }
            require(buzzer!=null,"Yard buzzer missing from manifest");
        }catch(Exception error){throw new IllegalStateException(error);}
        for(var pos:yard?List.of(YARD_CONTROL,YARD_LAMP,YARD_GAUGE,buzzer):List.of(API_LOAD))
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)level.setChunkForced((pos.getX()>>4)+dx,(pos.getZ()>>4)+dz,true);
    }
    static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wwpg-ci-ack").then(Commands.argument("phase",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->{
            ACKS.computeIfAbsent(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"phase"),k->new HashSet<>()).add(c.getSource().getPlayerOrException().getUUID());return 1;
        })));
    }
    static void prepare(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if(System.getProperty("wwpg.ci.scenario","").startsWith("yard"))return;
        var level=event.getServer().overworld();
        // Load the whole small Workshop before its first electrical tick. Its
        // controlled-source row lies beyond the ordinary spawn preparation area.
        for(int x=8992>>4;x<=9088>>4;x++)for(int z=8992>>4;z<=9088>>4;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
    }
    boolean tick() {
        require(level.getGameTime()-started<2400,"Smoke session timed out");
        if(stage<0){if(level.getGameTime()-started<100||!powered())return false;next();return false;}
        age++;
        if(stage==phases.length)return true;
        var phase=phases[stage];
        if(phase.equals("pause")) {
            RestorationCoordinator.restoring(level,new ChunkPos(yard?YARD_LAMP:API_LOAD));
            var results=ElectricalMeasurements.read(level,yard?YARD_LAMP:API_LOAD);
            if(results.status()==MeasurementStatus.NOT_READY){held=true;unavailableTicks++;}
            if(age>25){require(held&&unavailableTicks>0,"No actual unavailable reading during restoration");WorldWidePowerGrid.LOGGER.info("CI_SMOKE_UNAVAILABLE_PASSED: {}",unavailableTicks);next();}
            return false;
        }
        if(!ACKS.getOrDefault(phase,Set.of()).containsAll(players.stream().map(ServerPlayer::getUUID).toList()))return false;
        if(phase.equals("off"))require(!lit(),"Control packet did not reach server");
        if(phase.equals("on")||phase.equals("base")||phase.equals("recovered"))require(powered(),"Accepted power never recovered");
        if(phase.equals("audio"))try {
            var device=level.getBlockEntity(buzzer);var field=device.getClass().getDeclaredField("soundInstance");field.setAccessible(true);
            require(field.get(device)==null,"Logical server buzzer created a sound");
            WorldWidePowerGrid.LOGGER.info("CI_SMOKE_BUZZER_SIDE_PASSED");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        WorldWidePowerGrid.LOGGER.info("CI_SMOKE_SERVER_PASSED: {}",phase);next();return stage==phases.length;
    }
    static boolean flag(net.minecraft.world.level.block.state.BlockState state,String name) {
        var property=state.getProperties().stream().filter(p->p.getName().equals(name)).findFirst().orElseThrow();
        return Boolean.TRUE.equals(state.getValue(property));
    }
    private boolean lit(){var state=level.getBlockState(yard?YARD_LAMP:API_LOAD);return yard?state.getValue(com.george_vi.electroenergetics.content.bulb.BulbBlock.LIGHT)==15:flag(state,"lit");}
    private boolean powered(){
        if(!lit())return false;
        if(yard)return level.getBlockEntity(YARD_GAUGE) instanceof VoltageGaugeBlockEntity gauge&&Double.isFinite(gauge.getValue())&&Math.abs(gauge.getValue())>1;
        var result=ElectricalMeasurements.readVoltageBetween(level,API_LOAD,0,API_LOAD,1);
        return result.status()==MeasurementStatus.VALID&&Math.abs(result.voltage().orElseThrow().meanVoltageVolts()-20)<.1;
    }
    private void next(){
        stage++;age=0;var phase=stage==phases.length?"done":phases[stage];
        var target=phase.equals("audio")?buzzer:yard?YARD_CONTROL:API_LOAD;
        double volts=yard?((VoltageGaugeBlockEntity)level.getBlockEntity(YARD_GAUGE)).getValue():20;
        for(var player:players){
            if(!phase.equals("pause"))player.teleportTo(level,target.getX()+.5,target.getY()+1,target.getZ()+3,180,25);
            player.sendSystemMessage(Component.literal("CI_SMOKE "+phase+" "+target.getX()+" "+target.getY()+" "+target.getZ()+" "+volts));
        }
        if(phase.equals("done")) {
            var backend=org.patryk3211.powergrid.config.CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend"));
            require(org.cha0scollective.wwpg.bridge.Bridges.get(level).backendCounts().getOrDefault(backend,0L)>0,"Requested backend produced no accepted circuits");
            require(org.cha0scollective.wwpg.bridge.SolverAudit.ceeAttempts()==0,"CEE attempted an independent solve");
            WorldWidePowerGrid.LOGGER.info("CI_SMOKE_SERVER_COMPLETE: backend={}; players={}",backend,players.size());
        }
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
