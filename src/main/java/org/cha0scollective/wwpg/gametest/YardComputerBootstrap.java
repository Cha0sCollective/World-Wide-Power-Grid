package org.cha0scollective.wwpg.gametest;

import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.shared.computer.core.ServerComputer;
import java.io.IOException;

/** Test-yard startup handshake; it never changes native switch state. */
final class YardComputerBootstrap {
    static void start(ServerComputer computer,WritableMount mount,String station,long tick)throws IOException {
        boolean alreadyOn=computer.isOn(),previousReady=mount.exists("yard-ready.json");
        if(previousReady)mount.delete("yard-ready.json");
        // A restored computer may have completed its startup before the fixture
        // starts. turnOn does not restart an already running native shell.
        if(alreadyOn)computer.reboot();else computer.turnOn();
        org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("YARD_LUA_BOOT: station={}; alreadyOn={}; previousReady={}; action={}; tick={}",station,alreadyOn,previousReady,alreadyOn?"native-reboot":"native-turn-on",tick);
    }
}
