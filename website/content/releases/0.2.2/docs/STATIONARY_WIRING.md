# Stationary wiring and native tools

With **WWPG 0.2.2-beta.2, CEE 1.1.3 and Power Grid 0.6.2**, both mods' tools work on
supported stationary circuits. You can wire equipment across mods, measure it,
cut connections and repair broken parts. Follow the [installation guide](INSTALL.md)
for the full version list and the [Stationary Test Yard](STATIONARY_YARD.md) for
ready-made examples.

## Ordinary wires and cords

Use the wire or spool on two compatible terminals as you normally would. A
terminal is the connection point on a machine or connector. You can connect a
CEE machine to a PG machine, or use either wire system between supported
machines from the same mod.

The wire still has its original length, voltage and temperature limits. PG
terminals retain their normal restrictions on wire types; CEE terminals accept
PG wire types, including iron wire. Material costs, offhand dyes, cutting and
spool recovery follow the supplying mod's normal workflow. PG cords use sockets,
junctions and split ends; hidden cord connections do not accept bare wire.

All 57 ordinary CEE wire variants are covered. Colored wires behave like the
other wires in their family. **Glass insulation carries essentially no power**
in CEE 1.1.3, so its yard lamp is expected to stay dark. The hanging-insulator
item creates its usual loose connection point and also works on a fresh PG
terminal.

If a tool says **Device is loading. Try again in a moment.**, wait for the area
to finish loading, then try again. For readings, see [handheld meter instructions](INSTALL.md#handheld-meters).

## Duplex cable

CEE duplex cable carries two separate conductors in one visible cable. WWPG
completes the duplex spool's connection workflow, which is incomplete in the
published CEE release.

1. Click the two duplex terminations with the duplex spool.
2. Connect supply and return to the separate terminals at each end.
3. Use an empty spool or cutters to recover the cable when you want to remove it.

Removing the cable removes both conductors. Rotating or flipping the termination
keeps the conductors' terminal identities.

At yard station S77, the cable powers a red **12 V PG bulb**. Measure current on
one of the separate wires entering or leaving the termination. Measuring the
whole cable cancels equal supply and return currents, so zero there is expected.
CEE 1.1.3 has no survival recipe for the duplex spool; the yard supplies spares
for Creative-mode testing.

## Decorations and repair

To install a banner or wire damper, hold the lineman's stick in your main hand
and the attachment in your offhand, then click the wire. Hold Shift and click
with an empty offhand to remove it. A wrench can also remove attachments.
Installing or removing a decoration keeps the wire connected.

For an extended-reach current reading, hold the clamp meter in your offhand and
the lineman's stick in your main hand. This also measures PG wires. Release the
tool or remove the offhand clamp to stop measuring.

Turn the feed off before repairing or rewiring, and restore the station's normal
voltage before powering it again.

- **PG bulb:** remove the burned bulb with one click, then install a healthy one
  with a second click. Use a 12 V bulb in the low-voltage exhibit and a 120 V bulb
  in ordinary fixture exhibits.
- **CEE broken bulb or bare fuse:** repair it with copper wire.
- **PG fuse:** remove the blown wire, then install iron wire.

## Check records for maintainers

The wiring fixtures check native item costs, all ordinary CEE wire variants, PG iron on CEE terminals, native bulb installation and repair, duplex orientation, and five actual duplex-assembly unload/reload cycles with delayed entity loading. Cold checks inspect native cable and bulb state before the first solve, so restored power cannot hide state loss.

The [beta.5 evidence](../release/0.1.0-beta.5/evidence/) retains the original
fixtures, failed controls and corrected runs. The [0.2.2-beta.2 verification record](../release/0.2.2-beta.2/verification.json)
checks the inherited workflows with the current jar and downloadable worlds.
Earlier published artifacts and claims remain unchanged.
