package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.CEEPanelAttachmentTypes;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;

@GameTestHolder("wwpg_stationary")
@PrefixGameTestTemplate(false)
public final class DiagnosticsGameTests {
    @GameTest(template="empty",timeoutTicks=80)
    public static void diagnosticsReadNativePgModelsWithoutCreatingCompatibilityTopology(GameTestHelper h) {
        var source=h.absolutePos(new BlockPos(1,3,1));var load=source.east(3);
        for(var pos:new BlockPos[]{source,load})h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(source,ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
        h.getLevel().setBlockAndUpdate(load,ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
        h.runAtTickTime(5,()->{
            ShowroomTools.pgSource(h,source,13);ShowroomTools.resistance(h,load,1000);
            WiringGameTests.connect(h,source,0,load,0,true);WiringGameTests.connect(h,source,1,load,1,true);
        });
        h.runAtTickTime(30,()->{
            var bridge=Bridges.get(h.getLevel());var world=GlobalElectricNetworks.getWorldNetworks(h.getLevel());
            var infrastructure=InfrastructureSavedData.load(h.getLevel());
            int nodes=world.globalExternalNodes.size(),ceeNodes=infrastructure.getNodes().size();
            String before=bridge.status();
            var description=String.join("\n",bridge.describe(source));
            h.assertTrue(description.contains("block=powergrid:creative_voltage_source")&&description.contains("readiness=ready")
                    &&description.contains("nativePG=true")&&description.contains("terminal:0")&&description.contains("terminal:1"),
                    "Native PG diagnostic omitted its identity or terminals: "+description);
            h.assertTrue(description.contains("backend="+System.getProperty("wwpg.test.backend","NATIVE")),"Native diagnostic omitted the actual solver: "+description);
            h.assertTrue(world.globalExternalNodes.size()==nodes&&infrastructure.getNodes().size()==ceeNodes&&before.equals(bridge.status()),
                    "Read-only diagnostics changed electrical topology or created a CEE endpoint");
            BoardComponentGameTests.near(h,StationaryNetherGameTests.voltage(h.getLevel(),load),13,.013,"Native circuit after diagnostics");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=30)
    public static void diagnosticOfUnloadedCoordinatesDoesNotLoadChunksOrInventReadings(GameTestHelper h) {
        var pos=new BlockPos(14000000,64,14000000);var source=h.getLevel().getChunkSource();
        h.assertTrue(source.getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null,"Diagnostic target must start unloaded");
        var description=String.join("\n",Bridges.get(h.getLevel()).describe(pos));
        h.assertTrue(description.contains("block=unloaded")&&description.contains("readiness=waiting-for-block-chunk")
                &&!description.contains("voltage="),"Unloaded coordinates were given an invented measurement: "+description);
        h.assertTrue(source.getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null,"Diagnostic command loaded its target chunk");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void diagnosticsIdentifyPanelSlotsAndStableBoardComponentPads(GameTestHelper h) {
        var source=h.absolutePos(new BlockPos(1,3,1));var board=source.east(3);var panel=source.south(3);
        for(var pos:new BlockPos[]{source,board,panel})h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(source,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        h.getLevel().setBlockAndUpdate(board,ModdedBlocks.CIRCUIT_BOARD.getDefaultState());
        h.getLevel().setBlockAndUpdate(panel,CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING,Direction.NORTH));
        h.runAtTickTime(5,()->{
            ShowroomTools.scroll(h,source,10000);
            ((CircuitBoardBlockEntity)h.getLevel().getBlockEntity(board)).setSchematic(BoardWorkflowGameTests.viaLabelBoard());
            var meter=PanelGameTests.insert(h,panel,CEEPanelAttachmentTypes.VOLTMETER.get(),ElectricalPanelSlot.THIRD_LEFT);
            for(int index=0;index<2;index++) {
                WiringGameTests.connect(h,source,1-index,board,index,false);
                WiringGameTests.connect(h,source,1-index,panel,meter.nodes[index].id(),false);
            }
        });
        h.runAtTickTime(35,()->{
            var bridge=Bridges.get(h.getLevel());
            var panelText=String.join("\n",bridge.describe(panel));
            h.assertTrue(panelText.contains("panel slot=THIRD_LEFT")&&panelText.contains("type=electroenergetics:voltmeter")
                    &&panelText.contains("identity=block:"),"Panel slot/terminal identity missing: "+panelText);
            var boardText=String.join("\n",bridge.describe(board));
            var actualBoard=(CircuitBoardBlockEntity)h.getLevel().getBlockEntity(board);
            for(var component:actualBoard.getComponentsStream().toList())
                h.assertTrue(boardText.contains("uuid="+component.uuid),"Native component identity missing: "+boardText);
            h.assertTrue(boardText.contains("/pad:")&&boardText.contains("board component=powergrid:resistor")
                    &&boardText.contains("board component=powergrid:label"),"Board electrical and assembly details missing: "+boardText);
            h.assertTrue(!bridge.topologyHistory().isEmpty()&&bridge.status().contains("lastTopologyTick="),"Missing topology diagnostics");
            DynamicGameTests.audit(h);h.succeed();
        });
    }
}
