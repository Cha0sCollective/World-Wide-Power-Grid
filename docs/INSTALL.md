# Install and try WWPG

WWPG lets stationary equipment from **Create: Electro Energetics (CEE)** and **Power Grid (PG)** share power and wires. You can run mixed factories, use either mod's handheld meters, and control equipment with panels and circuit boards.

This guide installs **WWPG 0.2.2-beta.3**. Start with the [Stationary Test Yard](STATIONARY_YARD.md) if you want ready-built circuits to try. See [supported content](SUPPORT.md) for what works and [current status](STATUS.md) for limitations.

## Required versions

Use this tested combination, with the same mod versions on the server and every client. Use Java 21.

| Dependency | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.231 or 21.1.248 |
| Create | 6.0.10-280 |
| Create: Electro Energetics | 1.21.1-1.1.3 |
| Power Grid | Minecraft 1.21.1, 0.6.2 |
| Architectury API | NeoForge 13.0.8 |
| WWPG | 0.2.2-beta.3 |

WWPG 0.2.2-beta.3 supports NeoForge 21.1.231 and 21.1.248. Its declarations also
allow newer NeoForge, Create, CEE and Power Grid versions so players can try
them; those additional versions have not been verified. See
[Version compatibility](VERSION_COMPATIBILITY.md).

Use the full Create release jar. [The 0.2.2-beta.3 dependency list](../release/0.2.2-beta.3/dependencies.json) records the exact downloads and checksums. WWPG does not include these other mods; download and install them separately.

## Optional computer-control profile

To control circuits with computers, also install **CC;CPG: Pinout 0.2.1** and **CC:Tweaked 1.120.2** on the server and every client. Use the **Stationary Test Yard — Pinout** world for its ready-made programs. These two mods are optional for ordinary WWPG factories; [their download records](../release/optional-artifacts.json) identify the exact files.

## Installation

1. [Download the WWPG jar](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.2-beta.3/wwpg-0.2.2-beta.3.jar).
2. Put it and the required dependency jars in the instance's `mods` directory.
3. Launch once to generate configuration, then stop the game or server.
4. In `config/powergrid-server.toml`, edit the existing solver settings to the values below. Keep the other PG solver settings at their defaults.
5. Restart and load a supported circuit or one of the downloadable worlds below. If you have operator permission, `/wwpg` shows which power-calculation engine is running.

```toml
solverBackend = "NATIVE"
multiTicks = 16
```

These settings use PG's native power-calculation engine and 16 electrical steps per game tick, matching the release's tests. CEE's `microTicks` setting does not control this timing when WWPG is installed. Stop and restart after changing these settings.

## Native solver requirements

WWPG supplies the native engine files missing from PG 0.6.2. They come from the official PG 0.6.1 release and match PG 0.6.2's interface. **Install PG 0.6.2 only**; the 0.6.1 mod jar is not needed.

WWPG installs missing engine files in `.pg-native` inside the game's working directory. Existing administrator-installed files are preserved. PG's license and attribution remain included.

| Tested native platform | Requirement |
| --- | --- |
| Windows | x86-64 |
| Linux | x86-64; glibc 2.38 or newer. Release testing used Ubuntu 24.04. |

PG can also calculate power with its Java engine, and both engines passed the release's electrical tests on Windows and Linux. An operator can use `/wwpg` to check which one actually loaded. Selecting `NATIVE` in the file does not guarantee that your system can run it.

## Wires and tools

Use each mod's wire, spool and cord tools as usual. Both wire systems can connect supported terminals from either mod. Length limits, material costs and cutting still follow the mod that supplied the wire. PG cords use sockets, junctions and split ends; a hidden cord connection is not an attachment point for bare wire. [The wiring guide](STATIONARY_WIRING.md) covers duplex cables, decorations and bulb repairs.

## Handheld meters

- **Power Grid multimeter, voltage:** right-click two accessible terminals to attach the probes. These can be CEE terminals, PG terminals, or one of each. Walk around a machine if its other terminal is on the back. Shift-right-click releases the probes.
- **Power Grid multimeter, current:** right-click a wire from either mod. Selecting a wire switches to current mode; selecting a terminal switches back to voltage mode.
- **CEE clamp meter, current:** hold right-click while aiming at a wire from either mod. Release right-click or look away to stop measuring. A clamp measures a wire's current, not a terminal's voltage.

## Upgrading an existing world

Stop the game or server, back up your world, and replace the older WWPG jar on the server and every client. Keep only one WWPG jar in `mods`. Use the listed solver settings and matching dependency versions on the server and clients. Existing beta.5, 0.2.0, 0.2.1 and 0.2.2-alpha.1 worlds work with 0.2.2-beta.3; you do not need to download them again.

An update cannot restore wires or charge that an older version already lost and saved. The original beta.1 example also has an underpowered heater; use the current Stationary Yard or revised small example for a working burner demonstration.

## Diagnostic commands

These diagnostic commands require operator permission level 2:

| Command | What it shows |
| --- | --- |
| `/wwpg` | Which power engine is running and whether circuits are being calculated |
| `/wwpg errors` | Recent compatibility errors, with timestamps |
| `/wwpg topology` | Recent connection changes and the devices involved |
| `/wwpg at x y z` | A block's terminals, voltages, loading state and any reason it cannot participate |

## Download a world to try

Download the [example-worlds bundle](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.2-beta.3/wwpg-0.2.2-beta.3-example-worlds.zip) and extract it. In its `worlds` folder, choose `wwpg-0.2.2-beta.3-stationary-yard.zip` for the base installation, or `wwpg-0.2.2-beta.3-stationary-yard-pinout.zip` with the optional mods.

Extract the named save folder into your instance's `saves` directory. Goggles and tools are at spawn. Try switching the factory lamps, watching the pump and heater, changing transformer voltages, or operating panels and boards. The [station guide](STATIONARY_YARD.md) explains what to do, what you should see and how to reset each example.

The download keeps connected wires, Nether links and the Pinout edition's computer programs. Loose construction wire drops are cleaned up before export; wires you break during play still drop normally.

## Large benchmark yard

The same bundle also contains `wwpg-0.2.2-beta.3-benchmark-14650-nodes.zip`.
This is the original heavy mixed workload, separate from the playable Stationary Yard.
Read the [benchmark guide](BENCHMARK_YARD.md) before opening it. Its actual size is
14,650 electrical nodes; it is not certified to run at 20 TPS.

## Extension API Workshop

The [example-worlds bundle](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.2-beta.3/wwpg-0.2.2-beta.3-example-worlds.zip) also contains `wwpg-0.2.2-beta.3-api-workshop.zip` and its Pinout edition in `worlds`. This separate world requires both example add-ons from the bundle's `workshop-mods` folder on the server and every client:

- `wwpg-api-example-1.0.0.jar` — general API machines
- `wwpg-upstream-api-example-1.0.0.jar` — native panel and board extensions

The Pinout edition also requires the two optional mods above. Its eleven Workshop stations are the same; computer demonstrations live in the Pinout Stationary Yard. Ordinary factories do not require either example add-on.

Extract the named save folder into `saves`. Spawn is `(9004,64,8995)` and the tool chest is `(9000,64,8997)`. Wear goggles and follow the white paths. Empty-hand right-click on an API block prints readings; sneak-click changes its setting. The [Workshop guide](EXTENSION_API_WORKSHOP.md) gives expected outputs and resets. Keep a clean copy before destructive experiments.

If the PG design editor's name field or buttons are cut off, open **Options → Video Settings → GUI Scale** and reduce the scale. GUI Scale 2 displays the complete editor in a 1280 × 720 window.

If a terminal is still loading or its old wires await retirement, the wire tool displays **Device is loading. Try again in a moment.** Wait for the area to finish loading, then retry.

To write your own add-on, follow the [developer API guide](EXTENSION_API.md). Players need the ordinary WWPG jar and the two example mod jars for this world. Keep API, source and Javadoc jars out of `mods`.

## Historical small example

The older [panel-relay example](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.1.0-beta.2/wwpg-0.1.0-beta.2-example-v2.zip) is a smaller demonstration. Extract `WWPG Example - Panel Demo/` into `saves`; it appears as **WWPG - Panel Relay Demo**. The unchanged download keeps its beta.2 filename and was last verified with beta.5. Use the current Stationary Yard for the 0.2.2-beta.3 demonstration.

Spawn is at `(8, 64, 16)`:

- **Northern circuit:** the CEE panel controls a PG board containing a relay and capacitor. The relay routes a separate 300 V PG supply to two CEE lamps, mounted on green and red blocks and labeled **RUNNING** and **OFF**. The lamps' power passes through the board's contacts.
- **Southern circuits:** a 300 V PG source powers CEE lighting and a water pump connected to Create tanks and pipes. A separate 600 V PG source powers the CEE heater beneath a Create basin; after warming up, it reaches usable burner heat.
- **Tools at spawn:** the chest at `(6, 64, 16)` contains both meters, both wire tools, and Create's Engineer's Goggles.

To test the panel and board, right-click **Factory enable** with an empty hand:

1. **Switch on:** the green **RUNNING** lamp lights and the red **OFF** lamp goes out.
2. **Switch off:** the capacitor keeps the green lamp on for roughly three seconds. Then green goes out and red lights.
3. **Switch on again:** green returns after a short charging delay and red goes out. Repeat as often as you like.

With goggles on, the panel shows about **18.4 V** at the board's relay coil and **0.15 A** through the panel while on. The active lamp branch draws about **0.30 A**. The panel's stock meter needles move very little at these readings; use the numeric tooltip or the obvious lamp changes. Signs identify the controls and outputs in the world.

[The historical check record](../release/examples/panel-relay-v2/) documents this revision. Its small circuits do not establish large-server performance.

## Pinned upstream behavior

PG's portable battery charges a native energy item. Its placed block does not discharge into a wired circuit as a voltage source; use CEE's accumulator for reversible circuit storage.

In the pinned CEE 1.1.3 tests, the creative battery's AC mode produces a 20 Hz waveform despite its frequency selector. WWPG preserves that behavior. A PG AC source or driven CEE alternator provides other frequencies.
