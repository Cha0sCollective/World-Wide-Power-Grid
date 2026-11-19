package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;

/** Selected 0.2.10 baseline regressions under either CEE binary contract. */
@GameTestHolder("wwpg_cee_baseline")
@PrefixGameTestTemplate(false)
public final class CeeBaselineIntegrationGameTests {
    @BeforeBatch(batch="wwpg_cee_baseline")
    public static void configure(ServerLevel level){DynamicGameTests.substeps(level);}
    @GameTest(template="empty",batch="wwpg_cee_baseline",timeoutTicks=420)
    public static void unloadedAlternator(GameTestHelper h){AlternatorUnloadGameTests.unloadedAlternatorPowersLiveLoad(h);}
    @GameTest(template="empty",batch="wwpg_cee_baseline",timeoutTicks=420)
    public static void unloadedThreePhaseAlternator(GameTestHelper h){AlternatorUnloadGameTests.unloadedThreePhaseAlternatorPowersLiveLoads(h);}
    @GameTest(template="empty",batch="wwpg_cee_baseline",timeoutTicks=240)
    public static void motorReloadKeepsFans(GameTestHelper h){MotorRestorationGameTests.poweredMotorReloadKeepsAttachedFans(h);}
    @GameTest(template="empty",batch="wwpg_cee_baseline",timeoutTicks=100)
    public static void externallyDrivenMotor(GameTestHelper h){MotorRestorationGameTests.restorationKeepsExternallyDrivenMotorTurning(h);}
    @GameTest(template="empty",batch="wwpg_cee_baseline",timeoutTicks=20)
    public static void nativeGeneratorIntegration(GameTestHelper h){GeneratorTimingGameTests.directAndCustomRotorsKeepNativeIntegration(h);}
    @GameTest(template="empty",batch="wwpg_cee_baseline",timeoutTicks=60)
    public static void wireResultOwnership(GameTestHelper h){WireResultOwnershipGameTests.healthyNativeOrderAndRejectedEndpointsAndCutsUseTheActualSelector(h);}
}
