package org.cha0scollective.wwpg.compat.cee;

/** Trial-state operations only. Physical history is committed by the substep lifecycle. */
public interface CeeNonlinear {
    void iterate(double first, double second, boolean firstIteration);
    double current(double voltage);
    double gMin();
    Runnable checkpoint();
}
