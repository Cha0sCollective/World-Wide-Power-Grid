package org.cha0scollective.wwpg.acceptance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.cha0scollective.wwpg.WorldWidePowerGrid;

/** Finish through the same pause-menu action a player uses, before exiting. */
public final class ClientShutdown {
    private static volatile boolean requested;
    private static int age;
    public static void install() { NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ignored -> tick()); }
    public static void request() { requested=true; }
    public static boolean active() { return requested; }
    private static void tick() {
        if(!requested)return;
        var mc=Minecraft.getInstance();
        if(++age>1200)throw new IllegalStateException("Save and Quit never reached the title screen");
        if(mc.level!=null && age==1) {
            mc.setScreen(new PauseScreen(true));
            if(!(mc.screen instanceof PauseScreen))throw new IllegalStateException("Pause menu did not open");
            var save=Component.translatable("menu.returnToMenu").getString();
            var disconnect=Component.translatable("menu.disconnect").getString();
            var button=mc.screen.children().stream().filter(c->c instanceof Button)
                .map(c->(Button)c).filter(b->b.getMessage().getString().equals(save)||b.getMessage().getString().equals(disconnect)).findFirst().orElseThrow();
            WorldWidePowerGrid.LOGGER.info("CI_SAVE_QUIT_CLICK: {}",button.getMessage().getString());
            button.onPress();
        }
        if(age>20 && mc.level==null && mc.getSingleplayerServer()==null && mc.screen instanceof TitleScreen) {
            WorldWidePowerGrid.LOGGER.info("CI_SAVE_QUIT_TITLE_PASSED");
            mc.stop(); requested=false;
        }else if(age>20&&mc.level==null&&mc.screen!=null) {
            var back=Component.translatable("gui.back").getString();
            mc.screen.children().stream().filter(c->c instanceof Button).map(c->(Button)c)
                .filter(b->b.active&&b.getMessage().getString().equals(back)).findFirst().ifPresent(Button::onPress);
        }
    }
}
