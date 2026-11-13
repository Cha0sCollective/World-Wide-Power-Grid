package org.cha0scollective.wwpg.compat.cee;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Native CEE tags retain their meanings; this owned marker identifies their convention. */
public final class CeeSavedCurrent {
    public static final String MARKER="WWPGCurrentConvention";
    public enum Convention {
        LEGACY("cee-1.1"),MODERN("cee-1.2"),UNKNOWN("unknown");
        public final String marker;
        Convention(String marker){this.marker=marker;}
        public static Convention marker(String value){for(var c:values())if(c.marker.equals(value))return c;return UNKNOWN;}
        public static Convention version(String version){
            if(version.startsWith("1.21.1-1.1."))return LEGACY;
            if(version.startsWith("1.21.1-1.2."))return MODERN;
            return UNKNOWN;
        }
    }
    public record Reading(double current,boolean ambiguous,Convention source) { }
    private CeeSavedCurrent() { }
    public static Reading read(CompoundTag tag,Convention unmarked,Convention runtime,String override) {
        var source=tag.contains(MARKER,Tag.TAG_STRING)?Convention.marker(tag.getString(MARKER)):unmarked;
        if(source==Convention.UNKNOWN)source=switch(override){case "1.1"->Convention.LEGACY;case "1.2"->Convention.MODERN;default->Convention.UNKNOWN;};
        double current=tag.getDouble("LastCurrent");
        boolean ambiguous=source==Convention.UNKNOWN&&current!=0;
        if(source!=Convention.UNKNOWN&&source!=runtime)current=-current;
        return new Reading(current,ambiguous,source);
    }
    public static void mark(CompoundTag tag,Convention runtime,boolean ambiguous){tag.putString(MARKER,(ambiguous?Convention.UNKNOWN:runtime).marker);}
}
