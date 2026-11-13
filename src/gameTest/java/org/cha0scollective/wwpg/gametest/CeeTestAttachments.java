package org.cha0scollective.wwpg.gametest;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.*;
import java.util.*;
/** CEE 1.2 changed attachment fields from DeferredHolder to Supplier. */
final class CeeTestAttachments {
    private static final Map<String,PanelAttachmentType> TYPES=new HashMap<>();
    static com.george_vi.electroenergetics.foundation.nodes.InWorldNode[] nodes(PanelAttachmentType type,net.minecraft.core.BlockPos pos,com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot slot){
        try{return (com.george_vi.electroenergetics.foundation.nodes.InWorldNode[])com.george_vi.electroenergetics.content.electrical_panel.PanelAttachmentMode.class
                .getMethod("getNodesFor",net.minecraft.core.BlockPos.class,com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot.class).invoke(type.mode,pos,slot);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    static PanelAttachmentType type(String name){return TYPES.computeIfAbsent(name,key->{
        try{return (PanelAttachmentType)((java.util.function.Supplier<?>)CEEPanelAttachmentTypes.class.getField(key).get(null)).get();}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Unknown CEE attachment: "+key,error);}
    });}
}
