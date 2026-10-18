package org.cha0scollective.wwpg.acceptance;

import net.minecraft.gametest.framework.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.gametest.*;
import java.net.InetAddress;
import org.cha0scollective.wwpg.WorldWidePowerGrid;

/** Dedicated smoke entry point lives only in the disposable helper jar. */
@Mod(value="wwpg_acceptance",dist=net.neoforged.api.distmarker.Dist.DEDICATED_SERVER)
@GameTestHolder("wwpg_ci_client")
@PrefixGameTestTemplate(false)
public final class ServerAcceptance {
    public ServerAcceptance(){RuntimeIdentity.record();NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,SmokeSession::commands);NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class,SmokeSession::prepare);}
    @GameTest(template="empty",timeoutTicks=4000)
    public static void twoClients(GameTestHelper helper)throws Exception {
        var server=helper.getLevel().getServer();
        var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("maxPlayers");field.setAccessible(true);field.setInt(server.getPlayerList(),2);
        server.setUsesAuthentication(false);server.getPlayerList().setViewDistance(8);server.getPlayerList().setSimulationDistance(8);
        server.getConnection().startTcpServerListener(InetAddress.getByName("127.0.0.1"),25575);
        WorldWidePowerGrid.LOGGER.info("CI_TCP_READY");
        SmokeSession[] session={null};boolean[] done={false};int[] after={0};
        helper.onEachTick(()->{
            java.util.concurrent.locks.LockSupport.parkNanos(50_000_000L);
            var players=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().startsWith("WWPG_")).toList();
            if(done[0]){if(players.isEmpty()&&++after[0]>10)helper.succeed();return;}
            if(players.size()!=2)return;
            if(session[0]==null)session[0]=new SmokeSession(helper.getLevel(),players);
            done[0]=session[0].tick();
        });
    }
}
