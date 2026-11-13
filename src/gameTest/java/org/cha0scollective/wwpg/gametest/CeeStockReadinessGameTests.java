package org.cha0scollective.wwpg.gametest;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;
/** Only the version-matched CEE-only graphs run with the bridge-free control. */
@GameTestHolder("wwpg_cee_stock")
@PrefixGameTestTemplate(false)
public final class CeeStockReadinessGameTests {
    @BeforeBatch(batch="wwpg_cee_stock")public static void configure(ServerLevel level){CeeReadinessGameTests.configure(level);}
    @GameTest(template="empty",batch="wwpg_cee_stock",timeoutTicks=80)public static void signedNorton(GameTestHelper h){CeeReadinessGameTests.signedNortonCurrentAndPower(h);}
    @GameTest(template="empty",batch="wwpg_cee_stock",timeoutTicks=80)public static void capacitor(GameTestHelper h){CeeReadinessGameTests.capacitorVoltageCurrentHistoryAndDischarge(h);}
    @GameTest(template="empty",batch="wwpg_cee_stock",timeoutTicks=80)public static void diode(GameTestHelper h){CeeReadinessGameTests.nonlinearDiodeForwardReverseAndRecovery(h);}
    @GameTest(template="empty",batch="wwpg_cee_stock",timeoutTicks=100)public static void rectifier(GameTestHelper h){CeeReadinessGameTests.rectifierCapacitorTransientAndReverseHold(h);}
}
