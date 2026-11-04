package org.cha0scollective.wwpg.compat.cio;

import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.george_vi.electroenergetics.simulation.electrical_properties.MicroTickingElectricalProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.api.cee.RegisterCeePropertyAdaptersEvent;

import java.util.Set;

/** Optional integration; no CIO classes are required or bundled by WWPG. */
public final class CioCompatibility {
    public static final String MOD_ID = "createinteroperable";
    public static final String INVERTER_VERSION = "0.1.240-experimental";
    public static final String PHASE_TYPE = "com.cio.createinteroperable.inverter.cee.InverterDevice$PhaseSource";
    private static final Set<String> COUPLER_BLOCKS = Set.of(
            "com.cio.createinteroperable.bridge.MiniGridCouplerBlock",
            "com.cio.createinteroperable.bridge.InteroperableSmallBlock",
            "com.cio.createinteroperable.bridge.InteroperableCouplerBlock",
            "com.cio.createinteroperable.bridge.InteroperableDoubleCouplerBlock",
            "com.cio.createinteroperable.bridge.InteroperablePgKeystoneBlock",
            "com.cio.createinteroperable.bridge.InteroperableCeeKeystoneBlock",
            "com.cio.createinteroperable.bridge.InteroperableCoreBlock",
            "com.cio.createinteroperable.bridge.InteroperablePgAssembledBlock",
            "com.cio.createinteroperable.bridge.InteroperableCeeAssembledBlock",
            "com.cio.createinteroperable.bridge.InteroperableCoreAssembledBlock");

    private CioCompatibility() {}

    public static void registerAdapters(RegisterCeePropertyAdaptersEvent event) {
        var mod = ModList.get().getModContainerById(MOD_ID);
        if (mod.isEmpty()) return;
        String version = mod.get().getModInfo().getVersion().toString();
        if (!INVERTER_VERSION.equals(version)) {
            WorldWidePowerGrid.LOGGER.warn("WWPG: CIO {} inverter adapter is unverified; unknown electrical models remain unavailable", version);
            return;
        }
        try {
            var type = Class.forName(PHASE_TYPE, false, CioCompatibility.class.getClassLoader());
            if (!CioPhaseSourceAccess.class.isAssignableFrom(type)) {
                WorldWidePowerGrid.LOGGER.warn("WWPG: CIO {} private inverter model differs from the verified class; it remains unsupported", version);
                return;
            }
            event.register("wwpg:cio_inverter", type.asSubclass(MicroTickingElectricalProperties.class), new CioInverterAdapter());
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("The verified CIO inverter model is missing", failure);
        }
        WorldWidePowerGrid.LOGGER.info("WWPG: CIO {} inverter adapter enabled; redundant grid couplers are disabled", version);
    }

    public static boolean gridCoupler(SimulatedDevice device) {
        return device.getClass().getName().equals("com.cio.createinteroperable.bridge.InteroperableDevice");
    }

    public static boolean gridCoupler(Block block) { return COUPLER_BLOCKS.contains(block.getClass().getName()); }

    public static Component disabledMessage() {
        return Component.translatable("wwpg.cio.coupler_disabled").withStyle(ChatFormatting.YELLOW);
    }

    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (!gridCoupler(event.getPlacedBlock().getBlock())) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player)
            player.displayClientMessage(disabledMessage(), true);
    }

    public static void tooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() instanceof net.minecraft.world.item.BlockItem item && gridCoupler(item.getBlock()))
            event.getToolTip().add(disabledMessage());
    }
}
