package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Separate saves are built by running this namespace under BASE and PINOUT. */
@GameTestHolder("wwpg_yard")
@PrefixGameTestTemplate(false)
public final class StationaryYardGameTests {
    @GameTest(template = "empty", timeoutTicks = 1800)
    public static void stationaryYardHasWorkingInstructionsAndSurvivesRestart(GameTestHelper h) {
        ShowroomWorldGameTests.build(h, true);
    }
}
