package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.patryk3211.powergrid.electricity.modulardisplay.ModularDisplayBlockEntity;
import org.patryk3211.powergrid.electricity.modulardisplay.modules.ZeroToNineNumberModule;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.Set;
import java.util.function.Consumer;

/** A fixture-owned real pulse, independent of the cyclic display's starting digit. */
final class DisplayPulseCheck {
    private enum Phase { POWERED, OFF, ON, VERIFIED }

    private final GameTestHelper h;
    private final BlockPos display, control;
    private final Consumer<Boolean> feed;
    private Phase phase = Phase.POWERED;
    private long lastTick = Long.MIN_VALUE;
    private int stableTicks, previousIndex = -1, offIndex = -1, expectedIndex = -1;
    private final String restoredModule;

    DisplayPulseCheck(GameTestHelper h, BlockPos display, BlockPos control,
                      Consumer<Boolean> feed, boolean restore) {
        this.h = h; this.display = display; this.control = control; this.feed = feed;
        // Observe a restored module before this fixture operates its feed. Zero is
        // a valid persisted reel position, including at its blank/reset boundary.
        restoredModule = restore ? module().serialize() : null;
        if (restore) WorldWidePowerGrid.LOGGER.info("WWPG_DISPLAY_RESTORED: pos={}; module={}", display, restoredModule);
    }

    void await() {
        h.assertTrue(poll(), "Waiting for a real display OFF/ON pulse: " + phase);
    }

    boolean poll() {
        long tick = h.getLevel().getGameTime();
        if (tick == lastTick || phase == Phase.VERIFIED) return phase == Phase.VERIFIED;
        lastTick = tick;
        var sample = observe();
        if (!sample.available) { stableTicks = 0; return false; }
        switch (phase) {
            case POWERED -> {
                if (!sample.powered() || !sample.halfClick) { stableTicks = 0; return false; }
                if (++stableTicks < 2) return false;
                feed.accept(false);
                phase = Phase.OFF; stableTicks = 0; previousIndex = -1;
            }
            case OFF -> {
                if (!sample.off() || sample.halfClick) { stableTicks = 0; return false; }
                stableTicks = sample.index == previousIndex ? stableTicks + 1 : 1;
                previousIndex = sample.index;
                if (stableTicks < 3) return false;
                // PG settles its repeated-zero/transition frames (11 and 12)
                // back to zero while OFF. Its blank frame (10) remains armed
                // through the native reset winding and advances to 11 on ON.
                h.assertTrue(sample.index <= 10, "Display did not settle its native reset frame while OFF");
                offIndex = sample.index; expectedIndex = offIndex + 1;
                WorldWidePowerGrid.LOGGER.info("WWPG_DISPLAY_PULSE_OFF: pos={}; restored={}; index={}; voltage={}; current={}; halfClick={}",
                        display, restoredModule, offIndex, sample.voltage, sample.current, sample.halfClick);
                feed.accept(true);
                phase = Phase.ON; stableTicks = 0;
            }
            case ON -> {
                if (!sample.powered() || !sample.halfClick || sample.index != expectedIndex) {
                    stableTicks = 0; return false;
                }
                if (++stableTicks < 2) return false;
                phase = Phase.VERIFIED;
                WorldWidePowerGrid.LOGGER.info("WWPG_DISPLAY_PULSE_PASSED: pos={}; restored={}; before={}; after={}; voltage={}; current={}; halfClick={}",
                        display, restoredModule, offIndex, sample.index, sample.voltage, sample.current, sample.halfClick);
            }
            case VERIFIED -> { }
        }
        return phase == Phase.VERIFIED;
    }

    void verify() {
        var sample = observe();
        h.assertTrue(phase == Phase.VERIFIED && sample.available && sample.powered()
                        && sample.halfClick && sample.index == expectedIndex,
                "Display lacks a verified accepted OFF/ON transition: phase=" + phase + ", observation=" + sample);
    }

    int settledOffIndex() { return offIndex; }
    boolean verified() { return phase == Phase.VERIFIED; }

    private ModularDisplayBlockEntity entity() {
        var entity = h.getLevel().getBlockEntity(display);
        h.assertTrue(entity instanceof ModularDisplayBlockEntity, "Saved native modular display is missing");
        return (ModularDisplayBlockEntity) entity;
    }

    private ZeroToNineNumberModule module() {
        var module = entity().modules[0];
        h.assertTrue(module instanceof ZeroToNineNumberModule, "Saved native zero-to-nine module is missing or changed");
        h.assertTrue(module.getIndex() >= 0 && module.getIndex() <= 12,
                "Saved native display index lies outside its digit/blank/transition frames");
        return (ZeroToNineNumberModule) module;
    }

    private Observation observe() {
        var module = module();
        var voltage = ElectricalMeasurements.readVoltageBetween(h.getLevel(), display, 0, display, 8);
        if (voltage.status() != MeasurementStatus.VALID)
            return new Observation(false, module.getIndex(), module.getHalfClick(), Double.NaN, Double.NaN);
        var endpoint = new BlockWireEndpoint(display, 0);
        var wires = entity().getElectricBehaviour().getConnections().getOrDefault(endpoint, Set.of()).stream()
                .filter(wire -> !wire.isRemoved() && wire.isConnectedTo(control, 1)).toList();
        h.assertTrue(wires.size() == 1, "Display pulse fixture requires exactly one native feed wire");
        double current = Math.abs(wires.getFirst().current());
        return new Observation(Double.isFinite(current), module.getIndex(), module.getHalfClick(),
                voltage.voltage().orElseThrow().meanVoltageVolts(), current);
    }

    private record Observation(boolean available, int index, boolean halfClick, double voltage, double current) {
        // Native copper and the feed switch have real losses. The 25-ohm
        // coil needs at least .5 A; an ideal 24 V at its terminals is not required.
        boolean powered() { return voltage >= 12.5 && voltage <= 24.03 && current >= .5; }
        boolean off() { return Math.abs(voltage) < .001 && current < .001; }
    }
}
