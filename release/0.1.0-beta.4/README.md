# Beta.4 release record

**0.1.0-beta.4** publishes the pending-wire cleanup and board-capacitor persistence corrections from [PR #7](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/7). Dependencies, native resources, solver scheduling, and the stationary support set are unchanged. The expanded yard and visible panel demo are reused unchanged; existing worlds do not need replacing.

- [Release notes](RELEASE_NOTES.md) explain the player-visible changes and upgrade steps.
- `verification.json` identifies the final jar, its comparison with the tested PR #7 candidate, four equation checks, and native/Java downloaded-yard checks.
- `ci.json` records the versioned source commit, four Windows/Linux native/Java jobs, full packaged SETUP/VERIFY suites, upstream reference circuits, example restarts, and hashes of the downloaded CI jars.
- `distribution.json` identifies the published downloads and their hashes.
- [Restoration investigation](../fixes/restoration/) retains the negative controls and earlier diagnostic evidence. Historical release records remain unchanged.

The packaged suite has 123 checks. No new graphical-client or two-player checks are claimed for this release; see the earlier beta.2/beta.3 records for that evidence. PG's design-table saved-design error remains open. The old intermittent failures lacked readiness/history diagnostics, so the reproduced restoration defects cannot conclusively explain every historical zero reading.

Build with Java 21 using `./gradlew build`, then use `tools/package_beta4.py` after the complete versioned CI matrix and native/Java downloaded-yard checks pass. That packager verifies the exact jar, retained source hashes, and evidence before producing release downloads. Historical packaging scripts reproduce their respective releases.
