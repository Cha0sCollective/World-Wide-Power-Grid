package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.compat.cio.CioCompatibility;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Requires a disposable copy of the original 0.2.5 coupler reproduction save. */
@GameTestHolder("wwpg_cio_upgrade")
@PrefixGameTestTemplate(false)
public final class CioUpgradeGameTests {
    @GameTest(template="empty", timeoutTicks=180)
    public static void originalReleaseSaveStopsTransferringWithoutDeletingWires(GameTestHelper h) {
        var level=h.getLevel();var source=new BlockPos(3000,64,3000);
        var coupler=source.east(3);var load=source.east(6);
        level.setChunkForced(source.getX()>>4,source.getZ()>>4,true);
        level.getChunkAt(source);
        h.assertTrue(CioCompatibility.gridCoupler(level.getBlockState(coupler).getBlock()),"Original-release fixture is missing");
        h.runAtTickTime(90,()->{
            var behaviour=IElectric.getAt(level,coupler).getBehaviour(level,coupler,level.getBlockState(coupler));
            h.assertTrue(!behaviour.hasInternals(),"Restored coupler still transfers power");
            double pg=new BlockWireEndpoint(source,0).getNode(level).getVoltage()-new BlockWireEndpoint(source,1).getNode(level).getVoltage();
            var results=InfrastructureSavedData.load(level).ticker.lastResults;
            double output=results.getVoltageAt(load,0,1);
            h.assertTrue(Double.isFinite(pg)&&Math.abs(pg-120)<.02,"Upgrade changed the native PG supply: "+pg);
            h.assertTrue(Double.isFinite(output)&&Math.abs(output)<.001,"Disabled saved coupler still powers its CEE load: "+output);
            h.assertTrue(Double.isNaN(results.getVoltageAt(coupler,0,1)),"Disabled coupler falsely published an available CEE reading");
            h.assertTrue(behaviour.getConnections().values().stream().mapToInt(java.util.Collection::size).sum()==2,"Upgrade deleted a PG wire");
            for(int pin=0;pin<2;pin++)h.assertTrue(InfrastructureSavedData.load(level).getConnections(new InWorldNode(pin,coupler)).size()==1,"Upgrade deleted a CEE wire");
            var tooltip=new java.util.ArrayList<net.minecraft.network.chat.Component>();
            try { level.getBlockEntity(coupler).getClass().getMethod("addToGoggleTooltip",java.util.List.class,boolean.class).invoke(level.getBlockEntity(coupler),tooltip,false); }
            catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
            h.assertTrue(tooltip.stream().anyMatch(line->line.getString().contains("Disabled")||line.getString().contains("wwpg.cio.coupler_disabled")),"Existing coupler has no disabled explanation");
            System.out.println("CIO_ACCEPT original 0.2.5 save upgraded: PG="+pg+", CEE load="+output+", wires retained");
            h.succeed();
        });
    }
}
