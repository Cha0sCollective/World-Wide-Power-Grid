Start with a small circuit and the pinned dependencies. Keep a world backup before upgrading or making destructive changes.

## The circuit reads zero

1. Compare your mod versions and PG solver settings with [installation]({{docs}}install/).
2. Check the native feed switch, source, load, and complete return path.
3. Measure accessible terminals with PG's multimeter. Use its wire selection for current; use CEE's clamp meter on a wire rather than a terminal.
4. Compare the result with a known [yard exhibit]({{docs}}example-worlds/). Glass insulation carries essentially no power in the pinned CEE release; that exhibit's dark lamp is expected.
5. Ask an operator to check device mapping, loading state, and recent errors.

## Operator diagnostics

These commands require permission level 2.

| Command | Use |
| --- | --- |
| `/wwpg` | Inspect mapping counts, simulation steps, and actual backend use. |
| `/wwpg errors` | Read recent timestamped compatibility errors. |
| `/wwpg topology` | Inspect recent structural changes and device identities. |
| `/wwpg at x y z` | Inspect a mapped block's terminals, voltage, backend, and isolation state. |

Diagnostic queries do not load distant chunks or create electrical adapters.

## Computer or pin readings look wrong

Check both optional mods, the peripheral beside the computer, and the [native pin layout]({{docs}}lua/#pin-numbering-and-signed-voltage). Common is Lua pin 9, corresponding to PG terminal 0. Signed readings depend on which terminal is the voltage reference; a negative reading alone does not indicate a fault.

## A self-excited generator keeps surging

WWPG 0.2.7 fixes the timing between supported PG generators' field, rotor and clutch updates. Transformer, variac and carbon-pile excitation controls retain their normal operation. This does not guarantee that every unregulated circuit design will settle at a constant voltage.

1. Check that the server and all clients use 0.2.7, then confirm `multiTicks = 16` in the [PG configuration]({{docs}}configuration/).
2. Check the complete field-coil circuit, its return path and the excitation control setting. Make sure the mechanical drive can carry the applied load.
3. Hold the drive speed, load and excitation setting steady while watching the voltage and clutch load. Increase load gradually rather than changing several controls at once.
4. If it still cycles, record the circuit, settings and readings for a report. Keep native protection limits; changing substeps alone is not a reliable cure. Unsupported custom generator arrangements retain PG's native update path.

## A transformer's own terminals say Reading unavailable

On an assembled PG medium transformer, 0.2.7 lets the multimeter resolve the transformer's own terminals through its loaded controller. Both the terminal probes and connected wire connectors should give valid readings when the circuit is ready.

Check that the whole transformer is assembled and loaded. Wait for loading or rebuilding to finish, then shift-right-click to release the probes and select the two terminals again. A genuinely unavailable circuit still reports unavailable; the meter does not substitute zero or an old reading. If the problem persists, include the transformer size, selected terminals and `/wwpg errors` output in your report.

## Updating and reporting a problem

Stop the game and server, back up the world, and replace the WWPG jar on every client and the server. Keep one WWPG jar in `mods`. Follow the release's dependency requirements. Updating cannot recover wires or charge already lost and saved by an older version.

When [opening an issue](https://github.com/Cha0sCollective/World-Wide-Power-Grid/issues), include the mod versions, operating system, actual solver backend, relevant configuration, logs, and steps that reproduce the result. For a yard report, include the exhibit code, action, expected reading, and observed result.

The [release-pinned status record]({{source}}/docs/STATUS.md) describes the current fixes and remaining limits. Earlier intermittent failures keep their original scope where the cause was not established. Passing release checks do not certify every circuit design or large-server performance.

Sources: [installation]({{source}}/docs/INSTALL.md), [stationary yards]({{source}}/docs/STATIONARY_YARD.md), and [release status]({{source}}/docs/STATUS.md).
