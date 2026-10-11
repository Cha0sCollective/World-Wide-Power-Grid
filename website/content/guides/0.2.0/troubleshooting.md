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

## Updating and reporting a problem

Stop the game and server, back up the world, and replace the WWPG jar on every client and the server. Keep one WWPG jar in `mods`. Follow the release's dependency requirements. Updating cannot recover wires or charge already lost and saved by an older version.

When [opening an issue](https://github.com/Cha0sCollective/World-Wide-Power-Grid/issues), include the mod versions, operating system, actual solver backend, relevant configuration, logs, and steps that reproduce the result. For a yard report, include the exhibit code, action, expected reading, and observed result.

Beta.5 repairs several reproduced persistence and lifecycle problems. Earlier intermittent failures remain in the [release-pinned status record]({{source}}/docs/STATUS.md) where their original cause was not established. Passing current checks do not certify every configuration or large-server performance.

Sources: [installation]({{source}}/docs/INSTALL.md), [stationary yards]({{source}}/docs/STATIONARY_YARD.md), and [release status]({{source}}/docs/STATUS.md).
