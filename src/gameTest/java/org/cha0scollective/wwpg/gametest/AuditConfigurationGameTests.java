package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.experimental.ScopedRestoration;
import org.cha0scollective.wwpg.experimental.WireTreeBatch;

/** Each flag combination must run in a fresh JVM, including clean server teardown. */
@GameTestHolder("wwpg_audit_config")
@PrefixGameTestTemplate(false)
public final class AuditConfigurationGameTests {
    @GameTest(template="empty",timeoutTicks=40)
    public static void restorationHasCompatibleEffectiveSettings(GameTestHelper h) {
        DynamicGameTests.substeps(h.getLevel());
        boolean requested=Boolean.parseBoolean(System.getProperty("wwpg.experimental.scopedRestoration","true"));
        h.assertTrue(ScopedRestoration.ENABLED==(requested&&WireTreeBatch.BOUNDED),"Effective restoration flags disagree");
        var source=new net.minecraft.core.BlockPos(1,2,1);var load=new net.minecraft.core.BlockPos(4,2,1);
        h.setBlock(source,org.patryk3211.powergrid.collections.ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(load,com.george_vi.electroenergetics.CEEBlocks.RESISTOR.get());
        h.runAtTickTime(5,()->{
            ((org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity)h.getBlockEntity(source)).setValue(10);
            com.george_vi.electroenergetics.devices.device.DevicesSavedData.load(h.getLevel())
                    .getDevice(h.absolutePos(load),com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice.class)
                    .properties=com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties.resistor(10);
            for(int port=0;port<2;++port)WiringGameTests.connect(h,h.absolutePos(source),port,h.absolutePos(load),port,false);
        });
        h.runAtTickTime(25,()->{
            var first=new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(h.absolutePos(load),0).getNode(h.getLevel());
            var second=new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(h.absolutePos(load),1).getNode(h.getLevel());
            BoardComponentGameTests.near(h,first.getVoltage()-second.getVoltage(),10,.01,"Powered circuit under effective restoration flags");
            h.getLevel().getDataStorage().save();h.succeed();
        });
    }
}
