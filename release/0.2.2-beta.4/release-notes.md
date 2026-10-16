Beta.4 fixes circuit ownership after rebuilding or unloading, rejects unsafe native solver results, and restores handheld readings on wire and battery/generator connectors. API 1.0.0 and existing saves stay compatible.

- Keep matrix batches with their owning solver and regroup disconnected circuits after rebuilds and unloads.
- Reject failed factorizations, invalid permutations and nonfinite results; hold stored energy and preserve diagnostics during rejection.
- Keep multimeter readings consistent with accepted terminal samples, including compressed wires and refreshed battery/generator connectors.

## Download

**For ordinary play, download the mod jar and install the required upstream mods.**

| I want to… | Download |
| --- | --- |
| Install WWPG | [Mod jar](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.2-beta.4/wwpg-0.2.2-beta.4.jar) |
| Try ready-built circuits | [Example worlds](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.2-beta.4/wwpg-0.2.2-beta.4-example-worlds.zip) |
| Develop an add-on | [Developer SDK](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.2-beta.4/wwpg-0.2.2-beta.4-developer-sdk.zip) |
| Inspect test records | [Verification archive — optional; not needed to play](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.2-beta.4/wwpg-0.2.2-beta.4-verification.zip) |

## Install

Use Minecraft 1.21.1, NeoForge 21.1.231 or 21.1.248, Java 21, Create 6.0.10-280, CEE 1.1.3, Power Grid 0.6.2 and Architectury 13.0.8. Match server and client mod versions.

[Installation guide](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/INSTALL.md) · [Supported equipment and limits](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/SUPPORT.md) · [Checksums](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.2-beta.4/wwpg-0.2.2-beta.4-checksums.txt)
