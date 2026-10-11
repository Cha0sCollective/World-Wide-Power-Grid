# World-Wide Power Grid

**WWPG lets Create: Electro Energetics (CEE) and Power Grid (PG) work together in the same stationary factory.** Either mod can power the other's supported equipment using their native wires, controls and tools.

**[Download WWPG 0.2.0](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.0)**. This release retains the stationary factory support and adds the independently versioned **extension API 1.0.0** for add-on developers. Install Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280, CEE 1.21.1-1.1.3, PG 0.6.2 and Architectury 13.0.8. [Installation](docs/INSTALL.md) gives the exact versions and solver settings.

## What you can do

- Generate electricity, charge storage, run motors and pumps, light a factory and heat processing equipment across both mods.
- Build transformer and variac circuits, regulate voltage, use FE converters and send power through stationary Nether-transformer links.
- Wire, measure, switch and protect mixed circuits. Both handheld meters work across the two wire systems.
- Build and reuse saved circuit-board designs, use all 28 built-in PG board components and all 11 built-in CEE panel attachments, and repair native bulbs and fuses.
- Optionally add CC;CPG: Pinout 0.2.1 and CC:Tweaked 1.120.2 to control eight pins and read signed voltages with the add-on's native Lua API.
- Develop new stationary machines and instruments with the public API, or try its example machines, storage, panel attachment and board component in the API Workshop.

The native support set remains **164 base behavior groups, or 165 with Pinout**. Cosmetic variants, ingredients and assembly parts are accounted for separately; structural parts keep their native roles. API demonstrations are recorded separately from this native inventory. See the [support list](docs/SUPPORT.md).

## Try the worlds

Choose **Stationary Test Yard** or **Stationary Test Yard — Pinout** from the [release downloads](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.0). Each supported native behavior has a checked exhibit or assembly/repair workflow. Signs and books explain controls, readings and resets. Goggles and tools are at spawn. The [yard guide](docs/STATIONARY_YARD.md) points to transformers, gauges, boards, panels, storage, processing and the playable Nether route.

The separate **Extension API Workshop** has eleven stations demonstrating nine new machine models, a CEE storage indicator and a PG shunt board. Install both supplied example add-ons for this world. Its [guide](docs/EXTENSION_API_WORKSHOP.md) explains how to switch loads, change voltage, discharge storage and check readings. Ordinary factories need only WWPG and its upstream dependencies.

## Release status

The packaged 0.2.0 release preserves **204 base checks and 208 Pinout checks**, with additional API fixtures recorded by namespace in the [final verification record](release/0.2.0/verification.json). All eight Windows/Linux × native/Java × base/Pinout combinations pass the required cold restarts, actual unload/reload cycles, pause checks, fault isolation and both saved wire systems. [CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38143489285) records the frozen source and artifact hashes.

The exact exported worlds pass their cold reopen checks. Windows graphical and real two-client API Workshop sessions run for each profile/backend combination: eleven station views and nine phases covering viewing, wiring, settings, both meters, panel editing, board editing, cutting and repair. The SDK examples also build independently after extracting the exact downloadable source and Maven ZIPs. [Current status](docs/STATUS.md) distinguishes these checks from historical failures and limitations.

API 1.0.0 has a separate version and binary contract baseline. Add-on developers can download its API, source, Javadoc and Maven artifacts, then follow the [contract and SDK guide](docs/EXTENSION_API.md). WWPG supplies the API at runtime; compile-time SDK jars do not go in `mods`.

Moving trains and contraptions, other optional integrations, broad version ranges, large-server certification and asynchronous solving remain later work. Existing beta.5 worlds and identities remain compatible.

## How it works

CEE and extension devices prepare their electrical models → WWPG updates persistent PG elements → PG solves the circuit → owners consume accepted results for gameplay and displays. This runs synchronously under one electrical schedule. PG-native add-ons keep their native simulation route.

PG's **native solver is primary**. WWPG includes verified native v7 binaries omitted from PG 0.6.2, recovered from official PG 0.6.1 with a byte-identical JNI interface. Keep PG 0.6.2 as the runtime dependency. Java receives the same functional checks.

## Documentation

| Reader | Start here |
| --- | --- |
| Upstream authors and reviewers | [One-page project overview](docs/UPSTREAM_OVERVIEW.md) |
| Players and server owners | [Installation and worlds](docs/INSTALL.md) |
| Anyone checking coverage | [Supported content](docs/SUPPORT.md) and [current status](docs/STATUS.md) |
| Maintainers | [Development and test commands](docs/DEVELOPMENT.md), [contributing](CONTRIBUTING.md) |
| Add-on developers | [Extension API contract and SDK](docs/EXTENSION_API.md), [independent examples](examples/), [API Workshop](docs/EXTENSION_API_WORKSHOP.md) |
| Reviewers checking release evidence | [Current acceptance](release/0.2.0/verification.json), [release records](release/README.md), [changelog](CHANGELOG.md) |
| Readers evaluating the long-term design | [Product specification](SPEC.md) |

## Upstream projects and licensing

WWPG builds on [Create: Electro Energetics](https://github.com/george8188625/Create-Electro-Energetics) and [Power Grid](https://github.com/patryk3211/PowerGrid). Each upstream mod retains ownership of its content and native gameplay.

**WWPG is open source under the [MIT License](LICENSE).** You can use, modify, fork, and redistribute WWPG, including in modpacks, while keeping its copyright and license notice. Contributions are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md).

The MIT license also applies to WWPG's own code and documentation in **0.1.0-beta.1**. The original published jar retains its older “All Rights Reserved” metadata label; current builds declare MIT and include the license. Bundled PG native resources retain their [Apache 2.0 license](src/main/resources/META-INF/POWER-GRID-LICENSE.txt) and [attribution notice](src/main/resources/META-INF/WWPG-NATIVE-NOTICE.txt). Other dependencies retain their own licenses.
