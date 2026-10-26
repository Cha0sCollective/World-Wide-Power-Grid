package org.cha0scollective.wwpg.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.BlockHitResult;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableEditScreen;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableScreen;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;
import org.patryk3211.powergrid.collections.ModdedPackets;
import org.patryk3211.powergrid.network.packets.ChangeScreenC2SPacket;
import org.patryk3211.powergrid.network.packets.SaveSchematicC2SPacket;

/** Real client inventory, native menu packets and CC keyboard input only. */
final class StationaryMultiplayerClientDriver {
    static boolean tick(Minecraft mc,boolean a,String phase,int age)throws ReflectiveOperationException {
        if(phase.startsWith("stationary-design-")) {
            boolean active=phase.endsWith(a?"a":"b");
            var table=(CircuitDesignTableBlockEntity)mc.level.getBlockEntity(StationaryMultiplayerSession.TABLE);
            if(active) {
                if(age==25)click(mc,StationaryMultiplayerSession.TABLE,true);
                if(age==60) {
                    require(mc.screen instanceof CircuitDesignTableScreen,"Native design menu did not open");
                    if(table.getSchematic().components().isEmpty()) {
                        var saved=mc.player.containerMenu.getSlot(2).getItem();
                        require(CircuitSchematic.fromStack(mc.level.registryAccess(),saved).components().size()==5,"Real-client saved item lost components");
                        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,2,0,ClickType.PICKUP,mc.player);
                        mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,ClickType.PICKUP,mc.player);
                        ModdedPackets.sendToServer(new SaveSchematicC2SPacket(table,true));
                    }
                }
                if(age==100) {
                    require(table.getSchematic().components().size()==5,"Native real-client item load failed");
                    ModdedPackets.sendToServer(new ChangeScreenC2SPacket(table,1));
                }
                if(age==140) {
                    require(mc.screen instanceof CircuitDesignTableEditScreen,"Native design editor did not open");
                    var editor=(CircuitDesignTableEditScreen<?>)mc.screen;
                    var field=CircuitDesignTableEditScreen.class.getDeclaredField("nameField");field.setAccessible(true);
                    ((net.minecraft.client.gui.components.EditBox)field.get(editor)).setValue("WWPG multiplayer "+(a?"A":"B"));
                    shot(mc,"design-editor-"+(a?"A":"B")+".png");
                    editor.save();
                }
                if(age==180) {
                    require(mc.screen instanceof CircuitDesignTableScreen&&table.getSchematic().components().size()==5&&table.getSchematicName().equals("WWPG multiplayer "+(a?"A":"B")),"Native edit/save did not synchronize");
                    shot(mc,"design-saved-"+(a?"A":"B")+".png");mc.player.closeContainer();
                    WorldWidePowerGrid.LOGGER.info("STATIONARY_REAL_CLIENT_DESIGN: {} native load/editor/save passed",a?"A":"B");return true;
                }
            } else if(age==220) {
                require(((CircuitBoardBlockEntity)mc.level.getBlockEntity(StationaryMultiplayerSession.BOARD)).getComponentsStream().count()==5,"Other client's powered board lost components");
                return true;
            }
        } else if(phase.startsWith("stationary-pinout-")) {
            var computer=StationaryMultiplayerSession.pins(a).west();
            if(age==25)click(mc,computer,false);
            if(age==65) {
                String command=switch(phase) {
                    case "stationary-pinout-stop" -> "wwpg stop";
                    case "stationary-pinout-pattern" -> "wwpg pattern 85";
                    case "stationary-pinout-volts" -> "wwpg volts";
                    case "stationary-pinout-start" -> "wwpg start";
                    default -> throw new IllegalStateException("Unknown real-client phase "+phase);
                };
                typeCommand(mc,command);
            }
            if(age==220) {
                if(phase.equals("stationary-pinout-volts"))verifyVoltageText(terminalText(mc),85);
                shot(mc,phase+"-"+(a?"A":"B")+".png");mc.player.closeContainer();
            }
            if(age>=280) {
                int pattern=phase.endsWith("stop")?0:phase.endsWith("start")?255:85;
                String pending=null;
                for(int pin=1;pin<=8;pin++) {
                    var lamp=mc.level.getBlockEntity(StationaryMultiplayerSession.load(StationaryMultiplayerSession.pins(a),pin));
                    require(lamp!=null,"Native client pin lamp missing");
                    float light=lamp.saveWithoutMetadata(mc.level.registryAccess()).getFloat("FirstLight");
                    if(!((pattern&(1<<(pin-1)))!=0?light>.95:light<.01))pending="pin "+pin+" light="+light;
                }
                var pins=StationaryMultiplayerSession.pins(a);boolean run=(pattern&4)!=0;
                var running=(org.patryk3211.powergrid.electricity.light.fixture.AbstractLightFixtureBlockEntity)mc.level.getBlockEntity(pins.offset(15,0,10));
                if(!(run?running.getPowerLevel()>0:running.getPowerLevel()==0))pending="RUN light="+running.getPowerLevel();
                float stopped=mc.level.getBlockEntity(pins.offset(15,0,13)).saveWithoutMetadata(mc.level.registryAccess()).getFloat("FirstLight");
                if(!(run?stopped<.01:stopped>.95))pending="OFF light="+stopped;
                var panel=(com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity)mc.level.getBlockEntity(pins.south(10));
                var gauge=(com.george_vi.electroenergetics.content.electrical_panel.attachments.GaugePanelAttachment)panel.getAttachments()[com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot.THIRD_CENTERED.ordinal()];
                if(Math.abs(gauge.value-((pattern&16)!=0?70:0))>=.2)pending="panel voltage="+gauge.value;
                if(pending!=null) {
                    if(age%100==0)WorldWidePowerGrid.LOGGER.info("STATIONARY_REAL_CLIENT_SETTLING: pattern={}, age={}, pending={}",pattern,age,pending);
                    require(age<700,"Native computer outputs did not settle: pattern="+pattern+", "+pending);
                    return false;
                }
                WorldWidePowerGrid.LOGGER.info("STATIONARY_REAL_CLIENT_LUA: {} {} native keyboard and synchronized lamps passed",a?"A":"B",phase);
                return true;
            }
        }
        return false;
    }
    private static void click(Minecraft mc,net.minecraft.core.BlockPos pos,boolean table) {
        var point=pos.getCenter().add(0,table?.4:0,table?0:.5);
        var result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(point,table?Direction.UP:Direction.SOUTH,pos,false));
        require(result.consumesAction(),"Native real-client block click rejected "+pos);
    }
    private static void typeCommand(Minecraft mc,String command) {
        require(mc.screen!=null&&mc.screen.getClass().getName().startsWith("dan200.computercraft."),"Native computer screen did not open");
        for(char value:command.toCharArray())require(mc.screen.charTyped(value,0),"Native computer rejected keyboard character");
        require(mc.screen.keyPressed(257,0,0),"Native computer rejected Enter");
        WorldWidePowerGrid.LOGGER.info("STATIONARY_REAL_CLIENT_KEYBOARD: {}",command);
    }
    private static String terminalText(Minecraft mc)throws ReflectiveOperationException {
        Class<?> type=mc.screen.getClass();java.lang.reflect.Field field=null;
        while(type!=null&&field==null){try{field=type.getDeclaredField("terminalData");}catch(NoSuchFieldException ignored){type=type.getSuperclass();}}
        require(field!=null,"Native CC screen has no terminal data");field.setAccessible(true);var terminal=field.get(mc.screen);
        var text=new StringBuilder();int height=(int)terminal.getClass().getMethod("getHeight").invoke(terminal);
        for(int row=0;row<height;row++)text.append(terminal.getClass().getMethod("getLine",int.class).invoke(terminal,row)).append('\n');
        WorldWidePowerGrid.LOGGER.info("STATIONARY_REAL_CLIENT_TERMINAL:\n{}",text);return text.toString();
    }
    private static void verifyVoltageText(String text,int pattern) {
        var matcher=java.util.regex.Pattern.compile("(?m)^([1-8])\\s+(ON|OFF)\\s+([+-][0-9]+\\.[0-9]+) V").matcher(text);
        var seen=new java.util.HashSet<Integer>();
        while(matcher.find()) {
            int pin=Integer.parseInt(matcher.group(1));boolean on=(pattern&(1<<(pin-1)))!=0;double voltage=Double.parseDouble(matcher.group(3));
            require(matcher.group(2).equals(on?"ON":"OFF")&&Math.abs(voltage-(on?0:-70))<(on?.4:.2),"Native Lua signed terminal reading disagrees with loaded circuit: "+matcher.group());seen.add(pin);
        }
        require(seen.size()==8,"Actual computer screen did not report all eight signed voltages");
    }
    private static void shot(Minecraft mc,String name){net.minecraft.client.Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),text->WorldWidePowerGrid.LOGGER.info("STATIONARY_REAL_CLIENT_SCREENSHOT: {}",text.getString()));}
    private static void require(boolean passed,String message){if(!passed)throw new IllegalStateException(message);}
}
