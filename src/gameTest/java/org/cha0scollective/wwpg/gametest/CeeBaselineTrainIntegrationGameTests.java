package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;

/** Preserves the baseline train fixture's 96 x 24 x 96 structure and isolated batch. */
@GameTestHolder("wwpg_cee_baseline_train")
@PrefixGameTestTemplate(false)
public final class CeeBaselineTrainIntegrationGameTests {
    @BeforeBatch(batch="wwpg_cee_baseline_train")
    public static void configure(ServerLevel level){TrainCircuitGameTests.configure(level);}
    @GameTest(template="empty",batch="wwpg_cee_baseline_train",timeoutTicks=300)
    public static void trainHandoverGaugesAndRestoration(GameTestHelper h){TrainCircuitGameTests.transformerFedMovingTrainKeepsContactAndRecoversWithoutForeignCommits(h);}
}
