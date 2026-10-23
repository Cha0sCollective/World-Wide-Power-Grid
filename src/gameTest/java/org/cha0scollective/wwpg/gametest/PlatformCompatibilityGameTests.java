package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.WorldWidePowerGrid;

/** Prevent a compatibility run from silently testing the baseline loader. */
@GameTestHolder("wwpg_platform")
@PrefixGameTestTemplate(false)
public final class PlatformCompatibilityGameTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void requestedLoaderAndProfileActuallyLoaded(GameTestHelper helper) {
        String expected = System.getProperty("wwpg.test.expectedNeoForge", "");
        String actual = ModList.get().getModContainerById("neoforge").orElseThrow()
                .getModInfo().getVersion().toString();
        helper.assertTrue(!expected.isBlank() && actual.equals(expected),
                "Requested NeoForge " + expected + " but loaded " + actual);
        boolean pinout = "PINOUT".equals(System.getProperty("wwpg.test.profile", "BASE"));
        helper.assertTrue(ModList.get().isLoaded("pinout") == pinout, "Wrong Pinout installation profile");
        helper.assertTrue(ModList.get().isLoaded("computercraft") == pinout, "Wrong CC:Tweaked installation profile");
        WorldWidePowerGrid.LOGGER.info("WWPG_PLATFORM_COMPATIBILITY: neoforge={}; profile={}", actual,
                System.getProperty("wwpg.test.profile", "BASE"));
        helper.succeed();
    }
}
