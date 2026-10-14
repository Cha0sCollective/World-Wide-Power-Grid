package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.MeasurementStatus;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.electricswitch.SwitchBlockEntity;
import org.patryk3211.powergrid.electricity.modulardisplay.ModularDisplayBlockEntity;

import java.util.function.Consumer;

/** Native powered transitions must work across every saved display wrap frame. */
@GameTestHolder("wwpg_display_pulse")
@PrefixGameTestTemplate(false)
public final class DisplayPulseGameTests {
    @GameTest(template="empty",timeoutTicks=140)
    public static void savedBlankFrameCountsThroughItsResetWinding(GameTestHelper h) { boundary(h,10,10); }

    @GameTest(template="empty",timeoutTicks=140)
    public static void savedRepeatedZeroWrapsBeforeTheNextPulse(GameTestHelper h) { boundary(h,11,0); }

    @GameTest(template="empty",timeoutTicks=140)
    public static void savedTransitionFrameWrapsBeforeTheNextPulse(GameTestHelper h) { boundary(h,12,0); }

    private static void boundary(GameTestHelper h,int savedIndex,int offIndex) {
        var fixture = new Fixture(h,new BlockPos(1,2,1));
        DisplayPulseCheck[] pulse = {null};
        h.startSequence().thenIdle(5).thenExecute(fixture::connect).thenIdle(25).thenExecute(()->{
            fixture.restore(savedIndex);
            pulse[0] = fixture.pulse(fixture::feed);
        }).thenWaitUntil(()->pulse[0].await()).thenExecute(()->{
            h.assertTrue(pulse[0].settledOffIndex()==offIndex,"Native saved display reset settled at the wrong cyclic frame");
            pulse[0].verify();DynamicGameTests.audit(h);
        }).thenSucceed();
    }

    @GameTest(template="empty",timeoutTicks=140)
    public static void staleHighAndMissingRisingPulseCannotPass(GameTestHelper h) {
        var stuck = new Fixture(h,new BlockPos(1,2,1));
        var missing = new Fixture(h,new BlockPos(1,2,6));
        DisplayPulseCheck[] pulses = new DisplayPulseCheck[2];
        h.startSequence().thenIdle(5).thenExecute(()->{stuck.connect();missing.connect();})
                .thenIdle(25).thenExecute(()->{
                    stuck.restore(5);missing.restore(5);
                    pulses[0]=stuck.pulse(ignored->{}); // A stale powered nonzero digit cannot prove an OFF/ON operation.
                    pulses[1]=missing.pulse(closed->{if(!closed)missing.feed(false);});
                }).thenWaitUntil(()->{
                    for(var pulse:pulses)pulse.poll();
                    h.assertTrue(h.getTick()>=80,"Observe enough real powered ticks for both defective commands");
                }).thenExecute(()->{
                    for(var pulse:pulses)h.assertTrue(!pulse.verified(),"Display pulse check accepted an ineffective command");
                    var high=ElectricalMeasurements.readVoltageBetween(h.getLevel(),stuck.display,0,stuck.display,8);
                    var low=ElectricalMeasurements.readVoltageBetween(h.getLevel(),missing.display,0,missing.display,8);
                    h.assertTrue(high.status()==MeasurementStatus.VALID&&low.status()==MeasurementStatus.VALID,"Negative pulse fixtures need accepted electrical results");
                    ShowroomTools.near(h,high.voltage().orElseThrow().meanVoltageVolts(),24,.03,"Stuck-ON control");
                    ShowroomTools.near(h,low.voltage().orElseThrow().meanVoltageVolts(),0,.001,"Missing rising pulse");
                    DynamicGameTests.audit(h);
                }).thenSucceed();
    }

    private static final class Fixture {
        final GameTestHelper h;
        final BlockPos source, control, display;
        Fixture(GameTestHelper h,BlockPos relative) {
            this.h=h;source=h.absolutePos(relative);control=source.east(3);display=source.east(7);
            for(var pos:new BlockPos[]{source,control,display})h.getLevel().setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(source,CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            h.getLevel().setBlockAndUpdate(control,ModdedBlocks.MV_SWITCH.getDefaultState());
            h.getLevel().setBlockAndUpdate(display,ModdedBlocks.MODULAR_DISPLAY.getDefaultState());
        }
        void connect() {
            var player=h.makeMockPlayer(GameType.SURVIVAL);var item=ModdedItems.DISPLAY_MODULE.asStack();
            player.setItemInHand(InteractionHand.MAIN_HAND,item);entity().interact(0,player);
            h.assertTrue(item.isEmpty(),"Native display module was not installed through its normal interaction");
            ShowroomTools.scroll(h,source,24000);feed(true);
            ShowroomTools.wire(h,source,1,control,0,true);
            ShowroomTools.wire(h,control,1,display,0,true);
            ShowroomTools.wire(h,source,0,display,8,false);
            ShowroomTools.wire(h,source,0,display,1,true);
        }
        void restore(int index) {
            var tag=entity().saveWithoutMetadata(h.getLevel().registryAccess());
            String serialized="zerotonine:"+index+":true:white";
            tag.getList("slots",Tag.TAG_STRING).set(0,StringTag.valueOf(serialized));
            entity().loadWithComponents(tag,h.getLevel().registryAccess());
            h.assertTrue(entity().modules[0].serialize().equals(serialized),"Native saved display module changed before its first resumed solve");
        }
        DisplayPulseCheck pulse(Consumer<Boolean> command) { return new DisplayPulseCheck(h,display,control,command,true); }
        void feed(boolean closed) { ((SwitchBlockEntity)h.getLevel().getBlockEntity(control)).setState(closed); }
        ModularDisplayBlockEntity entity() { return (ModularDisplayBlockEntity)h.getLevel().getBlockEntity(display); }
    }
}
