package org.cha0scollective.wwpg.docs;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.gametest.BoardWorkflowGameTests;
import org.cha0scollective.wwpg.gametest.DynamicGameTests;

/** Isolated regression: the item assembly audit must not depend on other fixtures. */
@GameTestHolder("wwpg_board_startup")
@PrefixGameTestTemplate(false)
public final class BoardStartupAcceptance {
    @GameTest(template = "empty", timeoutTicks = 70)
    public static void assemblyAuditRunsWithoutOtherFixtures(GameTestHelper helper) {
        DynamicGameTests.substeps(helper.getLevel());
        BoardWorkflowGameTests.everyBuiltinBoardComponentAssemblesFromNativeItems(helper);
    }
}
