# WWPG: overview for CEE and Power Grid authors

**World-Wide Power Grid (WWPG)** lets CEE and PG participate in shared stationary electrical circuits using their native equipment, wires and player workflows. **[0.1.0-beta.5](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.5)** is the stationary feature-complete beta.

The pinned baseline is Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280, CEE 1.21.1-1.1.3, PG 0.6.2 and Architectury 13.0.8. Optional computer control uses CC;CPG: Pinout 0.2.1 with CC:Tweaked 1.120.2.

For players, either mod can power the other's supported machines: motors turn Create machinery, pumps move fluid, lamps light factories and heaters run processing equipment. Mixed circuits support generation, storage, AC, transformers, voltage regulation, FE conversion and stationary Nether links. Both handheld meters work across the wire systems. All 11 built-in CEE panel attachments and 28 built-in PG board components are covered, including saved design-table editing, copying and assembly. Pinout keeps its native eight-method Lua API and pin numbering.

WWPG intercepts CEE's simulation boundary, maps prepared models into persistent PG nodes/elements, and returns solved values to CEE's result queries. PG owns the substep schedule; CEE keeps gameplay, heat, damage, protection and synchronization. Prepare → solve → commit runs synchronously. Targeted compatibility hooks preserve the native models and workflows.

Native v7 is primary. WWPG supplies the missing binaries from official PG 0.6.1 after verifying hashes and the byte-identical JNI interface; the donor mod is excluded from runtime. PG's license and attribution are included. Java receives equal functional coverage.

The final jar passes 204 base checks and 208 optional checks on Windows/Linux with both solvers, three consecutive cold restarts, five actual assembly reloads, upstream references, exact exported worlds, full graphical visits and two real multiplayer clients in each profile/backend combination. The two downloadable yards make every supported behavior testable with signs, tools and visible outputs. [Verification](../release/0.1.0-beta.5/verification.json) and [CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38116785029) provide the evidence.

This work also repairs native design-table loading/saving, fuse persistence and repair wiring, small CEE diode updates and a reduced-control-node crash during PG nonlinear removal. Earlier failures are retained where their original cause remains unproven; [current status](STATUS.md) distinguishes those records from reproduced corrections. Moving systems, other add-ons, scale certification, asynchronous execution and a public API remain deferred.

WWPG's own code and documentation are [MIT licensed](../LICENSE). We welcome upstream review of the simulation boundary, model assumptions, terminal identities and lifecycle ownership. [Supported content](SUPPORT.md) defines the claim.
