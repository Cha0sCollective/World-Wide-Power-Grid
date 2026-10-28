0.2.4 fixes CEE electric train power interruptions at overhead-wire transitions and protects train readings from unrelated dimensions. API 1.0.0 and existing saves stay compatible.

- Preserve continuous train connections when crossing overhead-wire joints or adjacent contacts, avoiding false power drops.
- Keep train readings, stored energy and traction updates in their owning dimension instead of letting another world overwrite them.
- Avoid recursive API circuit loading during startup, and recover from unavailable train voltage while keeping genuine failed readings visible.

## Download

**For ordinary play, download the mod jar and install the required upstream mods.**

| I want to… | Download |
| --- | --- |
| Install WWPG | [Mod jar](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.4/wwpg-0.2.4.jar) |
| Try ready-built circuits | [Example worlds](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.4/wwpg-0.2.4-example-worlds.zip) |
| Develop an add-on | [Developer SDK](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.4/wwpg-0.2.4-developer-sdk.zip) |
| Inspect test records | [Verification archive — optional; not needed to play](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.4/wwpg-0.2.4-verification.zip) |

## Install

Use Minecraft 1.21.1, NeoForge 21.1.231 or 21.1.248, Java 21, Create 6.0.10-280, CEE 1.1.3, Power Grid 0.6.2 and Architectury 13.0.8. Match server and client mod versions.

[Installation guide](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/INSTALL.md) · [Supported equipment and limits](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/SUPPORT.md) · [Checksums](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.4/wwpg-0.2.4-checksums.txt)
