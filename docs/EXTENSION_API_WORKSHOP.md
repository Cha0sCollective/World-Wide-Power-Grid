# Extension API Workshop

The workshop is a separate playable save for WWPG 0.2.0's two independently built
example add-ons. It demonstrates new machines through the general API, a new CEE
panel attachment through the CEE property API, and a new PG board component
through PG's native system. The [Stationary Test Yard](STATIONARY_YARD.md) remains
the demonstration of upstream stationary content.

This is a development artifact until the exact exported ZIP passes acceptance.
See the [0.2.0 progress record](../release/0.2.0/progress.json) for current evidence.
The builder or a successful source compile alone does not establish a working
download.

## Install and enter

Use the same fixed installation: Minecraft 1.21.1, NeoForge 21.1.231, Java 21,
Create 6.0.10-280, CEE 1.1.3, Power Grid 0.6.2 and WWPG 0.2.0. Also install both
example mod jars:

- `wwpg-api-example-1.0.0.jar`, the general API machines.
- `wwpg-upstream-api-example-1.0.0.jar`, the native panel and board extensions.

These two add-ons are required for this save. Ordinary WWPG factories do not
require them. The Pinout installation edition additionally requires CC:Tweaked
1.120.2 and CC;CPG: Pinout 0.2.1. Its workshop stations are the same; the computer
demonstrations live in the Pinout Stationary Yard.

Extract the save folder into your instance's `saves` directory. The included
`WWPG_MANIFEST.json` lists exact dependency and example-jar hashes. Keep a clean
copy before destructive experiments.

Spawn is **9004, 64, 8995**. The chest at **9000, 64, 8997** holds goggles, both
handheld meters, cutters, spools, native assembly parts and the workshop guide.
Put on goggles, take both meters and walk south along the white paths. If an
existing player position overrides spawn, teleport to those coordinates.

## What to try

Each general API block has red and blue terminal markers on its north face.
Red identifies the positive terminal; blue identifies the negative terminal.
The upper pair is its input or primary winding. Blocks with a lower pair expose
their output or secondary winding there.

With an empty hand, click an API block for server readings in chat. Sneak-click
changes its setting. Bright block faces indicate accepted power or retained
charge. Sources and loads use both native wire systems; the native gauges and
handheld meters provide additional readings.

| Station | Device position | Action and expected result |
| --- | --- | --- |
| A01 Parallel load | 9006,64,9000 | Sneak-click the load: two parallel resistors change between ON and practically disconnected. ON is 20 V, 0.4 A total and 8 W. |
| A02 DC source | 9022,64,9000 | Sneak-click the gold source: the native resistor and gauge change between 20 V and zero. |
| A03 AC source | 9044,64,9000 | Sneak-click the source: the API load lights at 50 Hz, 20 V peak, 14.14 V RMS and 4 W total. |
| A04 Capacitor | 9006,64,9020 | Sneak-click green storage: the internal stored voltage settles from 20 to 10 V. Re-enable to charge back to 20 V. With discharge enabled, switch its gold feed OFF to drain it. |
| A05 Inductor | 9028,64,9020 | Toggle the gold feed: accepted coil current settles between about 0.2 A and zero. |
| A06 Diode | 9050,64,9020 | Toggle the gold feed: the forward diode powers a native resistor at about 19 V. Reversing the diode's connections blocks that feed. |
| A07 Transformer | 9006,64,9040 | Sneak-click copper coupling: its native output load changes between 40 and 10 V from a 20 V input. |
| A08 Voltage control | 9028,64,9040 | Sneak-click purple control: its native output load changes between 40 V and zero. |
| A09 Current control | 9050,64,9040 | Sneak-click orange control: its native output load changes between −20 V at 0.2 A and zero. |
| A10 CEE storage indicator | 9007,64,9060 | Open its adjacent native feed switch: the panel light fades over a few seconds. Close it: the 70 V feed restores its light. |
| A11 PG shunt board | 9029,64,9060 | Open/close its adjacent native feed switch: the assembled 100 Ω board and gauge change between 20 V/about 0.2 A and zero. |

AC voltage crosses zero repeatedly. An instantaneous zero at A03 can coexist
with a lit load and nonzero RMS voltage and power. The A09 negative voltage is
the expected orientation of that current-source circuit; it still delivers power
to its resistor. At A04, the gauge measures external terminal voltage; the chat
reading labels the different **internal stored voltage**.

A07 demonstrates the general API's ideal transformer model. It deliberately uses
a DC developer source to make ratios easy to read. Substitute the AC example
source to explore AC operation. Native transformers retain their own assembly
requirements and electrical models in the Stationary Yard.

## Reset and repair

Restore the gold supplies and all API block settings to enabled ON. On the green
capacitor, enabled ON means its discharge resistor is disconnected. Close the
native feed switches beside A10 and A11. Give the capacitor and panel indicator
a few seconds to settle.

Native cutters and spools operate on the example terminals. Reconnect matching
red/blue pairs; leave lower output pairs separate from upper input pairs at A07–A09.
The tool chest provides extra native panel and board components for assembly
experiments. Consult the independent projects' instructions before rebuilding
their native designs:

- [General machine example](../examples/extension-addon/README.md)
- [Native CEE/PG example](../examples/cee-addon/README.md)

Ideal sources and creative resistors are developer demonstrations. The sample
machines do not define balanced survival progression. Player-created wire drops
remain collectible. Construction debris cleanup runs only while preparing the
acceptance save; it does not run during normal play.

## Build and verify the downloadable save

The packaged `wwpg_api_world` fixture uses registry IDs and public contracts,
without importing either example's implementation classes. It installs the panel
through its native item interaction and assembles/places the board through native
items. It checks ON, changed-setting and restored states before saving.

Build both examples against the published API artifacts first. Then run the
workshop SETUP and three cold VERIFY passes with the same profile, backend and
three frozen mod jars. CI performs these steps for Windows/Linux, Java/native and
base/Pinout installations. The source save is exported only after all passes
succeed:

```text
python tools/export_api_workshop.py --run run/api-workshop --jar build/libs/wwpg-0.2.0.jar --example examples/extension-addon/build/libs/wwpg-api-example-1.0.0.jar --upstream-example examples/cee-addon/build/libs/wwpg-upstream-api-example-1.0.0.jar --profile BASE --backend NATIVE --output build/distributions/wwpg-api-workshop-BASE.zip
python tools/reopen_stationary_yard.py build/distributions/wwpg-api-workshop-BASE.zip --run run/exported-api-workshop
```

The exporter requires matching packaged runtime/add-on hashes, all eleven checked
stations and native saved chunk tickets. It preserves the workshop's saved blocks,
storage, wire entities, CEE connections and configuration. It never replaces an
existing ZIP or source save. The extracted ZIP must then pass three cold VERIFY
runs, followed by graphical-client and real two-client checks.

These playable checks complement separate pre-solve storage and actual unload
fixtures. A device recharging after a restart cannot prove its stored history
survived; the dedicated API lifecycle fixtures check the serialized state before
the first solve. Preserve failed-run evidence and investigate failures before
calling the workshop accepted.

The opt-in client harness exercises actual PG/CEE wire items, source/load
configuration, both handheld meters, native panel dyeing, design-table editing,
schematic item copying/loading, board updates, wire cutting and reconnection.
It runs once as a graphical single-player client and once as two real TCP
clients for each profile and backend. On Windows, the frozen-artifact runner is:

```powershell
tools/accept_api_clients.ps1 -Batch api-acceptance -SourceSha <frozen-commit> -ColdRestarts
```

It keeps each exact ZIP, launch descriptor, log, screenshot and SHA256 together.
Use a new batch name after a failure so the failed attempt remains available.
These drivers live outside the runtime jar. Their server coordination is active
only when the explicit `wwpg.test.apiWorkshop` test property is enabled.
