package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The native assembly audit must also pass before any other fixture builds a circuit. */
@GameTestHolder("wwpg_board_startup")
@PrefixGameTestTemplate(false)
public final class BoardStartupGameTests {
    @BeforeBatch(batch="wwpg_board_startup") public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }
    @GameTest(template="empty", batch="wwpg_board_startup", timeoutTicks=70)
    public static void assemblyAuditHasItsOwnPoweredCircuit(GameTestHelper helper) {
        BoardWorkflowGameTests.everyBuiltinBoardComponentAssemblesFromNativeItems(helper);
    }
}
