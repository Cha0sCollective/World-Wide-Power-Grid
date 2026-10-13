package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.RestorationCoordinator;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;

/** Run separately from loading fixtures: clear deliberately exercises global teardown. */
@GameTestHolder("wwpg_pause_epoch")
@PrefixGameTestTemplate(false)
public final class RestorationPauseEpochGameTests {
    @GameTest(template = "empty", timeoutTicks = 20, batch = "pause_epoch")
    public static void networkMarksExpireAcrossTicksAndTeardown(GameTestHelper h) throws ReflectiveOperationException {
        var first = new ElectricalNetwork(false);
        var second = new ElectricalNetwork(false);
        h.assertTrue(!RestorationCoordinator.paused(first) && !RestorationCoordinator.paused(second),
                "A fresh network inherited a restoration pause");
        RestorationCoordinator.begin(h.getLevel());
        mark(first);
        h.assertTrue(RestorationCoordinator.paused(first) && !RestorationCoordinator.paused(second),
                "One circuit's pause affected an unrelated network");
        RestorationCoordinator.begin(h.getLevel());
        h.assertTrue(RestorationCoordinator.paused(first), "Same-tick preparation expired a pause");
        int markedTick = h.getLevel().getServer().getTickCount();
        h.runAfterDelay(2, () -> {
            try {
                h.assertTrue(h.getLevel().getServer().getTickCount() != markedTick, "Expiry fixture did not reach a new server tick");
                // These standalone networks do not participate in the world's
                // electrical loop; drive its preparation boundary explicitly.
                RestorationCoordinator.begin(h.getLevel());
                h.assertTrue(!RestorationCoordinator.paused(first), "Previous tick's mark remained active");
                mark(first);
                var couple = RestorationCoordinator.class.getDeclaredMethod("couple", ElectricalNetwork.class, ElectricalNetwork.class);
                couple.setAccessible(true); couple.invoke(null, first, second);
                h.assertTrue(RestorationCoordinator.paused(first) && RestorationCoordinator.paused(second),
                        "Coupled peers did not share their restoration pause");
                RestorationCoordinator.clear();
                h.assertTrue(!RestorationCoordinator.paused(first) && !RestorationCoordinator.paused(second),
                        "Teardown retained a pause on surviving native objects");
                mark(second);
                h.assertTrue(!RestorationCoordinator.paused(first) && RestorationCoordinator.paused(second),
                        "New epoch resurrected an old mark");
                RestorationCoordinator.clear();
                h.succeed();
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            finally { first.cleanup(); second.cleanup(); }
        });
    }
    private static void mark(ElectricalNetwork network) throws ReflectiveOperationException {
        var mark = RestorationCoordinator.class.getDeclaredMethod("markPaused", ElectricalNetwork.class);
        mark.setAccessible(true); mark.invoke(null, network);
    }
}
