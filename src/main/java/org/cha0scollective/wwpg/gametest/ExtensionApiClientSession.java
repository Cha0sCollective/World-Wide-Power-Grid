package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.google.gson.JsonParser;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;
import org.patryk3211.powergrid.circuits.components.ResistorComponent;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Opt-in real-player coordinator shared by dedicated and integrated acceptance. */
@EventBusSubscriber(modid = "wwpg")
public final class ExtensionApiClientSession {
    public static final BlockPos PANEL = ExtensionApiWorldGameTests.ORIGIN.offset(7, 0, 60);
    public static final BlockPos BOARD = ExtensionApiWorldGameTests.ORIGIN.offset(29, 0, 60);
    public static final BlockPos TABLE = ExtensionApiWorldGameTests.ORIGIN.offset(36, 0, 60);
    private static final Map<String, Set<UUID>> ACKS = new HashMap<>();
    private static final String[] PHASES = {"view", "wire", "configure", "configure_on", "meter", "panel_edit", "board_edit", "repair", "repair_wire"};
    private final ServerLevel level;
    private final List<ServerPlayer> players;
    private final boolean multiplayer;
    private final long started;
    private Set<UUID> boardIds;
    private CompoundTag design;
    private int stage = -1;
    private long phaseTick;
    private boolean finished;

    public static BlockPos source(boolean a) { return ExtensionApiWorldGameTests.ORIGIN.offset(a ? 0 : 22, 0, 68); }
    public static BlockPos load(boolean a) { return source(a).east(6); }
    public static BlockPos view(int index) {
        if (index == 9) return PANEL;
        if (index == 10) return BOARD;
        var origin = ExtensionApiWorldGameTests.ORIGIN.offset(index % 3 * 22, 0, index / 3 * 20);
        return index == 1 || index == 2 ? origin : origin.east(6);
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("wwpg.test.apiWorkshop")) return;
        event.getDispatcher().register(Commands.literal("wwpg-api-ack")
                .then(Commands.argument("phase", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var phase = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "phase");
                    ACKS.computeIfAbsent(phase, key -> new HashSet<>()).add(player.getUUID());
                    return 1;
                })));
    }

    public ExtensionApiClientSession(ServerLevel level, List<ServerPlayer> players, boolean multiplayer, Path save) {
        require(Boolean.getBoolean("wwpg.test.apiWorkshop"), "API acceptance was not opted in");
        require(players.size() == (multiplayer ? 2 : 1), "Expected actual connected players");
        this.level = level; this.players = List.copyOf(players); this.multiplayer = multiplayer; started = level.getGameTime();
        DynamicGameTests.substeps(level);
        ACKS.clear();
        try {
            var manifest = JsonParser.parseString(Files.readString(save.resolve("WWPG_MANIFEST.json"))).getAsJsonObject();
            require(manifest.get("target").getAsString().equals(net.neoforged.fml.ModList.get().getModContainerById("wwpg").orElseThrow().getModInfo().getVersion().toString()) && manifest.get("api_version").getAsString().equals("1.0.0"), "Wrong exact exported Workshop");
            require(manifest.get("profile").getAsString().equals(System.getProperty("wwpg.test.profile", "BASE")), "Workshop installation profile differs");
        } catch (Exception error) { throw new IllegalStateException("Cannot read exact Workshop export manifest", error); }
        for (var player : players) {
            player.setGameMode(GameType.CREATIVE);
            player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());
        }
    }

    /** Advance only on the server game thread; native clients send every tested interaction. */
    public boolean tick() {
        if (finished) return true;
        require(level.getGameTime() - started < 10000, "API real-player session timed out in " + phase());
        require(ACKS.getOrDefault("failed", Set.of()).isEmpty(), "An actual API client reported a failed interaction");
        if (stage < 0) {
            for (int i = 0; i < 11; i++) if (!level.isPositionEntityTicking(view(i))) return false;
            if (level.getGameTime() - started < 100) return false;
            verifyOriginal();
            prepareBenches();
            stage = 0; announce(); return false;
        }
        if (!ACKS.getOrDefault(phase(), Set.of()).containsAll(players.stream().map(ServerPlayer::getUUID).toList())
                || level.getGameTime() - phaseTick < 30) return false;
        verifyPhase();
        WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_SERVER_PHASE_PASSED: {} players={}", phase(), players.stream().map(p -> p.getGameProfile().getName()).toList());
        if (++stage == PHASES.length) {
            verifyOriginal();
            require(org.cha0scollective.wwpg.bridge.SolverAudit.ceeAttempts() == 0, "CEE attempted an independent solve during client acceptance");
            var backend = org.patryk3211.powergrid.config.CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend", "NATIVE"));
            require(org.cha0scollective.wwpg.bridge.Bridges.get(level).backendCounts().getOrDefault(backend, 0L) > 0,
                    "Requested solver never produced the actual client session's accepted results");
            finished = true;
            WorldWidePowerGrid.LOGGER.info(multiplayer ? "API_WORKSHOP_TWO_CLIENTS_PASSED: profile={}; backend={}; players={}" : "API_WORKSHOP_INTEGRATED_SERVER_PASSED: profile={}; backend={}; players={}",
                    System.getProperty("wwpg.test.profile", "BASE"), System.getProperty("wwpg.test.backend", "NATIVE"), players.stream().map(p -> p.getGameProfile().getName()).toList());
            players.forEach(p -> p.sendSystemMessage(Component.literal("API_WORKSHOP_TEST done")));
            return true;
        }
        announce(); return false;
    }
    private String phase() { return stage < 0 ? "loading" : stage < PHASES.length ? PHASES[stage] : "done"; }
    private boolean a(ServerPlayer player) { return !multiplayer || player.getGameProfile().getName().endsWith("A"); }
    private void equip(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack); player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();
    }
    private void announce() {
        phaseTick = level.getGameTime();
        for (var player : players) {
            boolean a = a(player);
            ItemStack stack = switch (phase()) {
                case "wire" -> a ? ModdedItems.WIRE.asStack(64) : CEEItems.WIRE_SPOOL.asStack();
                case "meter" -> a ? ModdedItems.MULTIMETER.asStack() : CEEItems.CLAMP_METER.asStack();
                case "panel_edit" -> Items.BLUE_DYE.getDefaultInstance();
                case "repair" -> a ? ModdedItems.WIRE_CUTTER.asStack() : ItemStack.EMPTY;
                case "repair_wire" -> a ? ModdedItems.WIRE.asStack(64) : ItemStack.EMPTY;
                default -> ItemStack.EMPTY;
            };
            equip(player, stack);
            var position = phase().equals("panel_edit") ? PANEL : phase().equals("board_edit") ? TABLE : source(a);
            player.teleportTo(level, position.getX() + .5, 64, position.getZ() - 2.5, 0, 30);
            player.sendSystemMessage(Component.literal("API_WORKSHOP_TEST " + phase()));
        }
    }
    private void prepareBenches() {
        var apiLoad = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("wwpg_api_example:load"));
        var apiSource = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("wwpg_api_example:source"));
        for (boolean a : List.of(true, false)) {
            level.setBlockAndUpdate(source(a), (a ? apiSource : CEEBlocks.CREATIVE_BATTERY.get()).defaultBlockState());
            level.setBlockAndUpdate(load(a), apiLoad.defaultBlockState());
        }
        var board = (CircuitBoardBlockEntity) level.getBlockEntity(BOARD);
        boardIds = board.getComponentsStream().map(component -> component.uuid).collect(java.util.stream.Collectors.toUnmodifiableSet());
        design = board.getSchematic().serializeNbt(level.registryAccess());
        level.setBlockAndUpdate(TABLE, ModdedBlocks.CIRCUIT_DESIGN_TABLE.getDefaultState());
        var table = (CircuitDesignTableBlockEntity) level.getBlockEntity(TABLE);
        table.setSchematic(CircuitSchematic.fromNbt(level.registryAccess(), design));
        table.getInventory().setItem(1, ModdedItems.CIRCUIT_SCHEMATIC.asStack()); table.notifyUpdate();
    }
    private void verifyOriginal() {
        for (int i = 0; i < 9; i++) {
            var results = ElectricalMeasurements.read(level, view(i));
            require(results.status() == MeasurementStatus.VALID && !results.elements().isEmpty(), "API station lost accepted readings: A" + (i + 1));
        }
        var panel = (ElectricalPanelBlockEntity) level.getBlockEntity(PANEL);
        var attachment = panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()];
        var tag = new CompoundTag(); attachment.write(tag, true, level.registryAccess());
        require(tag.getFloat("Light") > .95 && tag.getDouble("ApiStoredVoltage") > 69, "Native custom panel is not visibly powered");
        near(voltage(BOARD), 20, .1, "Native custom board remains powered");
    }
    private double voltage(BlockPos position) {
        var measurement = ElectricalMeasurements.readVoltageBetween(level, position, 0, position, 1);
        require(measurement.status() == MeasurementStatus.VALID, "Unavailable terminal reading at " + position + ": " + measurement.detail());
        return measurement.voltage().orElseThrow().meanVoltageVolts();
    }
    private void verifyPhase() {
        switch (phase()) {
            case "view" -> {
                var battery = (SmartBlockEntity) level.getBlockEntity(source(false));
                battery.getBehaviour(ScrollValueBehaviour.TYPE).setValue(20000);
                DevicesSavedData.load(level).getDevice(source(false), CreativeBatteryDevice.class).voltage = 20;
            }
            case "wire", "configure_on", "meter", "repair_wire" -> {
                near(voltage(load(true)), 20, .05, "Actual PG item powers API bench");
                near(voltage(load(false)), 20, .05, "Actual CEE item powers API bench");
            }
            case "configure" -> {
                near(voltage(load(true)), 0, .001, "API source responds to actual client enable click");
                {
                    var result = ElectricalMeasurements.read(level, load(false));
                    require(result.status() == MeasurementStatus.VALID && Math.abs(result.element("load_a").orElseThrow().meanCurrentAmps()) < 1e-8, "Other client's API load configuration did not take effect");
                }
            }
            case "panel_edit" -> {
                var panel = (ElectricalPanelBlockEntity) level.getBlockEntity(PANEL);
                var attachment = panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()];
                var tag = new CompoundTag(); attachment.write(tag, true, level.registryAccess());
                require(tag.getString("Color").equals("blue") && tag.getDouble("ApiStoredVoltage") > 69, "Native dye edit lost panel configuration or accepted storage");
            }
            case "board_edit" -> {
                var board = (CircuitBoardBlockEntity) level.getBlockEntity(BOARD);
                require(board.getSchematic().getName().equals("WWPG API client edited"), "Native creative board edit packet did not update its design");
                require(board.getComponentsStream().map(component -> component.uuid).collect(java.util.stream.Collectors.toSet()).equals(boardIds), "Native edit replaced component identities");
                var shunt = board.getComponentsStream().filter(c -> ComponentRegistry.getId(c.component).toString().equals("wwpg_api_upstream:api_shunt")).findFirst().orElseThrow();
                require(shunt.get(ResistorComponent.RESISTANCE) == 100f && !shunt.destroyed, "Native GUI edit lost shunt settings");
                var table = (CircuitDesignTableBlockEntity) level.getBlockEntity(TABLE);
                var copiedItems = new ArrayList<ItemStack>(); copiedItems.add(table.getInventory().getItem(0));
                players.forEach(player -> copiedItems.addAll(player.getInventory().items));
                var schematicItems = copiedItems.stream().filter(stack -> stack.is(ModdedItems.CIRCUIT_SCHEMATIC.get())).toList();
                var copies = schematicItems.stream().map(stack -> CircuitSchematic.fromStack(level.registryAccess(), stack))
                        .filter(Objects::nonNull).toList();
                // Native item copies omit instance UUIDs; keep every serialized design field they do preserve.
                var expectedCopy = table.getSchematic().serializeSafeNbt(level.registryAccess());
                long matchingCopies = copies.stream().filter(copy -> copy.components().size() == 3
                        && "WWPG API client edited".equals(copy.getName())
                        && copy.serializeSafeNbt(level.registryAccess()).equals(expectedCopy)).count();
                require(matchingCopies > 0,
                        "Native schematic item copy/load did not reach the server/player inventory");
                require(table.getSchematic().components().size() == 3 && table.getSchematicName().equals("WWPG API client edited"), "Native copy lost the editable design");
                near(voltage(BOARD), 20, .1, "Board stayed powered during editing");
                WorldWidePowerGrid.LOGGER.info("API_WORKSHOP_NATIVE_COPY_PASSED: schematicItems={}; blankItems={}; parsedCopies={}; matchingCopies={}; fullNativeCopyNbt=true; boardComponentIdsPreserved=true",
                        schematicItems.size(), schematicItems.size() - copies.size(), copies.size(), matchingCopies);
            }
            case "repair" -> near(voltage(load(true)), 0, .001, "Native cutter disconnected the API load");
            default -> { }
        }
    }
    private static void near(double actual, double expected, double tolerance, String label) {
        require(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance, label + ": expected " + expected + ", got " + actual);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
