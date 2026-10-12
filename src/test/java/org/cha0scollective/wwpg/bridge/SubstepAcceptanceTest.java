package org.cha0scollective.wwpg.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubstepAcceptanceTest {
    @Test void convergedWarmupRemainsUnavailableEvenAtItsLastStep() {
        assertTrue(SubstepAcceptance.settling(3,2,false));
        assertTrue(SubstepAcceptance.settling(1,0,false));
        assertFalse(SubstepAcceptance.settling(0,0,true));
    }
    @Test void failedOrUnknownConvergenceCannotBeClassifiedAsWarmup() {
        assertFalse(SubstepAcceptance.settling(3,3,false));
        assertFalse(SubstepAcceptance.settling(0,0,false));
        assertFalse(SubstepAcceptance.settling(-1,-1,false));
        assertFalse(SubstepAcceptance.settling(3,1,false));
    }
}
