package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.CEEPanelAttachmentTypes;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.GaugePanelAttachment;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Optional API types stay in the lazily loaded runner, including its fields. */
@GameTestHolder("wwpg_pinout")
@PrefixGameTestTemplate(false)
public final class PinoutLifecycleGameTests {
    @GameTest(template = "empty", timeoutTicks = 4000)
    public static void nativePinsComputerPanelAndBoardSurviveColdRestartFiveChunkCyclesAndReplacement(GameTestHelper h) throws IOException {
        Runner.run(h);
    }

    private static final class Runner {
        private static final BlockPos SOURCE = new BlockPos(7168, 64, 7168), PINS = SOURCE.east(4), COMPUTER = PINS.west(), PANEL = PINS.south(12);
        private static final Path SNAPSHOT = Path.of("pinout-before-restart.nbt");

        static void run(GameTestHelper h) throws IOException {
            var level = h.getLevel();
            boolean verify = System.getProperty("wwpg.test.restartPhase", "SETUP").equals("VERIFY");
            var expected = verify ? NbtIo.readCompressed(SNAPSHOT, NbtAccounter.unlimitedHeap()) : null;
            var unloads = new AtomicInteger();
            Consumer<ChunkEvent.Unload> listener = event -> {
                if (event.getLevel() == level && event.getChunk().getPos().equals(new ChunkPos(PINS))) unloads.incrementAndGet();
            };
            NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, listener);
            force(level, true);
            if (!verify) {
                put(level, SOURCE, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
                put(level, PINS, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("pinout:pinout")).defaultBlockState());
                put(level, COMPUTER, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("computercraft:computer_normal")).defaultBlockState());
                put(level, PANEL, CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
                for (int pin = 1; pin <= 8; pin++) put(level, load(pin), pin == 3 ? ModdedBlocks.CIRCUIT_BOARD.getDefaultState()
                        : pin % 2 == 0 ? ModdedBlocks.CREATIVE_RESISTOR.getDefaultState() : CEEBlocks.CREATIVE_RESISTOR.getDefaultState());
            } else {
                pins(h, level.getBlockEntity(PINS).saveWithoutMetadata(level.registryAccess()), 85, "Cold native state before any solve or Lua write");
                settings(h, "Cold native player settings");
                h.assertTrue(level.getBlockEntity(COMPUTER).saveWithoutMetadata(level.registryAccess()).getInt("ComputerId") == expected.getInt("ComputerId"),
                        "Cold restart replaced the computer's native identity");
            }
            var computer = new dan200.computercraft.shared.computer.core.ServerComputer[1];
            var mount = new dan200.computercraft.api.filesystem.WritableMount[1];
            var ids = new HashSet<WorldNetworks.PartId>();
            var sequence = h.startSequence().thenIdle(10).thenExecute(() -> {
                if (!verify) {
                    ShowroomTools.scroll(h, SOURCE, 12_000);
                    ((CircuitBoardBlockEntity) level.getBlockEntity(load(3))).setSchematic(BoardWorkflowGameTests.viaLabelBoard());
                    for (int pin = 1; pin <= 8; pin++) {
                        // setValue() changes only PG's live wire, not the saved
                        // player setting. Use the native scroll/configuration.
                        if (pin % 2 == 0) ShowroomTools.resistance(h, load(pin), 1000);
                        else if (pin != 3) ((com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorBlockEntity)
                                level.getBlockEntity(load(pin))).setResistance(1000);
                        WiringGameTests.connect(h, SOURCE, 0, load(pin), 1, pin % 2 != 0);
                    }
                    var panel = (ElectricalPanelBlockEntity) level.getBlockEntity(PANEL);
                    var type = CEEPanelAttachmentTypes.VOLTMETER.get();
                    panel.getAttachments()[ElectricalPanelSlot.THIRD_CENTERED.ordinal()] = type.createNew(PANEL,
                            type.mode.getNodesFor(PANEL, ElectricalPanelSlot.THIRD_CENTERED), level,
                            ElectricalPanelSlot.THIRD_CENTERED, Direction.NORTH, level.registryAccess());
                    panel.attachmentUpdate();
                    var meter = gauge(level);
                    WiringGameTests.connect(h, load(5), 0, PANEL, meter.nodes[0].id(), true);
                    WiringGameTests.connect(h, load(5), 1, PANEL, meter.nodes[1].id(), false);
                    connectPins(h);
                }
                boot(h, computer, mount, !verify);
            }).thenWaitUntil(() -> complete(h, mount[0], "lifecycle.json"))
                    .thenExecute(() -> {
                electrical(h, mount[0]);
                ids.addAll(ids(level));
                h.assertTrue(ids.size() == 5, "Expected one common and four switched native PG wire identities");
                if (verify) h.assertTrue(identities(ids).equals(expected.getString("WireIds")), "Cold restart changed saved Pinout wire identities");
            });
            for (int cycle = 1; cycle <= 5; cycle++) {
                final int index = cycle;
                sequence.thenExecute(() -> {
                    org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("PINOUT_UNLOAD_REQUEST: cycle={}, computer={}, on={}", index, computer[0].getID(), computer[0].isOn());
                    computer[0].shutdown();
                    delete(mount[0], "lifecycle.json");
                }).thenIdle(5).thenExecute(() -> {
                    org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("PINOUT_COMPUTER_SAVE: cycle={}, state={}, nbt={}", index,
                            level.getBlockState(COMPUTER), level.getBlockEntity(COMPUTER).saveWithFullMetadata(level.registryAccess()));
                    level.getDataStorage().save(); level.getChunkSource().save(true); force(level, false);
                }).thenIdle(330).thenWaitUntil(() -> {
                    level.getChunkSource().tick(() -> true, true);
                    var chunk = new ChunkPos(PINS);
                    h.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null && unloads.get() >= index,
                            "Waiting for actual Pinout/computer unload " + index);
                }).thenExecute(() -> {
                    org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("PINOUT_ACTUAL_UNLOAD: cycle={}, unloads={}", index, unloads.get());
                    var disk = disk(level, PINS);
                    org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("PINOUT_COMPUTER_DISK: cycle={}, nbt={}", index, disk(level, COMPUTER));
                    pins(h, disk, 85, "Actual serialized Pinout state " + index);
                    for (int x = SOURCE.getX() >> 4; x <= (SOURCE.getX() >> 4) + 1; x++)
                        for (int z = SOURCE.getZ() >> 4; z <= (SOURCE.getZ() >> 4) + 1; z++) DelayedEntityLoads.hold(level, new ChunkPos(x, z), 60);
                    force(level, true);
                    pins(h, level.getBlockEntity(PINS).saveWithoutMetadata(level.registryAccess()), 85, "Reload before solving " + index);
                    settings(h, "Reloaded player settings " + index);
                }).thenIdle(20).thenExecute(() -> {
                    h.assertTrue(!level.areEntitiesLoaded(new ChunkPos(PINS).toLong()), "Delayed Pinout wire entities were not exercised");
                    h.assertTrue(ids.stream().allMatch(id -> GlobalElectricNetworks.getWorldNetworks(level).getPart(id) != null),
                            "Native Pinout wire identity expired while entities were delayed");
                }).thenWaitUntil(() -> h.assertTrue(level.areEntitiesLoaded(new ChunkPos(PINS).toLong()), "Waiting for Pinout wire readiness"))
                        .thenExecute(() -> boot(h, computer, mount, false))
                        .thenWaitUntil(() -> complete(h, mount[0], "lifecycle.json"))
                        .thenExecute(() -> {
                    electrical(h, mount[0]);
                    h.assertTrue(ids.equals(ids(level)), "Actual Pinout reload duplicated or changed wire identities");
                    org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("PINOUT_CYCLE: cycle={}, actualUnloads={}, computer={}", index, unloads.get(), computer[0].getID());
                });
            }
            sequence.thenExecute(() -> level.setBlockAndUpdate(PINS, Blocks.AIR.defaultBlockState()))
                    .thenWaitUntil(() -> complete(h, mount[0], "detached.json"))
                    .thenIdle(5).thenExecute(() -> {
                for (int pin = 1; pin <= 8; pin++) near(h, voltage(level, load(pin)), 0, "Removed Pinout left ghost power on pin " + pin);
                put(level, PINS, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("pinout:pinout")).defaultBlockState());
            }).thenWaitUntil(() -> complete(h, mount[0], "attached.json"))
                    .thenIdle(10).thenExecute(() -> {
                pins(h, level.getBlockEntity(PINS).saveWithoutMetadata(level.registryAccess()), 0, "Replacement uses native open defaults");
                connectPins(h);
            }).thenIdle(10).thenExecute(() -> {
                for (int pin = 1; pin <= 8; pin++) near(h, voltage(level, load(pin)), 0, "Open replacement reused an old solved result");
                computer[0].queueEvent("wwpg_replacement_ready");
            }).thenWaitUntil(() -> complete(h, mount[0], "replacement.json"))
                    .thenExecute(() -> {
                electrical(h, mount[0]);
                computer[0].shutdown();
            }).thenIdle(5).thenExecute(() -> {
                try {
                    var tag = new CompoundTag(); tag.putInt("ComputerId", computer[0].getID());
                    tag.putString("WireIds", identities(ids(level)));
                    NbtIo.writeCompressed(tag, SNAPSHOT);
                } catch (IOException error) { throw new IllegalStateException(error); }
                NeoForge.EVENT_BUS.unregister(listener);
                level.getDataStorage().save(); level.getChunkSource().save(true);
                force(level, false); DynamicGameTests.audit(h);
            }).thenSucceed();
        }

        private static void boot(GameTestHelper h, dan200.computercraft.shared.computer.core.ServerComputer[] computer,
                                 dan200.computercraft.api.filesystem.WritableMount[] mount, boolean install) {
            computer[0] = ((dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity) h.getLevel().getBlockEntity(COMPUTER)).createServerComputer();
            mount[0] = computer[0].createRootMount();
            for (var path : new String[]{"lifecycle.json", "detached.json", "attached.json", "replacement.json", "lifecycle-error.json"}) delete(mount[0], path);
            if (install) {
                try (var source = PinoutLifecycleGameTests.class.getResourceAsStream("/data/wwpg_pinout/programs/lifecycle.lua")) {
                    if (source == null) throw new IOException("Missing native lifecycle Lua");
                    try (var channel = mount[0].openFile("startup.lua", Set.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                        var bytes = ByteBuffer.wrap(source.readAllBytes()); while (bytes.hasRemaining()) channel.write(bytes);
                    }
                } catch (IOException error) { throw new IllegalStateException(error); }
            }
            computer[0].turnOn();
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("PINOUT_BOOT: install={}, computer={}, on={}, ticking={}, saved={}",
                    install, computer[0].getID() + "/" + computer[0].getInstanceUUID(), computer[0].isOn(), h.getLevel().isPositionEntityTicking(COMPUTER),
                    h.getLevel().getBlockEntity(COMPUTER).saveWithoutMetadata(h.getLevel().registryAccess()));
        }
        private static boolean complete(GameTestHelper h, dan200.computercraft.api.filesystem.WritableMount mount, String path) {
            try {
                h.assertTrue(!mount.exists("lifecycle-error.json"), mount.exists("lifecycle-error.json") ? read(mount, "lifecycle-error.json") : "Lua error");
                h.assertTrue(mount.exists(path), "Waiting for actual Lua report " + path);
                h.assertTrue(JsonParser.parseString(read(mount, path)).getAsJsonObject().get("ok").getAsBoolean(), "Native Lua report failed");
                return true;
            } catch (IOException error) { throw new IllegalStateException(error); }
        }
        private static void electrical(GameTestHelper h, dan200.computercraft.api.filesystem.WritableMount mount) {
            var level = h.getLevel();
            pins(h, level.getBlockEntity(PINS).saveWithoutMetadata(level.registryAccess()), 85, "Lua final native pattern");
            for (int pin = 1; pin <= 8; pin++) near(h, voltage(level, load(pin)), pin % 2 == 1 ? 12 : 0, "Independent mixed load " + pin);
            near(h, gauge(level).value, voltage(level, load(5)), "CEE panel monitors native Pinout output");
            var board = (CircuitBoardBlockEntity) level.getBlockEntity(load(3));
            var component = board.getComponentsStream().filter(p -> p.component == Components.RESISTOR.get()).findFirst().orElseThrow();
            // Native simple components are baked into shared wires. The optional
            // per-component wire list is empty for this resistor.
            var first = board.getBaked().getNode(new org.patryk3211.powergrid.circuits.schematic.CircuitSchematic.Node(component, 0));
            var second = board.getBaked().getNode(new org.patryk3211.powergrid.circuits.schematic.CircuitSchematic.Node(component, 1));
            BoardComponentGameTests.near(h, Math.abs(first.getVoltage()-second.getVoltage())/1000, .012, .00003, "Lua-powered native board trace/via circuit");
            handheldMeters(h);
        }
        private static void handheldMeters(GameTestHelper h) {
            var level = h.getLevel();
            var player = h.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
            player.setPos(PINS.getCenter());
            var stack = org.patryk3211.powergrid.collections.ModdedItems.MULTIMETER.asStack();
            var meter = (org.patryk3211.powergrid.equipment.multimeter.MultimeterItem) stack.getItem();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            for (var endpoint : new BlockWireEndpoint[]{new BlockWireEndpoint(PINS, 0), new BlockWireEndpoint(SOURCE, 0)}) {
                var result = meter.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(endpoint.getExactPosition(level), Direction.UP, endpoint.getPos(), false)));
                h.assertTrue(result.consumesAction(), "Handheld meter rejected mixed Pinout/CEE terminal");
            }
            meter.inventoryTick(stack, level, player, 0, true);
            near(h, Math.abs(meter.getMeasurement(level, stack)), 12, "Handheld voltage across Pinout/CEE terminals");
            var point = new com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint(
                    new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(5, PINS),
                    new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(0, load(5)), .5f);
            // Respect the native handheld reach when moving from voltage probes
            // to the wire midpoint; this station's wires are longer than reach.
            player.setPos(point.node1().getPosition(level).lerp(point.node2().getPosition(level), .5));
            com.george_vi.electroenergetics.CEEWireInteractionBehaviours.CLAMP_METER.get().interactWire(point, level, player, stack);
            h.assertTrue(meter.getMode(stack) == 1, "Native current selection did not attach within handheld reach");
            meter.inventoryTick(stack, level, player, 0, true);
            BoardComponentGameTests.near(h, Math.abs(meter.getMeasurement(level, stack)), .012, .00005, "Handheld current on Pinout's CEE spool wire");
            var clamp = com.george_vi.electroenergetics.CEEItems.CLAMP_METER.asStack();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, clamp);
            var wire = GlobalElectricNetworks.getWorldNetworks(level).findConnectedWires(new BlockWireEndpoint(PINS, 0)).getFirst().owner;
            player.setPos(wire.position());
            h.assertTrue(wire != null && wire.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction(),
                    "CEE clamp rejected the native Pinout common cord");
            clamp.getItem().onUseTick(level, player, clamp, 9000);
            var measurement = clamp.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            BoardComponentGameTests.near(h, Math.abs(measurement.getFloat(org.cha0scollective.wwpg.equipment.HandheldMeters.CURRENT)),
                    .048, .0001, "CEE clamp on PG common cord, four Lua-enabled outputs");
            player.releaseUsingItem();
        }
        private static void connectPins(GameTestHelper h) {
            WiringGameTests.connect(h, SOURCE, 1, PINS, 0, true);
            for (int pin = 1; pin <= 8; pin++) WiringGameTests.connect(h, PINS, pin, load(pin), 0, pin % 2 == 0);
        }
        private static GaugePanelAttachment gauge(ServerLevel level) { return (GaugePanelAttachment) ((ElectricalPanelBlockEntity) level.getBlockEntity(PANEL)).getAttachments()[ElectricalPanelSlot.THIRD_CENTERED.ordinal()]; }
        private static void settings(GameTestHelper h, String label) {
            var source = (com.simibubi.create.foundation.blockEntity.SmartBlockEntity) h.getLevel().getBlockEntity(SOURCE);
            h.assertTrue(source.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE).getValue() == 12_000,
                    label + ": source millivolts changed before solving");
            for (int pin = 2; pin <= 8; pin += 2)
                BoardComponentGameTests.near(h, ((ResistorBlockEntity) h.getLevel().getBlockEntity(load(pin))).getValue(), 1000, .001,
                        label + ": PG resistance on pin " + pin);
        }
        private static BlockPos load(int pin) { return PINS.east(4).south(pin * 2); }
        private static double voltage(ServerLevel level, BlockPos pos) { return new BlockWireEndpoint(pos, 0).getNode(level).getVoltage() - new BlockWireEndpoint(pos, 1).getNode(level).getVoltage(); }
        private static void near(GameTestHelper h, double actual, double expected, String label) { BoardComponentGameTests.near(h, actual, expected, .03, label); }
        private static void put(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) { level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(pos, state); }
        private static void force(ServerLevel level, boolean force) { for (int x = SOURCE.getX() >> 4; x <= (SOURCE.getX() >> 4) + 1; x++) for (int z = SOURCE.getZ() >> 4; z <= (SOURCE.getZ() >> 4) + 1; z++) { level.setChunkForced(x, z, force); if (force) level.getChunk(x, z); } }
        private static String identities(Set<WorldNetworks.PartId> ids) { return ids.stream().map(Object::toString).sorted().toList().toString(); }
        private static Set<WorldNetworks.PartId> ids(ServerLevel level) { var ids = new HashSet<WorldNetworks.PartId>(); var world = GlobalElectricNetworks.getWorldNetworks(level); for (int pin = 0; pin <= 8; pin++) { var parts = world.findConnectedWires(new BlockWireEndpoint(PINS, pin)); if (parts != null) for (var part : parts) ids.add(part.persistentOwnerId); } return ids; }
        private static void pins(GameTestHelper h, CompoundTag tag, int byteValue, String label) { for (int pin = 1; pin <= 8; pin++) h.assertTrue(tag.getBoolean("Pin" + pin) == ((byteValue & (1 << (pin - 1))) != 0), label + ": " + pin); }
        private static CompoundTag disk(ServerLevel level, BlockPos pos) { var tag = level.getChunkSource().chunkMap.read(new ChunkPos(pos)).join().orElseThrow(); for (var entry : tag.getList("block_entities", 10)) { var be = (CompoundTag) entry; if (be.getInt("x") == pos.getX() && be.getInt("y") == pos.getY() && be.getInt("z") == pos.getZ()) return be; } throw new IllegalStateException("Missing serialized Pinout at " + pos); }
        private static void delete(dan200.computercraft.api.filesystem.WritableMount mount, String path) { try { if (mount.exists(path)) mount.delete(path); } catch (IOException error) { throw new IllegalStateException(error); } }
        private static String read(dan200.computercraft.api.filesystem.WritableMount mount, String path) throws IOException { try (var channel = mount.openFile(path, Set.of(StandardOpenOption.READ))) { var bytes = ByteBuffer.allocate((int) channel.size()); while (bytes.hasRemaining() && channel.read(bytes) >= 0) { } return new String(bytes.array(), StandardCharsets.UTF_8); } }
    }
}
