package org.cha0scollective.wwpg.gametest;

import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Selected only by the opt-in loader workflow, never by normal gameplay. */
@GameTestHolder("wwpg_loader_installation")
@PrefixGameTestTemplate(false)
public final class LoaderInstallationGameTests {
    @GameTest(template="empty", timeoutTicks=40)
    public static void actualOptionalInstallationMatchesFrozenInputs(GameTestHelper h) throws Exception {
        String installation = System.getProperty("wwpg.loader.installation", "");
        h.assertTrue(installation.equals("BASE") || installation.equals("ADDONS"), "Missing explicit loader installation");
        boolean addons = installation.equals("ADDONS");
        var manifest = Path.of(System.getProperty("wwpg.loader.inputs"));
        var inputs = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        var artifacts = inputs.getAsJsonObject("artifacts");
        for (var value : inputs.getAsJsonObject("loader_compatibility").getAsJsonArray("artifacts")) {
            var item = artifacts.getAsJsonObject(value.getAsString());
            verify(h, item.get("mod_id").getAsString(), addons, item.get("sha256").getAsString());
        }
        verify(h, "wwpg_api_example", addons, artifacts.getAsJsonObject("example").get("sha256").getAsString());
        verify(h, "wwpg_api_upstream", addons, artifacts.getAsJsonObject("upstream_example").get("sha256").getAsString());
        h.succeed();
    }
    private static void verify(GameTestHelper h, String id, boolean expected, String hash) throws Exception {
        boolean present = ModList.get().isLoaded(id);
        h.assertTrue(present == expected, "Wrong optional installation: " + id);
        if (present) {
            var file = ModList.get().getModFileById(id).getFile().getFilePath();
            var actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
            h.assertTrue(actual.equals(hash), "Loaded optional artifact differs: " + id);
        }
        WorldWidePowerGrid.LOGGER.info("LOADER_OPTIONAL_VERIFIED: {} present={}", id, present);
    }
}
