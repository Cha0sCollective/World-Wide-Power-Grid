package org.cha0scollective.wwpg.api.cee;

import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.Objects;

/**
 * Registration event posted once to all mod event buses in mod order during the
 * serial enqueued FMLLoadComplete phase. Subscribe on your mod's event bus before
 * load completes. The game event bus is still stopped during this startup phase.
 * A registration ID and exact property class each have one owner. Registration
 * freezes after listeners return; late registration is rejected explicitly.
 * Existing built-in CEE models retain their WWPG adapters; extensions register
 * their own exact property classes rather than replacing upstream behavior.
 */
public final class RegisterCeePropertyAdaptersEvent extends Event implements IModBusEvent {
    /** Runtime-provided registration sink; add-ons use this event's register method. */
    public interface Registration {
        /** Register a namespaced adapter ID for one exact CEE property class. */
        <T extends ElectricalProperties> void register(String providerId, Class<T> type, CeePropertyAdapter<T> adapter);
    }

    private final Registration registration;

    /** Constructed by WWPG for its startup registration phase. */
    public RegisterCeePropertyAdaptersEvent(Registration registration) {
        this.registration = Objects.requireNonNull(registration, "registration");
    }

    /** Register a custom property adapter; duplicate IDs, duplicate classes and built-in overrides are errors. */
    public <T extends ElectricalProperties> void register(String providerId, Class<T> type, CeePropertyAdapter<T> adapter) {
        registration.register(providerId, type, adapter);
    }
}
