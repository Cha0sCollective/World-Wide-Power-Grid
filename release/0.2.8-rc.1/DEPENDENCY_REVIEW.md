# Runtime input review, November 10, 2026

The campaign selects the newest published NeoForge / Minecraft 1.21.1 inputs
reviewed before freezing. `runtime-selection.json` records exact selected versions,
download URLs, SHA256 values and the publisher's release classification.
The adjacent version-list JSON and NeoForge XML preserve the metadata response.

- [NeoForge Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml): 21.1.265 is the current 21.1 build.
- Modrinth project version metadata: CEE 1.1.4, Create 6.0.10, Architectury
  13.0.11, CC:Tweaked 1.121.0, Pinout 0.2.1 and Gauged Wires 1.0.6a.
  CC:Tweaked labels 1.121.0 beta; its earlier 1.120.2 was labelled alpha.
- [Power Grid files](https://www.curseforge.com/minecraft/mc-mods/power-grid/files/all): latest NeoForge 1.21.1 file remains PG 0.6.2 / 8880715.
- [Power Components files](https://www.curseforge.com/minecraft/mc-mods/create-power-components/files/all): latest file remains 1.3.0 / 9031604.

Selecting a file is not an acceptance result. The final full campaign must verify
these actual loaded files on its named environments. Minimum compile dependencies
and older loader compatibility lanes remain separate. No performance-mod bundle,
collector change or rejected worker/cache experiment is added to this release.
