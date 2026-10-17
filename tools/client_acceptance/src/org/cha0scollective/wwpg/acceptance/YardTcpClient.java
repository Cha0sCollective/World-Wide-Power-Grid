package org.cha0scollective.wwpg.acceptance;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.cha0scollective.wwpg.gametest.MultiplayerClientDriver;
import org.cha0scollective.wwpg.WorldWidePowerGrid;

/** Reuse the existing native input driver, but own the real disconnect path. */
final class YardTcpClient {
    private boolean done;
    YardTcpClient() {
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.System.class,event->{
            if(event.getMessage().getString().equals("WWPG_TEST done")) {
                done=true;WorldWidePowerGrid.LOGGER.info("WWPG_REAL_CLIENT_PASSED");ClientShutdown.request();
            } else forward(()->MultiplayerClientDriver.chat(event));
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,event->{
            if(!done && !ClientShutdown.active())forward(()->MultiplayerClientDriver.tick(event));
        });
    }
    private void forward(Runnable action) {
        // Dedicated clients have no integrated server. The legacy driver is
        // disabled between explicit calls, so its auto-subscriber cannot run it twice.
        System.setProperty("wwpg.test.multiplayer","true");
        try{action.run();}finally{System.setProperty("wwpg.test.multiplayer","false");}
    }
}
