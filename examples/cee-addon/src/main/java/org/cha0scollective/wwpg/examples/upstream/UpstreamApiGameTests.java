package org.cha0scollective.wwpg.examples.upstream;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.neoforge.ElectricalCapabilities;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedConfigs;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.equipment.multimeter.MultimeterItem;

/** Packaged external add-on tests, runnable on both PG backends. */
@GameTestHolder("wwpg_api_upstream")
@PrefixGameTestTemplate(false)
public final class UpstreamApiGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ceeAdapterCaptureFailureIsolatesExternallyDrivenTerminalsAndRecovers(GameTestHelper helper) {
        var sourceRelative = new BlockPos(1, 2, 1);
        var panelRelative = new BlockPos(4, 2, 1);
        helper.setBlock(sourceRelative.below(), Blocks.STONE);
        helper.setBlock(panelRelative.below(), Blocks.STONE);
        helper.setBlock(sourceRelative, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        helper.setBlock(panelRelative, CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        var source = helper.absolutePos(sourceRelative);
        var panel = helper.absolutePos(panelRelative);
        var attachment = new StorageIndicatorAttachment[1];
        var committed = new CompoundTag[1];
        helper.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) helper.getLevel().getBlockEntity(source)).setValue(70);
            attachment[0] = insert(helper, panel);
            NativeFixtures.wire(helper, source, 0, panel, attachment[0].nodes[0].id(), true);
            NativeFixtures.wire(helper, source, 1, panel, attachment[0].nodes[1].id(), false);
        });
        helper.runAtTickTime(25, () -> {
            var reading = ElectricalMeasurements.readVoltageBetween(helper.getLevel(), panel,
                    attachment[0].nodes[0].id(), panel, attachment[0].nodes[1].id());
            helper.assertTrue(reading.status() == MeasurementStatus.VALID, "Capture-failure fixture did not start with a healthy circuit");
            committed[0] = new CompoundTag();
            attachment[0].write(committed[0], false, helper.getLevel().registryAccess());
            attachment[0].electrical.failDuringCapture = true;
        });
        helper.runAtTickTime(26, () -> assertCaptureIsolation(helper, source, panel, attachment[0], committed[0]));
        helper.runAtTickTime(32, () -> {
            assertCaptureIsolation(helper, source, panel, attachment[0], committed[0]);
            attachment[0].electrical.failDuringCapture = false;
        });
        helper.runAtTickTime(40, () -> {
            var reading = ElectricalMeasurements.readVoltageBetween(helper.getLevel(), panel,
                    attachment[0].nodes[0].id(), panel, attachment[0].nodes[1].id());
            helper.assertTrue(reading.status() == MeasurementStatus.VALID && reading.voltage().isPresent(),
                    "Repaired capture failure did not regain public terminal measurements");
            helper.assertTrue(attachment[0].electrical.acceptedSubsteps > committed[0].getLong("ApiAcceptedSubsteps"),
                    "Repaired capture failure did not resume committed electrical history");
            helper.succeed();
        });
    }

    private static void assertCaptureIsolation(GameTestHelper helper, BlockPos source, BlockPos panel,
                                                StorageIndicatorAttachment attachment, CompoundTag committed) {
        assertIsolatedAndRolledBack(helper, panel, attachment, committed);
        var terminal = ElectricalMeasurements.readTerminal(helper.getLevel(), panel, attachment.nodes[0].id());
        helper.assertTrue(terminal.status() == MeasurementStatus.ISOLATED && terminal.voltage().isEmpty(),
                "A failed CEE capture exposed valid numbers at an externally driven terminal");
        double driven = new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(source, 0).getNode(helper.getLevel()).getVoltage()
                - new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(source, 1).getNode(helper.getLevel()).getVoltage();
        NativeFixtures.near(helper, driven, 70, .07, "Fixture must retain a live native PG supply while its CEE property is isolated");
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ceeAdapterCallbackFailureRollsBackHistoryAndIsolatesReadings(GameTestHelper helper) {
        var sourceRelative = new BlockPos(1, 2, 1);
        var panelRelative = new BlockPos(4, 2, 1);
        var ballastRelative = new BlockPos(1, 2, 4);
        var capacitorRelative = new BlockPos(4, 2, 4);
        var healthySourceRelative = new BlockPos(1, 2, 7);
        var healthyLoadRelative = new BlockPos(4, 2, 7);
        helper.setBlock(sourceRelative.below(), Blocks.STONE);
        helper.setBlock(panelRelative.below(), Blocks.STONE);
        helper.setBlock(sourceRelative, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        helper.setBlock(panelRelative, CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        var source = helper.absolutePos(sourceRelative);
        var panel = helper.absolutePos(panelRelative);
        for (var position : new BlockPos[]{ballastRelative, capacitorRelative, healthySourceRelative, healthyLoadRelative})
            helper.setBlock(position.below(), Blocks.STONE);
        helper.setBlock(ballastRelative, ModdedBlocks.CREATIVE_RESISTOR.get());
        helper.setBlock(capacitorRelative, CEEBlocks.CAPACITOR.get());
        helper.setBlock(healthySourceRelative, CEEBlocks.CREATIVE_BATTERY.get());
        helper.setBlock(healthyLoadRelative, ModdedBlocks.CREATIVE_RESISTOR.get());
        var ballast = helper.absolutePos(ballastRelative);
        var capacitor = helper.absolutePos(capacitorRelative);
        var healthySource = helper.absolutePos(healthySourceRelative);
        var healthyLoad = helper.absolutePos(healthyLoadRelative);
        var capacitorCommittedVoltage = new double[1];
        var meterPlayer = helper.makeMockPlayer(GameType.CREATIVE);
        var healthyMeterStack = ModdedItems.MULTIMETER.asStack();
        var failedMeterStack = ModdedItems.MULTIMETER.asStack();
        var meter = (MultimeterItem) healthyMeterStack.getItem();
        var attachment = new StorageIndicatorAttachment[1];
        var committed = new CompoundTag[1];
        helper.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) helper.getLevel().getBlockEntity(source)).setValue(70);
            attachment[0] = insert(helper, panel);
            NativeFixtures.wire(helper, source, 0, panel, attachment[0].nodes[0].id(), true);
            NativeFixtures.wire(helper, source, 1, panel, attachment[0].nodes[1].id(), false);
            ((ResistorBlockEntity) helper.getLevel().getBlockEntity(ballast)).setValue(100);
            var nativeCapacitor = DevicesSavedData.load(helper.getLevel()).getDevice(capacitor, CapacitorDevice.class);
            nativeCapacitor.capacitance = .1;
            nativeCapacitor.lastVoltage = 0;
            NativeFixtures.wire(helper, source, 0, ballast, 0, true);
            NativeFixtures.wire(helper, ballast, 1, capacitor, 0, false);
            NativeFixtures.wire(helper, capacitor, 1, source, 1, true);
            DevicesSavedData.load(helper.getLevel()).getDevice(healthySource, CreativeBatteryDevice.class).voltage = 20;
            ((ResistorBlockEntity) helper.getLevel().getBlockEntity(healthyLoad)).setValue(100);
            NativeFixtures.wire(helper, healthySource, 1, healthyLoad, 0, false);
            NativeFixtures.wire(helper, healthySource, 0, healthyLoad, 1, true);
        });
        helper.runAtTickTime(25, () -> {
            var reading = ElectricalMeasurements.readVoltageBetween(helper.getLevel(), panel,
                    attachment[0].nodes[0].id(), panel, attachment[0].nodes[1].id());
            helper.assertTrue(reading.status() == MeasurementStatus.VALID, "Healthy native CEE adapter has no valid public reading");
            NativeFixtures.near(helper, attachment[0].electrical.savedVoltage, 70, .07, "Healthy custom storage charge");
            committed[0] = new CompoundTag();
            attachment[0].write(committed[0], false, helper.getLevel().registryAccess());
            // GameTests run after the shared solve but before native CEE's
            // gameplay commit. That accepted frame will still commit at server
            // tick tail; the injected callback fault starts on the next frame.
            // Read its accepted property history rather than the device's
            // previous-tick serialization mirror as the rollback baseline.
            capacitorCommittedVoltage[0] = DevicesSavedData.load(helper.getLevel()).getDevice(capacitor, CapacitorDevice.class)
                    .properties.lastVoltage;
            helper.assertTrue(capacitorCommittedVoltage[0] > 0 && capacitorCommittedVoltage[0] < 60,
                    "Native CEE capacitor must be charging before the later-substep failure");
            attachWireMeter(helper, meterPlayer, healthyMeterStack, new NodeConnectionPoint(
                    new InWorldNode(1, healthySource), new InWorldNode(0, healthyLoad), .5f));
            attachWireMeter(helper, meterPlayer, failedMeterStack, new NodeConnectionPoint(
                    new InWorldNode(1, source), attachment[0].nodes[1], .5f));
            int steps = ModdedConfigs.server().electricity.solver.multiTicks.get();
            helper.assertTrue(steps >= 2, "Failure fixture must exercise rollback of an earlier accepted substep");
            attachment[0].electrical.failAfterSubstep = 1;
        });
        helper.runAtTickTime(26, () -> {
            assertIsolatedAndRolledBack(helper, panel, attachment[0], committed[0]);
            assertFaultHasNoCollateralEffects(helper, source, panel, capacitor, capacitorCommittedVoltage[0],
                    healthySource, healthyLoad, meterPlayer, meter, healthyMeterStack, failedMeterStack);
        });
        helper.runAtTickTime(32, () -> {
            assertIsolatedAndRolledBack(helper, panel, attachment[0], committed[0]);
            assertFaultHasNoCollateralEffects(helper, source, panel, capacitor, capacitorCommittedVoltage[0],
                    healthySource, healthyLoad, meterPlayer, meter, healthyMeterStack, failedMeterStack);
            attachment[0].electrical.failAfterSubstep = -1;
        });
        helper.runAtTickTime(40, () -> {
            var reading = ElectricalMeasurements.readVoltageBetween(helper.getLevel(), panel,
                    attachment[0].nodes[0].id(), panel, attachment[0].nodes[1].id());
            helper.assertTrue(reading.status() == MeasurementStatus.VALID && reading.voltage().isPresent(),
                    "Repaired CEE adapter did not regain valid public readings");
            helper.assertTrue(attachment[0].electrical.acceptedSubsteps > committed[0].getLong("ApiAcceptedSubsteps"),
                    "Repaired CEE adapter did not resume accepted state advancement");
            helper.assertTrue(DevicesSavedData.load(helper.getLevel()).getDevice(capacitor, CapacitorDevice.class).lastVoltage
                            > capacitorCommittedVoltage[0], "Repaired circuit did not resume native CEE capacitor charging");
            helper.succeed();
        });
    }

    private static void attachWireMeter(GameTestHelper helper, net.minecraft.world.entity.player.Player player,
                                        ItemStack stack, NodeConnectionPoint point) {
        player.setPos(point.node1().sourcePos().getCenter());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.assertTrue(com.george_vi.electroenergetics.CEEWireInteractionBehaviours.CLAMP_METER.get().isActiveFor(stack, player),
                "Native meter interaction did not accept a Power Grid multimeter on a CEE wire");
        com.george_vi.electroenergetics.CEEWireInteractionBehaviours.CLAMP_METER.get()
                .interactWire(point, helper.getLevel(), player, stack);
        helper.assertTrue(point.connection().equals(stack.get(com.george_vi.electroenergetics.CEEDataComponents.NODE_CONNECTION)),
                "Native meter interaction did not retain its selected CEE wire");
        var wire = InfrastructureSavedData.load(helper.getLevel()).getConnectionData(point.connection());
        helper.assertTrue(wire != null && Float.isFinite(wire.temperature()),
                "CEE wire already has invalid thermal state before callback injection: "
                        + (wire == null ? "missing" : wire.temperature()));
    }

    private static void assertFaultHasNoCollateralEffects(GameTestHelper helper, BlockPos source, BlockPos panel,
            BlockPos capacitor, double capacitorVoltage, BlockPos healthySource, BlockPos healthyLoad,
            net.minecraft.world.entity.player.Player player, MultimeterItem meter, ItemStack healthyStack, ItemStack failedStack) {
        NativeFixtures.near(helper, DevicesSavedData.load(helper.getLevel()).getDevice(capacitor, CapacitorDevice.class).lastVoltage,
                capacitorVoltage, 0, "A later adapter fault must roll back native CEE capacitor history in its circuit");
        NativeFixtures.near(helper, DevicesSavedData.load(helper.getLevel()).getDevice(capacitor, CapacitorDevice.class).properties.lastVoltage,
                capacitorVoltage, 0, "The native CEE capacitor property checkpoint retained an earlier substep from the rejected tick");
        var infrastructure = InfrastructureSavedData.load(helper.getLevel());
        helper.assertTrue(infrastructure.ticker.lastResults != null,
                "An unrelated failed adapter suppressed every healthy native CEE result");
        NativeFixtures.near(helper, supplyCurrent(helper, healthySource, healthyLoad), .2, .0002,
                "An unrelated failed adapter interrupted healthy native CEE supply current");
        var healthyReading = ElectricalMeasurements.readVoltageBetween(helper.getLevel(), healthyLoad, 0, healthyLoad, 1);
        helper.assertTrue(healthyReading.status() == MeasurementStatus.VALID && healthyReading.voltage().isPresent(),
                "An unrelated failed adapter invalidated a healthy mixed circuit's public measurement");
        player.setPos(healthySource.getCenter());
        meter.inventoryTick(healthyStack, helper.getLevel(), player, 0, true);
        NativeFixtures.near(helper, meter.getMeasurement(helper.getLevel(), healthyStack), .2, .0002,
                "An unrelated failed adapter interrupted the actual multimeter's healthy CEE wire reading");
        player.setPos(source.getCenter());
        helper.assertTrue(Float.isNaN(meter.getMeasurement(helper.getLevel(), failedStack)),
                "A same-frame failed CEE wire still exposed the native result cached before its gameplay commit");
        meter.inventoryTick(failedStack, helper.getLevel(), player, 0, true);
        helper.assertTrue(Float.isNaN(meter.getMeasurement(helper.getLevel(), failedStack)),
                "The actual multimeter displayed powered or fabricated-zero current for a failed CEE wire");
        helper.assertTrue(meter.getDial(helper.getLevel(), failedStack) == 0,
                "Unavailable current produced an invalid native analog pointer value");
        helper.assertTrue(meter.getText(helper.getLevel(), player, failedStack).getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        && text.getKey().equals("wwpg.reading.unavailable"),
                "The actual multimeter did not explain why its failed-wire reading is unavailable");
        var connection = failedStack.get(com.george_vi.electroenergetics.CEEDataComponents.NODE_CONNECTION);
        helper.assertTrue(connection != null, "Unavailable readings discarded the actual multimeter's selected CEE wire");
        var wire = infrastructure.getConnectionData(connection);
        helper.assertTrue(wire != null, "Callback isolation removed the selected physical CEE wire");
        helper.assertTrue(Float.isFinite(wire.temperature()),
                "Unavailable native results corrupted the failed CEE wire's thermal state: temperature=" + wire.temperature());
        var wireCache = infrastructure.wireSimulationState.getAllConnections().stream()
                .filter(entry -> entry.getKey().equals(connection)).findFirst().orElseThrow().getValue();
        helper.assertTrue(Double.isFinite(wireCache.dangerousDistanceSqr) && !wireCache.isOvervolted,
                "Unavailable native results corrupted or retained powered CEE wire hazard state");
    }

    private static void assertIsolatedAndRolledBack(GameTestHelper helper, BlockPos panel,
                                                   StorageIndicatorAttachment attachment, CompoundTag committed) {
        helper.assertTrue(attachment.electrical.acceptedSubsteps == committed.getLong("ApiAcceptedSubsteps"),
                "Failed CEE callback persisted partial or duplicate accepted history");
        NativeFixtures.near(helper, attachment.electrical.savedVoltage, committed.getDouble("ApiStoredVoltage"), 0,
                "Failed CEE callback must restore the last committed whole-tick voltage");
        var reading = ElectricalMeasurements.readVoltageBetween(helper.getLevel(), panel,
                attachment.nodes[0].id(), panel, attachment.nodes[1].id());
        helper.assertTrue(reading.status() != MeasurementStatus.VALID && reading.voltage().isEmpty(),
                "Failed CEE adapter exposed stale powered numbers through the public API");
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ceePanelAdapterPreservesNativeInstallationAndAcceptedStorage(GameTestHelper helper) {
        var sourceRelative = new BlockPos(1, 2, 1);
        var panelRelative = new BlockPos(4, 2, 1);
        helper.setBlock(sourceRelative.below(), Blocks.STONE);
        helper.setBlock(panelRelative.below(), Blocks.STONE);
        helper.setBlock(sourceRelative, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        helper.setBlock(panelRelative, CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        var source = helper.absolutePos(sourceRelative);
        var panel = helper.absolutePos(panelRelative);
        StorageIndicatorAttachment[] attachment = new StorageIndicatorAttachment[1];
        double[] initial = new double[1];
        long[] commitCount = new long[1];
        long[] dischargeCount = new long[1];
        helper.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) helper.getLevel().getBlockEntity(source)).setValue(70);
            attachment[0] = insert(helper, panel);
            NativeFixtures.wire(helper, source, 0, panel, attachment[0].nodes[0].id(), true);
            NativeFixtures.wire(helper, source, 1, panel, attachment[0].nodes[1].id(), false);
        });
        helper.runAtTickTime(25, () -> {
            NativeFixtures.near(helper, attachment[0].measuredVoltage, 70, .07, "Registered custom CEE panel receives PG voltage");
            NativeFixtures.near(helper, attachment[0].electrical.savedVoltage, 70, .07, "Custom capacitor receives accepted state");
            var visible = new CompoundTag();
            attachment[0].write(visible, true, helper.getLevel().registryAccess());
            helper.assertTrue(visible.getFloat("Light") > .95, "Native inherited panel indicator did not visibly light");
            commitCount[0] = attachment[0].electrical.acceptedSubsteps;
        });
        helper.runAtTickTime(26, () -> {
            helper.assertTrue(attachment[0].electrical.acceptedSubsteps - commitCount[0]
                            == ModdedConfigs.server().electricity.solver.multiTicks.get(),
                    "CEE adapter advanced storage a different number of times than the PG schedule");
            helper.setBlock(sourceRelative, Blocks.AIR);
        });
        helper.runAtTickTime(28, () -> {
            // Removing a source after preparation queues its electrical change for
            // the next safe phase. Start the analytical window after that boundary.
            helper.assertTrue(helper.getLevel().getBlockState(source).isAir(), "Removed source still exists");
            helper.assertTrue(attachment[0].electrical.savedVoltage > 0 && attachment[0].electrical.savedVoltage < 69,
                    "Source removal did not begin discharging the native attachment");
            initial[0] = attachment[0].electrical.savedVoltage;
            dischargeCount[0] = attachment[0].electrical.acceptedSubsteps;
        });
        helper.runAtTickTime(48, () -> {
            int steps = ModdedConfigs.server().electricity.solver.multiTicks.get();
            helper.assertTrue(attachment[0].electrical.acceptedSubsteps - dischargeCount[0] == 20L * steps,
                    "Discharge history did not advance exactly once per PG substep across its twenty-tick window");
            double expected = initial[0] * Math.pow(1 + .05 / steps, -20d * steps);
            NativeFixtures.near(helper, attachment[0].electrical.savedVoltage, expected, expected * .02,
                    "Source removal discharges custom CEE storage through its native lamp resistor");
            var saved = new CompoundTag();
            attachment[0].write(saved, false, helper.getLevel().registryAccess());
            var old = attachment[0];
            var replacement = (StorageIndicatorAttachment) old.type.createNew(panel, old.nodes,
                    helper.getLevel(), old.slot, Direction.NORTH, helper.getLevel().registryAccess());
            replacement.read(saved, false, helper.getLevel().registryAccess());
            NativeFixtures.near(helper, replacement.electrical.savedVoltage, old.electrical.savedVoltage, 0,
                    "Native saved attachment restores charge before a solve");
            helper.assertTrue(replacement.electrical.acceptedSubsteps == old.electrical.acceptedSubsteps,
                    "Saved attachment lost accepted-substep history");
            var blockEntity = (ElectricalPanelBlockEntity) helper.getLevel().getBlockEntity(panel);
            blockEntity.getAttachments()[old.slot.ordinal()] = replacement;
            blockEntity.setChanged();
            blockEntity.attachmentUpdate();
            attachment[0] = replacement;
        });
        helper.runAtTickTime(53, () -> {
            helper.assertTrue(attachment[0].electrical.savedVoltage > 0 && attachment[0].electrical.savedVoltage < 30,
                    "Replaced native attachment lost discharge operation or reset to its supply voltage");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void pgAddonComponentUsesNativeAssemblyAndKeepsOneElectricalOwner(GameTestHelper helper) {
        var sourceRelative = new BlockPos(1, 2, 1);
        var boardRelative = new BlockPos(4, 2, 1);
        helper.setBlock(sourceRelative.below(), Blocks.STONE);
        helper.setBlock(boardRelative.below(), Blocks.STONE);
        helper.setBlock(sourceRelative, CEEBlocks.CREATIVE_BATTERY.get());
        var source = helper.absolutePos(sourceRelative);
        var boardPosition = helper.absolutePos(boardRelative);
        java.util.UUID[] componentId = new java.util.UUID[1];
        helper.runAtTickTime(5, () -> {
            NativeFixtures.assembleAndPlace(helper, boardPosition, NativeFixtures.shuntSchematic());
            DevicesSavedData.load(helper.getLevel()).getDevice(source, CreativeBatteryDevice.class).voltage = 20;
            NativeFixtures.wire(helper, source, 1, boardPosition, 0, false);
            NativeFixtures.wire(helper, source, 0, boardPosition, 1, true);
        });
        helper.runAtTickTime(25, () -> {
            var board = (CircuitBoardBlockEntity) helper.getLevel().getBlockEntity(boardPosition);
            var component = board.getComponentsStream().filter(placed -> placed.component == UpstreamExample.SHUNT.get())
                    .findFirst().orElseThrow(() -> new IllegalStateException("Placed board lost its native add-on component: "
                            + board.getSchematic().serializeNbt(helper.getLevel().registryAccess())));
            helper.assertTrue(component.get(org.patryk3211.powergrid.circuits.components.ResistorComponent.RESISTANCE) == 100f,
                    "Native assembly lost the add-on component's resistance setting");
            NativeFixtures.near(helper, supplyCurrent(helper, source, boardPosition), .2, .0002,
                    "CEE powers an independently registered native PG board resistor");
            helper.assertTrue(!component.destroyed, "Native add-on board component failed below its rating");
            helper.assertTrue(helper.getLevel().getCapability(ElectricalCapabilities.DEVICE, boardPosition) == null,
                    "Native PG component acquired a duplicate extension electrical owner");
            componentId[0] = component.uuid;
            var saved = board.getSchematic().serializeNbt(helper.getLevel().registryAccess());
            board.setSchematic(CircuitSchematic.fromNbt(helper.getLevel().registryAccess(), saved));
        });
        helper.runAtTickTime(35, () -> {
            var board = (CircuitBoardBlockEntity) helper.getLevel().getBlockEntity(boardPosition);
            var component = board.getComponentsStream().filter(placed -> placed.component == UpstreamExample.SHUNT.get())
                    .findFirst().orElseThrow(() -> new IllegalStateException("Schematic reload lost its native add-on component: "
                            + board.getSchematic().serializeNbt(helper.getLevel().registryAccess())));
            helper.assertTrue(component.uuid.equals(componentId[0]) && board.terminalCount() == 2,
                    "Native schematic reload changed component or connector identities");
            NativeFixtures.near(helper, supplyCurrent(helper, source, boardPosition), .2, .0002,
                    "Native add-on component still receives mixed power after editing");
            helper.succeed();
        });
    }

    private static double supplyCurrent(GameTestHelper helper, BlockPos source, BlockPos board) {
        return Math.abs(InfrastructureSavedData.load(helper.getLevel()).ticker.lastResults.getCurrentThrough(
                new InWorldNode(1, source), new InWorldNode(0, board)));
    }

    static StorageIndicatorAttachment insert(GameTestHelper helper, BlockPos position) {
        helper.assertTrue(UpstreamExample.adapterRegistered,
                "The custom add-on did not receive WWPG's CEE adapter registration event before native installation");
        var slot = ElectricalPanelSlot.THIRD_LEFT;
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var stack = new ItemStack(UpstreamExample.PANEL_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var point = Vec3.atLowerCornerOf(position).add(slot.center.x, slot.center.y, .1);
        var result = helper.getLevel().getBlockState(position).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(point, Direction.NORTH, position, false));
        helper.assertTrue(result == ItemInteractionResult.SUCCESS && stack.isEmpty(),
                "Native CEE panel installation did not accept and consume the custom attachment item");
        var attachment = ((ElectricalPanelBlockEntity) helper.getLevel().getBlockEntity(position)).getAttachments()[slot.ordinal()];
        helper.assertTrue(attachment instanceof StorageIndicatorAttachment && attachment.type == UpstreamExample.PANEL.get(),
                "Native CEE installation selected a different attachment identity");
        return (StorageIndicatorAttachment) attachment;
    }
}
