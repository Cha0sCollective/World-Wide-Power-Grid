package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEDataComponents;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class PanelGameTests {
    @GameTest(template = "empty", timeoutTicks = 100) public static void panelControlsAndMonitorsPgFactory(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var p = new BlockPos(3, 2, 1); var b = new BlockPos(5, 2, 1);
        for (var pos : new BlockPos[] {a, p, b}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(p, CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        h.setBlock(b, ModdedBlocks.CREATIVE_RESISTOR.get());
        var source = h.absolutePos(a); var panel = h.absolutePos(p); var load = h.absolutePos(b); var level = h.getLevel();
        PanelAttachment[] attachments = new PanelAttachment[3];
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(10);
            ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(10);
            attachments[0] = insert(h, panel, CEEPanelAttachmentTypes.CUT_OFF_SWITCH.get(), ElectricalPanelSlot.THIRD_LEFT);
            attachments[1] = insert(h, panel, CEEPanelAttachmentTypes.VOLTMETER.get(), ElectricalPanelSlot.THIRD_CENTERED);
            attachments[2] = insert(h, panel, CEEPanelAttachmentTypes.INDICATOR_BULB.get(), ElectricalPanelSlot.THIRD_RIGHT);
            ((CutOffSwitchPanelAttachment) attachments[0]).isClosed = true;
            WiringGameTests.connect(h, source, 0, panel, attachments[0].nodes[0].id(), true);
            WiringGameTests.connect(h, panel, attachments[0].nodes[1].id(), load, 0, true);
            WiringGameTests.connect(h, source, 1, load, 1, true);
            for (int i = 1; i < 3; ++i) {
                WiringGameTests.connect(h, panel, attachments[i].nodes[0].id(), load, 0, true);
                WiringGameTests.connect(h, panel, attachments[i].nodes[1].id(), load, 1, true);
            }
        });
        h.runAtTickTime(30, () -> {
            h.assertTrue(Math.abs(voltage(h, load) - 10) < 0.1, "Panel switch failed to power PG load");
            h.assertTrue(((GaugePanelAttachment) attachments[1]).value > 8, "Panel gauge did not consume solved PG voltage");
            var state = new CompoundTag(); attachments[2].write(state, true, level.registryAccess());
            h.assertTrue(state.getFloat("Light") > 0.1f, "Panel indicator did not light");
            ((CutOffSwitchPanelAttachment) attachments[0]).isClosed = false;
        });
        h.runAtTickTime(40, () -> {
            h.assertTrue(Math.abs(voltage(h, load)) < 0.001, "Opening panel switch left power on");
            var be = (ElectricalPanelBlockEntity) level.getBlockEntity(panel);
            be.getAttachments()[ElectricalPanelSlot.THIRD_CENTERED.ordinal()] = null;
            be.attachmentUpdate();
            ((CutOffSwitchPanelAttachment) attachments[0]).isClosed = true;
        });
        h.runAtTickTime(50, () -> {
            h.assertTrue(Math.abs(voltage(h, load) - 10) < 0.1, "Panel edit broke the remaining control circuit");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    private static PanelAttachment insert(GameTestHelper h, BlockPos pos, PanelAttachmentType type, ElectricalPanelSlot slot) {
        var be = (ElectricalPanelBlockEntity) h.getLevel().getBlockEntity(pos);
        var attachment = type.createNew(pos, type.mode.getNodesFor(pos, slot), h.getLevel(), slot, Direction.NORTH, h.getLevel().registryAccess());
        be.getAttachments()[slot.ordinal()] = attachment;
        be.attachmentUpdate();
        return attachment;
    }

    @GameTest(template = "empty", timeoutTicks = 100) public static void panelPlayerStopAndMomentaryControl(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var p = new BlockPos(3, 2, 1); var b = new BlockPos(5, 2, 1);
        var q = new BlockPos(3, 2, 4); var c = new BlockPos(5, 2, 4);
        for (var pos : new BlockPos[] {a, p, b, q, c}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        for (var pos : new BlockPos[] {p, q}) h.setBlock(pos, CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        for (var pos : new BlockPos[] {b, c}) h.setBlock(pos, ModdedBlocks.CREATIVE_RESISTOR.get());
        var source = h.absolutePos(a); var panel = h.absolutePos(p); var momentaryPanel = h.absolutePos(q);
        var load = h.absolutePos(b); var momentaryLoad = h.absolutePos(c); var level = h.getLevel();
        PanelAttachment[] controls = new PanelAttachment[2];
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(10);
            for (var pos : new BlockPos[] {load, momentaryLoad}) ((ResistorBlockEntity) level.getBlockEntity(pos)).setValue(100);
            controls[0] = insertWithItem(h, panel, CEEPanelAttachmentTypes.ESTOP.get(), ElectricalPanelSlot.HALF_LEFT, null);
            controls[1] = insertWithItem(h, momentaryPanel, CEEPanelAttachmentTypes.MOMENTARY_SWITCH.get(), ElectricalPanelSlot.THIRD_LEFT, null);
            for (int i = 0; i < 2; ++i) {
                var destination = i == 0 ? load : momentaryLoad;
                WiringGameTests.connect(h, source, 0, controls[i].pos, controls[i].nodes[0].id(), true);
                WiringGameTests.connect(h, controls[i].pos, controls[i].nodes[1].id(), destination, 0, true);
                WiringGameTests.connect(h, source, 1, destination, 1, true);
            }
            interact(h, controls[0], true);
        });
        h.runAtTickTime(20, () -> {
            h.assertTrue(voltage(h, load) > 9.9, "Shift reset failed to close panel emergency stop");
            interact(h, controls[0], false);
        });
        h.runAtTickTime(30, () -> {
            h.assertTrue(Math.abs(voltage(h, load)) < 0.001, "Emergency stop left the PG load powered");
            interact(h, controls[0], true);
        });
        h.runAtTickTime(40, () -> {
            h.assertTrue(voltage(h, load) > 9.9, "Emergency stop reset did not restore PG load");
            interact(h, controls[1], false);
            h.assertTrue(((MomentarySwitchPanelAttachment) controls[1]).closedTicks == 4, "Momentary panel did not register its press");
        });
        h.runAtTickTime(42, () -> h.assertTrue(voltage(h, momentaryLoad) > 9.9, "Momentary press failed to power PG load"));
        h.runAtTickTime(50, () -> {
            h.assertTrue(Math.abs(voltage(h, momentaryLoad)) < 0.001, "Momentary switch did not release");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100) public static void panelBreakerTripsAndPlayerResets(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var p = new BlockPos(3, 2, 1); var b = new BlockPos(5, 2, 1);
        for (var pos : new BlockPos[] {a, p, b}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(p, CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        h.setBlock(b, ModdedBlocks.CREATIVE_RESISTOR.get());
        var source = h.absolutePos(a); var panel = h.absolutePos(p); var load = h.absolutePos(b); var level = h.getLevel();
        MCBPanelAttachment[] breaker = new MCBPanelAttachment[1];
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(10);
            ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(100);
            var type = CEEPanelAttachmentTypes.MINIATURE_CIRCUIT_BREAKER.get();
            var stack = new ItemStack(type.item); stack.set(CEEDataComponents.FUSE_AMPERAGE, 1);
            breaker[0] = (MCBPanelAttachment) insertWithItem(h, panel, type, ElectricalPanelSlot.THIRD_LEFT, stack);
            h.assertTrue(breaker[0].setAmperage == 1, "Panel insertion lost the breaker's item setting");
            WiringGameTests.connect(h, source, 0, panel, breaker[0].nodes[0].id(), false);
            WiringGameTests.connect(h, panel, breaker[0].nodes[1].id(), load, 0, false);
            WiringGameTests.connect(h, source, 1, load, 1, false);
            interact(h, breaker[0], false);
        });
        h.runAtTickTime(20, () -> {
            h.assertTrue(voltage(h, load) > 9.9 && breaker[0].isClosed, "Panel breaker did not close");
            ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(1);
        });
        h.runAtTickTime(35, () -> {
            h.assertTrue(!breaker[0].isClosed && Math.abs(voltage(h, load)) < 0.001, "Panel breaker did not disconnect overload");
            ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(100);
            interact(h, breaker[0], false);
        });
        h.runAtTickTime(50, () -> {
            h.assertTrue(breaker[0].isClosed && voltage(h, load) > 9.9, "Player reset did not restore panel breaker power");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100) public static void panelEnergyMeterAndAmmeterReadMixedLoad(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var p = new BlockPos(3, 2, 1); var q = new BlockPos(3, 2, 4); var b = new BlockPos(5, 2, 4);
        for (var pos : new BlockPos[] {a, p, q, b}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()); h.setBlock(b, ModdedBlocks.CREATIVE_RESISTOR.get());
        for (var pos : new BlockPos[] {p, q}) h.setBlock(pos, CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        var source = h.absolutePos(a); var panel = h.absolutePos(p); var gaugePanel = h.absolutePos(q); var load = h.absolutePos(b); var level = h.getLevel();
        EnergyMeterAttachment[] meter = new EnergyMeterAttachment[1]; GaugePanelAttachment[] gauge = new GaugePanelAttachment[1]; double[] energy = new double[1];
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(10);
            ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(10);
            meter[0] = (EnergyMeterAttachment) insertWithItem(h, panel, CEEPanelAttachmentTypes.ENERGY_METER.get(), ElectricalPanelSlot.FULL_SLOT, null);
            gauge[0] = (GaugePanelAttachment) insertWithItem(h, gaugePanel, CEEPanelAttachmentTypes.AMMETER.get(), ElectricalPanelSlot.THIRD_LEFT, null);
            WiringGameTests.connect(h, source, 0, panel, meter[0].nodes[0].id(), true);
            WiringGameTests.connect(h, source, 1, panel, meter[0].nodes[1].id(), true);
            WiringGameTests.connect(h, panel, meter[0].nodes[2].id(), gaugePanel, gauge[0].nodes[0].id(), true);
            WiringGameTests.connect(h, gaugePanel, gauge[0].nodes[1].id(), load, 0, true);
            WiringGameTests.connect(h, panel, meter[0].nodes[3].id(), load, 1, true);
        });
        h.runAtTickTime(30, () -> {
            h.assertTrue(Math.abs(gauge[0].value - 1) < 0.02, "Panel ammeter reading was " + gauge[0].value);
            h.assertTrue(Math.abs(meter[0].activePower - 10) < 0.2 && meter[0].totalEnergy > 0, "Panel energy meter failed to measure mixed power");
            energy[0] = meter[0].totalEnergy;
        });
        h.runAtTickTime(40, () -> {
            double expected = meter[0].activePower * 10 / 72_000_000;
            h.assertTrue(Math.abs(meter[0].totalEnergy - energy[0] - expected) < expected * 0.01, "Panel energy advanced at the wrong timestep");
            meter[0].disconnected = true;
        });
        h.runAtTickTime(50, () -> {
            h.assertTrue(Math.abs(voltage(h, load)) < 0.001 && meter[0].activePower == 0, "Energy meter disconnect left load powered");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100) public static void panelTriPolarMeterUsesAssignedTerminals(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var p = new BlockPos(3, 2, 1); var b = new BlockPos(5, 2, 1); var c = new BlockPos(5, 2, 4);
        for (var pos : new BlockPos[] {a, p, b, c}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(p, CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        for (var pos : new BlockPos[] {b, c}) h.setBlock(pos, ModdedBlocks.CREATIVE_RESISTOR.get());
        var source = h.absolutePos(a); var panel = h.absolutePos(p); var loadA = h.absolutePos(b); var loadB = h.absolutePos(c); var level = h.getLevel();
        TriPolarEnergyMeterAttachment[] meter = new TriPolarEnergyMeterAttachment[1]; double[] energy = new double[1];
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(10);
            for (var pos : new BlockPos[] {loadA, loadB}) ((ResistorBlockEntity) level.getBlockEntity(pos)).setValue(10);
            meter[0] = (TriPolarEnergyMeterAttachment) insertWithItem(h, panel, CEEPanelAttachmentTypes.TRI_POLAR_ENERGY_METER.get(), ElectricalPanelSlot.FULL_SLOT, null);
            for (int i : new int[] {0, 2}) WiringGameTests.connect(h, source, 0, panel, meter[0].nodes[i].id(), true);
            WiringGameTests.connect(h, source, 1, panel, meter[0].nodes[1].id(), true);
            WiringGameTests.connect(h, panel, meter[0].nodes[3].id(), loadA, 0, true);
            WiringGameTests.connect(h, panel, meter[0].nodes[5].id(), loadB, 0, true);
            for (var pos : new BlockPos[] {loadA, loadB}) WiringGameTests.connect(h, panel, meter[0].nodes[4].id(), pos, 1, true);
        });
        h.runAtTickTime(30, () -> {
            h.assertTrue(Math.abs(voltage(h, loadA) - 10) < 0.1 && Math.abs(voltage(h, loadB) - 10) < 0.1, "Three-pole panel failed to power both loads");
            h.assertTrue(Double.isFinite(meter[0].activePower) && Math.abs(meter[0].activePower - 20) < 0.3, "Three-pole panel power was " + meter[0].activePower);
            h.assertTrue(meter[0].totalEnergy > 0, "Three-pole panel accumulated no consumed energy");
            energy[0] = meter[0].totalEnergy;
        });
        h.runAtTickTime(40, () -> {
            double expected = meter[0].activePower * 10 / 72_000_000;
            h.assertTrue(Math.abs(meter[0].totalEnergy - energy[0] - expected) < expected * 0.01, "Three-pole panel energy timestep was incorrect");
            meter[0].disconnected = true;
        });
        h.runAtTickTime(50, () -> {
            h.assertTrue(Math.abs(voltage(h, loadA)) < 0.001 && Math.abs(voltage(h, loadB)) < 0.001, "Three-pole panel disconnect left load powered");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    private static PanelAttachment insertWithItem(GameTestHelper h, BlockPos pos, PanelAttachmentType type,
                                                   ElectricalPanelSlot slot, ItemStack configured) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var stack = configured == null ? new ItemStack(type.item) : configured;
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var click = Vec3.atLowerCornerOf(pos).add(slot.center.x, slot.center.y, 0.1);
        var result = h.getLevel().getBlockState(pos).useItemOn(stack, h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(click, Direction.NORTH, pos, false));
        h.assertTrue(result == ItemInteractionResult.SUCCESS, "Native panel insertion failed for " + type.item);
        h.assertTrue(stack.isEmpty(), "Survival panel insertion did not consume its attachment item");
        var attachment = ((ElectricalPanelBlockEntity) h.getLevel().getBlockEntity(pos)).getAttachments()[slot.ordinal()];
        h.assertTrue(attachment != null && attachment.type == type, "Native panel insertion chose the wrong slot");
        return attachment;
    }

    private static void interact(GameTestHelper h, PanelAttachment attachment, boolean shift) {
        var player = h.makeMockPlayer(GameType.SURVIVAL); player.setShiftKeyDown(shift);
        var click = Vec3.atLowerCornerOf(attachment.pos).add(attachment.slot.center.x, attachment.slot.center.y, 0.1);
        var result = h.getLevel().getBlockState(attachment.pos).useItemOn(ItemStack.EMPTY, h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(click, Direction.NORTH, attachment.pos, false));
        h.assertTrue(result == ItemInteractionResult.SUCCESS || result == ItemInteractionResult.CONSUME, "Native panel control interaction failed");
    }
    private static double voltage(GameTestHelper h, BlockPos pos) {
        return new BlockWireEndpoint(pos, 0).getNode(h.getLevel()).getVoltage() - new BlockWireEndpoint(pos, 1).getNode(h.getLevel()).getVoltage();
    }
}
