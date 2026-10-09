package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEERegistries;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class RegistryGameTests {
    @GameTest(template = "empty") public static void releaseInventoryMatchesPublishedRegistries(GameTestHelper h) throws Exception {
        if (Boolean.getBoolean("wwpg.test.packaged")) {
            var path = ModList.get().getModFileById("wwpg").getFile().getFilePath();
            h.assertTrue(java.nio.file.Files.isRegularFile(path) && path.toString().endsWith(".jar"), "Packaged check loaded an exploded WWPG mod: " + path);
        }
        try (var stream = RegistryGameTests.class.getResourceAsStream("/data/wwpg/release/content-matrix.json")) {
            h.assertTrue(stream != null, "Packaged support matrix is missing");
            var matrix = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var item : matrix.getAsJsonArray("content")) {
                var row = item.getAsJsonObject(); var id = ResourceLocation.parse(row.get("id").getAsString());
                boolean present = switch (row.get("registry").getAsString()) {
                    case "block" -> BuiltInRegistries.BLOCK.containsKey(id);
                    case "panel_attachment" -> CEERegistries.PANEL_ATTACHMENT_TYPE.containsKey(id);
                    case "board_component" -> ComponentRegistry.get(id) != null;
                    default -> false;
                };
                h.assertTrue(present, "Pinned registry does not contain " + id);
                if (row.has("variants")) for (var variant : row.getAsJsonArray("variants"))
                    h.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(variant.getAsString())), "Missing cosmetic variant " + variant);
            }
        }
        h.succeed();
    }
}
