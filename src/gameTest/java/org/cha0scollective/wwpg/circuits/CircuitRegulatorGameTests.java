package org.cha0scollective.wwpg.circuits;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
@GameTestHolder("wwpg_circuit_regulator")
public final class CircuitRegulatorGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        return CircuitLibraryGameTests.cases("regulator");
    }
}
