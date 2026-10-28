package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.extension.ApiCircuits;
import org.cha0scollective.wwpg.extension.ApiTerminals;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;

/** Exercise native PG terminal binding before CEE's first preparation tick. */
@GameTestHolder("wwpg_api") @PrefixGameTestTemplate(false)
public final class ExtensionApiStartupGameTests {
    private static BlockPos source;
    private static OwnedFloatingNode bound;
    private static boolean before,after,identitySaved,modelsUntouched;
    private static String failure;

    public static void beforeStartup(ServerStartedEvent event) {
        var level=event.getServer().overworld();
        try {
            source=level.getSharedSpawnPos().above(2).east(2);
            if(level.getChunkSource().getChunkNow(source.getX()>>4,source.getZ()>>4)==null)
                throw new IllegalStateException("Startup probe requires the already prepared spawn chunk");
            before=ceeInitialized(level);
            level.setBlockAndUpdate(source.below(),Blocks.STONE.defaultBlockState());
            var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("wwpg_api_example:source"));
            if(block==Blocks.AIR)throw new IllegalStateException("Startup probe requires the independent public API example");
            level.setBlockAndUpdate(source,block.defaultBlockState());
            var adapter=ApiTerminals.get(level,source);
            if(adapter==null)throw new IllegalStateException("Startup terminal provider missing");
            bound=adapter.behaviour().getTerminal(0);
            adapter.behaviour().getTerminal(1);
            after=ceeInitialized(level);
            identitySaved=ApiTerminals.knownSavedTerminal(level,source,0)&&ApiTerminals.knownSavedTerminal(level,source,1);
            modelsUntouched=ApiCircuits.objectCount(level,source)==0;
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("API_STARTUP_BINDING_OBSERVED: ceeBefore={}, ceeAfter={}, identitySaved={}, noModels={}",before,after,identitySaved,modelsUntouched);
        } catch (Exception error) { failure=error.getClass().getSimpleName()+": "+error.getMessage(); }
    }

    private static boolean ceeInitialized(ServerLevel level) throws ReflectiveOperationException {
        var storage=level.getDataStorage();var field=storage.getClass().getDeclaredField("cache");field.setAccessible(true);
        return ((java.util.Map<?,?>)field.get(storage)).containsKey("electroenergetics_infrastructure");
    }

    @GameTest(template="empty",batch="api_startup_binding",timeoutTicks=40)
    public static void terminalBindingDoesNotInitializeCeeDuringStartup(GameTestHelper h) {
        h.assertTrue(failure==null,"Startup observation failed: "+failure);
        if(System.getProperty("wwpg.test.restartPhase","SETUP").equals("SETUP"))
            h.assertTrue(!before,"Fresh startup fixture already initialized CEE infrastructure");
        h.assertTrue(before==after,"Native PG terminal binding initialized CEE infrastructure during startup");
        h.assertTrue(bound!=null&&identitySaved&&modelsUntouched,"Binding lost identity or prepared electrical models early");
        h.startSequence().thenIdle(10).thenExecute(()->{
            var reading=ElectricalMeasurements.readVoltageBetween(h.getLevel(),source,0,source,1);
            h.assertTrue(reading.status()==MeasurementStatus.VALID,"Source did not prepare normally after startup: "+reading);
            BoardComponentGameTests.near(h,reading.voltage().orElseThrow().meanVoltageVolts(),20,.05,"Startup source voltage");
            h.assertTrue(ApiTerminals.existing(h.getLevel(),source).preparedTerminal(0)==bound,"Startup preparation replaced the bound native node");
            h.getLevel().setBlockAndUpdate(source,Blocks.AIR.defaultBlockState());
        }).thenIdle(2).thenExecute(()->DynamicGameTests.audit(h)).thenSucceed();
    }
}
