package org.cha0scollective.wwpg.api;

/** Published API version, independently versioned from the WWPG runtime. */
public final class WWPGApi {
    /** Installed contract version, read at runtime rather than inlined into add-ons. */
    public static final String API_VERSION = version();
    /** Return the installed API's semantic version, independent of the mod version. */
    public static String version() { return "1.0.0"; }
    /** Maximum supported explicit terminal number. */
    public static final int MAX_TERMINAL_ID = 4095;
    /** Maximum number of terminals and internal nodes in one device. */
    public static final int MAX_NODES = 4096;
    /** Maximum number of independently identified elements in one device. */
    public static final int MAX_ELEMENTS = 8192;

    private WWPGApi() { }
}
