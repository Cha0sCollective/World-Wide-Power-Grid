package org.example.tutorialload;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.cha0scollective.wwpg.api.neoforge.ElectricalProviders;
import java.util.function.Supplier;

/** Ordinary NeoForge registration, plus one WWPG capability registration. */
@Mod(TutorialLoadMod.MOD_ID)
public final class TutorialLoadMod {
    public static final String MOD_ID = "tutorial_load";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final Supplier<LoadBlock> LOAD = BLOCKS.register("load", LoadBlock::new);
    public static final Supplier<Item> LOAD_ITEM = ITEMS.register("load", () -> new BlockItem(LOAD.get(), new Item.Properties()));
    public static final Supplier<BlockEntityType<LoadDevice>> LOAD_TYPE = TYPES.register("load", () ->
            BlockEntityType.Builder.of(LoadDevice::new, LOAD.get()).build(null));

    public TutorialLoadMod(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TYPES.register(modBus);
        modBus.addListener(TutorialLoadMod::registerElectricalDevice);
        modBus.addListener(TutorialLoadMod::creativeTab);
    }

    private static void registerElectricalDevice(RegisterCapabilitiesEvent event) {
        ElectricalProviders.registerBlockEntity(event, MOD_ID + ":load", LOAD_TYPE.get(), (entity, context) -> entity);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) event.accept(LOAD_ITEM.get());
    }
}
