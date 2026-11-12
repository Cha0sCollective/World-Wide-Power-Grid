package org.cha0scollective.wwpg.circuits;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
@GameTestHolder("wwpg_circuit_ram4x4")
public final class CircuitRam4X4GameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        return CircuitLibraryGameTests.cases("ram4x4");
    }
}
