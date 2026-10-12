package org.cha0scollective.wwpg.extension;

import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.ModLoader;
import org.cha0scollective.wwpg.api.cee.CeeElectricalParameters;
import org.cha0scollective.wwpg.api.cee.CeePropertyAdapter;
import org.cha0scollective.wwpg.api.cee.CeeSubstepContext;
import org.cha0scollective.wwpg.api.cee.CeeSubstepResult;
import org.cha0scollective.wwpg.api.cee.CeeStateCheckpoint;
import org.cha0scollective.wwpg.api.cee.RegisterCeePropertyAdaptersEvent;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Internal runtime registry and captured native-property facades; not public SDK. */
public final class CeeAdapters {
    private static final Set<String> NATIVE_PROPERTY_TYPES = Set.of(
            ElectricalProperties.class.getName(),
            "com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerElectricalProperties",
            "com.george_vi.electroenergetics.foundation.electrical_properties.CapacitorProperties",
            "com.george_vi.electroenergetics.foundation.electrical_properties.InductorProperties",
            "com.george_vi.electroenergetics.foundation.electrical_properties.DiodeProperties",
            "com.george_vi.electroenergetics.foundation.electrical_properties.AccumulatorProperties",
            "com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice$ACSource",
            "com.george_vi.electroenergetics.content.rotor.ThreePhaseAlternatorBrushesDevice$PhaseWindingProperties",
            "com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingInvertedElectricalProperties");
    private static final Registry REGISTRY = new Registry();
    private static boolean initialized;

    private CeeAdapters() { }

    public static synchronized void initialize(IEventBus modBus) {
        Objects.requireNonNull(modBus, "modBus");
        if (initialized) throw new IllegalStateException("WWPG CEE adapter initialization was requested twice");
        initialized = true;
        modBus.addListener((FMLLoadCompleteEvent event) -> event.enqueueWork(() -> {
            ModLoader.postEventWrapContainerInModOrder(new RegisterCeePropertyAdaptersEvent(REGISTRY));
            REGISTRY.freeze();
        }));
    }

    public static Session newSession() { return REGISTRY.newSession(); }
    public static boolean registered(Class<? extends ElectricalProperties> type) { return REGISTRY.registered(type); }
    public static boolean isAdapted(ElectricalProperties properties) { return properties instanceof SnapshotProperties; }
    public static List<String> registrations() { return REGISTRY.registrations(); }

    /** Isolated registries allow deterministic registration and lifecycle tests. */
    public static final class Registry implements RegisterCeePropertyAdaptersEvent.Registration {
        private final Map<Class<? extends ElectricalProperties>, Entry<?>> entries = new HashMap<>();
        private final Map<String, Class<? extends ElectricalProperties>> owners = new HashMap<>();
        private boolean frozen;

        @Override
        public synchronized <T extends ElectricalProperties> void register(String providerId, Class<T> type, CeePropertyAdapter<T> adapter) {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(type, "property class");
            Objects.requireNonNull(adapter, "adapter");
            if (providerId.length() > 256 || !providerId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("CEE adapter providerId must be a namespaced lowercase ID");
            if (frozen) throw new IllegalStateException("CEE adapter registration is frozen: " + providerId);
            if (NATIVE_PROPERTY_TYPES.contains(type.getName()))
                throw new IllegalArgumentException("CEE property " + type.getName()
                        + " has a built-in WWPG adapter; register your add-on's own property class instead");
            if (entries.containsKey(type))
                throw new IllegalArgumentException("CEE property " + type.getName() + " already belongs to "
                        + entries.get(type).providerId + "; cannot also register " + providerId);
            if (owners.containsKey(providerId))
                throw new IllegalArgumentException("CEE adapter ID " + providerId + " already owns " + owners.get(providerId).getName());
            entries.put(type, new Entry<>(providerId, type, adapter));
            owners.put(providerId, type);
        }

        public synchronized void freeze() { frozen = true; }
        public synchronized boolean registered(Class<? extends ElectricalProperties> type) { return entries.containsKey(type); }
        public synchronized List<String> registrations() {
            return owners.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + " -> " + entry.getValue().getName()).toList();
        }
        public synchronized Session newSession() {
            if (!frozen) throw new IllegalStateException("CEE adapters cannot be queried before registration freezes");
            return new Session(Map.copyOf(entries));
        }
    }

    /** Per-preparation identity map; inverse facades share one source and history. */
    public static final class Session {
        private final Map<Class<? extends ElectricalProperties>, Entry<?>> entries;
        private final IdentityHashMap<ElectricalProperties, Captured> captured = new IdentityHashMap<>();

        private Session(Map<Class<? extends ElectricalProperties>, Entry<?>> entries) { this.entries = entries; }

        public boolean hasAdapters() { return !entries.isEmpty(); }

        public Optional<Captured> capture(ElectricalProperties source) {
            Objects.requireNonNull(source, "CEE property");
            var previous = captured.get(source);
            if (previous != null) return Optional.of(previous);
            var entry = entries.get(source.getClass());
            if (entry == null) return Optional.empty();
            var binding = entry.bind(source);
            var original = new Captured(binding, binding.properties(), false);
            captured.put(source, original);
            var inverse = source.invert();
            if (inverse != source) captured.put(inverse, new Captured(binding, binding.invertedProperties(), true));
            return Optional.of(original);
        }

        public List<Binding> bindings() {
            return captured.values().stream().map(Captured::binding).distinct().toList();
        }
    }

    public record Captured(Binding binding, ElectricalProperties properties, boolean inverted) { }

    private record Entry<T extends ElectricalProperties>(String providerId, Class<T> type, CeePropertyAdapter<T> adapter) {
        Binding bind(ElectricalProperties source) {
            return new TypedBinding<>(providerId, type.cast(source), adapter);
        }
    }

    /** Stable translated facade for an original property within the current preparation. */
    public abstract static class Binding {
        private final String providerId;
        private final ElectricalProperties source;
        private final boolean dynamic;
        private final SnapshotProperties properties;
        private final SnapshotProperties invertedProperties;
        private CeeElectricalParameters parameters;
        private CeeStateCheckpoint tickCheckpoint, substepCheckpoint;
        private String failureReason;
        private boolean finished;
        private int declaredSubsteps = -1;
        private int lastBefore = -1, lastAfter = -1;

        private Binding(String providerId, ElectricalProperties source, boolean dynamic) {
            this.providerId = providerId;
            this.source = source;
            this.dynamic = dynamic;
            properties = new SnapshotProperties(this, false);
            invertedProperties = new SnapshotProperties(this, true);
        }

        public final String providerId() { return providerId; }
        public final ElectricalProperties source() { return source; }
        public final boolean dynamic() { return dynamic; }
        public final ElectricalProperties properties() { return properties; }
        public final ElectricalProperties invertedProperties() { return invertedProperties; }
        public final boolean failed() { return failureReason != null; }
        public final Optional<String> failureReason() { return Optional.ofNullable(failureReason); }

        public final void before(double previousVoltage, int step, int total, double seconds) {
            if (failed()) return;
            if (finished) throw new IllegalStateException("CEE adapter " + providerId + " tick is already finished");
            if (step != lastBefore + 1 || step > 0 && lastAfter != step - 1)
                throw new IllegalStateException("CEE adapter " + providerId + " beforeSubstep out of order: " + step);
            var context = new CeeSubstepContext(previousVoltage, step, total, seconds);
            if (declaredSubsteps != -1 && declaredSubsteps != total)
                throw new IllegalStateException("CEE adapter " + providerId + " changed its substep count mid-tick");
            declaredSubsteps = total;
            try {
                substepCheckpoint = Objects.requireNonNull(checkpoint(), "CEE adapter " + providerId + " returned null checkpoint");
                if (dynamic) beforeCaptured(context);
                var next = Objects.requireNonNull(capture(), "CEE adapter " + providerId + " returned null parameters");
                if (next.idealVoltageSource() != parameters.idealVoltageSource())
                    throw new IllegalStateException("ideal-voltage-source family changed inside an electrical tick");
                parameters = next;
                lastBefore = step;
            } catch (RuntimeException | LinkageError failure) {
                latchFailure(failure);
            }
        }

        public final void after(double voltage, double current, int step, int total, double seconds, boolean accepted) {
            if (failed()) return;
            if (finished) throw new IllegalStateException("CEE adapter " + providerId + " tick is already finished");
            if (step != lastBefore || step != lastAfter + 1)
                throw new IllegalStateException("CEE adapter " + providerId + " afterSubstep out of order: " + step);
            if (!accepted) {
                latchFailure(new IllegalStateException("electrical substep " + step + " was rejected"));
                return;
            }
            try {
                if (dynamic) afterCaptured(new CeeSubstepResult(voltage, current, step, total, seconds));
                substepCheckpoint = null;
                lastAfter = step;
            } catch (RuntimeException | LinkageError failure) {
                latchFailure(failure);
            }
        }

        public final void finish(boolean acceptedTick) {
            if (finished) throw new IllegalStateException("CEE adapter " + providerId + " tick was finished twice");
            if (acceptedTick && !failed() && dynamic && (declaredSubsteps < 1 || lastAfter != declaredSubsteps - 1))
                latchFailure(new IllegalStateException("electrical tick did not complete its declared substeps"));
            if (!acceptedTick && !failed()) latchFailure(new IllegalStateException("electrical tick was rejected"));
            if (failed()) restore(tickCheckpoint);
            substepCheckpoint = null;
            tickCheckpoint = null;
            finished = true;
        }

        private void latchFailure(Throwable failure) {
            if (failureReason == null) failureReason = bounded("CEE adapter " + providerId + ": "
                    + failure.getClass().getSimpleName() + ": " + Objects.toString(failure.getMessage(), "callback failed"));
            restore(substepCheckpoint);
            substepCheckpoint = null;
            boolean ideal = parameters != null && parameters.idealVoltageSource();
            parameters = new CeeElectricalParameters(0, 0, 0, ideal);
        }

        private void restore(CeeStateCheckpoint saved) {
            if (saved == null) return;
            try {
                saved.restore();
            } catch (RuntimeException | LinkageError failure) {
                failureReason = bounded(Objects.toString(failureReason, "CEE adapter " + providerId + " rollback failed")
                        + "; checkpoint restore failed: " + failure.getClass().getSimpleName() + ": "
                        + Objects.toString(failure.getMessage(), "no detail"));
            }
        }

        private static String bounded(String message) {
            return message.length() <= 1024 ? message : message.substring(0, 1024);
        }

        protected final void initializeParameters() {
            tickCheckpoint = Objects.requireNonNull(checkpoint(), "CEE adapter " + providerId + " returned null checkpoint");
            try {
                parameters = Objects.requireNonNull(capture(), "CEE adapter " + providerId + " returned null parameters");
            } finally {
                tickCheckpoint.restore();
            }
        }
        protected abstract CeeElectricalParameters capture();
        protected abstract CeeStateCheckpoint checkpoint();
        protected abstract void beforeCaptured(CeeSubstepContext context);
        protected abstract void afterCaptured(CeeSubstepResult result);
    }

    private static final class TypedBinding<T extends ElectricalProperties> extends Binding {
        private final T typedSource;
        private final CeePropertyAdapter<T> adapter;

        private TypedBinding(String providerId, T source, CeePropertyAdapter<T> adapter) {
            super(providerId, source, adapter.dynamic());
            typedSource = source;
            this.adapter = adapter;
            initializeParameters();
        }
        @Override protected CeeElectricalParameters capture() { return adapter.prepare(typedSource); }
        @Override protected CeeStateCheckpoint checkpoint() { return adapter.checkpoint(typedSource); }
        @Override protected void beforeCaptured(CeeSubstepContext context) { adapter.beforeSubstep(typedSource, context); }
        @Override protected void afterCaptured(CeeSubstepResult result) { adapter.afterSubstep(typedSource, result); }
    }

    private static final class SnapshotProperties extends ElectricalProperties {
        private final Binding binding;
        private final boolean inverted;

        private SnapshotProperties(Binding binding, boolean inverted) {
            super(1, 0, 0);
            this.binding = binding;
            this.inverted = inverted;
        }
        @Override public double resistance() {
            return conductance() == 0 ? 1e11 : 1 / conductance();
        }
        @Override public double conductance() { return binding.parameters.conductanceSiemens(); }
        @Override public double currentSource() { return (inverted ? -1 : 1) * binding.parameters.currentSourceAmps(); }
        @Override public double voltageSource() { return (inverted ? -1 : 1) * binding.parameters.voltageSourceVolts(); }
        @Override public boolean isVoltageSource() { return binding.parameters.idealVoltageSource(); }
        @Override public boolean isCurrentSource() { return currentSource() != 0; }
        @Override public boolean isSimpleResistor() { return !isVoltageSource() && !isCurrentSource(); }
        @Override public ElectricalProperties invert() { return inverted ? binding.properties : binding.invertedProperties; }
        @Override public boolean equals(Object other) { return other == this; }
        @Override public int hashCode() { return System.identityHashCode(this); }
    }
}
