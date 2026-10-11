package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.kinetics.punchcard.PunchCardReaderBlock;
import org.patryk3211.powergrid.kinetics.punchcard.PunchCardReaderBlockEntity;

import static org.cha0scollective.wwpg.gametest.StationaryEquipmentGameTests.*;

@GameTestHolder("wwpg_stationary")
@PrefixGameTestTemplate(false)
public final class StationaryReaderGameTests {
    @GameTest(template = "empty", timeoutTicks = 340)
    public static void nativePunchCardScansEveryRowAndControlsEightMixedLoads(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1);
        var reader = new BlockPos(4, 2, 3);
        var motor = reader.east();
        var loads = new BlockPos[8];
        byte[] patterns = {1, 2, 4, 8, 16, 32, 64, (byte) 128, 85, (byte) 170,
                (byte) 165, (byte) 255, 0, 3, 64, (byte) 128};
        place(h, source, CEEBlocks.CREATIVE_BATTERY.get());
        h.setBlock(reader, ModdedBlocks.PUNCH_CARD_READER.getDefaultState()
                .setValue(PunchCardReaderBlock.HORIZONTAL_FACING, Direction.SOUTH));
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST));
        for (int i = 0; i < loads.length; i++) {
            loads[i] = new BlockPos(1 + i % 4 * 3, 2, 7 + i / 4 * 3);
            place(h, loads[i], i % 2 == 0 ? CEEBlocks.CREATIVE_RESISTOR.get() : ModdedBlocks.CREATIVE_RESISTOR.get());
        }
        h.runAtTickTime(5, () -> {
            source(h, source, 12);
            ((SmartBlockEntity) h.getBlockEntity(motor)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(-16);
            wire(h, source, 1, reader, 0, true);
            for (int i = 0; i < loads.length; i++) {
                if (i % 2 == 0) resistor(h, loads[i], 1000);
                else ((ResistorBlockEntity) h.getBlockEntity(loads[i])).setValue(1000);
                wire(h, reader, i + 1, loads[i], 0, i % 2 == 0);
                wire(h, source, 0, loads[i], 1, i % 2 != 0);
            }
            var card = ModdedItems.PUNCH_CARD.asStack();
            var data = new CompoundTag(); data.putByteArray("Data", patterns);
            card.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, card);
            var absolute = h.absolutePos(reader);
            var result = h.getBlockState(reader).useItemOn(card, h.getLevel(), player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(absolute.getCenter(), Direction.UP, absolute, false));
            h.assertTrue(result.consumesAction() && card.isEmpty(), "Native punch-card insertion did not consume one card");
        });
        for (int row = 0; row < 16; row++) {
            final int index = row;
            h.runAtTickTime(13 + row * 16, () -> {
                var be = (PunchCardReaderBlockEntity) h.getBlockEntity(reader);
                h.assertTrue(be.getSpeed() == 16 && be.getRedstoneOutput() == index,
                        "Native mechanical scan did not reach row " + index + "; row=" + be.getRedstoneOutput() + ", speed=" + be.getSpeed());
                for (int pin = 0; pin < 8; pin++) {
                    double actual;
                    if (pin % 2 == 0) actual = ceeVoltage(h, loads[pin]);
                    else {
                        var absolute = h.absolutePos(loads[pin]);
                        actual = new BlockWireEndpoint(absolute, 0).getNode(h.getLevel()).getVoltage()
                                - new BlockWireEndpoint(absolute, 1).getNode(h.getLevel()).getVoltage();
                    }
                    near(h, actual, (patterns[index] & 1 << pin) != 0 ? 12 : 0, .02,
                            "Punch-card row " + index + " bit " + (pin + 1));
                }
            });
        }
        h.runAtTickTime(270, () -> {
            var be = h.getBlockEntity(reader);
            var saved = be.saveWithFullMetadata(h.getLevel().registryAccess());
            var cold = BlockEntity.loadStatic(h.absolutePos(reader), h.getBlockState(reader), saved, h.getLevel().registryAccess());
            var restored = cold.saveWithoutMetadata(h.getLevel().registryAccess());
            h.assertTrue(saved.getCompound("Inv").equals(restored.getCompound("Inv"))
                    && saved.getFloat("Angle") == restored.getFloat("Angle"), "Cold loading lost native card data or scan position");
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            var absolute = h.absolutePos(reader);
            h.assertTrue(h.getBlockState(reader).useWithoutItem(h.getLevel(), player,
                    new BlockHitResult(absolute.getCenter(), Direction.UP, absolute, false)).consumesAction()
                    && player.getMainHandItem().is(ModdedItems.PUNCH_CARD.get()), "Native empty-hand card extraction failed");
        });
        h.runAtTickTime(290, () -> {
            var be = (PunchCardReaderBlockEntity) h.getBlockEntity(reader);
            h.assertTrue(be.currentItem().isEmpty() && be.getRedstoneOutput() == 0, "Empty reader retained a card or output");
            for (var load : loads) {
                var absolute = h.absolutePos(load);
                near(h, new BlockWireEndpoint(absolute, 0).getNode(h.getLevel()).getVoltage()
                        - new BlockWireEndpoint(absolute, 1).getNode(h.getLevel()).getVoltage(), 0, .001,
                        "Removed punch card left ghost mixed output");
            }
            finish(h);
        });
    }
}
