# Install and try WWPG

**0.2.0** retains the declared stationary workflows and optional Pinout profile, and adds extension API 1.0.0 for add-ons. Read [current status](STATUS.md) for verification, [supported content](SUPPORT.md) for the exact claim, and the [stationary yard guide](STATIONARY_YARD.md) for hands-on tests.

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
| WWPG | 0.2.0 |

Use Create's full published release jar. CEE, PG, and Create download references and checksums are recorded in [artifacts.json](../release/artifacts.json). The WWPG download contains the compatibility mod and native solver resources; install the required upstream mods separately.

## Optional computer-control profile

For Pinout support, additionally install **CC;CPG: Pinout 0.2.1** and **CC:Tweaked 1.120.2** on the server and clients. The base jar works without them. Use the separately named Pinout world; [locked artifacts and checksums](../release/optional-artifacts.json) identify the downloads.

## Installation

1. [Download the WWPG jar](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.0/wwpg-0.2.0.jar).
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

## Handheld meters

- **Power Grid multimeter, voltage:** right-click two accessible terminals to attach the probes. These can be CEE terminals, PG terminals, or one of each. Walk around a machine if its other terminal is on the back. Shift-right-click releases the probes.
- **Power Grid multimeter, current:** right-click a wire from either mod. Selecting a wire switches to current mode; selecting a terminal switches back to voltage mode.
- **CEE clamp meter, current:** hold right-click while aiming at a wire from either mod. Release right-click or look away to stop measuring. A clamp measures a wire's current, not a terminal's voltage.

When upgrading, stop the game/server, back up your world, and replace the older WWPG jar on the server and every client. Keep only one WWPG jar in `mods`. Keep the same dependency versions and solver settings; existing beta.5 worlds and saved identities remain compatible with 0.2.0 and do not need to be downloaded again. The update cannot recover wires or charge already lost and saved by an older version. The original beta.1 example remains underpowered at its heater; use the updated example below for the working burner demonstration.

## Diagnostic commands

These diagnostic commands require operator permission level 2:

| Command | What it shows |
| --- | --- |
| `/wwpg` | Mapping counts, simulation steps, backend counts, and CEE solve attempts |
| `/wwpg errors` | Recent timestamped compatibility errors |
| `/wwpg topology` | Recent structural changes and device identities |
| `/wwpg at x y z` | Mapped block terminals, voltage, backend, and isolation state |

## Expanded test yard

Download [Stationary Test Yard](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.0/wwpg-0.2.0-stationary-yard.zip) for the base installation, or [Stationary Test Yard — Pinout](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.0/wwpg-0.2.0-stationary-yard-pinout.zip) with the optional mods. Extract the named save folder into `saves`. The [station guide](STATIONARY_YARD.md) explains controls, expected readings and resets. Goggles and tools are at spawn. Every claimed behavior maps to a checked exhibit or native assembly/repair workflow. The ZIP retains native Nether loading settings, connected wires and optional computer programs. Loose construction wire drops are removed before export.

## Extension API Workshop

Download [Extension API Workshop](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.0/wwpg-0.2.0-api-workshop.zip), or its [Pinout installation edition](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.0/wwpg-0.2.0-api-workshop-pinout.zip). This separate world requires both example add-ons on the server and every client:

- [General API machines](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.0/wwpg-api-example-1.0.0.jar)
- [Native panel and board extensions](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.0/wwpg-upstream-api-example-1.0.0.jar)

The Pinout edition also requires the two optional mods above. Its eleven Workshop stations are the same; computer demonstrations live in the Pinout Stationary Yard. Ordinary factories do not require either example add-on.

Extract the named save folder into `saves`. Spawn is `(9004,64,8995)` and the tool chest is `(9000,64,8997)`. Wear goggles and follow the white paths. Empty-hand right-click on an API block prints readings; sneak-click changes its setting. The [Workshop guide](EXTENSION_API_WORKSHOP.md) gives expected outputs and resets. Keep a clean copy before destructive experiments.

If the PG design editor's name field or buttons are cut off, open **Options → Video Settings → GUI Scale** and reduce the scale. GUI Scale 2 displays the complete editor in a 1280 × 720 window.

If a terminal is still loading or its old wires await retirement, the wire tool displays **Device is loading. Try again in a moment.** Wait for the area to finish loading, then retry.

For development, follow the [API SDK guide](EXTENSION_API.md). The runtime WWPG jar supplies both APIs. Compile-time API jars belong in the build's dependencies; keep them out of `mods` and do not shade their classes into an add-on.

## Historical small example

Download [the revised example-world ZIP](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.1.0-beta.2/wwpg-0.1.0-beta.2-example-v2.zip) and extract `WWPG Example - Panel Demo/` into the client's `saves` directory. It appears as **WWPG - Panel Relay Demo** in the world list and was verified with **beta.5**. Its historical verification remains available; use the current Stationary Yard for the 0.2.0 acceptance world. Its original beta.2 filename is retained because the world is unchanged. For a dedicated server, use that directory as the server's level directory. Install the dependencies and solver settings above first. The earlier example remains available as a separate download; this revision has its own save folder.

Spawn is at `(8, 64, 16)`:

- **Northern circuit:** the CEE panel controls a PG board containing a relay and capacitor. The relay routes a separate 300 V PG supply to two CEE lamps, mounted on green and red blocks and labeled **RUNNING** and **OFF**. The lamps' power passes through the board's contacts.
- **Southern circuits:** a 300 V PG source powers CEE lighting and a water pump connected to Create tanks and pipes. A separate 600 V PG source powers the CEE heater beneath a Create basin; after warming up, it reaches usable burner heat.
- **Tools at spawn:** the chest at `(6, 64, 16)` contains both meters, both wire tools, and Create's Engineer's Goggles.

To test the panel and board, right-click **Factory enable** with an empty hand:

1. **Switch on:** the green **RUNNING** lamp lights and the red **OFF** lamp goes out.
2. **Switch off:** the capacitor keeps the green lamp on for roughly three seconds. Then green goes out and red lights.
3. **Switch on again:** green returns after a short charging delay and red goes out. Repeat as often as you like.

With goggles on, the panel shows about **18.4 V** at the board's relay coil and **0.15 A** through the panel while on. The active lamp branch draws about **0.30 A**. The panel's stock meter needles move very little at these readings; use the numeric tooltip or the obvious lamp changes. Signs identify the controls and outputs in the world.

The example checks exercise the actual panel interaction, capacitor delay, relay state, both lamps, numeric meters, tools, and saved state. [Revision-2 evidence](../release/examples/panel-relay-v2/) records creation/restart and exported-world checks. It provides a repeatable small demonstration; it does not establish large-network performance.

## Pinned upstream behavior

PG's portable battery charges a native energy item. Its placed block does not discharge into a wired circuit as a voltage source; use CEE's accumulator for reversible circuit storage.

In the pinned CEE 1.1.3 tests, the creative battery's AC mode produces a 20 Hz waveform despite its frequency selector. WWPG preserves that behavior. A PG AC source or driven CEE alternator provides other frequencies.
