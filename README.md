# World-Wide Power Grid

**World-Wide Power Grid (WWPG)** lets **Create: Electro Energetics (CEE)** and **Power Grid (PG)** share electrical circuits. Use either mod's native wire tools to connect supported terminals across both mods. PG solves the circuit; CEE keeps its machines, controls, visuals, heat, damage, and other gameplay.

The first release, **0.1.0-beta.1**, focuses on stationary factories. It is [published on GitHub](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.1), and its implementation is merged into `main`.

## Try the beta

[Download the mod jar](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.1.0-beta.1/wwpg-0.1.0-beta.1.jar) or [the complete bundle](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.1.0-beta.1/wwpg-0.1.0-beta.1-release.zip), which includes the example world, documentation, and test evidence.

Use **Minecraft 1.21.1**, **NeoForge 21.1.231**, **Java 21**, **Create 6.0.10-280**, **CEE 1.21.1-1.1.3**, **PG 0.6.2**, and **Architectury 13.0.8**. Follow the [installation guide](docs/INSTALL.md), including PG's solver settings.

## What the beta covers

The declared support set covers factory generation, motors, pumping, lighting, heating, storage, capacitors and inductors, transformers, wiring, controls, protection, and meters. It includes **all 11 built-in CEE panel attachments** and **all 28 built-in PG board components**. Native placement, assembly, configuration, and interaction workflows remain in use.

The [support matrix](docs/SUPPORT.md) records the checked behaviors and additional unverified content. Moving systems, optional integrations, and large-network testing are deferred.

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

## Current status

The release has passing runs of **114 packaged electrical, gameplay, and lifecycle tests** on Windows and Linux with native and Java backends. Additional checks cover electrical equations, saved upstream reference circuits, example-world restart, and two real multiplayer clients. The implementation's [main-branch CI run passed](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/37990481752).

An earlier Linux/native CI run failed a chunk-reload assertion. Later runs passed, but the cause remains unresolved. This is a beta with a declared stationary support set; see [current status and known issues](docs/STATUS.md) before evaluating it.

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

WWPG's project license has not yet been selected; the mod metadata currently says **All Rights Reserved**. Bundled PG native resources retain their [Apache 2.0 license](src/main/resources/META-INF/POWER-GRID-LICENSE.txt) and [attribution notice](src/main/resources/META-INF/WWPG-NATIVE-NOTICE.txt).
