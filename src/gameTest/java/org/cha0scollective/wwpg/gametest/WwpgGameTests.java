package org.cha0scollective.wwpg.gametest;

import java.util.Arrays;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** All fixture registration belongs to the test companion, not the player mod. */
@Mod("wwpg_tests")
public final class WwpgGameTests {
    public WwpgGameTests(IEventBus modBus) {
        if (Boolean.getBoolean("wwpg.test.energyFixtures"))
            modBus.addListener(FixtureEnergyCapabilities::register);
        var namespaces = Arrays.asList(System.getProperty("neoforge.enabledGameTestNamespaces", "").split(","));
        if (Boolean.getBoolean("wwpg.test.packaged") && namespaces.contains("wwpg_api"))
            modBus.addListener(ExtensionApiDiodeParameterGameTests::register);
        if (namespaces.contains("wwpg_api"))
            NeoForge.EVENT_BUS.addListener(ExtensionApiStartupGameTests::beforeStartup);
        if (Boolean.getBoolean("wwpg.test.packaged") && namespaces.contains("wwpg_api_ownership"))
            modBus.addListener(ExtensionApiTerminalRefreshGameTests::register);
        NeoForge.EVENT_BUS.addListener(MultiplayerGameTests::commands);
        if (Boolean.getBoolean("wwpg.test.packaged") && namespaces.size() == 1) {
            switch (namespaces.getFirst()) {
                case "wwpg_api_pending_restart" -> NeoForge.EVENT_BUS.addListener(ExtensionApiPendingRestartGameTests::installBeforeStartup);
                case "wwpg_yard" -> NeoForge.EVENT_BUS.addListener(StationaryYardChunkTickets::preserveForExport);
                case "wwpg_api_world" -> NeoForge.EVENT_BUS.addListener(ExtensionApiWorldGameTests::preserveForExport);
                default -> { }
            }
        }
    }
}
