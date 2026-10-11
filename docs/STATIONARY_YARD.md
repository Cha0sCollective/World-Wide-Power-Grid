# Stationary Test Yard

The beta.5 yard is still a release candidate. It has two editions: **Stationary Test Yard** for the base mods, and **Stationary Test Yard — Pinout** with CC:Tweaked and Pinout installed. Earlier downloadable worlds and their support claims remain unchanged.

Take goggles, both handheld meters and the guide books from the spawn chest at **8, 64, 16**. Follow the white paths. Each exhibit has instructions, expected readings and a reset action. Click its feed switch with an empty hand to stop or resume power. Turn power off before repairing a bulb, fuse or wire.

The earlier factory, boards, panels, transformers and meter banks remain on the west side. The east side adds 79 base exhibits; the Pinout edition adds two computer exhibits. The export manifest maps each claimed behavior to a checked exhibit or native assembly/repair workflow. Cabinets provide spares and do not count as working demonstrations.

Useful starting points:

| Exhibit | What to try | Expected result |
| --- | --- | --- |
| Factory panel | Toggle Factory enable | RUN/OFF lamps change after a short capacitor delay. |
| Transformer rows | Compare input/output gauges; adjust native winding turns | Load voltage changes with the ratio and winding polarity. |
| Core and radiator | Inspect the working assembly and cooling panel | The native core supplies the mixed load; the radiator contributes cooling. |
| S61 saved design | Open the powered table; move its saved output into the load slot | Load, edit, save, copy and assemble the supplied design using PG's menus and items. The nearby board runs at about 10 V / 10 mA. |
| S65 Nether route | Enter the portal; inspect the remote lamp and gauge | About 70 V powers the mixed equipment at both ends. Both transformer ends are kept loaded in this demonstration. |
| S68–S76 wire families | Switch the feed and measure the load | Conductive wires light the indicator. Glass insulation carries essentially no power in the pinned CEE release, so its lamp stays dark. |
| S77 duplex cable | Switch the feed; meter a breakout wire | A red 12 V PG bulb lights through the two native conductors. Measuring the whole cable cancels equal supply and return currents. |
| S78 wire attachments | Use the lineman's stick with a banner/damper in the offhand | Native attachments install and can be removed. An offhand clamp meter also measures PG wire current. |
| S79 three-pole meter | Switch the feed; inspect both lamps and the energy meter | Both lamps light at about 120 V / 30 W each; the meter totals about 60 W. Red and blue lines have opposite signs relative to their common middle line. |

Small board currents are expected. A control circuit can operate a relay while drawing only milliamps; measure the separate factory-load feed to see its larger current. Wear goggles for precise gauge readings. Temperature-dependent lamps and storage circuits can change their readings while warming up or charging.

In the Pinout edition, S80 and S81 let each upstream supply power eight computer-controlled lamps, a board relay and a panel gauge. Open a computer and type `wwpg start`, `wwpg stop`, `wwpg pattern 85`, `wwpg demo` or `wwpg volts`. `wwpg layout` explains the pins. Lua common pin 9 maps to PG terminal 0; pins 1–8 retain their native numbers. ON pins read slightly below zero relative to common because of contact resistance. OFF pins read about −70 V in these loaded examples.

Keep a clean save before destructive experiments. Export cleanup removes construction wire drops inside the yard; normal player drops remain during play. Connected wire entities, Nether saved data, native device identities and optional computer files are retained in the ZIP.

For a report, include the exhibit code, what you operated, the reading you expected and what happened. An operator can run `/wwpg at x y z` to inspect device identities and loading readiness, `/wwpg topology` for recent structural changes, and `/wwpg errors` for recent simulation errors. Diagnostic queries do not load distant chunks or create electrical adapters.
