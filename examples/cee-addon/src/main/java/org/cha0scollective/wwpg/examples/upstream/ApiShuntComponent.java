package org.cha0scollective.wwpg.examples.upstream;

import net.minecraft.resources.ResourceLocation;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.circuits.components.ResistorComponent;
import org.patryk3211.powergrid.circuits.schematic.ComponentFootprint;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;

import java.util.Collection;

/**
 * Native PG resistor component with its own registry identity and assembly item.
 * PG owns its model, settings, heat and damage. WWPG creates no second model.
 * Reusing the native resistor visual avoids inventing a replacement editor flow.
 */
public final class ApiShuntComponent extends ResistorComponent {
    public ApiShuntComponent() {
        super(new ComponentFootprint.Builder(5, 3).addPad(0, 1, 0).addPad(4, 1, 1).withItem().withOutline().build());
    }

    @Override public ResourceLocation getModelId(PlacedComponent placed) {
        return Components.RESISTOR.get().getModelId(placed);
    }

    @Override public Collection<ResourceLocation> requestedModels() {
        return Components.RESISTOR.get().requestedModels();
    }
}
