package org.cha0scollective.wwpg.mixin;

import com.george_vi.electroenergetics.content.electrical_panel.attachments.BaseEnergyMeterAttachment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BaseEnergyMeterAttachment.class, remap = false)
public interface PanelMeterAccessor {
    @Accessor("inverted") boolean wwpg$isInverted();
}
