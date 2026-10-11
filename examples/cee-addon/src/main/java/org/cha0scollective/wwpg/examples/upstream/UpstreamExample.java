package org.cha0scollective.wwpg.examples.upstream;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.electrical_panel.PanelAttachmentMode;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.cha0scollective.wwpg.api.cee.CeeElectricalParameters;
import org.cha0scollective.wwpg.api.cee.CeePropertyAdapter;
import org.cha0scollective.wwpg.api.cee.CeeSubstepContext;
import org.cha0scollective.wwpg.api.cee.CeeSubstepResult;
import org.cha0scollective.wwpg.api.cee.CeeStateCheckpoint;
import org.cha0scollective.wwpg.api.cee.RegisterCeePropertyAdaptersEvent;
import org.patryk3211.powergrid.circuits.components.Component;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;

/** Native content registration plus the separately published CEE adapter API. */
@Mod(UpstreamExample.ID)
public final class UpstreamExample {
    static boolean adapterRegistered;
    public static final String ID = "wwpg_api_upstream";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<PanelAttachmentType> PANELS = DeferredRegister.create(CEERegistries.PANEL_ATTACHMENT_TYPE, ID);
    private static final DeferredRegister<Component> COMPONENTS = DeferredRegister.create(ComponentRegistry.REGISTRY_KEY, ID);
    public static final DeferredItem<Item> PANEL_ITEM = ITEMS.registerSimpleItem("panel_storage_indicator");
    public static final DeferredItem<Item> SHUNT_ITEM = ITEMS.registerSimpleItem("api_shunt");
    public static final DeferredHolder<PanelAttachmentType, PanelAttachmentType> PANEL = PANELS.register("panel_storage_indicator",
            () -> new PanelAttachmentType(StorageIndicatorAttachment::new, PANEL_ITEM, PanelAttachmentMode.HALF_OR_THIRD));
    public static final DeferredHolder<Component, ApiShuntComponent> SHUNT = COMPONENTS.register("api_shunt", ApiShuntComponent::new);

    public UpstreamExample(IEventBus modBus) {
        ITEMS.register(modBus);
        PANELS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(UpstreamExample::creativeItems);
        modBus.addListener(UpstreamExample::adapters);
    }

    private static void creativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(PANEL_ITEM);
            event.accept(SHUNT_ITEM);
        }
    }

    private static void adapters(RegisterCeePropertyAdaptersEvent event) {
        event.register(ID + ":panel_storage", StorageIndicatorProperties.class, new CeePropertyAdapter<>() {
            @Override public CeeElectricalParameters prepare(StorageIndicatorProperties source) {
                if (source.failDuringCapture) {
                    // Deliberate invalid add-on behavior for packaged rollback
                    // acceptance. Real preparation must be pure.
                    source.savedVoltage += 3;
                    throw new IllegalStateException("Example CEE adapter failed while capturing parameters");
                }
                return new CeeElectricalParameters(source.conductance(), source.currentSource(), 0, false);
            }
            @Override public boolean dynamic() { return true; }
            @Override public CeeStateCheckpoint checkpoint(StorageIndicatorProperties source) {
                double stored = source.savedVoltage;
                long accepted = source.acceptedSubsteps;
                double resistance = source.resistance;
                double current = source.currentSource;
                return () -> {
                    source.savedVoltage = stored;
                    source.acceptedSubsteps = accepted;
                    source.resistance = resistance;
                    source.currentSource = current;
                };
            }
            @Override public void beforeSubstep(StorageIndicatorProperties source, CeeSubstepContext context) {
                source.prepareSubstep(context.substepSeconds());
            }
            @Override public void afterSubstep(StorageIndicatorProperties source, CeeSubstepResult result) {
                source.acceptVoltage(result.voltageVolts());
                if (source.failAfterSubstep == result.substep())
                    throw new IllegalStateException("Example CEE adapter failed after mutating accepted electrical state");
            }
        });
        adapterRegistered = true;
    }
}
