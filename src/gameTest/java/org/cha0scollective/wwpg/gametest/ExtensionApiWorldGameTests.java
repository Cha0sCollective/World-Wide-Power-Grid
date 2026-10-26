package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.electrical_panel.*;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.api.*;
import org.cha0scollective.wwpg.api.neoforge.ElectricalCapabilities;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.circuitboard.IncompleteCircuitItem;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.circuits.components.ResistorComponent;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity;
import org.patryk3211.powergrid.electricity.electricswitch.SwitchBlock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.cha0scollective.wwpg.gametest.ShowroomTools.*;

/** A playable external-add-on workshop. Uses registries/public contracts, never example classes. */
@GameTestHolder("wwpg_api_world")
@PrefixGameTestTemplate(false)
public final class ExtensionApiWorldGameTests {
    public static final BlockPos ORIGIN = new BlockPos(9000, 64, 9000);
    public static final BlockPos SPAWN = ORIGIN.offset(4, 0, -5);
    private static final ResourceLocation PANEL = id("wwpg_api_upstream", "panel_storage_indicator");
    private static final ResourceLocation SHUNT = id("wwpg_api_upstream", "api_shunt");
    private static final String[] KINDS = {"load", "source", "ac_source", "capacitor", "inductor",
            "diode", "transformer", "voltage_control", "current_control"};
    private static final String[] NAMES = {"Parallel variable load", "API DC supply", "API AC supply",
            "Saved capacitor", "Saved inductor", "Forward diode", "Ideal transformer",
            "Controlled voltage", "Controlled current"};
    private static final String[] SIGN_NAMES = {"LOAD", "DC SOURCE", "AC SOURCE", "CAPACITOR", "INDUCTOR",
            "DIODE", "TRANSFORMER", "VOLT CONTROL", "AMP CONTROL"};
    private static final String[][] INSTRUCTIONS = {
            {"Sneak-click load", "20 V / 0.4 A / 8 W", "Re-enable to reset"},
            {"Sneak-click source", "Native load: 20 V", "Re-enable to reset"},
            {"Sneak-click source", "50 Hz / 14.14 Vrms", "Load lights: 4 W"},
            {"Sneak-click green", "Stored: 20 -> 10 V", "Feed OFF: discharges"},
            {"Toggle gold feed", "20 V / about 0.2 A", "Feed ON to reset"},
            {"Toggle gold feed", "Forward: about 19 V", "Reverse wires: OFF"},
            {"Sneak-click copper", "40 V -> 10 V output", "Restore ratio 2"},
            {"Sneak-click purple", "40 V -> 0 V output", "Re-enable to reset"},
            {"Sneak-click orange", "-20 V / 0.2 A", "Re-enable to reset"}
    };
    private record Station(int index, BlockPos source, BlockPos device, BlockPos load, BlockPos gauge) { }

    private static ResourceLocation id(String namespace, String path) { return ResourceLocation.fromNamespaceAndPath(namespace, path); }
    private static Station station(int index) {
        var source = ORIGIN.offset(index % 3 * 22, 0, index / 3 * 20);
        return new Station(index, source, source.east(6), source.east(12), source.offset(12, 0, 3));
    }
    private static BlockPos panelOrigin() { return ORIGIN.south(60); }
    private static BlockPos panelPosition() { return panelOrigin().east(7); }
    private static BlockPos boardOrigin() { return ORIGIN.offset(22, 0, 60); }
    private static BlockPos boardPosition() { return boardOrigin().east(7); }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void buildAndVerifyPublicExtensionWorkshop(GameTestHelper h) {
        boolean restore = "VERIFY".equals(System.getProperty("wwpg.test.restartPhase", "SETUP"));
        ModdedConfigs.server().electricity.solver.multiTicks.set(16);
        force(h.getLevel());
        requireExamples(h);
        if (!restore) {
            buildFloor(h);
            buildGeneral(h);
            buildUpstream(h);
            tools(h);
        }
        // Forced block chunks do not imply that their native wire-entity inboxes
        // have loaded. Start construction/operation after actual readiness in
        // both new saves and cold verification; retain every settling window.
        h.startSequence().thenWaitUntil(() -> requireWorkshopReady(h))
                .thenExecute(() -> { if (!restore) { configureGeneral(h); configureUpstream(h); } })
                .thenIdle(restore ? 150 : 130).thenExecute(() -> verify(h, false))
                .thenIdle(10).thenExecute(() -> changeControls(h, true))
                .thenIdle(80).thenExecute(() -> verify(h, true))
                .thenIdle(10).thenExecute(() -> changeControls(h, false))
                .thenIdle(160).thenExecute(() -> {
            verify(h, false);
            ExampleWorldGameTests.cleanDroppedWireItems(h.getLevel(), new AABB(8988, 62, 8988, 9076, 80, 9076));
            h.getLevel().setDefaultSpawnPos(SPAWN, 0);
            h.getLevel().getServer().getWorldData().setGameType(GameType.CREATIVE);
            writeManifest(h, restore);
            DynamicGameTests.audit(h);
            h.getLevel().getDataStorage().save();
            h.getLevel().getChunkSource().save(true);
            WorldWidePowerGrid.LOGGER.info("WWPG_API_WORKSHOP_PASSED: phase={}; nine general devices, native panel storage indicator and assembled PG shunt; real wiring and ON/OFF/reset checks", restore ? "VERIFY" : "SETUP");
        }).thenSucceed();
    }

    private static void requireWorkshopReady(GameTestHelper h) {
        var positions = new ArrayList<BlockPos>();
        for (int i = 0; i < KINDS.length; i++) {
            var s = station(i); positions.add(s.source); positions.add(s.device);
            if (hasSeparateLoad(i)) positions.add(s.load);
            if (i != 2) positions.add(s.gauge);
        }
        positions.addAll(List.of(panelOrigin(), panelOrigin().east(3), panelPosition(),
                boardOrigin(), boardOrigin().east(3), boardPosition(), boardPosition().south(3)));
        for (var pos : positions) h.assertTrue(org.cha0scollective.wwpg.extension.ApiTerminals.canWire(h.getLevel(), pos),
                "Waiting for Workshop chunk/entity readiness and retired ownership at " + pos);
        WorldWidePowerGrid.LOGGER.info("WWPG_API_WORKSHOP_READY: phase={}; all station endpoints ticking, entities loaded, no pending retirement",
                System.getProperty("wwpg.test.restartPhase", "SETUP"));
    }

    private static void requireExamples(GameTestHelper h) {
        for (String kind : KINDS)
            h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id("wwpg_api_example", kind)), "Missing independent example block: " + kind);
        h.assertTrue(BuiltInRegistries.ITEM.containsKey(PANEL) && BuiltInRegistries.ITEM.containsKey(SHUNT), "Install both independent example jars for the workshop");
    }
    private static void force(net.minecraft.server.level.ServerLevel level) {
        for (int x = 8992 >> 4; x <= 9072 >> 4; x++) for (int z = 8992 >> 4; z <= 9072 >> 4; z++) {
            level.setChunkForced(x, z, true);
            level.getChunk(x, z);
        }
        level.getDataStorage().computeIfAbsent(net.minecraft.world.level.ForcedChunksSavedData.factory(), "chunks").setDirty();
    }
    /** Register only for an opted-in packaged wwpg_api_world run; normal play never calls it. */
    public static void preserveForExport(ServerStoppingEvent event) {
        force(event.getServer().overworld());
        WorldWidePowerGrid.LOGGER.info("WWPG_API_WORKSHOP_NATIVE_TICKETS_SAVED: restored workshop tickets after GameTest cleanup");
    }
    private static void buildFloor(GameTestHelper h) {
        for (int x = -8; x <= 72; x++) for (int z = -8; z <= 72; z++)
            put(h, ORIGIN.offset(x, -1, z), x % 22 == 18 || z % 20 == 15 ? Blocks.WHITE_CONCRETE : Blocks.SMOOTH_STONE);
        var level = h.getLevel();
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.setWeatherParameters(0, 1000000, false, false);
        sign(h, ORIGIN.offset(6, 0, -3), "API WORKSHOP", "Goggles / tools", "9 API / 2 native", "Follow white paths");
    }
    private static void example(GameTestHelper h, BlockPos position, String kind) {
        put(h, position, BuiltInRegistries.BLOCK.get(id("wwpg_api_example", kind)));
    }
    private static boolean isDirectSource(int index) { return index == 1 || index == 2; }
    private static boolean hasSeparateLoad(int index) { return index >= 5; }
    private static void buildGeneral(GameTestHelper h) {
        for (int i = 0; i < KINDS.length; i++) {
            var s = station(i);
            if (i == 0) {
                put(h, s.source, CEEBlocks.CREATIVE_BATTERY.get());
                example(h, s.device, "load");
            } else if (isDirectSource(i)) {
                example(h, s.source, KINDS[i]);
                if (i == 2) example(h, s.device, "load");
                else put(h, s.device, ModdedBlocks.CREATIVE_RESISTOR.get());
            } else {
                example(h, s.source, "source");
                example(h, s.device, KINDS[i]);
            }
            if (hasSeparateLoad(i)) put(h, s.load, ModdedBlocks.CREATIVE_RESISTOR.get());
            if (i != 2) put(h, s.gauge, ModdedBlocks.VOLTAGE_METER.get());
            sign(h, s.device.south(5), "A" + String.format("%02d", i + 1) + " " + SIGN_NAMES[i],
                    INSTRUCTIONS[i][0], INSTRUCTIONS[i][1], INSTRUCTIONS[i][2]);
            sign(h, s.source.north(2), i == 0 ? "CEE SUPPLY" : "GOLD FEED", "Empty-hand read", "Sneak-click toggles", "Restore ON");
        }
    }
    private static void gauge(GameTestHelper h, Station s, BlockPos measured, int first, int second) {
        wire(h, measured, first, s.gauge, 0, false);
        wire(h, measured, second, s.gauge, 1, true);
    }
    private static void configureGeneral(GameTestHelper h) {
        // Establish native ballast settings before connecting any live supply;
        // parameter configuration is separate from the native wiring workflow.
        resistance(h, station(1).device, 100);
        for (int i = 5; i < KINDS.length; i++) resistance(h, station(i).load, 100);
        for (int i = 0; i < KINDS.length; i++) {
            var s = station(i);
            if (i == 0) {
                scroll(h, s.source, 20000);
                wire(h, s.source, 1, s.device, 0, true);
                wire(h, s.source, 0, s.device, 1, false);
            } else if (i == 5) {
                wire(h, s.source, 0, s.device, 0, false);
                wire(h, s.device, 1, s.load, 0, true);
                wire(h, s.source, 1, s.load, 1, false);
            } else {
                wire(h, s.source, 0, s.device, 0, i % 2 == 0);
                wire(h, s.source, 1, s.device, 1, i % 2 != 0);
                if (hasSeparateLoad(i)) {
                    wire(h, s.device, 2, s.load, 0, true);
                    wire(h, s.device, 3, s.load, 1, false);
                }
            }
            if (i != 2) gauge(h, s, hasSeparateLoad(i) ? s.load : s.device, 0, 1);
        }
    }

    private static void buildUpstream(GameTestHelper h) {
        put(h, panelOrigin(), ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        put(h, panelOrigin().east(3), ModdedBlocks.LV_SWITCH.get());
        h.getLevel().setBlockAndUpdate(panelPosition(), CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        sign(h, panelPosition().south(5), "A10 CEE ADD-ON", "Native storage lamp", "70 V: visible ON", "Feed switch: OFF/ON");
        put(h, boardOrigin(), CEEBlocks.CREATIVE_BATTERY.get());
        put(h, boardOrigin().east(3), ModdedBlocks.LV_SWITCH.get());
        put(h, boardPosition().south(3), ModdedBlocks.VOLTAGE_METER.get());
        sign(h, boardPosition().south(5), "A11 PG ADD-ON", "Assembled API shunt", "20 V / about 0.2 A", "Feed switch: OFF/ON");
    }
    private static void configureUpstream(GameTestHelper h) {
        var level = h.getLevel();
        pgSource(h, panelOrigin(), 70);
        setFeed(h, panelOrigin().east(3), true);
        var slot = ElectricalPanelSlot.THIRD_LEFT;
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(PANEL));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var point = Vec3.atLowerCornerOf(panelPosition()).add(slot.center.x, slot.center.y, .1);
        var installed = level.getBlockState(panelPosition()).useItemOn(stack, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(point, Direction.NORTH, panelPosition(), false));
        h.assertTrue(installed == ItemInteractionResult.SUCCESS && stack.isEmpty(), "Native panel item installation failed");
        var attachment = panelAttachment(h);
        wire(h, panelOrigin(), 0, panelOrigin().east(3), 0, true);
        wire(h, panelOrigin().east(3), 1, panelPosition(), attachment.nodes[0].id(), false);
        wire(h, panelOrigin(), 1, panelPosition(), attachment.nodes[1].id(), true);

        scroll(h, boardOrigin(), 20000);
        setFeed(h, boardOrigin().east(3), true);
        assembleBoard(h);
        wire(h, boardOrigin(), 1, boardOrigin().east(3), 0, false);
        wire(h, boardOrigin().east(3), 1, boardPosition(), 0, true);
        wire(h, boardOrigin(), 0, boardPosition(), 1, false);
        wire(h, boardPosition(), 0, boardPosition().south(3), 0, true);
        wire(h, boardPosition(), 1, boardPosition().south(3), 1, false);
    }
    private static PanelAttachment panelAttachment(GameTestHelper h) {
        var attachment = ((ElectricalPanelBlockEntity) h.getLevel().getBlockEntity(panelPosition())).getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()];
        var type = CEERegistries.PANEL_ATTACHMENT_TYPE.get(PANEL);
        h.assertTrue(attachment != null && attachment.type == type, "Workshop lost the custom native panel identity");
        return attachment;
    }
    private static void assembleBoard(GameTestHelper h) {
        var type = ComponentRegistry.get(SHUNT);
        h.assertTrue(type != null, "Workshop shunt component is not registered");
        var schematic = new CircuitSchematic();
        schematic.setName("WWPG API workshop shunt");
        schematic.placeComponent(new PlacedComponent(Components.CONNECTOR.get(), 0, 5, null), 0, 5);
        schematic.placeComponent(new PlacedComponent(Components.CONNECTOR.get(), 13, 5, null), 13, 5);
        var component = new PlacedComponent(type, 5, 5, null);
        component.set(ResistorComponent.RESISTANCE, 100f);
        schematic.placeComponent(component, 5, 5);
        schematic.front().addHorizontalLine(6, 1, 5);
        schematic.front().addHorizontalLine(6, 9, 14);
        var tag = new CompoundTag();
        tag.put("Schematic", schematic.serializeNbt(h.getLevel().registryAccess()));
        ItemStack board = ModdedItems.INCOMPLETE_CIRCUIT.asStack();
        board.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        for (var part : schematic.components()) {
            var item = ComponentRegistry.getItem(h.getLevel(), part.component);
            h.assertTrue(item != null, "Missing native assembly item for " + ComponentRegistry.getId(part.component));
            board = IncompleteCircuitItem.insert(h.getLevel(), board, new ItemStack(item));
            h.assertTrue(board != null, "Native component rejected board assembly");
        }
        h.assertTrue(board.is(ModdedBlocks.CIRCUIT_BOARD.get().asItem()), "Native assembly did not finish a board");
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, board);
        var result = board.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(boardPosition().below().getCenter().add(0, .5, 0), Direction.UP, boardPosition().below(), false)));
        h.assertTrue(result.consumesAction() && board.isEmpty(), "Native assembled board placement failed");
    }

    private static DeviceResults result(GameTestHelper h, BlockPos position) {
        var value = ElectricalMeasurements.read(h.getLevel(), position);
        h.assertTrue(value.status() == MeasurementStatus.VALID, "Workshop result unavailable at " + position + ": " + value.status() + " " + value.detail());
        return value;
    }
    private static void verify(GameTestHelper h, boolean off) {
        var load = result(h, station(0).device);
        near(h, load.element("load_a").orElseThrow().meanCurrentAmps(), off ? 0 : .2, .001, "A01 first parallel load");
        near(h, load.element("load_b").orElseThrow().meanCurrentAmps(), off ? 0 : .2, .001, "A01 second parallel load");
        var source = result(h, station(1).source);
        near(h, source.element("supply").orElseThrow().meanCurrentAmps(), off ? 0 : -.2, .001, "A02 signed source current");
        var ac = result(h, station(2).source);
        near(h, ac.element("supply").orElseThrow().rmsVoltageVolts(), off ? 0 : 20 / Math.sqrt(2), .03, "A03 genuine RMS");
        near(h, result(h, station(2).device).element("load_a").orElseThrow().meanPowerWatts(), off ? 0 : 2, .012, "A03 AC mean power");
        double stored = ((ElementState.Capacitor) result(h, station(3).device).state("storage").orElseThrow()).voltageVolts();
        near(h, stored, off ? 10 : 20, off ? .25 : .10, "A04 accepted capacitor state");
        near(h, ((ElementState.Inductor) result(h, station(4).device).state("storage").orElseThrow()).currentAmps(), off ? 0 : .2, .001, "A05 accepted inductor state");
        var diode = result(h, station(5).device).element("junction").orElseThrow();
        if (off) near(h, volts(h, station(5).load, 0, 1), 0, .001, "A06 disconnected feed");
        else h.assertTrue(diode.meanCurrentAmps() > .17 && diode.meanCurrentAmps() < .2 && volts(h, station(5).load, 0, 1) > 17,
                "A06 diode did not carry forward power into its native load");
        var transformer = result(h, station(6).device).element("coupling").orElseThrow();
        near(h, volts(h, station(6).load, 0, 1), off ? 10 : 40, .10, "A07 signed winding ratio");
        near(h, transformer.meanCurrentAmps(), off ? .05 : .8, .004, "A07 reflected load current");
        near(h, result(h, station(7).device).element("controlled").orElseThrow().meanVoltageVolts(), off ? 0 : 40, .02, "A08 controlled voltage");
        var controlled = result(h, station(8).device).element("controlled").orElseThrow();
        near(h, controlled.meanCurrentAmps(), off ? 0 : .2, .001, "A09 oriented current output");
        near(h, controlled.meanVoltageVolts(), off ? 0 : -20, .10, "A09 signed output voltage");
        for (int index : new int[]{0, 1, 2, 4, 5, 7, 8}) {
            var s = station(index); var position = isDirectSource(index) ? s.source : s.device;
            h.assertTrue(h.getLevel().getBlockState(position).getLightEmission(h.getLevel(), position) == (off ? 0 : 15), "A" + (index + 1) + " visible output disagrees with power");
        }
        verifyUpstream(h, off);
    }
    private static void verifyUpstream(GameTestHelper h, boolean off) {
        var attachment = panelAttachment(h);
        var tag = new CompoundTag(); attachment.write(tag, true, h.getLevel().registryAccess());
        if (off) h.assertTrue(tag.getFloat("Light") < .05, "A10 stored indicator did not discharge after its feed opened");
        else h.assertTrue(tag.getFloat("Light") > .95, "A10 native indicator is not visibly powered: " + tag
                + "; feed=" + h.getLevel().getBlockState(panelOrigin().east(3)));
        var board = (CircuitBoardBlockEntity) h.getLevel().getBlockEntity(boardPosition());
        var shunt = board.getComponentsStream().filter(p -> SHUNT.equals(ComponentRegistry.getId(p.component))).findFirst().orElseThrow();
        h.assertTrue(shunt.get(ResistorComponent.RESISTANCE) == 100f && !shunt.destroyed, "A11 lost native shunt configuration");
        near(h, volts(h, boardPosition(), 0, 1), off ? 0 : 20, .06, "A11 native board voltage");
        h.assertTrue(h.getLevel().getCapability(ElectricalCapabilities.DEVICE, boardPosition()) == null, "A11 native PG board acquired a duplicate electrical owner");
    }
    private static void toggle(GameTestHelper h, BlockPos position) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        h.assertTrue(h.getLevel().getBlockState(position).useWithoutItem(h.getLevel(), player,
                new BlockHitResult(position.getCenter(), Direction.NORTH, position, false)).consumesAction(), "Example rejected native empty-hand control");
    }
    private static void changeControls(GameTestHelper h, boolean off) {
        for (int i = 0; i < KINDS.length; i++) {
            var s = station(i);
            toggle(h, i == 1 || i == 2 || i == 4 || i == 5 ? s.source : s.device);
        }
        for (var position : List.of(panelOrigin().east(3), boardOrigin().east(3))) {
            setFeed(h, position, !off);
        }
    }
    private static void setFeed(GameTestHelper h, BlockPos position, boolean enabled) {
        // Native PG reconstructs a switch from its OPEN block state on reload.
        // Keep the exhibit's saved/visible setting and live electrical state
        // together, as its normal player interaction does.
        h.getLevel().setBlockAndUpdate(position,
                h.getLevel().getBlockState(position).setValue(SwitchBlock.OPEN, !enabled));
        ((SwitchBlockEntity) h.getLevel().getBlockEntity(position)).setState(enabled);
    }
    private static ItemStack guide() {
        var pages = new ArrayList<Filterable<Component>>();
        pages.add(Filterable.passThrough(Component.literal("EXTENSION API\nWORKSHOP\n\nWear goggles. Take\nboth meters. Follow\nthe white paths.\n\nRed/blue markers:\npositive/negative.\nNative wires work\non these new blocks.")));
        pages.add(Filterable.passThrough(Component.literal("CONTROLS\n\nEmpty-hand click:\nchat readings.\nSneak-click:\nchange API setting.\n\nBright block means\naccepted power or\nstored charge.\nRestore enabled ON\nafter each test.")));
        pages.add(Filterable.passThrough(Component.literal("AC AND SIGNS\n\nA03: 20 V peak,\n14.14 V RMS, 50 Hz.\nInstantaneous V can\nbe zero while watts\nand light remain ON.\n\nA09: -20 V is the\ncurrent-source sign,\nnot missing power.")));
        pages.add(Filterable.passThrough(Component.literal("STORAGE\n\nA04 green toggle:\nstored 20 to 10 V.\nGold feed OFF:\ncharge falls to zero.\n\nA05 coil settles fast.\nToggle gold feed to\nchange its current.\nSave/reopen keeps\naccepted history.")));
        pages.add(Filterable.passThrough(Component.literal("TRANSFORMER\n\nA07 demonstrates\nideal API coupling.\n20 V input gives\n40 V or 10 V out.\n\nUse an AC supply to\nexplore AC ratios.\nNative transformers\nhave their own real\nassembly/models.")));
        pages.add(Filterable.passThrough(Component.literal("UPSTREAM ADD-ONS\n\nA10 native CEE panel:\ncustom storage lamp.\nOpen feed: decays.\nClose: lights again.\n\nA11 native PG board:\nassembled API shunt.\n20 V / about 0.2 A.\nFeed switch: OFF/ON.")));
        pages.add(Filterable.passThrough(Component.literal("REPAIR AND RESET\n\nUse native cutters\nand spools; reconnect\nthe same terminals.\nGuides mark polarity.\n\nKeep a clean ZIP\nbefore dismantling.\nPlayer wire drops\nremain collectible.\nNo cleanup runs\nduring normal play.")));
        var item = Items.WRITTEN_BOOK.getDefaultInstance();
        item.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("API Workshop"), "Cha0sCollective", 0, pages, true));
        return item;
    }
    private static void tools(GameTestHelper h) {
        var position = ORIGIN.offset(0, 0, -3);
        put(h, position, Blocks.CHEST);
        var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(position);
        List<ItemStack> tools = List.of(AllItems.GOGGLES.asStack(), ModdedItems.MULTIMETER.asStack(), CEEItems.CLAMP_METER.asStack(),
                ModdedItems.WIRE_CUTTER.asStack(), AllItems.WRENCH.asStack(), ModdedItems.WIRE.asStack(64),
                CEEItems.COPPER_WIRE_SPOOL.asStack(16), CEEItems.EMPTY_SPOOL.asStack(16), guide(),
                new ItemStack(BuiltInRegistries.ITEM.get(PANEL), 16), new ItemStack(BuiltInRegistries.ITEM.get(SHUNT), 16),
                ModdedItems.CIRCUIT_SCHEMATIC.asStack(16), ModdedItems.INCOMPLETE_CIRCUIT.asStack(16));
        for (int i = 0; i < tools.size(); i++) chest.setItem(i, tools.get(i));
        chest.setChanged();
    }
    private static void writeManifest(GameTestHelper h, boolean restore) {
        var report = new JsonObject();
        report.addProperty("schema", 1); report.addProperty("target", net.neoforged.fml.ModList.get().getModContainerById("wwpg").orElseThrow().getModInfo().getVersion().toString()); report.addProperty("api_version", WWPGApi.API_VERSION);
        report.addProperty("profile", System.getProperty("wwpg.test.profile", "BASE"));
        report.addProperty("backend", System.getProperty("wwpg.test.backend", "NATIVE"));
        report.addProperty("phase", restore ? "VERIFY" : "SETUP"); report.addProperty("completed", true);
        report.addProperty("runtime_station_checks", 11); report.addProperty("native_item_workflows", true);
        var spawn = new JsonArray(); spawn.add(SPAWN.getX()); spawn.add(SPAWN.getY()); spawn.add(SPAWN.getZ()); report.add("spawn", spawn);
        var stations = new JsonArray();
        for (int i = 0; i < KINDS.length; i++) {
            var s = station(i); var record = new JsonObject();
            record.addProperty("code", "A" + String.format("%02d", i + 1)); record.addProperty("name", NAMES[i]);
            record.addProperty("registry_id", "wwpg_api_example:" + KINDS[i]);
            var position = isDirectSource(i) ? s.source : s.device;
            var coordinates = new JsonArray(); coordinates.add(position.getX()); coordinates.add(position.getY()); coordinates.add(position.getZ());
            record.add("position", coordinates); record.addProperty("checked", true);
            var instructions = new JsonArray(); for (String text : INSTRUCTIONS[i]) instructions.add(text); record.add("instructions", instructions);
            record.add("results", new GsonBuilder().create().toJsonTree(result(h, position))); stations.add(record);
        }
        for (var entry : List.of(new Object[]{"A10", PANEL.toString(), panelPosition()}, new Object[]{"A11", SHUNT.toString(), boardPosition()})) {
            var record = new JsonObject(); record.addProperty("code", (String) entry[0]); record.addProperty("registry_id", (String) entry[1]); record.addProperty("checked", true);
            var at = (BlockPos) entry[2]; var coordinates = new JsonArray(); coordinates.add(at.getX()); coordinates.add(at.getY()); coordinates.add(at.getZ()); record.add("position", coordinates); stations.add(record);
        }
        report.add("stations", stations);
        var nativeTickets = new JsonArray();
        for (int x = 8992 >> 4; x <= 9072 >> 4; x++) for (int z = 8992 >> 4; z <= 9072 >> 4; z++) {
            var coordinate = new JsonArray(); coordinate.add(x); coordinate.add(z); nativeTickets.add(coordinate);
        }
        report.add("native_forced_chunks", nativeTickets);
        report.addProperty("loose_construction_wire_items", 0);
        report.addProperty("history_claim", "Playable station checks. Pre-solve state and actual unload/reload certification require the separate lifecycle evidence.");
        try { Files.writeString(Path.of("api-workshop-stations.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n", StandardCharsets.UTF_8); }
        catch (Exception error) { throw new IllegalStateException("Cannot record exact workshop stations", error); }
    }
    private ExtensionApiWorldGameTests() { }
}
