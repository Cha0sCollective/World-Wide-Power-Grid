# Current status

**[WWPG 0.2.8-beta.3](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.8-beta.3)**
is the current prerelease. It removes duplicate CEE restoration scans and restores
PG's original generator, rotor and clutch timing, including its intended oscillation.
The independent audit safety corrections remain in place.
The previous stable release remains **0.2.7**. Extension API **1.0.0** is unchanged.
Use matching server/client mods from the [installation guide](INSTALL.md).

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

Beta.3 completed all **654 required headless jobs** across local Windows and
Ubuntu/WSL, Native/Java and BASE/PINOUT. NeoForge 21.1.266 is the complete primary
environment; 21.1.231, 21.1.248 and 21.1.265 retain compatibility lanes. All
**12 real Windows client scenarios** passed, including integrated and actual TCP
sessions, tools, powered readings and clean shutdown. The actual dependency
versions and hashes, extracted SDK builds and original execution records remain locked.

Completed passing lifecycle groups were retained after interrupted orchestration;
incomplete groups ran in fresh directories. Interrupted processes remain failed
or incomplete, never passing evidence. The historical Linux Native/BASE/.248 motor
assertion remains unexplained. See the [acceptance record](../release/0.2.8-beta.3/ACCEPTANCE.md).

## Generator and transformer fixes

PG owns generator field sampling, rotor motion, torque and clutch timing again.
The stabilization introduced in 0.2.7 through 0.2.8-beta.2 is removed following
upstream confirmation that the observed oscillation is intended. A self-excited
generator can therefore hunt under the same conditions as stock PG. WWPG keeps
16 electrical substeps and the independent restoration and failure safeguards.
Transformer, variac and carbon-pile controls retain focused comparison coverage.

The PG multimeter can read assembled medium-transformer terminals through their
loaded controller. Invalidated or unavailable samples remain unavailable until
the circuit recovers. API 1.0.0 and save formats are unchanged; beta.2 updates
the guarded native libraries for allocation safety.

## Loading and ticking

The loading fixes group updates for affected circuits, preserve stored
energy while those circuits wait, and reduce repeated graph and matrix work.
Meters report unavailable readings during the pause, and devices resume without
simulating the time spent waiting. Unrelated ready factories continue operating.

Earlier improvements group add-on work by circuit, reuse accepted terminal and
wire readings, and skip empty input-assembly searches. These changes retain
electrical accuracy, stored energy and all 16 substeps. The earlier 0.2.2-beta.3 reduced
terminal preparation, CEE description rebuilding and result allocations.
The earlier 0.2.2-beta.4 corrected matrix ownership, unload/rebuild regrouping and handheld
connector samples. Failed native factors, invalid permutations and nonfinite
results are rejected before unsafe reuse; rejected circuits hold stored energy.
The original [14,650-node benchmark](BENCHMARK_YARD.md) is included
as a separate capacity save. Its beta.3 Native benchmark is recorded separately;
ordinary gameplay acceptance does not establish a 20 TPS capacity guarantee.

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

- The focused CEE train case does not certify every moving electrical system, train dimension travel or client train audio. Other moving contraptions remain future work.
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
