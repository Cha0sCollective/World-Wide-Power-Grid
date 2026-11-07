package org.cha0scollective.wwpg.bridge;

/** PG's force law evaluated without changing the live rotor or clutch readouts. */
public final class RotorStep {
    private RotorStep() {}
    public record Drive(double target, double maximumForce) {}
    public record Result(double speed, double netForce, double lastPower, double[] loads) {}

    public static Result evaluate(double speed, double oldSpeed, double inertia, double friction,
                                  double kp, double kd, double dt, double electricalForce,
                                  Drive[] drives) {
        return evaluate(speed, oldSpeed, inertia, friction, kp, kd, dt, electricalForce, drives, true);
    }

    public static Result evaluate(double speed, double oldSpeed, double inertia, double friction,
                                  double kp, double kd, double dt, double electricalForce,
                                  Drive[] drives, boolean firstStep) {
        return evaluate(speed, speed, oldSpeed, inertia, friction, kp, kd, dt, electricalForce, drives, firstStep);
    }

    /** The clutch samples rotor speed once per game tick; physics can advance more often. */
    public static Result evaluate(double speed, double controlSpeed, double oldSpeed,
                                  double inertia, double friction, double kp, double kd,
                                  double dt, double electricalForce, Drive[] drives, boolean firstStep) {
        if (!(inertia > 0) || !(dt > 0)) throw new IllegalArgumentException("Invalid rotor timestep or inertia");
        if (!Double.isFinite(controlSpeed)) throw new IllegalStateException("Nonfinite clutch speed sample");
        double appliedFriction = Math.min(Math.abs(speed) * inertia / dt, friction);
        double force = electricalForce - Math.signum(speed) * appliedFriction;
        double next = speed + force * dt / inertia;
        // PG snaps a nearly stopped rotor before applying clutch drive, once
        // per game tick. Repeating this on every microstep would erase a weak
        // drive's acceleration; moving it after the drive would erase it too.
        if (firstStep && Math.abs(next) < .01) next = 0;
        // Retain PG's full-tick prediction and sequential clutch response.
        // Using the shorter physical dt here changes near-target force even
        // when gains and force ceilings stay the same. Re-sampling the moving
        // rotor every substep would also change PG's transient response.
        double controlFriction = Math.min(Math.abs(controlSpeed) * 20 * inertia, friction);
        double predicted = controlSpeed + (electricalForce - Math.signum(controlSpeed) * controlFriction) / 20 / inertia;
        if (Math.abs(predicted) < .01) predicted = 0;
        double power = 0;
        var loads = new double[drives.length];
        for (int i = 0; i < drives.length; i++) {
            var drive = drives[i];
            double error = drive.target - predicted;
            if (drive.target < 0) error = -error;
            error = Math.max(0, error);
            // PG's coefficients describe its original 20 Hz controller force.
            // Changing integration dt must not multiply available force by N.
            double requested = (kp * error + kd * (oldSpeed - predicted)) * 20 * inertia;
            double supplied = Math.min(Math.abs(requested), drive.maximumForce) * Math.signum(drive.target);
            predicted += supplied / 20 / inertia;
            next += supplied * dt / inertia;
            loads[i] = drive.maximumForce == 0 ? 0 : Math.abs(supplied / drive.maximumForce);
            force += supplied;
            power = supplied * next * Math.PI / 30;
        }
        if (!Double.isFinite(next) || !Double.isFinite(force) || !Double.isFinite(power))
            throw new IllegalStateException("Nonfinite coupled rotor result");
        return new Result(next, force, power, loads);
    }
}
