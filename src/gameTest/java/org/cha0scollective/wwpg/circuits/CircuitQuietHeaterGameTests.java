package org.cha0scollective.wwpg.circuits;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
@GameTestHolder("wwpg_circuit_quiet_heater")
public final class CircuitQuietHeaterGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        return CircuitLibraryGameTests.cases("quiet-heater");
    }
}
