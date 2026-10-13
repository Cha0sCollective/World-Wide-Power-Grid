package org.cha0scollective.wwpg.bridge;

/** A numerically converged warm-up step remains unavailable without being a solver fault. */
public final class SubstepAcceptance {
    private SubstepAcceptance() { }
    public static boolean settling(int before, int after, boolean accepted) {
        return !accepted && before > 0 && after == before - 1;
    }
}
