# WWPG upstream API example

This independent NeoForge add-on demonstrates a custom **CEE panel attachment**,
its separately registered electrical-property adapter, and a native **Power Grid
circuit-board component**. It compiles against published API artifacts and the
pinned upstream mods. It imports no WWPG implementation classes or Mixins.

This example ships with WWPG 0.2.1 and requires extension API 1.0.0.
The [API Workshop](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/EXTENSION_API_WORKSHOP.md) provides a playable panel
and board demonstration. The [release verification](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/release/0.2.1/verification.json)
identifies the tested artifacts and accepted checks.

## Build independently

Use Java 21. Download, verify and extract the
[developer SDK](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.1/wwpg-0.2.1-developer-sdk.zip),
then extract its `maven/wwpg-api-1.0.0-maven.zip`. The directory containing `org/cha0scollective/wwpg` is the Maven
repository root. Supply that directory's absolute path and run this project's
wrapper from the project directory:

```text
./gradlew build -PapiRepository=/absolute/path/to/wwpg-api-maven --no-daemon
```

On Windows, use `gradlew.bat`. The output is
`build/libs/wwpg-upstream-api-example-1.0.0.jar`. Compilation uses
`org.cha0scollective.wwpg:wwpg-api:1.0.0` and
`org.cha0scollective.wwpg:wwpg-cee-api:1.0.0`, together with Minecraft 1.21.1,
NeoForge 21.1.231, Create 6.0.10-280, CEE 1.1.3 and PG 0.6.2. The build does not
include the WWPG source project and does not shade any API or upstream classes.

The compile-only upstream coordinates are locked in `build.gradle`. Install the
real upstream jars and WWPG at runtime using the main project's
[dependency manifest](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/release/0.2.1/dependencies.json) and
[installation instructions](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/INSTALL.md). This add-on belongs on the
server and every client. WWPG itself does not require it. Pinout and CC:Tweaked
are optional.

## CEE panel example

Craft an **API Storage Indicator** using redstone and glass, or obtain it from
the Creative Redstone Blocks tab:

```text
/give @s wwpg_api_upstream:panel_storage_indicator
```

Install it on an existing CEE electrical panel using CEE's normal attachment
interaction. It supports half or third slots. Use either mod's wire tool to join
its two terminals to a supply. At 70 V the native indicator is fully bright.
Its electrical model combines a 1000-ohm lamp load with a 0.001 F capacitor.
Disconnect both supply wires to see the light fade as stored energy discharges.
With a disconnected supply, voltage falls to roughly 37% after one simulated
second. Dyeing, native terminal selection, removal and panel serialization follow
CEE's own workflows.

`StorageIndicatorProperties` retains CEE's native property interface.
`UpstreamExample` registers its exact class during
`RegisterCeePropertyAdaptersEvent` on its mod event bus. WWPG posts this event to
all mod buses in mod order during the serial enqueued load-complete phase; the
game event bus is still stopped then. The adapter prepares a Norton-equivalent
snapshot before each substep and stores the accepted voltage after each substep.
Its source values use **CEE's native polarity**, which WWPG translates to PG.
The callback receives no PG object, solver array or graph index.
The adapter checkpoints every numerical field it can mutate. WWPG restores that
checkpoint if a callback or solve fails, including earlier accepted substeps from
the same rejected tick. This restores the checkpointed CEE property's numerical
state, keeping its saved charge at the last completed game tick. PG retains its
native accepted-step behavior. Checkpoints cannot undo arbitrary gameplay effects;
the callbacks therefore perform numerical work only, and the CEE device applies
gameplay effects after an accepted tick.

`StorageIndicatorAttachment` saves capacitor voltage and accepted-substep count
in its versioned native NBT. Reading and preparing the attachment do not advance
its electrical state. Its inherited CEE rendering uses actual solved voltage.

## Power Grid board example

Craft an **API Circuit Shunt** using redstone and copper, or obtain it from the
Creative Redstone Blocks tab:

```text
/give @s wwpg_api_upstream:api_shunt
```

The native PG circuit designer lists the new component. It has the same footprint,
resistance setting, rotation, visual model and thermal behavior as PG's resistor,
with a separate component and assembly-item identity. Place two native connectors
and this component, route each connector to one resistor pad, save the design,
assemble the board using native component items, and place it normally. Set it to
100 ohms and supply 20 V from CEE: expected current is approximately 0.2 A, with
small native wire losses.

PG continues to own this component's electrical model and damage behavior. It
requires no WWPG property adapter and receives no duplicate extension model.
The `component_items` data file supplies its item to native assembly, while native
schematic serialization keeps the add-on's component ID and configuration.

## Packaged acceptance fixtures

`UpstreamApiGameTests` declares tests in namespace `wwpg_api_upstream`. Run them
from WWPG's packaged acceptance profile after adding this example jar to its
runtime and enabling the namespace. The tests use upstream item interactions:

- A custom CEE attachment installs with its item, receives PG power through both
  wire systems, lights, advances storage exactly once per substep, discharges
  after source removal, and restores native saved state before another solve.
- A custom PG component assembles from its native item, places as a board,
  receives CEE power, and retains component and terminal identities after native
  schematic reload. It also verifies that PG retains electrical ownership.
- An injected CEE callback failure after mutating history restores the custom
  property's checkpointed numerical state and native CEE capacitor history in the
  rejected circuit. Its public and handheld readings become unavailable while a
  separate healthy mixed circuit keeps working. Operation resumes after repair.

`UpstreamRestartGameTests` uses namespace `wwpg_api_upstream_restart`. Run it in a
saved acceptance world with `wwpg.test.restartPhase=SETUP`, then start separate
server JVMs with `VERIFY`. The cold fixture occupies `(24640, 64, 24640)` and saves
`upstream-api-before-restart.nbt` when server ticking stops. It checks exact
attachment charge and accepted-substep count, board component UUIDs and settings,
traces, and terminal IDs immediately after loading, before another solve can hide
state loss. Another fixture completes five actual unload/reload cycles at
`(25344, 64, 25344)`, including native PG wire-identity checks. The fixtures are
far enough apart that the cold fixture's forced-chunk ticket cannot keep the
unload fixture's chunk holder alive.

These fixtures check electrical and saved-state behavior. Graphical editing and
multiplayer have separate acceptance sessions, recorded alongside them in the
[final verification](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/release/0.2.1/verification.json). The
[maintainer release guide](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/API_RELEASE_CHECKS.md) describes the complete gates.
