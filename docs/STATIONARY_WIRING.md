# Stationary wiring and native tools

These changes are being verified for WWPG beta.5 with CEE 1.1.3 and Power Grid 0.6.2. They do not expand earlier releases' support claims.

Both mods' ordinary wires connect compatible stationary terminals across mods. Keep the native wire's length, voltage and temperature limits in mind. PG endpoints retain their native wire restrictions; CEE terminals accept PG wire types, including iron wire. Wire costs, offhand dyes, cutting and spool recovery use the upstream workflows.

CEE's 57 ordinary wire variants are checked individually. Colored variants share their family's electrical model. Glass insulation carries essentially no power in the published CEE 1.1.3 artifact: a dark lamp at that exhibit is expected. Its hanging-insulator item creates a native loose endpoint and also works on a fresh PG terminal.

## Duplex cable

The published CEE duplex spool accepts clicks but does not complete a connection. WWPG completes that existing item workflow and corrects the native conductor-node helper. One native cable is saved and rendered; its two conductors are derived for the electrical simulation. Each conductor uses CEE's configured resistance multiplied by the native cable length. Unchanged cables retain their PG electrical objects.

Click the two duplex terminations with the duplex spool. Connect supply and return to the separate terminals at each end. Use an empty spool or cutters to recover the cable. Removing the parent cable removes both electrical conductors. Facing, roll and flip retain CEE's terminal identities.

The 12 V yard exhibit lights a red PG low-voltage bulb through this cable. Measure current on a breakout wire. Equal supply and return currents cancel when measuring the whole duplex cable. CEE 1.1.3 supplies no survival recipe for the duplex spool; WWPG preserves that native availability and provides spares in the test yard.

## Decorations and repair

To install a banner or wire damper, hold the lineman's stick in the main hand and the attachment in the offhand. Click the wire. Shift-click with an empty offhand to remove the attachment. Native attachment edits preserve wire type, length and electrical topology. A wrench can also remove attachments.

Holding the clamp meter in the offhand turns the lineman's stick into its native extended-reach meter. This workflow also selects PG wires, reads their solved current and clears the selection when released or when the offhand clamp is removed.

A burned PG bulb takes two clicks: first remove it, then install a healthy bulb. Use a 12 V bulb in the low-voltage exhibit and a 120 V bulb in the ordinary fixture exhibits. Turn the feed off before repairing or rewiring. CEE's broken bulb uses its separate native copper-wire repair workflow.

## Evidence and remaining acceptance

The wiring fixtures check native item costs, all ordinary CEE wire variants, PG iron on CEE terminals, native bulb installation and repair, duplex orientation, and five actual duplex-assembly unload/reload cycles with delayed entity loading. Cold checks inspect native cable and bulb state before the first solve, so restored power cannot hide state loss.

The current stage's evidence is recorded under `release/0.1.0-beta.5/evidence/`. Final stationary completion still requires the complete packaged platform/profile matrix, extracted-world checks, fresh graphical checks and two real multiplayer clients.
