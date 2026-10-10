package org.cha0scollective.wwpg;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.cha0scollective.wwpg.bridge.Bridges;

@Mod(WorldWidePowerGrid.ID)
public final class WorldWidePowerGrid {
    public static final String ID = "wwpg";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WorldWidePowerGrid(net.neoforged.bus.api.IEventBus modBus) {
        org.cha0scollective.wwpg.wiring.CeeElectric.register(modBus);
        if (Boolean.getBoolean("wwpg.test.energyFixtures"))
            modBus.addListener(org.cha0scollective.wwpg.gametest.FixtureEnergyCapabilities::register);
        LOGGER.info("WWPG: CEE 1.1.3 / Power Grid 0.6.2 compatibility loaded");
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(org.cha0scollective.wwpg.gametest.MultiplayerGameTests::commands);
        NeoForge.EVENT_BUS.addListener(this::unload);
        NeoForge.EVENT_BUS.addListener(this::stop);
    }

    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wwpg").requires(s -> s.hasPermission(2))
                .executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal(Bridges.get(c.getSource().getLevel()).status()), false);
                    return 1;
                }).then(Commands.literal("errors").executes(c -> {
                    var messages = Bridges.get(c.getSource().getLevel()).diagnostics();
                    c.getSource().sendSuccess(() -> Component.literal(messages.isEmpty() ? "WWPG: no recent errors" : String.join("\n", messages)), false);
                    return messages.size();
                })).then(Commands.literal("at").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                    var pos = BlockPosArgument.getBlockPos(c, "pos");
                    var mappings = Bridges.get(c.getSource().getLevel()).describe(pos);
                    c.getSource().sendSuccess(() -> Component.literal(mappings.isEmpty() ? "WWPG: no mapped terminals at " + pos.toShortString() : String.join("\n", mappings)), false);
                    return mappings.size();
                }))));
    }
    private void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) Bridges.unload(level);
        if (event.getLevel() instanceof net.minecraft.world.level.Level level) org.cha0scollective.wwpg.wiring.Terminals.unload(level);
    }
    private void stop(ServerStoppedEvent event) {
        Bridges.clear();
        org.cha0scollective.wwpg.bridge.ServerElectricalSchedule.clear();
        org.cha0scollective.wwpg.wiring.Terminals.clear();
    }
}
