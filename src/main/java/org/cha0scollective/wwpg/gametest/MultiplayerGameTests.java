package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.net.InetAddress;
import java.util.*;

/** Opt-in TCP test. No mock players or local packet-handler shortcuts. */
@GameTestHolder("wwpg_multiplayer")
@PrefixGameTestTemplate(false)
public final class MultiplayerGameTests {
    public static final BlockPos SOURCE=new BlockPos(3072,64,3072);
    public static final BlockPos LOAD=SOURCE.offset(3,0,0);
    public static final BlockPos METER=SOURCE.offset(1,0,3);
    private static final Map<String,Set<UUID>> acknowledgments=new HashMap<>();
    public static void commands(RegisterCommandsEvent event){
        if(!Boolean.getBoolean("wwpg.test.multiplayer"))return;
        event.getDispatcher().register(Commands.literal("wwpg-test").then(Commands.argument("checkpoint",com.mojang.brigadier.arguments.StringArgumentType.word())
                .executes(c->{var player=c.getSource().getPlayerOrException();
                    acknowledgments.computeIfAbsent(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"checkpoint"),k->new HashSet<>()).add(player.getUUID());return 1;})));
    }
    @GameTest(template="empty",timeoutTicks=4000)
    public static void twoRealClients(GameTestHelper h){
        h.assertTrue(Boolean.getBoolean("wwpg.test.multiplayer"),"Enable the opt-in multiplayer launch property");
        var level=h.getLevel();var server=level.getServer();acknowledgments.clear();
        try{
            // Vanilla's GameTest player list is capped at one. This changes only
            // the opt-in test instance, never a user's dedicated server settings.
            var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("maxPlayers");field.setAccessible(true);field.setInt(server.getPlayerList(),2);
            server.setUsesAuthentication(false);server.getPlayerList().setViewDistance(4);server.getPlayerList().setSimulationDistance(4);
            server.getConnection().startTcpServerListener(InetAddress.getByName("127.0.0.1"),Integer.getInteger("wwpg.test.port",25575));
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("WWPG multiplayer loopback server ready on port {}",Integer.getInteger("wwpg.test.port",25575));
        }catch(Exception e){throw new IllegalStateException("Cannot start loopback multiplayer fixture",e);}
        level.setChunkForced(192,192,true);level.getChunk(192,192);
        for(int x=-2;x<=5;++x)for(int z=-2;z<=5;++z)level.setBlockAndUpdate(SOURCE.offset(x,-1,z),Blocks.STONE.defaultBlockState());
        for(var p:new BlockPos[]{SOURCE,LOAD,METER})level.setBlockAndUpdate(p,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(SOURCE,CEEBlocks.CREATIVE_BATTERY.getDefaultState().setValue(com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryBlock.FACING,net.minecraft.core.Direction.NORTH));level.setBlockAndUpdate(LOAD,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,net.minecraft.core.Direction.DOWN).setValue(org.patryk3211.powergrid.electricity.base.SurfaceElectricBlock.ALONG_FIRST_AXIS,true));level.setBlockAndUpdate(METER,CEEBlocks.VOLTMETER.getDefaultState());
        int[] phase={0};long[] phaseTick={0};
        h.onEachTick(()->{
            // Vanilla GameTestServer runs without its normal tick pacing. Real
            // clients need wall-clock time to load and exchange ordinary packets.
            java.util.concurrent.locks.LockSupport.parkNanos(50_000_000L);
            var players=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().startsWith("WWPG_")).toList();
            if(players.size()!=2)return;
            if(phase[0]==0){
                DevicesSavedData.load(level).getDevice(SOURCE,CreativeBatteryDevice.class).voltage=10;
                ((SmartBlockEntity)level.getBlockEntity(LOAD)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(35);
                WiringGameTests.connect(h,SOURCE,1,METER,0,false);WiringGameTests.connect(h,SOURCE,0,METER,1,false);
                for(var p:players){p.setGameMode(GameType.CREATIVE);p.teleportTo(level,SOURCE.getX()+1.5,64,SOURCE.getZ()+(p.getGameProfile().getName().endsWith("A")?-1.5:2.5),180,35);
                    p.setItemInHand(InteractionHand.MAIN_HAND,p.getGameProfile().getName().endsWith("A")?ModdedItems.WIRE.asStack(64):CEEItems.WIRE_SPOOL.asStack());p.inventoryMenu.broadcastChanges();}
                announce(players,"wire");phase[0]=1;phaseTick[0]=level.getGameTime();
            }else if(phase[0]==1&&acked("wired",players)&&level.getGameTime()-phaseTick[0]>60){
                near(h,voltage(h),10,"Real-client simultaneous wire placement");
                announce(players,"configure");phase[0]=2;phaseTick[0]=level.getGameTime();
            }else if(phase[0]==2&&acked("configured",players)&&level.getGameTime()-phaseTick[0]>40){
                near(h,DevicesSavedData.load(level).getDevice(SOURCE,CreativeBatteryDevice.class).voltage,20,"CEE native settings packet");
                near(h,((ResistorBlockEntity)level.getBlockEntity(LOAD)).getValue(),20,"PG native settings packet");
                near(h,voltage(h),20,"Configured mixed circuit");
                announce(players,"verify");phase[0]=3;phaseTick[0]=level.getGameTime();
            }else if(phase[0]==3&&acked("verified",players)){
                h.assertTrue(!acked("failed",players),"A real client reported stale displays or missing wire rendering data");
                DynamicGameTests.audit(h);announce(players,"done");phase[0]=4;
                h.runAfterDelay(50,h::succeed);
            }
            if(!acknowledgments.getOrDefault("failed",Set.of()).isEmpty())h.fail("Real client validation failed; inspect its client log");
        });
    }
    private static void announce(List<ServerPlayer> players,String phase){players.forEach(p->p.sendSystemMessage(Component.literal("WWPG_TEST "+phase)));}
    private static boolean acked(String phase,List<ServerPlayer> players){return acknowledgments.getOrDefault(phase,Set.of()).containsAll(players.stream().map(ServerPlayer::getUUID).toList());}
    private static double voltage(GameTestHelper h){return new BlockWireEndpoint(LOAD,0).getNode(h.getLevel()).getVoltage()-new BlockWireEndpoint(LOAD,1).getNode(h.getLevel()).getVoltage();}
    private static void near(GameTestHelper h,double value,double expected,String label){BoardComponentGameTests.near(h,value,expected,0.15,label);}
}
