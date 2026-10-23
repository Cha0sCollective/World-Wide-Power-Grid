package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.WorldWidePowerGrid;

import java.net.InetAddress;
import java.nio.file.Path;

/** Actual TCP clients interact with a fresh extraction of the exact Workshop ZIP. */
@GameTestHolder("wwpg_api_multiplayer")
@PrefixGameTestTemplate(false)
public final class ExtensionApiMultiplayerGameTests {
    @GameTest(template = "empty", timeoutTicks = 14000)
    public static void twoActualClientsOperateThePublicApiWorkshop(GameTestHelper helper) {
        helper.assertTrue(Boolean.getBoolean("wwpg.test.apiWorkshop"), "Enable opt-in API client acceptance");
        var server = helper.getLevel().getServer();
        try {
            var maximum = net.minecraft.server.players.PlayerList.class.getDeclaredField("maxPlayers");
            maximum.setAccessible(true); maximum.setInt(server.getPlayerList(), 2);
            server.setUsesAuthentication(false); server.getPlayerList().setViewDistance(5); server.getPlayerList().setSimulationDistance(5);
            server.getConnection().startTcpServerListener(InetAddress.getByName("127.0.0.1"), Integer.getInteger("wwpg.test.port", 25575));
            WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_TCP_READY: port={}", Integer.getInteger("wwpg.test.port", 25575));
        } catch (Exception error) { throw new IllegalStateException("Cannot open opt-in API TCP listener", error); }
        var session = new ExtensionApiClientSession[1]; var done = new boolean[1];
        helper.onEachTick(() -> {
            java.util.concurrent.locks.LockSupport.parkNanos(50_000_000L);
            if (done[0]) return;
            try {
                var players = server.getPlayerList().getPlayers().stream().filter(p -> p.getGameProfile().getName().startsWith("WWPG_")).toList();
                if (players.size() != 2) return;
                if (session[0] == null) session[0] = new ExtensionApiClientSession(helper.getLevel(), players, true, Path.of("world"));
                if (session[0].tick()) { done[0] = true; DynamicGameTests.audit(helper); helper.runAfterDelay(120, helper::succeed); }
            } catch (Throwable error) {
                done[0] = true; WorldWidePowerGrid.LOGGER.error("API_WORKSHOP_SERVER_FAILED", error); helper.fail(error.getMessage());
            }
        });
    }
    private ExtensionApiMultiplayerGameTests() { }
}
