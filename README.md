# World-Wide Power Grid

**World-Wide Power Grid (WWPG)** lets **Create: Electro Energetics (CEE)** and **Power Grid (PG)** share electrical circuits. Use either mod's native wire tools to connect supported terminals across both mods. PG solves the circuit; CEE keeps its machines, controls, visuals, heat, damage, and other gameplay.

The latest beta, **0.1.0-beta.2**, focuses on stationary factories. It fixes handheld measurements across the two mods and gives the example heater enough power to work as a burner. See the [GitHub release](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.2).

## Try the beta

[Download the mod jar](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.1.0-beta.2/wwpg-0.1.0-beta.2.jar) or [the complete bundle](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.1.0-beta.2/wwpg-0.1.0-beta.2-release.zip), which includes the example world, documentation, and test evidence.

Use **Minecraft 1.21.1**, **NeoForge 21.1.231**, **Java 21**, **Create 6.0.10-280**, **CEE 1.21.1-1.1.3**, **PG 0.6.2**, and **Architectury 13.0.8**. Follow the [installation guide](docs/INSTALL.md), including PG's solver settings.

## What works in-game

**You can build a stationary factory that uses CEE and Power Grid equipment together on the same electrical circuit.** With the [supported equipment](docs/SUPPORT.md), you can:

- **Share power between the mods.** Run CEE equipment from a Power Grid source, or Power Grid equipment from a CEE source.
- **Wire your factory with either mod's tools.** Connect supported terminals across both mods, add branches, and cut or reconnect wires using the usual interactions.
- **Run useful machines.** Power motors to drive Create machinery, pumps to move water through pipes and tanks, lights to illuminate your factory, and heaters to produce heat.
- **Store and change power.** Charge and discharge CEE accumulators and capacitors in mixed circuits, change voltage with transformers and variacs, and use AC generation and equipment.
- **Control and protect circuits.** Turn loads on and off with switches and relays, adjust power with electrical controls, read meters, and use fuses and breakers that trip when overloaded.
- **Check your wiring with handheld meters.** Use PG's multimeter on either mod's terminals or wires, and CEE's clamp meter on either mod's wires. [Meter instructions](docs/INSTALL.md#handheld-meters) explain the controls.
- **Build control panels and electronics.** Use all **11 built-in CEE panel attachments** and all **28 built-in Power Grid circuit-board components** in mixed circuits, including switches, indicators, gauges, and displays.

You still place, assemble, configure, and repair equipment through each mod's normal gameplay. PG's portable battery keeps its normal item-charging role; use a CEE accumulator to store power and supply it back to a wired circuit.

The [example world](docs/INSTALL.md#example-world) lets you try a PG-powered CEE pump, lights, and heater, plus a CEE-powered PG capacitor board controlled by a CEE panel.

## Beta status

The original beta passed **114 automated checks** on Windows and Linux. Beta.2 expands the suite to **117**, adding handheld-meter checks and real-client measurement tests. Further checks cover saving and reopening worlds and **two players wiring, configuring, and measuring the same circuit**. See [current test results](docs/STATUS.md) and the [original release acceptance](docs/FIRST_RELEASE.md).

**One reload issue remains unexplained:** an earlier test found a circuit had no power after a section of the world was unloaded and loaded again. Later runs passed, but we have not confirmed whether this was a test-timing problem or a gameplay bug. See [current status and known issues](docs/STATUS.md).

This release covers the stationary equipment listed above. Moving trains and contraptions, optional add-ons, and very large power grids are outside its tested scope. The [support list](docs/SUPPORT.md) identifies the exact equipment covered and items still unverified.

## How it works

```text
CEE prepares its electrical models
             ↓
WWPG updates persistent PG nodes and elements
             ↓
PG solves the circuit using its substep schedule
             ↓
WWPG returns electrical results to CEE's gameplay systems
```

PG's **native solver is primary**. WWPG includes the verified native v7 binaries omitted from the pinned PG 0.6.2 jar, recovered from official PG 0.6.1 with a byte-identical JNI interface. PG 0.6.1 is used only as a binary source during the build. Java remains a tested fallback.

## Documentation

| Reader | Start here |
| --- | --- |
| Upstream authors and reviewers | [One-page project overview](docs/UPSTREAM_OVERVIEW.md) |
| Players and server owners | [Installation and example world](docs/INSTALL.md) |
| Anyone checking coverage | [Supported content](docs/SUPPORT.md) and [current status](docs/STATUS.md) |
| Maintainers | [Development and test commands](docs/DEVELOPMENT.md), [contributing](CONTRIBUTING.md) |
| Reviewers checking release evidence | [Beta acceptance](docs/FIRST_RELEASE.md), [release records](release/README.md), [changelog](CHANGELOG.md) |
| Readers evaluating the long-term design | [Product specification](SPEC.md) |

## Upstream projects and licensing

WWPG builds on [Create: Electro Energetics](https://github.com/george8188625/Create-Electro-Energetics) and [Power Grid](https://github.com/patryk3211/PowerGrid). Each upstream mod retains ownership of its content and native gameplay.

**WWPG is open source under the [MIT License](LICENSE).** You can use, modify, fork, and redistribute WWPG, including in modpacks, while keeping its copyright and license notice. Contributions are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md).

The MIT license also applies to WWPG's own code and documentation in **0.1.0-beta.1**. The original published jar retains its older “All Rights Reserved” metadata label; current builds declare MIT and include the license. Bundled PG native resources retain their [Apache 2.0 license](src/main/resources/META-INF/POWER-GRID-LICENSE.txt) and [attribution notice](src/main/resources/META-INF/WWPG-NATIVE-NOTICE.txt). Other dependencies retain their own licenses.
