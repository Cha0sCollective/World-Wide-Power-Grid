package org.cha0scollective.wwpg.bridge;

/** Keeps contact detection active through a brief solver initialization. */
public interface ContactHazardHistory {
    void wwpg$recordHazard(double voltage, long tick);
    double wwpg$initializingHazard(long tick);
    void wwpg$clearHazard();
}
