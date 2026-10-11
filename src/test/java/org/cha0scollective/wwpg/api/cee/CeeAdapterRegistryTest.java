package org.cha0scollective.wwpg.api.cee;

import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import org.cha0scollective.wwpg.extension.CeeAdapters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CeeAdapterRegistryTest {
    private static class CustomProperty extends ElectricalProperties {
        CustomProperty() { super(1, 0, 0); }
    }
    private static final class DerivedProperty extends CustomProperty { }
    private static final class StoredProperty extends MicroTickingElectricalProperties {
        double savedVoltage = 10;
        int commits;
        @Override public void tick(double[] voltages, int step, int total, int first, int second) {
            fail("the generic native callback is not the registered adapter callback");
        }
    }
    private static final CeePropertyAdapter<CustomProperty> LINEAR = source -> new CeeElectricalParameters(.1, 2, -12, true);

    @Test void exactClassAndNamespacedOwnershipFreezeWithoutAmbiguousFallback() {
        var registry = new CeeAdapters.Registry();
        assertThrows(IllegalStateException.class, registry::newSession);
        registry.register("example:load", CustomProperty.class, LINEAR);
        var duplicate = assertThrows(IllegalArgumentException.class,
                () -> registry.register("other:load", CustomProperty.class, LINEAR));
        assertTrue(duplicate.getMessage().contains("example:load"));
        assertTrue(duplicate.getMessage().contains("other:load"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("example:load", DerivedProperty.class, source -> new CeeElectricalParameters(1, 0, 0, false)));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("unqualified", DerivedProperty.class, source -> new CeeElectricalParameters(1, 0, 0, false)));
        registry.freeze();
        assertThrows(IllegalStateException.class,
                () -> registry.register("example:late", DerivedProperty.class, source -> new CeeElectricalParameters(1, 0, 0, false)));
        var session = registry.newSession();
        assertTrue(session.capture(new CustomProperty()).isPresent());
        assertTrue(session.capture(new DerivedProperty()).isEmpty());
        assertTrue(session.capture(new ElectricalProperties(1, 0, 0)).isEmpty());
    }

    @Test void snapshotsUseValidatedCapturedValuesAndStableInverseOrientation() {
        var registry = new CeeAdapters.Registry();
        registry.register("example:load", CustomProperty.class, LINEAR);
        registry.freeze();
        var source = new CustomProperty();
        var session = registry.newSession();
        var captured = session.capture(source).orElseThrow();
        var parameters = captured.properties();
        source.currentSource = 99;
        source.voltageSource = 200;
        source.resistance = 1000;
        assertEquals(.1, parameters.conductance());
        assertEquals(2, parameters.currentSource());
        assertEquals(-12, parameters.voltageSource());
        assertTrue(parameters.isVoltageSource());
        assertEquals(-2, parameters.invert().currentSource());
        assertEquals(12, parameters.invert().voltageSource());
        assertSame(parameters, parameters.invert().invert());
        assertSame(captured.binding(), session.capture(source).orElseThrow().binding());
        assertTrue(CeeAdapters.isAdapted(parameters));
        assertFalse(CeeAdapters.isAdapted(source));
    }

    @Test void registrationCannotReplaceExistingNativePropertyModels() {
        var registry = new CeeAdapters.Registry();
        var linear = assertThrows(IllegalArgumentException.class,
                () -> registry.register("example:replace_native", ElectricalProperties.class,
                        source -> new CeeElectricalParameters(1, 0, 0, false)));
        assertTrue(linear.getMessage().contains("built-in WWPG adapter"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("example:replace_storage",
                        com.george_vi.electroenergetics.foundation.electrical_properties.CapacitorProperties.class,
                        source -> new CeeElectricalParameters(1, 0, 0, false)));
        registry.register("example:own_storage", StoredProperty.class,
                source -> new CeeElectricalParameters(1, 0, 0, false));
        assertEquals(1, registry.registrations().size());
    }

    @Test void dynamicCommitAdvancesOnlyAcceptedSubstepsOnceAndPreservesSavedState() {
        var adapter = new CeePropertyAdapter<StoredProperty>() {
            @Override public boolean dynamic() { return true; }
            @Override public CeeStateCheckpoint checkpoint(StoredProperty source) {
                double voltage = source.savedVoltage, current = source.currentSource;
                int commits = source.commits;
                return () -> { source.savedVoltage = voltage; source.currentSource = current; source.commits = commits; };
            }
            @Override public CeeElectricalParameters prepare(StoredProperty source) {
                return new CeeElectricalParameters(.02, source.currentSource, 0, false);
            }
            @Override public void beforeSubstep(StoredProperty source, CeeSubstepContext context) {
                source.currentSource = -.02 * source.savedVoltage;
            }
            @Override public void afterSubstep(StoredProperty source, CeeSubstepResult result) {
                source.savedVoltage = result.voltageVolts();
                ++source.commits;
            }
        };
        var registry = new CeeAdapters.Registry();
        registry.register("example:storage", StoredProperty.class, adapter);
        registry.freeze();
        var source = new StoredProperty();
        var session = registry.newSession();
        var binding = session.capture(source).orElseThrow().binding();
        var inverse = session.capture(source.invert()).orElseThrow();
        assertSame(binding, inverse.binding());
        assertTrue(inverse.inverted());
        assertEquals(10, source.savedVoltage);
        assertEquals(0, source.commits);
        binding.before(0, 0, 16, .003125);
        assertEquals(-.2, binding.properties().currentSource(), 1e-12);
        assertThrows(IllegalStateException.class, () -> binding.before(0, 0, 16, .003125));
        binding.after(7, .14, 0, 16, .003125, true);
        assertEquals(7, source.savedVoltage);
        assertEquals(1, source.commits);
        assertThrows(IllegalStateException.class, () -> binding.after(8, .16, 0, 16, .003125, true));
        assertEquals(1, source.commits);
        binding.before(7, 1, 16, .003125);
        assertEquals(-.14, binding.properties().currentSource(), 1e-12);
        binding.after(999, 999, 1, 16, .003125, false);
        assertEquals(7, source.savedVoltage);
        assertEquals(1, source.commits);
        assertTrue(binding.failed());
        assertTrue(binding.failureReason().orElseThrow().contains("rejected"));
        assertEquals(0, binding.properties().conductance());
        assertEquals(0, binding.properties().currentSource());
        binding.before(7, 2, 16, .003125);
        binding.after(1000, 1000, 2, 16, .003125, true);
        assertEquals(1, source.commits);
        binding.finish(false);
        assertEquals(10, source.savedVoltage);
        assertEquals(0, source.commits);
        assertEquals(0, source.currentSource);
        assertThrows(IllegalStateException.class, () -> binding.finish(false));
    }

    @Test void thrownDynamicCommitRollsBackItsMutationAndTheEntireRejectedTick() {
        var registry = new CeeAdapters.Registry();
        registry.register("example:fault", StoredProperty.class, new CeePropertyAdapter<>() {
            @Override public boolean dynamic() { return true; }
            @Override public CeeStateCheckpoint checkpoint(StoredProperty source) {
                double voltage = source.savedVoltage;
                int commits = source.commits;
                return () -> { source.savedVoltage = voltage; source.commits = commits; };
            }
            @Override public CeeElectricalParameters prepare(StoredProperty source) {
                return new CeeElectricalParameters(0, 0, source.savedVoltage, true);
            }
            @Override public void afterSubstep(StoredProperty source, CeeSubstepResult result) {
                source.savedVoltage = result.voltageVolts();
                ++source.commits;
                if (result.substep() == 1) throw new IllegalStateException("failed after mutating owned history");
            }
        });
        registry.freeze();
        var source = new StoredProperty();
        var binding = registry.newSession().capture(source).orElseThrow().binding();
        binding.before(10, 0, 2, .025);
        binding.after(8, 1, 0, 2, .025, true);
        assertEquals(8, source.savedVoltage);
        binding.before(8, 1, 2, .025);
        assertDoesNotThrow(() -> binding.after(6, 1, 1, 2, .025, true));
        assertEquals(8, source.savedVoltage);
        assertEquals(1, source.commits);
        assertTrue(binding.failed());
        assertTrue(binding.failureReason().orElseThrow().contains("mutating owned history"));
        assertTrue(binding.properties().isVoltageSource());
        assertEquals(0, binding.properties().voltageSource());
        binding.finish(true);
        assertEquals(10, source.savedVoltage);
        assertEquals(0, source.commits);
    }

    @Test void completedTickKeepsAcceptedStateButDynamicAdaptersMustProvideCheckpoints() {
        var missing = new CeeAdapters.Registry();
        missing.register("example:missing_checkpoint", StoredProperty.class, new CeePropertyAdapter<>() {
            @Override public boolean dynamic() { return true; }
            @Override public CeeElectricalParameters prepare(StoredProperty source) {
                return new CeeElectricalParameters(1, 0, 0, false);
            }
        });
        missing.freeze();
        assertTrue(assertThrows(IllegalStateException.class,
                () -> missing.newSession().capture(new StoredProperty())).getMessage().contains("checkpoint"));

        var registry = new CeeAdapters.Registry();
        registry.register("example:complete", StoredProperty.class, new CeePropertyAdapter<>() {
            @Override public boolean dynamic() { return true; }
            @Override public CeeStateCheckpoint checkpoint(StoredProperty source) {
                double voltage = source.savedVoltage;
                int commits = source.commits;
                return () -> { source.savedVoltage = voltage; source.commits = commits; };
            }
            @Override public CeeElectricalParameters prepare(StoredProperty source) {
                return new CeeElectricalParameters(1, 0, 0, false);
            }
            @Override public void afterSubstep(StoredProperty source, CeeSubstepResult result) {
                source.savedVoltage = result.voltageVolts(); ++source.commits;
            }
        });
        registry.freeze();
        var source = new StoredProperty();
        var binding = registry.newSession().capture(source).orElseThrow().binding();
        binding.before(10, 0, 1, .05);
        binding.after(7, 1, 0, 1, .05, true);
        binding.finish(true);
        assertFalse(binding.failed());
        assertEquals(7, source.savedVoltage);
        assertEquals(1, source.commits);
        assertThrows(IllegalStateException.class, () -> binding.before(7, 1, 1, .05));
    }

    @Test void adapterErrorsRemainExplicitInsteadOfUsingUntranslatedNativeFields() {
        var registry = new CeeAdapters.Registry();
        registry.register("example:invalid", CustomProperty.class,
                source -> new CeeElectricalParameters(Double.NaN, 0, 0, false));
        registry.freeze();
        assertThrows(IllegalArgumentException.class, () -> registry.newSession().capture(new CustomProperty()));
        var nullRegistry = new CeeAdapters.Registry();
        nullRegistry.register("example:null", CustomProperty.class, source -> null);
        nullRegistry.freeze();
        assertTrue(assertThrows(NullPointerException.class,
                () -> nullRegistry.newSession().capture(new CustomProperty())).getMessage().contains("example:null"));
    }

    @Test void zeroCrossingRetainsIdealSourceFamilyAndInvalidSchedulesAreRejected() {
        var atZero = new CeeElectricalParameters(0, 0, 0, true);
        assertTrue(atZero.idealVoltageSource());
        assertTrue(atZero.inverted().idealVoltageSource());
        assertThrows(IllegalArgumentException.class, () -> new CeeElectricalParameters(-1, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new CeeElectricalParameters(0, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new CeeSubstepContext(0, 16, 16, .003125));
        assertThrows(IllegalArgumentException.class, () -> new CeeSubstepResult(Double.NaN, 0, 0, 16, .003125));
        assertEquals(1, new CeeSubstepResult(1, 2, 0, 16, .003125).voltageVolts());
    }
}
