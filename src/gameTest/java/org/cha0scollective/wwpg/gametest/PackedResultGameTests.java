package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Focused powered history/publication checks, with per-contribution bit verification. */
@GameTestHolder("wwpg_packed_results")
@PrefixGameTestTemplate(false)
public final class PackedResultGameTests {
    @BeforeBatch(batch = "wwpg_packed_results")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }
    @GameTest(template="empty", batch="wwpg_packed_results", timeoutTicks=100)
    public static void reactiveWindingCurrents(GameTestHelper h) { DynamicGameTests.reactiveAcTransformerKeepsWindingCurrent(h); }
    @GameTest(template="empty", batch="wwpg_packed_results", timeoutTicks=80)
    public static void capacitorHistory(GameTestHelper h) { DynamicGameTests.capacitorChargesOnceAndDischarges(h); }
    @GameTest(template="empty", batch="wwpg_packed_results", timeoutTicks=100)
    public static void inductorHistory(GameTestHelper h) { DynamicGameTests.inductorAdvancesOnceIntoPgResistor(h); }
    @GameTest(template="empty", batch="wwpg_packed_results", timeoutTicks=110)
    public static void nonfiniteRecovery(GameTestHelper h) { IsolationGameTests.nonfinitePropertyIsolatesAndRecoversWithoutStalePower(h); }
    @GameTest(template="empty", batch="wwpg_packed_results", timeoutTicks=110)
    public static void customPropertyRecovery(GameTestHelper h) { IsolationGameTests.unknownPropertyIsolatesAndRecoversWithoutStalePower(h); }
    @GameTest(template="empty", batch="wwpg_packed_results", timeoutTicks=90)
    public static void topologyEdits(GameTestHelper h) { TopologyGameTests.mixedBranchesLoopCutReconnectAndRemoval(h); }
    @GameTest(template="empty", batch="wwpg_packed_results", timeoutTicks=160)
    public static void terminalOwnerEdits(GameTestHelper h) { ExtensionApiTerminalRefreshGameTests.unchangedFacadeAndLiveTerminalEditsPreservePoweredOwnership(h); }
    @GameTest(template="empty", batch="wwpg_packed_results", timeoutTicks=100)
    public static void rejectedSolve(GameTestHelper h) { ExtensionApiRejectedSolveGameTests.nonthrowingRejectedSolveRollsBackCeeAndApiButKeepsHealthyCircuit(h); }
}
