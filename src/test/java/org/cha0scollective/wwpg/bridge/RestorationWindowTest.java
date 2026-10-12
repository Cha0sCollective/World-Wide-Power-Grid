package org.cha0scollective.wwpg.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RestorationWindowTest {
    @Test void continuousArrivalsCannotExtendTwentyTickBoundary() {
        var window = new RestorationWindow(); window.touch(0);
        for (int tick = 1; tick < 20; tick++) { window.touch(tick); assertTrue(window.hold(tick, false)); }
        window.touch(20); assertFalse(window.hold(20, false));
    }
    @Test void readyCircuitClosesAfterTwoQuietTicks() {
        var window = new RestorationWindow(); window.touch(17);
        assertTrue(window.hold(18, true)); assertFalse(window.hold(19, true));
    }
    @Test void mergingWindowsKeepsEarliestDeadlineAndLatestArrival() {
        var first = new RestorationWindow(); first.touch(0);
        var second = new RestorationWindow(); second.touch(18); first.absorb(second);
        assertTrue(first.hold(19, true)); assertFalse(first.hold(20, false));
    }
}
