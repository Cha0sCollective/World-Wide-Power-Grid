package org.cha0scollective.wwpg.bridge;

/** A finite allowance for unaccepted upstream initialization work, never an accepted sample. */
public final class InitializationWindow {
    public static final int MAX_REJECTED_STEPS=16;
    private int rejected;
    public boolean hold() { if(rejected>=MAX_REJECTED_STEPS)return false; ++rejected;return true; }
}
