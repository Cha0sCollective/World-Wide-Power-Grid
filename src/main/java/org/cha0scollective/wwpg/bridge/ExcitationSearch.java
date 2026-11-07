package org.cha0scollective.wwpg.bridge;

import java.util.function.DoubleUnaryOperator;

/** A load-line search for the only nonlinear source in a recognized network. */
public final class ExcitationSearch {
    private boolean initialHook;
    private int trialIteration;
    private double voltage, current, currentSlope;
    private double[] currents = new double[0], slopes = new double[0];
    private boolean responseReady;

    public ExcitationSearch() { reset(0); }

    public void reset(int fields) {
        initialHook = true;
        trialIteration = -1;
        responseReady = false;
        if (currents.length != fields) { currents = new double[fields]; slopes = new double[fields]; }
    }

    public void observe(int iteration, double trialVoltage, double trialCurrent, double[] fieldCurrents) {
        if (initialHook) {
            initialHook = false;
        } else if (iteration != trialIteration) {
            // The first hook observes the old state; later calls in the same
            // iteration are backtracking blends. Neither necessarily satisfies
            // a variac's newly changed linear constraints. Compare only the
            // first full electrical solution of successive Newton iterations.
            double delta = trialVoltage - voltage;
            if (trialIteration >= 0 && Math.abs(delta) > 1e-10 * Math.max(1, Math.abs(trialVoltage))) {
                currentSlope = (trialCurrent - current) / delta;
                for (int i = 0; i < slopes.length; i++) slopes[i] = (fieldCurrents[i] - currents[i]) / delta;
                responseReady = true;
            }
            trialIteration = iteration;
            voltage = trialVoltage;
            current = trialCurrent;
            System.arraycopy(fieldCurrents, 0, currents, 0, currents.length);
        }
    }

    public double currentAt(double trialVoltage) { return current + currentSlope * (trialVoltage - voltage); }
    public double fieldCurrentAt(int field, double trialVoltage) { return currents[field] + slopes[field] * (trialVoltage - voltage); }

    public double derivative(double trialVoltage, double resistance, DoubleUnaryOperator emf) {
        if (!responseReady) return 0;
        double passiveSlope = 1 - resistance * currentSlope;
        if (!(passiveSlope > 0) || !Double.isFinite(passiveSlope)) return 0;
        DoubleUnaryOperator residual = v -> v - resistance * currentAt(v) - emf.applyAsDouble(v);
        double start = residual.applyAsDouble(trialVoltage);
        if (!Double.isFinite(start) || Math.abs(start) < 1e-9) return 0;

        // During field build-up the residual can initially grow in the
        // physical direction. Local Newton/backtracking then creeps uphill
        // until its ordinary iteration limit. Bracket the load-line crossing
        // first; the normal solver still checks the complete source equation.
        double direction = -Math.signum(start);
        double distance = Math.max(1, Math.abs(trialVoltage) * .05);
        double end = trialVoltage, endResidual = start;
        for (int i = 0; i < 48; i++, distance *= 2) {
            end = trialVoltage + direction * distance;
            endResidual = residual.applyAsDouble(end);
            if (!Double.isFinite(endResidual)) return 0;
            if (Math.signum(endResidual) != Math.signum(start)) break;
        }
        if (Math.signum(endResidual) == Math.signum(start)) return 0;
        double near = trialVoltage;
        for (int i = 0; i < 80 && Math.abs(endResidual) > 1e-9; i++) {
            double middle = near + (end - near) * .5;
            if (middle == near || middle == end) break;
            double value = residual.applyAsDouble(middle);
            if (!Double.isFinite(value)) return 0;
            if (Math.signum(value) == Math.signum(start)) near = middle;
            else { end = middle; endResidual = value; }
        }
        // A sign discontinuity is not a root, including native field reversal
        // limiting. Never direct the solver toward a merely bracketed jump.
        if (Math.abs(endResidual) > 1e-7) return 0;
        double delta = end - trialVoltage;
        double result = (emf.applyAsDouble(end) - emf.applyAsDouble(trialVoltage)) / delta;
        return Double.isFinite(result) ? result : 0;
    }
}
