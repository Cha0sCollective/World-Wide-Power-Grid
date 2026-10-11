package org.cha0scollective.wwpg.example;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.*;
import org.cha0scollective.wwpg.api.neoforge.ElectricalDevices;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.api.neoforge.ElectricalProviders;

import java.util.*;

/** Fault and lifecycle fixtures compiled exclusively against NeoForge and the public API. */
@GameTestHolder("wwpg_api_faults")
@PrefixGameTestTemplate(false)
public final class FaultFixtures {
    private static final String PROVIDER = ExampleMod.ID + ":fault_probe";
    private static final Map<ServerLevel, Map<BlockPos, State>> STATES = new WeakHashMap<>();
    private static final List<Terminal> TERMINALS = List.of(
            new Terminal("positive", 0, new LocalPoint(.25, .5, 0), .12),
            new Terminal("negative", 1, new LocalPoint(.75, .5, 0), .12));

    private FaultFixtures() { }

    /** The constructor installs this hook only in packaged acceptance runs. */
    static void register(IEventBus bus) { bus.addListener(FaultFixtures::capabilities); }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        ElectricalProviders.registerBlock(event, PROVIDER, (level, pos, block, entity, context) -> {
            State state = level instanceof ServerLevel server ? STATES.getOrDefault(server, Map.of()).get(pos) : null;
            if (state != null) ++state.capabilityLookups;
            if (state != null && state.mode == Mode.THROW_CAPABILITY)
                throw new NoClassDefFoundError("Deliberate missing provider dependency " + "x".repeat(4096));
            // Every lookup returns a new wrapper. Unarmed vanilla gold blocks
            // expose only an open, unpowered description in this test profile.
            return new Probe(state);
        }, Blocks.GOLD_BLOCK);
    }

    private enum Mode { DC, RC, MALFORMED, THROW_PREPARE, THROW_COMMIT, THROW_CAPABILITY, THROW_TERMINALS,
        MALFORMED_AND_THROW_INVALID_COMMIT }

    private static final class State {
        Mode mode = Mode.DC;
        boolean groundPositive;
        long prepares, commits, capabilityLookups, commitFailures;
        double savedVoltage;
        DeviceResults latest;
    }

    private static final class Probe implements ElectricalDevice {
        private final State state;
        Probe(State state) { this.state = state; }
        @Override public String providerId() { return PROVIDER; }
        @Override public List<Terminal> terminals() {
            if (state != null && state.mode == Mode.THROW_TERMINALS)
                throw new NoClassDefFoundError("Deliberate missing terminal dependency");
            return TERMINALS;
        }
        @Override public CircuitDescription prepare(TickContext context) {
            if (state == null) return new CircuitDescription(TERMINALS, List.of(), List.of(), Set.of());
            ++state.prepares;
            if (state.mode == Mode.THROW_PREPARE) throw new IllegalStateException("Deliberate prepare failure");
            if (state.mode == Mode.MALFORMED || state.mode == Mode.MALFORMED_AND_THROW_INVALID_COMMIT) return new CircuitDescription(TERMINALS, List.of(),
                    List.of(new Element("invalid", "positive", "missing", new ElectricalModel.Resistor(200))), Set.of());
            var elements = new ArrayList<Element>();
            elements.add(new Element("supply", "positive", "negative", new ElectricalModel.VoltageSource(20)));
            var nodes = new ArrayList<InternalNode>();
            if (state.mode == Mode.RC) {
                nodes.add(new InternalNode("stored"));
                elements.add(new Element("ballast", "positive", "stored", new ElectricalModel.Resistor(100)));
                // Deliberately offer zero on every fresh wrapper. A retained
                // element must ignore this creation-only input after tick one.
                elements.add(new Element("storage", "stored", "negative", new ElectricalModel.Capacitor(.01, 0)));
            } else elements.add(new Element("load", "positive", "negative", new ElectricalModel.Resistor(200)));
            return new CircuitDescription(TERMINALS, nodes, elements,
                    Set.of(state.groundPositive ? "positive" : "negative"));
        }
        @Override public void commit(DeviceResults results) {
            if (state == null) return;
            if (state.mode == Mode.THROW_COMMIT && results.status() == MeasurementStatus.VALID
                    || state.mode == Mode.MALFORMED_AND_THROW_INVALID_COMMIT && results.status() != MeasurementStatus.VALID) {
                ++state.commitFailures;
                throw new IllegalStateException("Deliberate commit failure");
            }
            ++state.commits;
            state.latest = results;
            if (results.status() == MeasurementStatus.VALID)
                results.state("storage").ifPresent(history -> state.savedVoltage = ((ElementState.Capacitor) history).voltageVolts());
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void malformedAndThrowingPreparationIsolateWithoutStaleResults(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var state = arm(h, pos, Mode.DC);
        h.runAtTickTime(10, () -> { healthy(h, pos, .1); state.mode = Mode.MALFORMED; });
        h.runAtTickTime(13, () -> { isolated(h, pos); state.mode = Mode.THROW_PREPARE; });
        h.runAtTickTime(17, () -> {
            isolated(h, pos);
            h.assertTrue(state.latest != null && state.latest.status() == MeasurementStatus.ISOLATED,
                    "Prepare failures never delivered invalid results to gameplay");
            state.mode = Mode.DC;
            h.assertTrue(ElectricalDevices.changed(h.getLevel(), pos), "Explicit repair notification was rejected");
        });
        h.runAtTickTime(24, () -> { healthy(h, pos, .1); finish(h, pos); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void capabilityAndTerminalLinkageFailuresIsolateWireLookupAndRecover(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var state = arm(h, pos, Mode.DC);
        h.runAtTickTime(10, () -> { healthy(h, pos, .1); state.mode = Mode.THROW_CAPABILITY; rejectedWireLookup(h, pos); });
        h.runAtTickTime(14, () -> {
            isolated(h, pos);
            String detail = ElectricalMeasurements.read(h.getLevel(), pos).detail();
            h.assertTrue(detail.contains(PROVIDER) && detail.contains(pos.toShortString()) && detail.length() <= 2048,
                    "Capability diagnostic lacks bounded provider/position identity");
            state.mode = Mode.THROW_TERMINALS;
            rejectedWireLookup(h, pos);
        });
        h.runAtTickTime(18, () -> {
            isolated(h, pos);
            state.mode = Mode.DC;
            h.assertTrue(ElectricalDevices.changed(h.getLevel(), pos), "Repaired missing dependency could not be nominated");
        });
        h.runAtTickTime(25, () -> { healthy(h, pos, .1); finish(h, pos); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void throwingCommitRequiresExplicitChangedToRecover(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var state = arm(h, pos, Mode.DC);
        h.runAtTickTime(10, () -> { healthy(h, pos, .1); state.mode = Mode.THROW_COMMIT; });
        h.runAtTickTime(14, () -> {
            isolated(h, pos);
            h.assertTrue(state.commitFailures == 1, "Faulting commit was repeatedly retried before repair");
            state.mode = Mode.DC;
        });
        h.runAtTickTime(19, () -> {
            isolated(h, pos);
            h.assertTrue(state.commitFailures == 1, "Commit quarantine was bypassed");
            h.assertTrue(ElectricalDevices.changed(h.getLevel(), pos), "Explicit commit repair was rejected");
        });
        h.runAtTickTime(26, () -> { healthy(h, pos, .1); finish(h, pos); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void throwingUnavailableCommitIsQuarantinedUntilExplicitRepair(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var state = arm(h, pos, Mode.DC);
        var callbacks = new long[2];
        h.runAtTickTime(10, () -> { healthy(h, pos, .1); state.mode = Mode.MALFORMED_AND_THROW_INVALID_COMMIT; });
        h.runAtTickTime(14, () -> {
            isolated(h, pos);
            h.assertTrue(state.commitFailures == 1, "Unavailable-result commit was repeatedly retried before repair");
            callbacks[0] = state.prepares; callbacks[1] = state.commits;
            // Correcting the inputs alone must not bypass a gameplay callback's
            // quarantine, regardless of whether its last snapshot was valid.
            state.mode = Mode.DC;
        });
        h.runAtTickTime(19, () -> {
            isolated(h, pos);
            h.assertTrue(state.commitFailures == 1 && state.prepares == callbacks[0] && state.commits == callbacks[1],
                    "Invalid-result commit quarantine allowed another prepare or gameplay callback");
            h.assertTrue(ElectricalDevices.changed(h.getLevel(), pos), "Explicit unavailable-commit repair was rejected");
        });
        h.runAtTickTime(26, () -> { healthy(h, pos, .1); finish(h, pos); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void freshCapabilityWrappersRetainStorageAndReadonlyQueriesHaveNoCallbacks(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var state = arm(h, pos, Mode.RC);
        final double[] first = new double[1];
        h.runAtTickTime(10, () -> {
            var result = accepted(h, pos);
            first[0] = ((ElementState.Capacitor) result.state("storage").orElseThrow()).voltageVolts();
            h.assertTrue(first[0] > 1 && first[0] < 15, "RC fixture did not begin a measurable charge window");
        });
        h.runAtTickTime(30, () -> {
            var result = accepted(h, pos);
            double voltage = ((ElementState.Capacitor) result.state("storage").orElseThrow()).voltageVolts();
            h.assertTrue(voltage > first[0] + 4 && voltage < 20,
                    "Fresh capability wrappers reset retained storage history: " + first[0] + " -> " + voltage);
            h.assertTrue(state.capabilityLookups > state.commits, "Fixture did not exercise fresh wrapper discovery");
            long prepares = state.prepares, commits = state.commits, capabilities = state.capabilityLookups;
            double retained = state.savedVoltage;
            for (int i = 0; i < 100; ++i) {
                h.assertTrue(ElectricalMeasurements.read(h.getLevel(), pos) == result, "Read-only device query rebuilt its snapshot");
                h.assertTrue(ElectricalMeasurements.readTerminal(h.getLevel(), pos, 0).status() == MeasurementStatus.VALID,
                        "Existing terminal query lost an accepted frame");
                near(h, ElectricalMeasurements.readVoltageBetween(h.getLevel(), pos, 0, pos, 1)
                        .voltage().orElseThrow().meanVoltageVolts(), 20, .00001, "Differential DC reading");
                var absent = ElectricalMeasurements.readTerminal(h.getLevel(), pos, 99);
                h.assertTrue(absent.status() == MeasurementStatus.NOT_READY && absent.voltage().isEmpty(),
                        "Unknown terminal query created a numeric zero endpoint");
            }
            h.assertTrue(prepares == state.prepares && commits == state.commits && capabilities == state.capabilityLookups
                            && retained == state.savedVoltage,
                    "Read-only queries invoked provider discovery, preparation, commit, or history advancement");
            h.assertTrue(result.nodes().size() == 3 && result.elements().size() == 3, "Read-only queries changed the device's node/element inventory");
            finish(h, pos); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void switchedGroundsKeepExactReferenceAndPhysicalCurrent(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var state = arm(h, pos, Mode.DC);
        h.runAtTickTime(10, () -> {
            healthy(h, pos, .1);
            near(h, accepted(h, pos).node("negative").orElseThrow().lastVoltageVolts(), 0, 1e-10, "Negative exact ground");
            state.groundPositive = true;
        });
        h.runAtTickTime(16, () -> {
            healthy(h, pos, .1);
            var result = accepted(h, pos);
            near(h, result.node("positive").orElseThrow().lastVoltageVolts(), 0, 1e-10, "Moved positive exact ground");
            near(h, result.node("negative").orElseThrow().lastVoltageVolts(), -20, .00001, "Moved reference preserves polarity");
            state.groundPositive = false;
        });
        h.runAtTickTime(23, () -> {
            healthy(h, pos, .1);
            near(h, accepted(h, pos).node("negative").orElseThrow().lastVoltageVolts(), 0, 1e-10, "Restored exact ground");
            finish(h, pos); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void loadedBlockEntityReplacementCutsOldWiresAndNativePgReplacementCanBeRewired(GameTestHelper h) {
        var source = h.absolutePos(new BlockPos(1, 2, 2));
        var load = h.absolutePos(new BlockPos(4, 2, 2));
        arm(h, source, Mode.DC);
        h.getLevel().setBlockAndUpdate(load.below(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(load, block("wwpg_api_example", "load").defaultBlockState());
        h.runAtTickTime(5, () -> wireExample(h, source, load));
        h.runAtTickTime(15, () -> { examplePowered(h, load); emptyHandControls(h, load); replaceEntity(h, load); });
        h.runAtTickTime(21, () -> { unpowered(h, load); wireExample(h, source, load); });
        h.runAtTickTime(28, () -> { examplePowered(h, load); replaceEntity(h, load); });
        h.runAtTickTime(34, () -> { unpowered(h, load); wireExample(h, source, load); });
        h.runAtTickTime(41, () -> {
            examplePowered(h, load);
            var nativeState = floorState(block("powergrid", "creative_resistor").defaultBlockState());
            h.getLevel().setBlockAndUpdate(load, nativeState);
            h.assertTrue(ElectricalMeasurements.read(h.getLevel(), load).status() == MeasurementStatus.NOT_READY,
                    "Native replacement inherited old API device results");
        });
        h.runAtTickTime(48, () -> {
            var reading = ElectricalMeasurements.readVoltageBetween(h.getLevel(), load, 0, load, 1);
            if (reading.status() == MeasurementStatus.VALID)
                near(h, reading.voltage().orElseThrow().lastVoltageVolts(), 0, .00001, "Replacement must not inherit old API wires");
            wire(h, source, new Vec3(.25, .5, 0), load, new Vec3(.5, .3125, 1.75 / 16));
            wire(h, source, new Vec3(.75, .5, 0), load, new Vec3(.5, .3125, 14.25 / 16));
        });
        h.runAtTickTime(57, () -> {
            var reading = ElectricalMeasurements.readVoltageBetween(h.getLevel(), load, 0, load, 1);
            h.assertTrue(reading.status() == MeasurementStatus.VALID, "Native PG replacement lost its native endpoint nodes: " + reading.detail());
            // Pinned PG's default creative resistor is 0.001 ohm. Native CEE
            // creative wires retain 0.00001 ohm/m, so this tiny load has a
            // measurable voltage drop even though the supply is ideal.
            double outbound = new Vec3(.25, .5, 0).add(Vec3.atLowerCornerOf(source))
                    .distanceTo(new Vec3(.5, .3125, 1.75 / 16).add(Vec3.atLowerCornerOf(load)));
            double returning = new Vec3(.75, .5, 0).add(Vec3.atLowerCornerOf(source))
                    .distanceTo(new Vec3(.5, .3125, 14.25 / 16).add(Vec3.atLowerCornerOf(load)));
            double expected = 20 * .001 / (.001 + .00001 * (outbound + returning));
            near(h, reading.voltage().orElseThrow().lastVoltageVolts(), expected, .0001, "Rewired native PG replacement including native wire resistance");
            h.getLevel().setBlockAndUpdate(load, Blocks.AIR.defaultBlockState());
            finish(h, source); h.succeed();
        });
    }

    private static State arm(GameTestHelper h, BlockPos pos, Mode mode) {
        var state = new State(); state.mode = mode;
        STATES.computeIfAbsent(h.getLevel(), ignored -> new HashMap<>()).put(pos.immutable(), state);
        h.getLevel().setBlockAndUpdate(pos, Blocks.GOLD_BLOCK.defaultBlockState());
        h.assertTrue(ElectricalDevices.changed(h.getLevel(), pos), "Loaded plain-block device could not be nominated");
        return state;
    }
    private static void finish(GameTestHelper h, BlockPos pos) {
        h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        STATES.getOrDefault(h.getLevel(), Map.of()).remove(pos);
        ElectricalDevices.changed(h.getLevel(), pos);
    }
    private static DeviceResults accepted(GameTestHelper h, BlockPos pos) {
        var result = ElectricalMeasurements.read(h.getLevel(), pos);
        h.assertTrue(result.status() == MeasurementStatus.VALID, "API probe is " + result.status() + ": " + result.detail());
        return result;
    }
    private static void healthy(GameTestHelper h, BlockPos pos, double current) {
        var result = accepted(h, pos);
        near(h, result.element("load").orElseThrow().meanCurrentAmps(), current, .000001, "Probe load current");
        near(h, result.element("supply").orElseThrow().meanCurrentAmps(), -current, .000001, "Probe source polarity");
    }
    private static void isolated(GameTestHelper h, BlockPos pos) {
        var result = ElectricalMeasurements.read(h.getLevel(), pos);
        h.assertTrue(result.status() == MeasurementStatus.ISOLATED && result.nodes().isEmpty()
                        && result.elements().isEmpty() && result.electricalState().isEmpty(),
                "Fault exposed stale or partial numeric results: " + result.status());
        var terminal = ElectricalMeasurements.readTerminal(h.getLevel(), pos, 0);
        h.assertTrue(terminal.status() != MeasurementStatus.VALID && terminal.voltage().isEmpty(), "Fault exposed a stale terminal voltage");
    }
    private static void examplePowered(GameTestHelper h, BlockPos pos) {
        near(h, accepted(h, pos).element("load_a").orElseThrow().meanCurrentAmps(), .2, .0001, "Independent example powered load");
    }
    private static void unpowered(GameTestHelper h, BlockPos pos) {
        var result = ElectricalMeasurements.read(h.getLevel(), pos);
        if (result.status() == MeasurementStatus.VALID)
            near(h, result.element("load_a").orElseThrow().meanPowerWatts(), 0, .00001, "Replacement retained ghost power");
        else h.assertTrue(result.nodes().isEmpty() && result.elements().isEmpty(), "Unpowered replacement retained stale results");
    }
    private static void replaceEntity(GameTestHelper h, BlockPos pos) {
        var level = h.getLevel(); var state = level.getBlockState(pos);
        var previous = level.getBlockEntity(pos);
        level.removeBlockEntity(pos);
        var replacement = new ExampleMod.Machine(pos, state);
        level.setBlockEntity(replacement);
        h.assertTrue(previous != replacement && level.getBlockEntity(pos) == replacement, "Fixture did not actually replace the loaded block entity");
        ElectricalDevices.changed(level, pos);
        h.assertTrue(ElectricalMeasurements.read(level, pos).status() == MeasurementStatus.NOT_READY,
                "Loaded block entity replacement exposed previous-owner results");
    }
    private static net.minecraft.world.level.block.Block block(String namespace, String path) {
        var id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalStateException("Missing fixture block " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }
    private static BlockState floorState(BlockState state) {
        for (var property : state.getProperties()) {
            if (property instanceof DirectionProperty direction && property.getName().equals("facing")) state = state.setValue(direction, Direction.DOWN);
            if (property instanceof BooleanProperty axis && property.getName().equals("along_first_axis")) state = state.setValue(axis, true);
        }
        return state;
    }
    private static void wireExample(GameTestHelper h, BlockPos source, BlockPos load) {
        wire(h, source, new Vec3(.25, .5, 0), load, new Vec3(.25, .85, 0), true);
        wire(h, source, new Vec3(.75, .5, 0), load, new Vec3(.75, .85, 0), true);
    }
    private static void emptyHandControls(GameTestHelper h, BlockPos target) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Public-api-block-use"));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        var hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.NORTH, target, false);
        for (boolean expected : new boolean[]{false, true}) {
            h.assertTrue(player.gameMode.useItemOn(player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit).consumesAction(),
                    "Native empty-hand example configuration was rejected");
            h.assertTrue(h.getLevel().getBlockState(target).getValue(ExampleMod.ExampleBlock.ENABLED) == expected,
                    "Native empty-hand example configuration did not change its enabled state");
        }
        player.setShiftKeyDown(false);
    }
    private static void rejectedWireLookup(GameTestHelper h, BlockPos target) {
        var player = new Player(h.getLevel(), target, 0, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Unavailable-api-fixture")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return true; }
        };
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("electroenergetics", "creative_wire_spool"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(.25, .5, 0).add(Vec3.atLowerCornerOf(target)), Direction.UP, target, false));
        h.assertTrue(!item.useOn(context).consumesAction(), "Broken terminal declaration remained selectable by native wiring");
    }
    private static void wire(GameTestHelper h, BlockPos source, Vec3 sourcePoint, BlockPos target, Vec3 targetPoint) {
        wire(h, source, sourcePoint, target, targetPoint, false);
    }
    private static void wire(GameTestHelper h, BlockPos source, Vec3 sourcePoint, BlockPos target, Vec3 targetPoint, boolean nativeBlockDispatch) {
        var id = ResourceLocation.fromNamespaceAndPath("electroenergetics", "creative_wire_spool");
        h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "Native CEE creative wire spool is missing");
        Player player = nativeBlockDispatch ? net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Public-api-item-use")) : new Player(h.getLevel(), source, 0, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "Public-api-fixture")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return true; }
        };
        if (player instanceof net.minecraft.server.level.ServerPlayer server) server.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BuiltInRegistries.ITEM.get(id)));
        for (var click : List.of(new BlockHitResult(sourcePoint.add(Vec3.atLowerCornerOf(source)), Direction.UP, source, false),
                new BlockHitResult(targetPoint.add(Vec3.atLowerCornerOf(target)), Direction.UP, target, false))) {
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND, click);
            var result = nativeBlockDispatch ? ((net.minecraft.server.level.ServerPlayer) player).gameMode.useItemOn(
                    (net.minecraft.server.level.ServerPlayer) player, h.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, click)
                    : player.getMainHandItem().getItem().useOn(context);
            h.assertTrue(result.consumesAction(), "Native CEE spool rejected public API terminal");
        }
    }
    private static void near(GameTestHelper h, double value, double expected, double tolerance, String detail) {
        h.assertTrue(Double.isFinite(value) && Math.abs(value - expected) <= tolerance, detail + ": " + value + " expected " + expected);
    }
}
