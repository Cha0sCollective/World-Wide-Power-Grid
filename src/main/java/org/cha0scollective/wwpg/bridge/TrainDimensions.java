package org.cha0scollective.wwpg.bridge;

import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.List;

/** One train's shared state is committed once, by its physical dimension. */
public final class TrainDimensions {
    private TrainDimensions() { }
    public static boolean owns(Train train, ResourceKey<Level> dimension) {
        return owns(train.carriages.stream().map(carriage -> carriage.getPresentDimensions()).toList(), dimension);
    }
    static boolean owns(List<? extends List<ResourceKey<Level>>> carriages, ResourceKey<Level> dimension) {
        return !carriages.isEmpty() && carriages.stream().allMatch(present -> present.size() == 1 && present.getFirst().equals(dimension));
    }
}
