# WWPG 0.2.6 release record

[Downloads](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.6)
· [Verification](verification.json) · [Download hashes](download-layout.json).

Promotes dev.116 with the verified CIO inverter, safe redundant-coupler disabling,
initialization/history classification and Sable terminal-picking fixes. Public
API 1.0.0, native binaries, saves and all 16 substeps remain unchanged.

The exact gameplay jar passed all 342 local Windows/Linux headless jobs on
Native/Java, BASE/PINOUT and NeoForge 21.1.231/21.1.248. All 12 separate Windows
client scenarios passed with successful save/quit or disconnect/save/stop,
and zero WWPG warning/error lines. Both extracted SDK examples and starter
compiled independently. The companion keeps tests and fixtures out of gameplay.

Full lanes use CEE 1.1.3; focused CEE 1.1.4, Gauged, CIO and Sable records retain
their original execution identities. Arbitrary third-party builds, every moving
assembly's rendering and 20 TPS on the 14,650-node yard are not certified.
The benchmark save is retained with release metadata only; no new benchmark ran.

Later publication and documentation commits retain identical protected runtime,
SDK and helper inputs. Reused acceptance stays explicitly labelled and retains
the tested source. Original failed attempts, including the resolved Java-tool
selection attempt, remain preserved.
