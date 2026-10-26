# WWPG 0.2.3 preparation

This patch prepares exposed CEE terminal targeting for PG wires and the separate
GameTest companion. PG copper, iron, golden and insulated copper selection uses
the accessible native CEE boxes, including regulator upper posts and capacitor
pins outside the ordinary block outline. Client crosshair picking and server
wire selection share the same path while holding a PG wire. Native reach,
obstructions, terminal IDs, wire costs and electrical behavior are retained.

The player jar keeps gameplay code, public APIs, Lua programs, native binaries
and their provenance. Tests and fixtures are installed separately for acceptance
and retained in the verification bundle. Ordinary players install only the mod
jar. All earlier tests remain, with five targeting regressions added.

Focused dev.102 checks passed 34 selected wire/distribution GameTests on each
solver, six companion-packaging checks and 33 suite-tooling checks. They include
regulator rotations/stacking, both capacitor roll states in all six facings,
reach, obstruction, removal and a powered circuit completed through actual
player rays and PG's wire handler. These records are focused headless checks;
they do not certify real-client visuals or complete release acceptance.

**Publication is pending full headless and graphical release acceptance.**
The user must explicitly request those lanes before they run. Earlier dev.101
full headless acceptance and 0.2.2 graphical records keep their actual identities;
they cannot certify the changed targeting runtime. API 1.0.0, native solver
binaries, saves and all sixteen substeps are unchanged. The 14,650-node speed
target remains uncertified. Published 0.2.2 assets and records are preserved.

Use [the release process](../../docs/RELEASING.md) and
`tools/release_downloads.py` to stage exactly five downloads after acceptance:
the mod jar, example-worlds ZIP, developer-SDK ZIP, verification ZIP and checksums.
No release has been published by this preparation record.
