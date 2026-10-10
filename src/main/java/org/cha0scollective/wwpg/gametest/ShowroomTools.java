package org.cha0scollective.wwpg.gametest;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

/** Exhibit construction only: native settings remain editable and survive saving. */
final class ShowroomTools {
    static void checkAll(GameTestHelper h,Runnable... checks) {
        var failures=new java.util.ArrayList<String>();
        for(var check:checks)try {check.run();}catch(RuntimeException failure){
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.error("Showroom station check failed",failure);
            failures.add(failure.getMessage());
        }
        h.assertTrue(failures.isEmpty(),"Showroom station failures: "+failures);
    }
    static void checked(String phase,Runnable work) {
        try { work.run(); } catch(RuntimeException failure) {
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.error("Showroom failed during "+phase,failure);throw failure;
        }
    }
    static void put(GameTestHelper h, BlockPos at, Block block) { h.getLevel().setBlockAndUpdate(at, block.defaultBlockState()); }
    static void scroll(GameTestHelper h, BlockPos at, int setting) {
        var behaviour=((SmartBlockEntity) h.getLevel().getBlockEntity(at)).getBehaviour(ScrollValueBehaviour.TYPE);
        if(behaviour==null)throw new IllegalStateException("Missing native scroll setting at "+at+": "+h.getLevel().getBlockState(at));
        behaviour.setValue(setting);
    }
    static void pgSource(GameTestHelper h, BlockPos at, double voltage) {
        // Persist both PG's native source value and its user-facing setting.
        scroll(h, at, voltage <= 250 ? (int) voltage : 250 + (int) voltage / 100);
        ((CreativeSourceBlockEntity) h.getLevel().getBlockEntity(at)).setValue((float)voltage);
        h.getLevel().getBlockEntity(at).setChanged();
    }
    static void resistance(GameTestHelper h, BlockPos at, double resistance) {
        int exponent = (int) Math.floor(Math.log10(resistance));
        int mantissa = (int) Math.round(resistance / Math.pow(10, exponent));
        scroll(h, at, (exponent + 3) * 9 + mantissa - 1);
        near(h, ((ResistorBlockEntity) h.getLevel().getBlockEntity(at)).getValue(), resistance, Math.max(.00001, resistance * .000001), "Saved ballast setting");
    }
    static void wire(GameTestHelper h, BlockPos a, int ta, BlockPos b, int tb, boolean pg) {
        try { WiringGameTests.connect(h, a, ta, b, tb, pg); }
        catch(RuntimeException failure) {
            var sd=com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData.load(h.getLevel());
            var first=new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(ta,a);var last=new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(tb,b);
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.error("Showroom wiring {}:{} [{}] -> {}:{} [{}], PG={}, CEE nodes present={}/{}, already connected={}",a,ta,h.getLevel().getBlockState(a),b,tb,h.getLevel().getBlockState(b),pg,sd.getNodes().contains(first),sd.getNodes().contains(last),sd.isConnected(first,last),failure);
            throw failure;
        }
    }
    static double volts(GameTestHelper h, BlockPos at, int first, int second) {
        return new BlockWireEndpoint(at, first).getNode(h.getLevel()).getVoltage() - new BlockWireEndpoint(at, second).getNode(h.getLevel()).getVoltage();
    }
    static void near(GameTestHelper h, double actual, double expected, double tolerance, String label) { BoardComponentGameTests.near(h, actual, expected, tolerance, label); }
    static void sign(GameTestHelper h, BlockPos at, String... lines) {
        put(h, at, Blocks.OAK_SIGN);
        var sign = (SignBlockEntity) h.getLevel().getBlockEntity(at);
        var text = sign.getFrontText().setColor(DyeColor.BLACK);
        for (int i = 0; i < 4; i++) text = text.setMessage(i, Component.literal(i < lines.length ? lines[i] : ""));
        sign.setText(text, true); sign.setText(text, false); sign.setWaxed(true); sign.setChanged();
    }
    private ShowroomTools() {}
}
