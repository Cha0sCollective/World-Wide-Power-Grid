# Install and try WWPG

**0.1.0-beta.1** is the first stationary compatibility beta. Read [current status](STATUS.md) for the unresolved chunk-reload test failure and [supported content](SUPPORT.md) for the release's coverage.

## Required versions

Install this exact combination on the server and every client. Use Java 21.

| Dependency | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.231 |
| Create | 6.0.10-280 |
| Create: Electro Energetics | 1.21.1-1.1.3 |
| Power Grid | Minecraft 1.21.1, 0.6.2 |
| Architectury API | NeoForge 13.0.8 |
| WWPG | 0.1.0-beta.1 |

Use Create's full published release jar. CEE, PG, and Create download references and checksums are recorded in [artifacts.json](../release/artifacts.json). The WWPG download contains the compatibility mod and native solver resources; install the required upstream mods separately.

## Installation

1. [Download the WWPG jar](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.1.0-beta.1/wwpg-0.1.0-beta.1.jar).
2. Put it and the required dependency jars in the instance's `mods` directory.
3. Launch once to generate configuration, then stop the game or server.
4. In `config/powergrid-server.toml`, edit the existing solver settings to the values below. Keep the other PG solver settings at their defaults.
5. Restart, build or load a small supported circuit, and run `/wwpg` to check which backend has performed simulation steps.

```toml
solverBackend = "NATIVE"
multiTicks = 16
```

The release's AC, motor, storage, and electronics tests use 16 PG electrical substeps. CEE's `microTicks` setting does not set the simulation schedule with WWPG installed. Stop and restart after changing the solver configuration.

## Native solver requirements

WWPG includes the verified native v7 DLL/SO from official PG 0.6.1 because the pinned PG 0.6.2 jar omitted those files. Their `NativeMNA.class` JNI interfaces are byte-identical. **PG 0.6.2 is the runtime dependency**; keep the 0.6.1 binary-source jar out of `mods`.

Before PG loads its backend, WWPG installs a missing binary into `.pg-native` in the game's working directory. It preserves an existing administrator-installed binary. Native resources retain PG's license and attribution.

| Tested native platform | Requirement |
| --- | --- |
| Windows | x86-64 |
| Linux | x86-64; glibc 2.38 or newer. Release testing used Ubuntu 24.04. |

PG retains its Java backend and fallback. The same electrical suite has passing Java runs on both operating systems. Check `/wwpg` for the backend actually used; selecting native in the configuration is not proof that it loaded.

## Wiring and diagnostics

Use the upstream wire, spool, and cord tools on supported terminals. Wire lengths, types, costs, and cutting follow their native workflows. PG cords use sockets, junctions, and split ends; hidden cord terminals are not bare-wire attachment points. CEE insulators and structural poles retain their native role.

These diagnostic commands require operator permission level 2:

| Command | What it shows |
| --- | --- |
| `/wwpg` | Mapping counts, simulation steps, backend counts, and CEE solve attempts |
| `/wwpg errors` | Recent compatibility errors |
| `/wwpg at x y z` | Mapped block terminals, voltage, backend, and isolation state |

## Example world

Download [the example-world ZIP](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.1.0-beta.1/wwpg-0.1.0-beta.1-example.zip) and extract `WWPG Example/` into the client's `saves` directory. For a dedicated server, use that directory as the server's level directory. Install the dependencies and solver settings above first.

Spawn is at `(8, 64, 16)`:

- **Northern circuit:** a CEE source supplies a PG resistor and capacitor board through a CEE control/meter panel. The panel's **Factory enable** switch controls the storage circuit.
- **Southern circuit:** a PG source powers CEE lighting, heating, and a water pump connected to Create tanks and pipes.

The world has passed creation and restart checks. It provides a repeatable small demonstration; it does not establish large-network performance. Maintainers can rebuild it with [the development commands](DEVELOPMENT.md#example-world).

## Pinned upstream behavior

PG's portable battery charges a native energy item. Its placed block does not discharge into a wired circuit as a voltage source; use CEE's accumulator for reversible circuit storage.

In the pinned CEE 1.1.3 tests, the creative battery's AC mode produces a 20 Hz waveform despite its frequency selector. WWPG preserves that behavior. A PG AC source or driven CEE alternator provides other frequencies.
