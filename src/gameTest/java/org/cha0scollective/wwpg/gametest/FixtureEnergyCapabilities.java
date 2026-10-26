package org.cha0scollective.wwpg.gametest;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.EnergyStorage;

import java.util.Map;
import java.util.WeakHashMap;

/** Test providers/receivers are enabled only by the packaged GameTest launch. */
public final class FixtureEnergyCapabilities {
    private static final Map<BlockEntity, Storage> FIXTURES = new WeakHashMap<>();

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BlockEntityType.BARREL,
                (barrel, side) -> FIXTURES.get(barrel));
    }

    static Storage install(BlockEntity barrel, boolean provider, int initialEnergy) {
        var storage = new Storage(provider, initialEnergy);
        FIXTURES.put(barrel, storage);
        return storage;
    }

    static final class Storage extends EnergyStorage {
        long received, extracted;
        Storage(boolean provider, int initialEnergy) {
            super(10000, provider ? 0 : 10000, provider ? 10000 : 0, initialEnergy);
        }
        void setEnergy(int value) { energy = value; }
        @Override public int receiveEnergy(int value, boolean simulate) {
            int amount = super.receiveEnergy(value, simulate);
            if (!simulate) received += amount;
            return amount;
        }
        @Override public int extractEnergy(int value, boolean simulate) {
            int amount = super.extractEnergy(value, simulate);
            if (!simulate) extracted += amount;
            return amount;
        }
    }
}
