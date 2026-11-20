package org.cha0scollective.wwpg.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.util.Arrays;

/** Isolated title-screen smoke check. Never opens or creates a world. */
@Mod("wwpg_preview_smoke")
public final class PreviewSmoke {
    private boolean done;
    public PreviewSmoke() { NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,event->tick()); }
    private void tick() {
        var mc=Minecraft.getInstance();
        if(done||!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null) return;
        done=true;
        try {
            if(mc.level!=null||mc.player!=null) throw new AssertionError("Smoke check must not open a world");
            var target=Class.forName("com.george_vi.electroenergetics.content.wire_spool.WireApplyingBehaviour");
            var handlers=Arrays.stream(target.getDeclaredMethods())
                    .filter(m->m.getName().contains("wwpg$hoveredTerminalPosition")||m.getName().contains("wwpg$skipUnavailablePosition"))
                    .toList();
            if(handlers.size()!=2) throw new AssertionError("Expected two applied preview handlers, found "+handlers);
            for(var handler:handlers) if(!Modifier.isPrivate(handler.getModifiers())||!Modifier.isStatic(handler.getModifiers()))
                throw new AssertionError("Invalid handler visibility: "+handler);
            // Execute the transformed method's no-world path, exercising verification as well as loading.
            target.getMethod("tick").invoke(null);
            LoggerFactory.getLogger(PreviewSmoke.class).info("WWPG_PREVIEW_MIXIN_SMOKE_PASSED wwpg={} cee={} handlers={}",
                    ModList.get().getModContainerById("wwpg").orElseThrow().getModInfo().getVersion(),
                    ModList.get().getModContainerById("electroenergetics").orElseThrow().getModInfo().getVersion(),handlers.size());
            mc.stop();
        } catch(Throwable failure) {
            LoggerFactory.getLogger(PreviewSmoke.class).error("WWPG_PREVIEW_MIXIN_SMOKE_FAILED",failure);
            throw new IllegalStateException("Client preview mixin smoke failed",failure);
        }
    }
}
