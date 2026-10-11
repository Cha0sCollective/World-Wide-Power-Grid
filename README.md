# World-Wide Power Grid

**WWPG lets Create: Electro Energetics (CEE) and Power Grid (PG) work together in the same stationary factory.** Either mod can power the other's supported equipment using their native wires, controls and tools.

**[Download WWPG 0.2.0](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.0)** for Minecraft 1.21.1 and NeoForge. Follow [installation](docs/INSTALL.md) for the required mod versions and settings. Add-on developers can also use the separately versioned **extension API 1.0.0**.

## What you can do

- Generate electricity, charge storage, run motors and pumps, light a factory and heat processing equipment across both mods.
- Build transformer and variac circuits, regulate voltage, use FE converters and send power through stationary Nether-transformer links.
- Wire, measure, switch and protect mixed circuits. Both handheld meters work across the two wire systems.
- Build and reuse saved circuit-board designs, use all 28 built-in PG board components and all 11 built-in CEE panel attachments, and repair native bulbs and fuses.
- Optionally add CC;CPG: Pinout 0.2.1 and CC:Tweaked 1.120.2 to control eight pins and read signed voltages with the add-on's native Lua API.
- Develop new stationary machines and instruments with the public API, or try its example machines, storage, panel attachment and board component in the API Workshop.

The [support list](docs/SUPPORT.md) gives the equipment and workflows covered by this release. It also explains each mod's built-in limits.

## Try the worlds

Choose **Stationary Test Yard** or **Stationary Test Yard — Pinout** from the [release downloads](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.0). Each supported native behavior has a checked exhibit or assembly/repair workflow. Signs and books explain controls, readings and resets. Goggles and tools are at spawn. The [yard guide](docs/STATIONARY_YARD.md) points to transformers, gauges, boards, panels, storage, processing and the playable Nether route.

The separate **Extension API Workshop** has eleven stations demonstrating nine new machine models, a CEE storage indicator and a PG shunt board. Install both supplied example add-ons for this world. Its [guide](docs/EXTENSION_API_WORKSHOP.md) explains how to switch loads, change voltage, discharge storage and check readings. Ordinary factories need only WWPG and its upstream dependencies.

## Release status

The release has passed checks for mixed factories, saving and reopening worlds, chunk loading, and native player tools. The downloadable worlds were tested after extraction, and the API Workshop was checked with two game clients connected to a server. [Current status](docs/STATUS.md) explains what was tested and the remaining limits; [release records](release/README.md) contain the detailed evidence.

Existing beta.5 worlds remain compatible. Moving trains and contraptions, other optional add-ons, broader mod-version support and large-server testing remain future work.

## How it works

Power Grid calculates the shared circuit's voltage and current. CEE keeps its machines, appearance, controls, heating and damage behavior. You connect the mods' terminals directly with their usual wire tools.

PG's native solver is the default on Windows and Linux. WWPG supplies its missing native files; install PG 0.6.2 as directed in the [installation guide](docs/INSTALL.md). The Java alternative receives the same functional checks.

## Documentation

| Reader | Start here |
| --- | --- |
| Players and server owners | [Installation and worlds](docs/INSTALL.md) |
| Anyone checking coverage | [Supported content](docs/SUPPORT.md) and [current status](docs/STATUS.md) |
| Maintainers | [Development and test commands](docs/DEVELOPMENT.md), [contributing](CONTRIBUTING.md) |
| Add-on developers | [Extension API contract and SDK](docs/EXTENSION_API.md), [independent examples](examples/), [API Workshop](docs/EXTENSION_API_WORKSHOP.md) |
| Reviewers checking release evidence | [Current acceptance](release/0.2.0/verification.json), [release records](release/README.md), [changelog](CHANGELOG.md) |
| Readers evaluating the long-term design | [Product specification](SPEC.md) |

The [documentation index](docs/README.md) separates current guides, developer references and archived release history.

## Upstream projects and licensing

WWPG builds on [Create: Electro Energetics](https://github.com/george8188625/Create-Electro-Energetics) and [Power Grid](https://github.com/patryk3211/PowerGrid). Each upstream mod retains ownership of its content and native gameplay.

**WWPG is open source under the [MIT License](LICENSE).** You can use, modify, fork, and redistribute WWPG, including in modpacks, while keeping its copyright and license notice. Contributions are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md).

The MIT license also applies to WWPG's own code and documentation in **0.1.0-beta.1**. The original published jar retains its older “All Rights Reserved” metadata label; current builds declare MIT and include the license. Bundled PG native resources retain their [Apache 2.0 license](src/main/resources/META-INF/POWER-GRID-LICENSE.txt) and [attribution notice](src/main/resources/META-INF/WWPG-NATIVE-NOTICE.txt). Other dependencies retain their own licenses.
