package org.cha0scollective.wwpg.bridge;

public record BranchKey(EndpointKey first, EndpointKey second) {
    public static BranchKey of(EndpointKey a, EndpointKey b) {
        return a.compareTo(b) < 0 ? new BranchKey(a, b) : new BranchKey(b, a);
    }
}
