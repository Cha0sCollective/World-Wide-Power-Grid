package org.cha0scollective.wwpg.gametest;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.*;
import java.util.*;
/** CEE 1.2 changed attachment fields from DeferredHolder to Supplier. */
final class CeeTestAttachments {
    private static final Map<String,PanelAttachmentType> TYPES=new HashMap<>();
    // 1.1 attachments implement Create's link directly; 1.2 owns one or more
    // linkables. Steering's positive channel is the second link in 1.2.
    static int positiveChannel(PanelAttachment attachment) {
        return org.cha0scollective.wwpg.compat.cee.CeeCompatibility.modern()
                && attachment.type==type("STEERING_WHEEL") ? 1 : 0;
    }
    static com.simibubi.create.content.redstone.link.IRedstoneLinkable positiveLink(PanelAttachment attachment) {
        if(attachment instanceof com.simibubi.create.content.redstone.link.IRedstoneLinkable legacy)return legacy;
        try {
            var links=(Object[])com.george_vi.electroenergetics.content.electrical_panel.link.ElectricalPanelLink.class
                    .getMethod("getLinkables").invoke(attachment);
            return (com.simibubi.create.content.redstone.link.IRedstoneLinkable)links[positiveChannel(attachment)];
        } catch(ReflectiveOperationException error){throw new IllegalStateException("Unsupported panel link contract",error);}
    }
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
