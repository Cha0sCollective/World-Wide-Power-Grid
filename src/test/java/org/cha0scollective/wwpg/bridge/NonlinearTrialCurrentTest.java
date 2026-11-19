package org.cha0scollective.wwpg.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NonlinearTrialCurrentTest {
    @Test void diodeOverflowIsARejectableTrialWithFiniteSquaredMerit() {
        double forward=1e-12*Math.expm1(70/.02585);
        assertEquals(Double.POSITIVE_INFINITY,forward);
        double trial=NonlinearTrialCurrent.residual(forward);
        assertTrue(trial>1e30&&Double.isFinite(trial*trial));
        assertEquals(-trial,NonlinearTrialCurrent.residual(-forward));
        assertThrows(IllegalArgumentException.class,()->NonlinearTrialCurrent.accepted(forward));
    }
    @Test void AcceptedPhysicalCurrentsUseTheExactShockleyValue() {
        for(double voltage:new double[]{-70,-.1,0,.1,.5,.65,.8}) {
            double exact=1e-12*Math.expm1(voltage/.02585);
            assertEquals(exact,NonlinearTrialCurrent.residual(exact));
            assertEquals(exact,NonlinearTrialCurrent.accepted(exact));
        }
    }
    @Test void LimitedOrInvalidValuesCannotBecomeAcceptedMeasurements() {
        assertTrue(Double.isFinite(NonlinearTrialCurrent.residual(1e200)));
        assertThrows(IllegalArgumentException.class,()->NonlinearTrialCurrent.accepted(1e200));
        assertThrows(IllegalArgumentException.class,()->NonlinearTrialCurrent.residual(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->NonlinearTrialCurrent.accepted(Double.NaN));
    }
}
