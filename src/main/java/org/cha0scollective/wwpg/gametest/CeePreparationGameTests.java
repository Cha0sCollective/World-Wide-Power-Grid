package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused selection of existing CEE lifecycle/electrical fixtures. */
@GameTestHolder("wwpg_cee_preparation")
@PrefixGameTestTemplate(false)
public final class CeePreparationGameTests {
    @BeforeBatch(batch = "wwpg_cee_preparation")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template="empty", batch="wwpg_cee_preparation", timeoutTicks=80)
    public static void transformerRatioChanges(GameTestHelper h) { DynamicGameTests.transformerRatioChanges(h); }
    @GameTest(template="empty", batch="wwpg_cee_preparation", timeoutTicks=100)
    public static void reactiveTransformerCurrents(GameTestHelper h) { DynamicGameTests.reactiveAcTransformerKeepsWindingCurrent(h); }
    @GameTest(template="empty", batch="wwpg_cee_preparation", timeoutTicks=80)
    public static void capacitorHistory(GameTestHelper h) { DynamicGameTests.capacitorChargesOnceAndDischarges(h); }
    @GameTest(template="empty", batch="wwpg_cee_preparation", timeoutTicks=80)
    public static void highVoltageCapacitorHistory(GameTestHelper h) { DynamicGameTests.highVoltageCapacitorChargesOnceAndDischarges(h); }
    @GameTest(template="empty", batch="wwpg_cee_preparation", timeoutTicks=100)
    public static void inductorHistory(GameTestHelper h) { DynamicGameTests.inductorAdvancesOnceIntoPgResistor(h); }
    @GameTest(template="empty", batch="wwpg_cee_preparation", timeoutTicks=90)
    public static void accumulatorHistory(GameTestHelper h) { DynamicGameTests.accumulatorChargesFromPgAndPowersPgLoad(h); }
    @GameTest(template="empty", batch="wwpg_cee_preparation", timeoutTicks=100)
    public static void physicalGroundChanges(GameTestHelper h) { GroundingChangeGameTests.physicalGroundChangesKeepExistingTopology(h); }
    @GameTest(template="empty", batch="wwpg_cee_preparation", timeoutTicks=100)
    public static void referenceAndGroundTakeover(GameTestHelper h) { GroundingChangeGameTests.preferredReferenceChangesAndPhysicalGroundTakeover(h); }
    @GameTest(template="empty", batch="wwpg_cee_preparation", timeoutTicks=1100)
    public static void groundUnloadReload(GameTestHelper h) { GroundingChangeGameTests.groundRodChunkUnloadReloadUpdatesExistingCircuit(h); }
    @GameTest(template="empty", batch="wwpg_cee_preparation", timeoutTicks=90)
    public static void loopCutReplacementAndReconnect(GameTestHelper h) { TopologyGameTests.mixedBranchesLoopCutReconnectAndRemoval(h); }
    @GameTest(template="empty", batch="wwpg_cee_preparation", timeoutTicks=110)
    public static void unsupportedPropertyRecovery(GameTestHelper h) { IsolationGameTests.unknownPropertyIsolatesAndRecoversWithoutStalePower(h); }
    @GameTest(template="empty", batch="wwpg_cee_preparation", timeoutTicks=110)
    public static void nonfinitePropertyRecovery(GameTestHelper h) { IsolationGameTests.nonfinitePropertyIsolatesAndRecoversWithoutStalePower(h); }
}
