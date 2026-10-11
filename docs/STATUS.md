# Current status

**[WWPG 0.2.1](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.1)**
is the current release. It supports the declared stationary factories and
optional Pinout computer control, and includes **extension API 1.0.0** for
add-on developers. Existing beta.5 worlds remain compatible; update WWPG on
the server and every client using the [installation guide](INSTALL.md).
NeoForge **21.1.231 and 21.1.248** are tested. Newer NeoForge, Create, CEE and
Power Grid versions are allowed to load for player testing; see
[version compatibility](VERSION_COMPATIBILITY.md) for the tested combination.

## What works

You can connect supported CEE and Power Grid equipment in the same circuit
using either mod's usual wire tools. The release covers generation, storage,
motors, pumps, lighting, heating, transformers, controls, protection and meters.
CEE panels and PG circuit boards keep their native editing and assembly
workflows. Native FE conversion and stationary Nether-transformer links are
also included. See [supported content](SUPPORT.md) for the exact equipment and
each mod's built-in limits.

The [Stationary Test Yard](STATIONARY_YARD.md) demonstrates those behaviors.
The separate [API Workshop](EXTENSION_API_WORKSHOP.md) demonstrates add-on
machines, a panel attachment and a board component; it requires both example
add-ons. Both worlds have an optional Pinout installation edition.

## Saving, loading and tools

Release checks cover saving and reopening worlds, loading and unloading chunks,
both saved wire systems, retained storage, device replacement and repairs.
They also cover a save made while old wire entities are still loading, so
replacing a machine does not leave its old connections attached to a new one.

Meters report unavailable readings while an extension circuit is loading,
paused or invalid. They do not keep showing its last successful measurement.
If a wire tool says **Device is loading. Try again in a moment.**, wait for
the area to finish loading and try again.

WWPG also isolates failing add-on callbacks, preserves accepted electrical
history after rejected calculations, and allows recovery when the add-on reports
a repaired device. The [developer contract](EXTENSION_API.md) explains those rules.

## What was checked

The frozen release passed the full Windows/Linux × native/Java × base/Pinout
server matrix. Each combination includes three cold restarts and five actual
assembly unload/reload cycles. The exact four downloadable world ZIPs passed
reopening checks. Windows graphical and two-client server sessions exercised
the API Workshop's wiring, settings, meters, panel and board editing, cutting
and repairs. Linux coverage comes from server tests, rather than graphical clients.

Both example add-ons also built independently from their downloadable source
and Maven archives. API 1.0.0 establishes the first published API contract.

For maintainers, the detailed [verification record](../release/0.2.1/verification.json)
retain exact versions, hashes and results. There are 204 base checks or 208 with
Pinout, plus 34 extension/lifecycle checks and 62 Java unit checks per CI job.
These are test counts, not counts of items in the game.

## Limits and older reports

- Moving trains, contraptions and other moving electrical systems are future work.
- The [listed versions](INSTALL.md#required-versions) and declared optional
  integration are verified. Newer allowed dependencies need player testing.
  Large-server performance has not been certified.
- Example API components are creative demonstrations; they do not define survival
  recipes or balance for a finished add-on.
- Add-ons own their gameplay and saving. WWPG can restore its electrical history
  after a rejected calculation, but it cannot undo arbitrary inventory or world
  changes made by an add-on's callback. PG retains its own native behavior.

No failing claimed workflow remains in the frozen release acceptance. Older
intermittent reports are preserved in [release history](RELEASE_HISTORY.md),
including cases where the original logs could not establish an exact cause.
Passing current checks does not retroactively explain those older failures.

The [product specification](../SPEC.md) describes the eventual full product.
Its future product 1.0 goals are separate from the released extension API 1.0.0.
