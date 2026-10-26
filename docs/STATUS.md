# Current status

**[WWPG 0.2.3](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.3)**
is the stable release. It supports the declared stationary factories and
optional Pinout computer control, and includes **extension API 1.0.0** for
add-on developers. Existing 0.1.0-beta.5 and beta.4 worlds remain compatible; update WWPG on
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
add-ons. The combined Stationary Yard requires CC:Tweaked and Pinout. The API
Workshop has BASE and Pinout editions.

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

0.2.3 passed a fresh full headless campaign covering Windows/Linux, Native/Java,
BASE/PINOUT and NeoForge 21.1.231/21.1.248: 342 catalog jobs. The Stationary Yard
is the combined PINOUT edition; runtime and API installation retain both profiles.
Cold restarts, actual unload/reload cycles, rejection checks, saved-state checks
and exact exported-archive reopens keep their separate process outcomes.

All 12 separately requested Windows graphical scenarios passed, including yard
and Workshop interactions, Native/Java integration, two real TCP clients,
the independently built SDK starter and clean Save and Quit or disconnect/stop.
A supplemental real-client check verified the changed regulator/capacitor pins:
actual aiming, PG highlights, native wire input and server acknowledgment.

The gameplay jar excludes GameTests and fixtures. The separate test companion
retains the earlier tests and adds five exposed-pin regressions. Ordinary play
uses the gameplay jar alone. API 1.0.0, saves, native solver binaries and all
sixteen electrical substeps remain compatible.

The [verification record](../release/0.2.3/verification.json) retains actual
source, runtime, companion, helper, world and process identities. Earlier failed
attempts remain failed. Electrical diagnostics are retained alongside passing
observations; a passing suite does not certify warning-free operation.

While holding PG wire, exposed regulator and capacitor pins can be selected
outside the ordinary block body. Goggles alone retain their existing picking.
The targeted checks do not certify every terminal on every CEE item or arbitrary
third-party terminal geometry. Workshop spawn remains fixed to the platform.

## Loading and ticking

The loading fixes group updates for affected circuits, preserve stored
energy while those circuits wait, and reduce repeated graph and matrix work.
Meters report unavailable readings during the pause, and devices resume without
simulating the time spent waiting. Unrelated ready factories continue operating.

Earlier improvements group add-on work by circuit, reuse accepted terminal and
wire readings, and skip empty input-assembly searches. These changes retain
electrical accuracy, stored energy and all 16 substeps. Beta.3 further reduces
terminal preparation, CEE description rebuilding and result allocations.
Beta.4 also corrects matrix ownership, unload/rebuild regrouping and handheld
connector samples. Failed native factors, invalid permutations and nonfinite
results are rejected before unsafe reuse; rejected circuits hold stored energy.
The original [14,650-node benchmark](BENCHMARK_YARD.md) is included
as a separate capacity save. It has not reached 20 TPS; ordinary gameplay
acceptance does not establish that target.

Fresh PG pentodes can also need a few startup calculations before measurements
become available. The checks hold their unaccepted results and stored
history, with a fixed initialization limit; persistent and later failures remain
visible. Failed calculations now identify the affected circuit and terminals.

Large-network speed remains uncertified,
and an intermittent Java solver stall during large-world reconstruction remains
under investigation. Ordinary gameplay passes do not close that separate issue.

CEE buzzer audio runs only in the client world, avoiding concurrent sound access
from single-player server ticks. The SDK includes an independently buildable
adjustable-load starter and step-by-step authoring, testing and repair guides.

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
