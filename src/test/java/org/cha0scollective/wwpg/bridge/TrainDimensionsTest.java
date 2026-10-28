package org.cha0scollective.wwpg.bridge;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TrainDimensionsTest {
    @Test void eachDimensionOwnsOnlyItsPresentTrain() {
        var overworld = List.of(List.of(Level.OVERWORLD), List.of(Level.OVERWORLD));
        assertTrue(TrainDimensions.owns(overworld, Level.OVERWORLD));
        assertFalse(TrainDimensions.owns(overworld, Level.NETHER));
        assertTrue(TrainDimensions.owns(List.of(List.of(Level.NETHER)), Level.NETHER));
    }
    @Test void absentAndDimensionSpanningCarriagesHaveNoAmbiguousCommitOwner() {
        assertFalse(TrainDimensions.owns(List.of(), Level.OVERWORLD));
        assertFalse(TrainDimensions.owns(List.of(List.of()), Level.OVERWORLD));
        assertFalse(TrainDimensions.owns(List.of(List.of(Level.OVERWORLD, Level.NETHER)), Level.OVERWORLD));
        assertFalse(TrainDimensions.owns(List.of(List.of(Level.OVERWORLD), List.of(Level.NETHER)), Level.OVERWORLD));
    }
}
