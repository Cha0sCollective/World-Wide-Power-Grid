package org.cha0scollective.wwpg.extension;

/** Keeps provider failures within the public result contract's diagnostic limit. */
final class ApiDiagnostics {
    private static final int MAX_DETAIL_LENGTH = 1024;

    private ApiDiagnostics() {}

    static String detail(String diagnostic) {
        return diagnostic.length() <= MAX_DETAIL_LENGTH ? diagnostic
                : diagnostic.substring(0, MAX_DETAIL_LENGTH - 3) + "...";
    }
}
