# Expanded test yard, revision 1

This additional world uses the published **WWPG 0.1.0-beta.2 jar** and its exact dependency versions. Download and player instructions are in [FIXTURE_WORLD.md](../../../docs/FIXTURE_WORLD.md).

The yard contains 25 electrical board stations, a routing board, all 11 built-in CEE panel attachment types, seven transformer/variac circuits, three meter banks, 22 additional device/storage circuits, and the original pump/heater/relay factory. It includes a guidebook, signs, goggles, both handheld meters, and parts cabinets.

[coverage.json](coverage.json) distinguishes working exhibits from parts in cabinets or assembly references. All 28 PG board component types and 11 CEE panel types appear in live circuits; the remaining equipment in cabinets is available for players' own builds. The fixture tests validate electrical behavior and native controls, rather than treating the presence of an item as proof of operation.

The builder removes loose wire/spool items left by its setup and repair checks before saving, and asserts that connected PG wire/cord entities are retained. This is an export cleanup; it does not remove players' dropped items during ordinary play.

[verification.json](verification.json) records local creation/restart, reopening the exact exported ZIP with both solvers, equation checks, and artifact hashes. [ci.json](ci.json) records four Windows/Linux native/Java jobs, including the 118-check regression suite, reference circuits, the smaller example, and the expanded yard.

The test harness adds GameTest classes and one empty GameTest namespace template. Its template is compared with the original empty template, and every remaining non-GameTest jar entry is compared byte for byte with the published beta.2 runtime. The distributed mod jar, original worlds, bundles, checksums, tags, and previous evidence remain unchanged.

This world does not resolve the earlier intermittent reload/restart failures. See [current status](../../../docs/STATUS.md). No new real-client visual or multiplayer acceptance is claimed for this world; its automated world checks are separate from the earlier beta's real-client evidence.
