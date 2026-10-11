# World-Wide Power Grid

**WWPG lets Create: Electro Energetics (CEE) and Power Grid (PG) work together in the same stationary factory.** Either mod can power the other's supported equipment using their native wires, controls and tools.

**[Download 0.1.0-beta.5](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.5)**, the stationary feature-complete beta. Install Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280, CEE 1.21.1-1.1.3, PG 0.6.2 and Architectury 13.0.8. [Installation](docs/INSTALL.md) gives the exact versions and solver settings.

## What you can do

- Generate electricity, charge storage, run motors and pumps, light a factory and heat processing equipment across both mods.
- Build transformer and variac circuits, regulate voltage, use FE converters and send power through stationary Nether-transformer links.
- Wire, measure, switch and protect mixed circuits. Both handheld meters work across the two wire systems.
- Build and reuse saved circuit-board designs, use all 28 built-in PG board components and all 11 built-in CEE panel attachments, and repair native bulbs and fuses.
- Optionally add CC;CPG: Pinout 0.2.1 and CC:Tweaked 1.120.2 to control eight pins and read signed voltages with the add-on's native Lua API.

The release covers **164 base behavior groups, or 165 with Pinout**. Cosmetic variants, ingredients and assembly parts are accounted for separately; structural parts keep their native roles. See the [support list](docs/SUPPORT.md).

## Try the stationary yards

Choose **Stationary Test Yard** or **Stationary Test Yard — Pinout** from the [release downloads](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.5). Each supported behavior has a checked exhibit or native assembly/repair workflow. Signs and books explain the controls, expected readings and reset steps. Goggles and tools are at spawn. The [yard guide](docs/STATIONARY_YARD.md) points to transformers, gauges, boards, panels, storage, processing and the playable Nether route.

## Beta status

The packaged release passes **204 checks in the base profile and 208 with Pinout** on Windows and Linux using both native and Java solvers. Acceptance includes three consecutive cold restarts, five actual assembly unload/reload cycles, pinned upstream reference circuits, the exact exported ZIPs, full graphical yard visits and two real multiplayer clients in each profile/backend combination. [CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38116785029) and the [final verification record](release/0.1.0-beta.5/verification.json) retain the evidence.

Beta.5 repairs saved design-table loading, fuse saving and wiring through native repair, small CEE diode updates, and nonlinear component removal during chunk unloading. Older intermittent failures remain documented where their original logs did not establish a cause. Read [current status](docs/STATUS.md).

Moving trains and contraptions, other optional integrations, broad version ranges, large-server certification, asynchronous solving and a public extension API remain later work. Existing beta.4 worlds and identities remain compatible.

## How it works

CEE prepares its electrical models → WWPG updates persistent PG elements → PG solves the circuit → CEE consumes the results for gameplay, heat, protection and displays. This runs synchronously under one electrical schedule.

PG's **native solver is primary**. WWPG includes verified native v7 binaries omitted from PG 0.6.2, recovered from official PG 0.6.1 with a byte-identical JNI interface. Keep PG 0.6.2 as the runtime dependency. Java receives the same functional checks.

## Documentation

| Reader | Start here |
| --- | --- |
| Upstream authors and reviewers | [One-page project overview](docs/UPSTREAM_OVERVIEW.md) |
| Players and server owners | [Installation and example world](docs/INSTALL.md) |
| Anyone checking coverage | [Supported content](docs/SUPPORT.md) and [current status](docs/STATUS.md) |
| Maintainers | [Development and test commands](docs/DEVELOPMENT.md), [contributing](CONTRIBUTING.md) |
| Reviewers checking release evidence | [Current acceptance](release/0.1.0-beta.5/verification.json) and [first-release history](docs/FIRST_RELEASE.md), [release records](release/README.md), [changelog](CHANGELOG.md) |
| Readers evaluating the long-term design | [Product specification](SPEC.md) |

## Upstream projects and licensing

WWPG builds on [Create: Electro Energetics](https://github.com/george8188625/Create-Electro-Energetics) and [Power Grid](https://github.com/patryk3211/PowerGrid). Each upstream mod retains ownership of its content and native gameplay.

**WWPG is open source under the [MIT License](LICENSE).** You can use, modify, fork, and redistribute WWPG, including in modpacks, while keeping its copyright and license notice. Contributions are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md).

The MIT license also applies to WWPG's own code and documentation in **0.1.0-beta.1**. The original published jar retains its older “All Rights Reserved” metadata label; current builds declare MIT and include the license. Bundled PG native resources retain their [Apache 2.0 license](src/main/resources/META-INF/POWER-GRID-LICENSE.txt) and [attribution notice](src/main/resources/META-INF/WWPG-NATIVE-NOTICE.txt). Other dependencies retain their own licenses.
