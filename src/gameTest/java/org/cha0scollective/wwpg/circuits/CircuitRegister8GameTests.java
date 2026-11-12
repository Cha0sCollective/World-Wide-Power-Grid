package org.cha0scollective.wwpg.circuits;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
@GameTestHolder("wwpg_circuit_register8")
public final class CircuitRegister8GameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        return CircuitLibraryGameTests.cases("register8");
    }
}
