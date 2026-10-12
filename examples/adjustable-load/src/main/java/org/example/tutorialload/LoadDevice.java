package org.example.tutorialload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.cha0scollective.wwpg.api.*;
import org.cha0scollective.wwpg.api.neoforge.ElectricalDevices;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** WWPG integration and native saved settings. No PG or CEE implementation imports. */
public final class LoadDevice extends BlockEntity implements ElectricalDevice {
    private static final double[] RESISTANCES = {100, 200, 400};
    private static final List<Terminal> TERMINALS = List.of(
            new Terminal("positive", 0, new LocalPoint(.25, .85, 0), .12),
            new Terminal("negative", 1, new LocalPoint(.75, .85, 0), .12));
    private boolean enabled = true;
    private int resistanceIndex;
    private String reading = "unavailable: waiting for electrical results";

    public LoadDevice(BlockPos pos, BlockState state) { super(TutorialLoadMod.LOAD_TYPE.get(), pos, state); }
    @Override public String providerId() { return TutorialLoadMod.MOD_ID + ":load"; }
    @Override public List<Terminal> terminals() { return TERMINALS; }

    @Override public CircuitDescription prepare(TickContext context) {
        // The off state keeps the same terminals, internal node and element IDs.
        return new CircuitDescription(TERMINALS, List.of(new InternalNode("load_in")), List.of(
                new Element("enable", "positive", "load_in", new ElectricalModel.Switch(enabled, .01)),
                new Element("load", "load_in", "negative", new ElectricalModel.Resistor(resistanceOhms()))), Set.of());
    }

    @Override public void commit(DeviceResults results) {
        boolean lit = false;
        if (results.status() == MeasurementStatus.VALID) {
            var load = results.element("load").orElseThrow();
            double volts = results.node("positive").orElseThrow().lastVoltageVolts()
                    - results.node("negative").orElseThrow().lastVoltageVolts();
            reading = String.format(Locale.ROOT, "%.3f V / %.3f A / %.3f W", volts, load.meanCurrentAmps(), load.meanPowerWatts());
            lit = load.meanPowerWatts() > .01;
        } else {
            reading = "unavailable: " + results.status() + " — " + results.detail();
            // Do not change enabled/resistance, or overwrite any saved storage history.
        }
        if (level instanceof ServerLevel && getBlockState().getValue(LoadBlock.LIT) != lit)
            level.setBlock(worldPosition, getBlockState().setValue(LoadBlock.LIT, lit), 3);
    }

    public boolean enabled() { return enabled; }
    public double resistanceOhms() { return RESISTANCES[resistanceIndex]; }
    public String reading() { return reading; }
    public String settings() { return (enabled ? "ON" : "OFF") + " / " + (int) resistanceOhms() + " ohms"; }
    public void toggleEnabled() { enabled = !enabled; configurationChanged(); }
    public void cycleResistance() { resistanceIndex = (resistanceIndex + 1) % RESISTANCES.length; configurationChanged(); }
    private void configurationChanged() {
        reading = "unavailable: waiting for next solve after configuration change";
        setChanged();
        if (level instanceof ServerLevel server) {
            if (getBlockState().getValue(LoadBlock.LIT))
                server.setBlock(worldPosition, getBlockState().setValue(LoadBlock.LIT, false), 3);
            ElectricalDevices.changed(server, worldPosition);
        }
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) ElectricalDevices.changed(server, worldPosition);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("TutorialLoadVersion", 1);
        tag.putBoolean("Enabled", enabled);
        tag.putInt("ResistanceIndex", resistanceIndex);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains("TutorialLoadVersion")) return; // A new block keeps its defaults.
        if (tag.getInt("TutorialLoadVersion") != 1) throw new IllegalArgumentException("Unknown tutorial load save format");
        int restored = tag.getInt("ResistanceIndex");
        if (restored < 0 || restored >= RESISTANCES.length) throw new IllegalArgumentException("Invalid saved tutorial resistance index");
        enabled = tag.getBoolean("Enabled");
        resistanceIndex = restored;
    }
}
