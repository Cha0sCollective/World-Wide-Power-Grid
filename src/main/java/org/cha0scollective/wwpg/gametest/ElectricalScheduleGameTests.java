package org.cha0scollective.wwpg.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.config.CSolver;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.CurrentSourceWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.special.SplitTransformerControllerWire;

/** Native controller equations, without replacing the split transformer's model. */
@GameTestHolder("wwpg_distribution")
@PrefixGameTestTemplate(false)
public final class ElectricalScheduleGameTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void splitControllerPreservesPolarityAndStopsAtOneTwoAndSixteenSubsteps(GameTestHelper h) {
        for (int steps : new int[]{1, 2, 16}) {
            var primary = new ElectricalNetwork(false);
            var secondary = new ElectricalNetwork(false);
            var backend = CSolver.SolverBackend.valueOf(System.getProperty("wwpg.test.backend", "NATIVE"));
            primary.switchBackend(backend); secondary.switchBackend(backend);
            primary.warmUp(-1); secondary.warmUp(-1);
            try {
                var p = new FloatingNode(); var p0 = new FloatingNode();
                var s = new FloatingNode(); var s0 = new FloatingNode();
                primary.addNodes(p, p0); secondary.addNodes(s, s0);
                primary.addWire(new ElectricWire(.005, p0, null));
                secondary.addWire(new ElectricWire(.005, s0, null));
                var source = new CurrentSourceWire(p, p0, 1000);
                source.setCurrent(40000);
                primary.addWire(source);
                primary.addWire(new ElectricWire(1000, p, p0));
                secondary.addWire(new ElectricWire(1000, s, s0));
                var first = new SplitTransformerControllerWire(p, p0, .0075, 75);
                var second = new SplitTransformerControllerWire(s, s0, .0075, 75);
                first.secondary = second; second.secondary = first;
                primary.addWire(first); secondary.addWire(second);
                for (int tick = 0; tick < 120; tick++) tick(h, primary, secondary, steps);
                near(h, s.getVoltage()-s0.getVoltage(), 40, .04, "Native split-controller powered voltage");
                source.setCurrent(-40000);
                for (int tick = 0; tick < 120; tick++) tick(h, primary, secondary, steps);
                near(h, s.getVoltage()-s0.getVoltage(), -40, .04, "Native split-controller polarity");
                source.setCurrent(0);
                for (int tick = 0; tick < 90; tick++) {
                    tick(h, primary, secondary, steps);
                    h.assertTrue(Math.abs(s.getVoltage()-s0.getVoltage()) <= 40.04,
                            "Split-controller shutdown amplified stored samples at " + steps + " substeps");
                }
                near(h, s.getVoltage()-s0.getVoltage(), 0, .001, "Native split-controller shutdown");
                h.assertTrue(first.secondary == second && second.secondary == first,
                        "Parameter changes replaced native controller identities");
            } finally {
                primary.cleanup(); secondary.cleanup();
            }
        }
        DynamicGameTests.audit(h);
        h.succeed();
    }
    private static void tick(GameTestHelper h, ElectricalNetwork a, ElectricalNetwork b, int steps) {
        a.prepare(steps); b.prepare(steps);
        int first = a.getStamp(), second = b.getStamp();
        for (int i = 0; i < steps; i++) { a.singleTick(); b.singleTick(); }
        h.assertTrue(a.getStamp() - first == steps && b.getStamp() - second == steps,
                "Cross-dimension controller advanced more than once per substep");
    }
    private static void near(GameTestHelper h, double actual, double expected, double tolerance, String message) {
        BoardComponentGameTests.near(h, actual, expected, tolerance, message);
    }
}
