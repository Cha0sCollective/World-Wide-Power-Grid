package org.cha0scollective.wwpg.circuits;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
@GameTestHolder("wwpg_circuit_adder3")
public final class CircuitAdder3GameTests {
    // Configure once before any circuit in the shared batch starts ticking.
    @BeforeBatch(batch="circuit-library") public static void configure(net.minecraft.server.level.ServerLevel level) {
        org.cha0scollective.wwpg.gametest.DynamicGameTests.substeps(level);
    }
    @GameTestGenerator public static Collection<TestFunction> cases() {
        return CircuitLibraryGameTests.cases("adder3");
    }
}
