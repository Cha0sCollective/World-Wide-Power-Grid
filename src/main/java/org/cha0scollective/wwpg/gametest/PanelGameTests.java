package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
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
    private static double voltage(GameTestHelper h, BlockPos pos) {
        return new BlockWireEndpoint(pos, 0).getNode(h.getLevel()).getVoltage() - new BlockWireEndpoint(pos, 1).getNode(h.getLevel()).getVoltage();
    }
}
