package org.cha0scollective.wwpg.bridge;

/** Last accepted wire hazard, used only to keep contact detection through brief solver warm-up. */
public interface ContactHazardHistory {
    void wwpg$acceptedHazard(double voltage, long tick);
    double wwpg$initializingHazard(long tick);
    void wwpg$clearHazard();
}
