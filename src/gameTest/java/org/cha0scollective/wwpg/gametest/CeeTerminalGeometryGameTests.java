package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.CEEPanelAttachmentTypes;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.GaugePanelAttachment;
import com.george_vi.electroenergetics.content.electronic_components.capacitor.CapacitorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.wiring.CeeElectric;
import org.cha0scollective.wwpg.wiring.Terminals;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Native CEE operations share geometry only for the duration of that operation. */
@GameTestHolder("wwpg_wires")
@PrefixGameTestTemplate(false)
public final class CeeTerminalGeometryGameTests {
    @BeforeBatch(batch = "wwpg_cee_geometry")
    public static void configure(net.minecraft.server.level.ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template = "empty", batch = "wwpg_cee_geometry", timeoutTicks = 180)
    public static void poweredSparsePanelOperationsObserveLiveGeometry(GameTestHelper h) {
        var sourceRelative = new BlockPos(1, 2, 1);
        var panelRelative = new BlockPos(4, 2, 1);
        var loadRelative = new BlockPos(7, 2, 1);
        var capacitorRelative = new BlockPos(4, 2, 4);
        for (var pos : new BlockPos[] {sourceRelative, panelRelative, loadRelative, capacitorRelative}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(sourceRelative, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(loadRelative, ModdedBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(panelRelative, CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        h.setBlock(capacitorRelative, CEEBlocks.CAPACITOR.get());
        var level = h.getLevel();
        var source = h.absolutePos(sourceRelative);
        var panel = h.absolutePos(panelRelative);
        var load = h.absolutePos(loadRelative);
        var capacitor = h.absolutePos(capacitorRelative);
        var gauge = new GaugePanelAttachment[1];
        var adapter = new CeeElectric[1];
        var originalOrigin = new Vec3[1];
        h.startSequence().thenIdle(5).thenExecute(() -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(20);
            ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(100);
            DevicesSavedData.load(level).getDevice(capacitor, CapacitorDevice.class).capacitance = .01;
            gauge[0] = (GaugePanelAttachment) PanelGameTests.insert(h, panel, CEEPanelAttachmentTypes.VOLTMETER.get(), ElectricalPanelSlot.THIRD_CENTERED);
            h.assertTrue(gauge[0].nodes[0].id() == 8 && gauge[0].nodes[1].id() == 11, "Pinned panel fixture must use sparse native IDs 8 and 11");
            wireGauge(h, source, panel, gauge[0]);
            WiringGameTests.connect(h, source, 0, load, 0, true);
            WiringGameTests.connect(h, source, 1, load, 1, true);
            WiringGameTests.connect(h, source, 0, capacitor, 0, true);
            WiringGameTests.connect(h, source, 1, capacitor, 1, true);
            adapter[0] = Terminals.cee(level, panel);
            h.assertTrue(adapter[0] != null, "Native panel sidecar was not discovered");
        }).thenWaitUntil(() -> powered(h, load, capacitor, gauge[0])).thenExecute(() -> {
            operations(h, panel, capacitor, adapter[0]);
            originalOrigin[0] = adapter[0].terminal(level.getBlockState(panel), 8).getOrigin();
            level.setBlockAndUpdate(panel, level.getBlockState(panel).setValue(ElectricalPanelBlock.FACING, Direction.EAST));
            var currentOrigin = adapter[0].terminal(level.getBlockState(panel), 8).getOrigin();
            h.assertTrue(!originalOrigin[0].equals(currentOrigin), "A subsequent operation retained geometry from before panel rotation");
            operations(h, panel, capacitor, adapter[0]);
        }).thenIdle(10).thenWaitUntil(() -> powered(h, load, capacitor, gauge[0])).thenExecute(() -> {
            var device = DevicesSavedData.load(level).getDevice(capacitor, CapacitorDevice.class);
            var saved = saved(device);
            double numericalHistory = device.properties.lastVoltage;
            var be = (ElectricalPanelBlockEntity) level.getBlockEntity(panel);
            gauge[0].onRemoved(h.makeMockPlayer(GameType.SURVIVAL));
            be.getAttachments()[ElectricalPanelSlot.THIRD_CENTERED.ordinal()] = null;
            be.attachmentUpdate();
            Terminals.refresh(level);
            h.assertTrue(!adapter[0].behaviour().hasTerminal(8) && !adapter[0].behaviour().hasTerminal(11), "Removed attachment retained native terminal validity");
            h.assertTrue(adapter[0].behaviour().getExternalNodes().isEmpty() && adapter[0].terminalCount() == 0, "Removed attachment retained enumerated native nodes");
            h.assertTrue(adapter[0].behaviour().getTerminal(8) == null && adapter[0].terminalAt(level.getBlockState(panel), originalOrigin[0]) == null,
                    "A fresh terminal operation reused removed geometry");
            h.assertTrue(adapter[0].behaviour().getConnections().keySet().stream().noneMatch(endpoint -> endpoint.getTerminal() == 8 || endpoint.getTerminal() == 11),
                    "Invalid sparse terminal wires survived native cleanup");
            h.assertTrue(saved.equals(saved(device)) && numericalHistory == device.properties.lastVoltage, "Terminal cleanup advanced unrelated capacitor history");
            sync(h, panel, adapter[0], true);
        }).thenIdle(10).thenWaitUntil(() -> powered(h, load, capacitor, null)).thenExecute(() -> {
            h.assertTrue(ElectricalMeasurements.readTerminal(level, panel, 8).status() == MeasurementStatus.NOT_READY, "Removed panel terminal exposed a stale valid measurement");
            gauge[0] = (GaugePanelAttachment) PanelGameTests.insert(h, panel, CEEPanelAttachmentTypes.VOLTMETER.get(), ElectricalPanelSlot.THIRD_CENTERED);
            wireGauge(h, source, panel, gauge[0]);
        }).thenWaitUntil(() -> powered(h, load, capacitor, gauge[0])).thenExecute(() -> {
            h.assertTrue(Terminals.cee(level, panel) == adapter[0], "Attachment edits replaced the unchanged native panel owner");
            operations(h, panel, capacitor, adapter[0]);
            DynamicGameTests.audit(h);
        }).thenSucceed();
    }

    private static void wireGauge(GameTestHelper h, BlockPos source, BlockPos panel, GaugePanelAttachment gauge) {
        WiringGameTests.connect(h, source, 0, panel, gauge.nodes[0].id(), true);
        WiringGameTests.connect(h, source, 1, panel, gauge.nodes[1].id(), true);
    }

    private static void powered(GameTestHelper h, BlockPos load, BlockPos capacitor, GaugePanelAttachment gauge) {
        for (var pos : new BlockPos[] {load, capacitor}) {
            var reading = ElectricalMeasurements.readVoltageBetween(h.getLevel(), pos, 0, pos, 1);
            h.assertTrue(reading.status() == MeasurementStatus.VALID, "Waiting for accepted native voltage at " + pos + ": " + reading);
            BoardComponentGameTests.near(h, reading.voltage().orElseThrow().meanVoltageVolts(), 20, .05, "Powered native fixture voltage");
        }
        var device = DevicesSavedData.load(h.getLevel()).getDevice(capacitor, CapacitorDevice.class);
        h.assertTrue(device.lastVoltage > 19.9 && device.lastVoltage == device.properties.lastVoltage, "Waiting for committed native capacitor history");
        if (gauge != null) BoardComponentGameTests.near(h, gauge.value, 20, .05, "Native panel consumes actual powered results");
    }

    private static void operations(GameTestHelper h, BlockPos panel, BlockPos capacitor, CeeElectric adapter) {
        var level = h.getLevel();
        var device = DevicesSavedData.load(level).getDevice(capacitor, CapacitorDevice.class);
        var saved = saved(device);
        double numericalHistory = device.properties.lastVoltage;
        var state = level.getBlockState(panel);
        var block = (ElectricalDeviceBlock<?>) state.getBlock();
        var geometry = new TreeMap<Integer, Vec3>();
        for (var entry : block.getNodePositions(level, panel, state).entrySet())
            if (block.isNodeAccessible(level, panel, state, entry.getKey())) geometry.put(entry.getKey(), entry.getValue());
        h.assertTrue(new ArrayList<>(geometry.keySet()).equals(List.of(8, 11)), "Powered panel lost its native sparse IDs");
        h.assertTrue(adapter.terminalCount() == 12, "Sparse terminal count was compacted into local ordinals");
        var nodes = adapter.behaviour().getExternalNodes();
        var ids = nodes.stream().map(node -> ((BlockWireEndpoint) node.endpoint).getTerminal()).toList();
        h.assertTrue(ids.equals(new ArrayList<>(geometry.keySet())), "Native terminal enumeration changed its sorted ID order");
        for (var entry : geometry.entrySet()) {
            var terminal = adapter.terminal(state, entry.getKey());
            h.assertTrue(terminal != null && terminal.getOrigin().equals(entry.getValue()), "Native placement did not observe current geometry");
            h.assertTrue(terminal instanceof org.patryk3211.powergrid.electricity.base.IDecoratedTerminal,
                    "CEE terminal cannot use PG's native highlighter");
            var decorated = (org.patryk3211.powergrid.electricity.base.IDecoratedTerminal)terminal;
            double radius=block.getNodeSize(level,panel,state,entry.getKey())/2.0;
            var origin=entry.getValue();
            h.assertTrue(decorated.getOutline().equals(new net.minecraft.world.phys.AABB(
                    origin.x-radius,origin.y-radius,origin.z-radius,origin.x+radius,origin.y+radius,origin.z+radius)),
                    "Highlight box differs from CEE's selectable bounds");
            h.assertTrue(decorated.getName().equals(block.getNodeLabel(level,panel,state,entry.getKey())),
                    "Highlight label differs from CEE's native terminal label");
            int selected = adapter.terminalIndexAt(state, entry.getValue());
            h.assertTrue(selected == entry.getKey(), "Native selection returned a compacted or stale terminal ID");
            var selection = adapter.terminalAt(state, entry.getValue());
            h.assertTrue(selection != null && selection.getOrigin().equals(entry.getValue()), "Native placement selection used a different geometry snapshot");
            h.assertTrue(adapter.behaviour().getTerminal(entry.getKey()) == nodes.get(ids.indexOf(entry.getKey())), "Enumeration allocated another node for an existing native endpoint");
        }
        h.assertTrue(adapter.terminal(state, 0) == null && !adapter.behaviour().hasTerminal(0), "Sparse ID gap became an accessible terminal");
        h.assertTrue(adapter.terminalAt(state, new Vec3(20, 20, 20)) == null && adapter.terminalIndexAt(state, new Vec3(20, 20, 20)) == -1,
                "An out-of-bounds player selection resolved a native terminal");
        for (int pass = 0; pass < 3; ++pass) {
            sync(h, panel, adapter, true);
            sync(h, panel, adapter, false);
        }
        h.assertTrue(saved.equals(saved(device)) && numericalHistory == device.properties.lastVoltage, "Geometry enumeration, selection or sync advanced native stored energy");
    }

    private static void sync(GameTestHelper h, BlockPos panel, CeeElectric adapter, boolean doubles) {
        var nodes = adapter.behaviour().getExternalNodes();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            adapter.behaviour().writeToSync(buffer, doubles, ignored -> null);
            h.assertTrue(buffer.readVarInt() == nodes.size(), "Native sync count differs from accessible terminal enumeration");
            for (var node : nodes) {
                int id = ((BlockWireEndpoint) node.endpoint).getTerminal();
                h.assertTrue(buffer.readVarInt() == id, "Native sync reordered sparse terminal IDs");
                double expected = doubles ? node.getVoltage() : (double) (float) node.getVoltage();
                double actual = doubles ? buffer.readDouble() : buffer.readFloat();
                h.assertTrue(Double.doubleToLongBits(expected) == Double.doubleToLongBits(actual), "Native sync changed an accepted voltage value");
            }
            h.assertTrue(buffer.readableBytes() == 0, "Native sync left an unexpected terminal payload");
            // Applying exact double values preserves the server's current numerical state.
            // Float encoding is inspected only: client packet rounding must not alter this powered server fixture.
            if (doubles) {
                buffer.readerIndex(0);
                adapter.behaviour().readFromSync(buffer, true);
                h.assertTrue(buffer.readableBytes() == 0, "Native sync reader did not consume the complete payload");
            }
            buffer.clear();
            buffer.writeVarInt(1); buffer.writeVarInt(9999); buffer.writeDouble(123);
            adapter.behaviour().readFromSync(buffer, true);
            h.assertTrue(buffer.readableBytes() == 0, "Native sync reader did not consume an invalid terminal's value");
            var world = GlobalElectricNetworks.getWorldNetworks((net.minecraft.world.level.LevelAccessor) h.getLevel());
            h.assertTrue(world.globalExternalNodes.get(new BlockWireEndpoint(panel, 9999)) == null, "Invalid sync ID created a native graph endpoint");
        } finally { buffer.release(); }
    }

    private static CompoundTag saved(CapacitorDevice device) {
        var tag = new CompoundTag();
        device.write(tag);
        return tag;
    }
}
