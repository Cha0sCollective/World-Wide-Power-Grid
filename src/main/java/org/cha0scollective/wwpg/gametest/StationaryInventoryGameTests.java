package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.fuse.fuse_held.FuseHoldable;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;

/** Runtime registries, including items not represented by blockstate assets. */
@GameTestHolder("wwpg_stationary")
@PrefixGameTestTemplate(false)
public final class StationaryInventoryGameTests {
    private static final Set<String> NAMESPACES = Set.of("electroenergetics", "powergrid", "pinout");

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void recordCompletePinnedStationaryInventory(GameTestHelper h) throws Exception {
        var report = new JsonObject();
        report.addProperty("schema", 1);
        report.addProperty("profile", ModList.get().isLoaded("pinout") ? "PINOUT" : "BASE");
        var rows = new JsonArray();
        BuiltInRegistries.BLOCK.entrySet().stream().filter(e -> NAMESPACES.contains(e.getKey().location().getNamespace()))
                .sorted(java.util.Comparator.comparing(e -> e.getKey().location().toString())).forEach(e -> {
                    var row = row("block", e.getKey().location().toString(), e.getValue());
                    row.addProperty("has_item", e.getValue().asItem() != net.minecraft.world.item.Items.AIR);
                    rows.add(row);
                });
        BuiltInRegistries.ITEM.entrySet().stream().filter(e -> NAMESPACES.contains(e.getKey().location().getNamespace()))
                .sorted(java.util.Comparator.comparing(e -> e.getKey().location().toString())).forEach(e -> {
                    var row = row("item", e.getKey().location().toString(), e.getValue());
                    if (e.getValue() instanceof BlockItem item)
                        row.addProperty("block", BuiltInRegistries.BLOCK.getKey(item.getBlock()).toString());
                    rows.add(row);
                });
        CEERegistries.WIRE_TYPE.entrySet().forEach(e -> rows.add(row("wire_type", e.getKey().location().toString(), e.getValue())));
        CEERegistries.WIRE_ATTACHMENT_TYPE.entrySet().forEach(e -> rows.add(row("wire_attachment", e.getKey().location().toString(), e.getValue())));
        CEERegistries.PANEL_ATTACHMENT_TYPE.entrySet().forEach(e -> rows.add(row("panel_attachment", e.getKey().location().toString(), e.getValue())));
        ComponentRegistry.entries().forEach(e -> rows.add(row("board_component", ComponentRegistry.getId(e).toString(), e)));
        FuseHoldable.ALL.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> rows.add(row("fuse_holdable", e.getKey().toString(), e.getValue())));
        report.add("entries", rows);
        Files.writeString(Path.of("stationary-registry.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        try (var stream = StationaryInventoryGameTests.class.getResourceAsStream(
                "/data/wwpg/release/0.1.0-beta.5/registry-inventory.json")) {
            h.assertTrue(stream != null, "Packaged stationary accounting record is missing");
            var expected = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("entries");
            var expectedClasses = new java.util.TreeMap<String, String>();
            var actualClasses = new java.util.TreeMap<String, String>();
            for (var entry : expected) {
                var row = entry.getAsJsonObject();
                if (!ModList.get().isLoaded("pinout") && row.get("id").getAsString().startsWith("pinout:")) continue;
                expectedClasses.put(row.get("registry").getAsString() + "/" + row.get("id").getAsString(),
                        row.get("implementation").getAsString());
            }
            for (var entry : rows) {
                var row = entry.getAsJsonObject();
                actualClasses.put(row.get("registry").getAsString() + "/" + row.get("id").getAsString(),
                        row.get("implementation").getAsString());
            }
            h.assertTrue(expectedClasses.equals(actualClasses), "Pinned inventory differs from its complete accounting record");
        }
        h.assertTrue(ModList.get().isLoaded("pinout") == System.getProperty("wwpg.test.profile", "BASE").equals("PINOUT"),
                "Requested optional profile does not match loaded mods");
        h.succeed();
    }

    private static JsonObject row(String registry, String id, Object value) {
        var row = new JsonObject();
        row.addProperty("registry", registry); row.addProperty("id", id);
        row.addProperty("implementation", value.getClass().getName());
        return row;
    }
}
