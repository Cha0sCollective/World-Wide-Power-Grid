package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.ElementState;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Run this namespace by itself: /tick freeze deliberately pauses the entire server. */
@GameTestHolder("wwpg_api_pause")
@PrefixGameTestTemplate(false)
public final class ExtensionApiPauseGameTests {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath("wwpg_api_upstream", "panel_storage_indicator");

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void freezeAndOneTickStepPreserveApiAndCeeCommittedHistory(GameTestHelper helper) {
        var sourceRelative = new BlockPos(1, 2, 1);
        var capacitorRelative = new BlockPos(4, 2, 1);
        var panelRelative = new BlockPos(7, 2, 1);
        var capacitorId = ResourceLocation.fromNamespaceAndPath("wwpg_api_example", "capacitor");
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(capacitorId), "Independent API capacitor add-on is required");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(PANEL), "Independent CEE adapter add-on is required");
        helper.assertTrue(!helper.getLevel().getServer().tickRateManager().isFrozen(), "Pause fixture must start on an unfrozen server");
        for (var relative : new BlockPos[]{sourceRelative, capacitorRelative, panelRelative})
            helper.setBlock(relative.below(), Blocks.STONE);
        helper.setBlock(sourceRelative, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        helper.setBlock(capacitorRelative, BuiltInRegistries.BLOCK.get(capacitorId));
        helper.setBlock(panelRelative, CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        var source = helper.absolutePos(sourceRelative);
        var capacitor = helper.absolutePos(capacitorRelative);
        var panel = helper.absolutePos(panelRelative);
        helper.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) helper.getLevel().getBlockEntity(source)).setValue(20);
            var attachment = installPanel(helper, panel);
            WiringGameTests.connect(helper, source, 0, capacitor, 0, true);
            WiringGameTests.connect(helper, source, 1, capacitor, 1, false);
            WiringGameTests.connect(helper, source, 0, panel, attachment.nodes[0].id(), false);
            WiringGameTests.connect(helper, source, 1, panel, attachment.nodes[1].id(), true);
            new PauseCheck(helper, source, capacitor, attachment).start();
        });
    }

    private static PanelAttachment installPanel(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        var slot = ElectricalPanelSlot.THIRD_LEFT;
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(PANEL));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var point = Vec3.atLowerCornerOf(pos).add(slot.center.x, slot.center.y, .1);
        var installed = level.getBlockState(pos).useItemOn(stack, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(point, Direction.NORTH, pos, false));
        helper.assertTrue(installed == ItemInteractionResult.SUCCESS && stack.isEmpty(), "Native CEE panel item installation failed");
        var attachment = ((ElectricalPanelBlockEntity) level.getBlockEntity(pos)).getAttachments()[slot.ordinal()];
        helper.assertTrue(attachment != null && attachment.type == CEERegistries.PANEL_ATTACHMENT_TYPE.get(PANEL),
                "Native CEE adapter attachment identity was not preserved");
        return attachment;
    }

    private static final class PauseCheck {
        private static final Pattern COUNTERS = Pattern.compile(", prepared=(\\d+), committed=(\\d+), PG substeps=(\\d+)");
        private final GameTestHelper helper;
        private final BlockPos capacitor;
        private final BlockPos source;
        private final PanelAttachment attachment;
        private final Consumer<ServerTickEvent.Post> ticking = this::tick;
        private final Consumer<ServerStoppingEvent> stopping;
        private int frames, stage, stageFrames;
        private boolean closed;
        private long prepared, committed, substeps, frozenGameTime;
        private long frozenCallbacks, steppedCommits;
        private CompoundTag frozenApi, frozenCee, steppedApi, steppedCee;
        private ApiMeterFixture meters;

        PauseCheck(GameTestHelper helper, BlockPos source, BlockPos capacitor, PanelAttachment attachment) {
            this.helper = helper;
            this.capacitor = capacitor;
            this.source = source;
            this.attachment = attachment;
            stopping = event -> {
                if (event.getServer() == helper.getLevel().getServer()) cleanup();
            };
        }

        void start() {
            NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, ticking);
            NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, stopping);
        }

        private void tick(ServerTickEvent.Post event) {
            if (closed || event.getServer() != helper.getLevel().getServer()) return;
            if (helper.testInfo.hasFailed()) { cleanup(); return; }
            try {
                if (++frames > 150) throw new IllegalStateException("Freeze/step fixture exceeded its real server-frame limit");
                switch (stage) {
                    case 0 -> warmup();
                    case 1 -> captureFrozenBoundary();
                    case 2 -> waitFrozenAndRequestOneStep();
                    case 3 -> verifySteppedSolve();
                    case 4 -> captureSteppedCommit();
                    case 5 -> verifyPausedAfterStep();
                    case 6 -> verifyResumed();
                    default -> throw new IllegalStateException("Unknown pause fixture stage");
                }
            } catch (RuntimeException | AssertionError failure) {
                cleanup();
                // This listener is outside GameTestTicker's exception boundary.
                helper.testInfo.fail(failure);
            }
        }

        private void warmup() {
            if (frames < 20) return;
            var reading = ElectricalMeasurements.read(helper.getLevel(), capacitor);
            helper.assertTrue(reading.status() == MeasurementStatus.VALID, "API capacitor did not reach a healthy state before freeze");
            var api = apiState();
            helper.assertTrue(api.getDouble("StoredVoltage") > 1 && api.getDouble("StoredVoltage") < 19,
                    "Freeze fixture requires a capacitor that is still charging");
            frozenCee = ceeState();
            helper.assertTrue(frozenCee.getLong("ApiAcceptedSubsteps") > 0, "CEE custom adapter has no accepted history before freeze");
            meters = new ApiMeterFixture(helper, source, 0, capacitor, 0, 1, 1);
            meters.assertAvailable("Before freeze");
            var counters = counters(); prepared = counters[0]; committed = counters[1]; substeps = counters[2];
            command("tick freeze");
            helper.assertTrue(helper.getLevel().getServer().tickRateManager().isFrozen(), "Actual /tick freeze command failed");
            stage = 1;
        }

        private void captureFrozenBoundary() {
            // ServerTickEvent.Post precedes CEE's server-tail gameplay commit.
            // Let the already solved frame commit before recording API NBT.
            frozenApi = apiState();
            frozenGameTime = helper.getLevel().getGameTime();
            var counters = counters();
            helper.assertTrue(counters[0] == prepared && counters[2] == substeps,
                    "Freeze started an extra CEE preparation or PG electrical frame");
            helper.assertTrue(counters[1] == committed + 1, "The final pre-freeze frame did not commit exactly once");
            committed = counters[1];
            sameCee(frozenCee, ceeState(), "First frozen frame");
            stage = 2; stageFrames = 0;
        }

        private void waitFrozenAndRequestOneStep() {
            assertFrozen(frozenApi, frozenCee, prepared, committed, substeps, frozenGameTime, "Frozen server");
            if (++stageFrames < 10) return;
            command("tick step 1");
            helper.assertTrue(helper.getLevel().getServer().tickRateManager().isSteppingForward(), "Actual /tick step 1 command was not accepted");
            stage = 3;
        }

        private void verifySteppedSolve() {
            int steps = ModdedConfigs.server().electricity.solver.multiTicks.get();
            var counters = counters();
            helper.assertTrue(counters[0] == prepared + 1 && counters[2] == substeps + steps,
                    "One requested step did not prepare and solve exactly one shared PG frame");
            steppedCee = ceeState();
            helper.assertTrue(steppedCee.getLong("ApiAcceptedSubsteps") - frozenCee.getLong("ApiAcceptedSubsteps") == steps,
                    "One requested step advanced custom CEE history a different number of times than PG substeps");
            helper.assertTrue(helper.getLevel().getServer().tickRateManager().isFrozen(), "One requested step unexpectedly unfroze the server");
            stage = 4;
        }

        private void captureSteppedCommit() {
            steppedApi = apiState();
            steppedCommits = steppedApi.getLong("CommitCount") - frozenApi.getLong("CommitCount");
            helper.assertTrue(steppedCommits == 1,
                    "One requested step did not deliver exactly one API gameplay commit");
            helper.assertTrue(steppedApi.getDouble("StoredVoltage") > frozenApi.getDouble("StoredVoltage"),
                    "One requested step did not advance the charging API capacitor");
            var reading = ElectricalMeasurements.read(helper.getLevel(), capacitor);
            helper.assertTrue(reading.status() == MeasurementStatus.NOT_READY && reading.state("storage").isEmpty(),
                    "Paused API query exposed a stale numerical frame");
            prepared++; committed++; substeps += ModdedConfigs.server().electricity.solver.multiTicks.get();
            frozenGameTime++;
            assertFrozen(steppedApi, steppedCee, prepared, committed, substeps, frozenGameTime, "After one tick step");
            stage = 5; stageFrames = 0;
        }

        private void verifyPausedAfterStep() {
            assertFrozen(steppedApi, steppedCee, prepared, committed, substeps, frozenGameTime, "Paused after one tick step");
            if (++stageFrames < 10) return;
            command("tick unfreeze");
            stage = 6; stageFrames = 0;
        }

        private void verifyResumed() {
            if (++stageFrames < 3) return;
            helper.assertTrue(apiState().getLong("CommitCount") > steppedApi.getLong("CommitCount"), "Unfreezing did not resume API commits");
            helper.assertTrue(ceeState().getLong("ApiAcceptedSubsteps") > steppedCee.getLong("ApiAcceptedSubsteps"),
                    "Unfreezing did not resume custom CEE accepted history");
            var reading = ElectricalMeasurements.read(helper.getLevel(), capacitor);
            helper.assertTrue(reading.status() == MeasurementStatus.VALID, "Unfreezing did not restore public API readings");
            var history = (ElementState.Capacitor) reading.state("storage").orElseThrow();
            helper.assertTrue(history.voltageVolts() == apiState().getDouble("StoredVoltage"),
                    "Resumed API result and owning add-on NBT disagree about committed storage");
            meters.assertAvailable("After unfreeze");
            DynamicGameTests.audit(helper);
            com.mojang.logging.LogUtils.getLogger().info(
                    "API_PAUSE_PASSED: frozenCallbacks={}; steppedCommits={}; resumed=true; pausedMeasurementsUnavailable=true",
                    frozenCallbacks, steppedCommits);
            cleanup(); helper.succeed();
        }

        private void assertFrozen(CompoundTag api, CompoundTag cee, long expectedPrepared, long expectedCommitted,
                                  long expectedSubsteps, long expectedGameTime, String context) {
            sameApi(api, apiState(), context); sameCee(cee, ceeState(), context);
            var actual = counters();
            frozenCallbacks += Math.max(0, actual[0] - expectedPrepared);
            helper.assertTrue(actual[0] == expectedPrepared && actual[1] == expectedCommitted && actual[2] == expectedSubsteps,
                    context + " changed preparation, gameplay commits, or electrical substeps");
            helper.assertTrue(helper.getLevel().getGameTime() == expectedGameTime, context + " unexpectedly advanced game time");
            meters.assertUnavailable(context);
        }

        private CompoundTag apiState() {
            var entity = helper.getLevel().getBlockEntity(capacitor);
            helper.assertTrue(entity != null, "API capacitor block entity vanished");
            return entity.saveWithoutMetadata(helper.getLevel().registryAccess());
        }
        private CompoundTag ceeState() {
            var tag = new CompoundTag(); attachment.write(tag, false, helper.getLevel().registryAccess()); return tag;
        }
        private void sameApi(CompoundTag before, CompoundTag after, String context) {
            for (var key : new String[]{"StoredVoltage", "StoredCurrent", "CommitCount", "WWPGExampleStateVersion"})
                helper.assertTrue(Objects.equals(before.get(key), after.get(key)), context + " changed API " + key);
        }
        private void sameCee(CompoundTag before, CompoundTag after, String context) {
            for (var key : new String[]{"ApiStoredVoltage", "ApiAcceptedSubsteps", "ApiStateVersion"})
                helper.assertTrue(Objects.equals(before.get(key), after.get(key)), context + " changed CEE " + key);
        }
        private long[] counters() {
            var matcher = COUNTERS.matcher(Bridges.get(helper.getLevel()).status());
            helper.assertTrue(matcher.find(), "Electrical lifecycle diagnostic counters are unavailable");
            return new long[]{Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)), Long.parseLong(matcher.group(3))};
        }
        private void command(String text) {
            var server = helper.getLevel().getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), text);
        }
        private void cleanup() {
            if (closed) return;
            closed = true;
            var manager = helper.getLevel().getServer().tickRateManager();
            manager.stopStepping(); manager.setFrozen(false);
            NeoForge.EVENT_BUS.unregister(ticking); NeoForge.EVENT_BUS.unregister(stopping);
        }
    }
}
