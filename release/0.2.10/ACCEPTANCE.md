# 0.2.10 release acceptance

[PR #68](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/68)
allows an unchanged Power Grid wire segment to reattach to its live line
without an extra chunk-readiness hold. The train held cruise at 30 blocks per
second on both solvers while intermediate wire chunks reloaded. The Native
gauges remained available. Java recorded one tick of unavailable readings.
Separate Windows client runs confirmed that both needles stayed visible during
normal powered travel, with 95 valid moving samples on each solver.

0.2.10 reuses the accepted runtime from
`af75b674f05165e118ecf3a82e75059aeaab0de9` and preserves the original artifact
hashes and process records in the verification download. The release gate
compares every entry in the gameplay and GameTest jars. Only their version
declarations and the companion's matching dependency range change. The world
archives retain their exact save bytes with updated release labels.

- 342 headless checks passed across Windows and Linux, Native and Java, and
  BASE and PINOUT. The [continuation](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/44533192745)
  retained 334 passing checks and reran eight reference checks after
  [PR #69](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/69)
  corrected the stock Power Grid fixture. The initial attempts remain recorded.
- All 12 Windows graphical scenarios passed, including both client solvers,
  real multiplayer, clean saves and the SDK starter. The client checks used
  the same three world exports verified by the headless checks.
- Both public API jars match the published 0.2.9 SDK. Independent example
  builds and SDK starter proof are retained.

The actual test inputs included Minecraft 1.21.1, Java 21, NeoForge 21.1.262,
Create 6.0.10, CEE 1.1.4, Power Grid 0.6.2 and Architectury 13.0.12. PINOUT used
CC:Tweaked 1.120.2 and Pinout 0.2.1. The benchmark bundle carries forward the
previously published 14,650-node save. Its performance remains uncertified at
20 TPS.
