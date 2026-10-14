# Stationary Test Yard

This world lets you try WWPG 0.2.2-beta.2 without building a factory first. Switch lamps, pump water, run a heater, compare transformer voltages, charge storage, and operate circuit boards and electrical panels. The equipment mixes CEE and Power Grid in working circuits.

Choose the edition that matches your installation:

Download and extract the [example-worlds bundle](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.2-beta.2/wwpg-0.2.2-beta.2-example-worlds.zip). Choose a ZIP from its `worlds` folder:

- **Stationary Test Yard** (`wwpg-0.2.2-beta.2-stationary-yard.zip`): the required base mods.
- **Stationary Test Yard — Pinout** (`wwpg-0.2.2-beta.2-stationary-yard-pinout.zip`): the base mods plus CC:Tweaked 1.120.2 and Pinout 0.2.1.

Follow the [installation guide](INSTALL.md), then extract the named save folder into your instance's `saves` directory. Keep a clean copy before cutting wires or deliberately breaking equipment.

## Start here

Take Engineer's Goggles, both handheld meters and the guide books from the spawn chest at **8, 64, 16**. Wear the goggles and look at gauges to see their numeric readings. Follow the white paths. Each exhibit has instructions, expected readings and a reset action. Right-click its feed switch with an empty hand to stop or resume power. Turn power off before repairing a bulb, fuse or wire.

Start with the factory on the west side: toggle **Factory enable** and watch the RUN/OFF lamps change. That side also contains the board and panel galleries, transformers and meter banks. The east side adds 79 exhibits, including saved designs, new wiring examples and a Nether route. The Pinout edition adds two computer exhibits. Cabinets contain spares for repairs and your own builds.

Useful starting points:

| Exhibit | What to try | Expected result |
| --- | --- | --- |
| Factory panel | Toggle Factory enable | RUN/OFF lamps change after a short capacitor delay. |
| Transformer rows | Compare input/output gauges; adjust native winding turns | Load voltage changes with the ratio and winding polarity. |
| Core and radiator | Inspect the working assembly and cooling panel | The native core supplies the mixed load; the radiator contributes cooling. |
| S15 plotter | Look at the paper graph; switch its signal feed | The trace responds to the electrical input. A name tag opens PG's native format menu. |
| S24–S26 CRTs | Switch the heater feed; adjust the X/Y supplies or grid cutoff | A bright dot moves with the deflection inputs. Raising the cutoff voltage extinguishes it. |
| S61 saved design | Open the powered table; move its saved output into the load slot | Load, edit, save, copy and assemble the supplied design using PG's menus and items. The nearby board runs at about 10 V / 10 mA. |
| S65 Nether route | Enter the portal; inspect the remote lamp and gauge | About 70 V powers the mixed equipment at both ends. Both transformer ends are kept loaded in this demonstration. |
| S68–S76 wire families | Switch the feed and measure the load | Conductive wires light the indicator. Glass insulation carries essentially no power in the pinned CEE release, so its lamp stays dark. |
| S77 duplex cable | Switch the feed; meter a breakout wire | A red 12 V PG bulb lights through the two native conductors. Measuring the whole cable cancels equal supply and return currents. |
| S78 wire attachments | Use the lineman's stick with a banner/damper in the offhand | Native attachments install and can be removed. An offhand clamp meter also measures PG wire current. |
| S79 three-pole meter | Switch the feed; inspect both lamps and the energy meter | Both lamps light at about 120 V / 30 W each; the meter totals about 60 W. Red and blue lines have opposite signs relative to their common middle line. |

Small board currents are expected: **10 mA is 0.010 A**. A control circuit can operate a relay while drawing very little current; measure the separate feed to the factory load to see its larger current. A nearly motionless gauge needle can still have a valid numeric reading. Lamps and storage circuits can change their readings while warming up or charging.

## Computer controls in the Pinout edition

At S80 and S81, each mod's supply powers eight computer-controlled lamps, a board relay and a panel gauge. Right-click a computer to open its screen, then type a command and press Enter:

| Command | What happens |
| --- | --- |
| `wwpg start` | All eight pin lamps turn on. |
| `wwpg stop` | All eight pin lamps turn off. |
| `wwpg pattern 85` | Alternating pins turn on. |
| `wwpg demo` | Runs the demonstration program. |
| `wwpg volts` | Prints the pin voltages. |
| `wwpg layout` | Explains which pin controls each output. |

Voltages are measured relative to the common connection, so a negative number is expected here. ON pins read slightly below zero because of contact resistance; OFF pins read about **−70 V**. The computer calls common pin 9; PG labels that terminal 0. Pins 1–8 keep their usual numbers.

`wwpg stop` switches off all eight pin lamps. The separate board RUN lamp stays on briefly while its capacitor discharges, then the OFF lamp lights. Allow about three seconds for the relay to respond after starting or stopping. A native PG diode keeps that stored energy from feeding back into pin 3's indicator.

## Reset, repair and report a problem

Follow the station's reset sign to restore its original settings and turn its feed back on. Give charging circuits and relay delays a few seconds to settle. For bulbs, wire decorations and duplex cables, use the [wiring and repair guide](STATIONARY_WIRING.md). Re-extract your clean download if you want to undo extensive changes.

The download has connected wires, saved Nether links and the Pinout computer programs intact. Loose construction wire drops are removed before export; wires you break during play still drop normally.

For a report, include the exhibit code, what you operated, the reading you expected and what happened. [Current status](STATUS.md) lists release limitations. An operator can run `/wwpg at x y z` to inspect the affected block, `/wwpg topology` for recent connection changes, and `/wwpg errors` for recent errors. These commands do not load distant chunks.

The [0.2.2-beta.2 verification record](../release/0.2.2-beta.2/verification.json) records checks on the exact downloadable worlds. The yard covers the [declared stationary content](SUPPORT.md); moving machinery and large-server performance remain outside this release's scope. Earlier downloads and their check records remain unchanged.
