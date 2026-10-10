# Beta.3 release record

**0.1.0-beta.3** publishes the grounding correction from [PR #5](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/5). It retains beta.2's dependency versions, native resources, handheld meters, startup fix, and stationary content set. The expanded yard and visible panel demo are reused unchanged; existing worlds do not need replacing.

- [Release notes](RELEASE_NOTES.md) explain the change and upgrade steps.
- `verification.json` records the final jar, real-client checks, and comparison with the previously tested grounding-fix jar. Only mod-version metadata may differ.
- `ci.json` records Windows/Linux native/Java packaged tests, references, example restarts, and hashes of the four CI jars.
- `distribution.json` identifies the published downloads and their hashes.

The release's verification archive retains logs and earlier failure evidence. Passing retries do not resolve the intermittent chunk-reload or board-capacitor restart issues. The PG circuit design-table saved-design error is also still open; see [current status](../../docs/STATUS.md).

The packaged suite has 121 checks, including three grounding regressions. The separate graphical-client driver opens the exact downloaded expanded yard and checks 69 stations, 54 synchronized gauges, factory delay and panel packets, and 282 retained PG wire entities. Its helper mod is excluded from the release jar. Earlier two-client and handheld-item interaction evidence remains separate.

Build with Java 21 using `./gradlew build`, then use `tools/package_beta3.py` after the versioned client checks and complete CI matrix pass. Version-specific beta.1/beta.2 packaging scripts and records reproduce their historical releases; they are not beta.3 packagers.
