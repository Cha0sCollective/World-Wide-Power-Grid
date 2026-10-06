# World-Wide Power Grid

WWPG makes **Create: Electro Energetics (CEE)** and **Power Grid (PG)** operate as one stationary electrical system. Both mods' wires connect directly to their native terminals. PG owns the electrical solve; CEE keeps its machines, controls, visuals, heat, damage and other gameplay.

**0.1.0-beta.1** targets Minecraft **1.21.1**, NeoForge **21.1.231**, Java **21**, Create **6.0.10-280**, CEE **1.21.1-1.1.3**, PG **0.6.2** and Architectury **13.0.8**. Use the published upstream jars and the exact versions in [the installation guide](docs/INSTALL.md).

The declared stationary support set includes factory generation, motors, pumping, lighting, heating, storage, reactives, transformers, wiring, protection, controls and meters; **all 11 built-in CEE panel attachments and all 28 built-in PG board components** are included. [The support checklist](docs/SUPPORT.md) records the bounded behaviors and unclaimed content.

PG's **native solver is primary**. WWPG includes the verified native v7 binaries missing from PG 0.6.2, taken from its official 0.6.1 artifact with the same JNI interface. The 0.6.1 mod is not a runtime dependency. Java remains a tested fallback/regression backend.

```text
CEE preparation → topology/parameter updates → PG substeps
                                              ↓
                    CEE gameplay ← compatible electrical results
```

The packaged jar passes 114 electrical/gameplay/lifecycle fixtures with both backends on Windows and Linux, plus equation checks, upstream saved-world comparisons, an example-world restart and a two-client TCP wiring/configuration check. [Verification evidence](release/verification.json) distinguishes local results from the committed CI workflow. [Release hashes](release/distribution.json) identify the local jar and example-world ZIP.

```sh
./gradlew build --no-daemon
```

Use a Java 21 JDK; Windows users can run `gradlew.bat`. The jar is `build/libs/wwpg-0.1.0-beta.1.jar`. Release delivery files are in `build/distributions`. Follow [INSTALL.md](docs/INSTALL.md) for solver configuration and importing the example world.

- [Installation](docs/INSTALL.md)
- [Supported content and limitations](docs/SUPPORT.md)
- [Release acceptance](docs/FIRST_RELEASE.md)
- [Development and test commands](docs/DEVELOPMENT.md)
- [Changelog](CHANGELOG.md)
- [Long-term specification](SPEC.md) and [contribution workflow](CONTRIBUTING.md)

Moving systems, Sable sublevels, optional integrations and server-wide scale certification are deferred. This beta does not claim every upstream item or configuration. Unknown electrical constructs are diagnosed and isolated without a second solver or stale results.

Upstream projects: [Create: Electro Energetics](https://github.com/george8188625/Create-Electro-Energetics) and [Power Grid](https://github.com/patryk3211/PowerGrid). WWPG is an interoperability mod, not a replacement distribution of either project.

A project license has not yet been selected. Bundled PG native resources retain their Apache license and attribution. This local beta preparation does not publish a GitHub or mod-site release.
