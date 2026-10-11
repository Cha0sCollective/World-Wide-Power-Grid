# WWPG: overview for CEE and Power Grid authors

**World-Wide Power Grid (WWPG)** lets CEE and PG share stationary electrical circuits using their native equipment, wires and player workflows. **[0.2.0](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.0)** adds the independently versioned **extension API 1.0.0** while retaining the stationary support set.

The pinned baseline is Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280, CEE 1.21.1-1.1.3, PG 0.6.2 and Architectury 13.0.8. Optional computer control uses CC;CPG: Pinout 0.2.1 with CC:Tweaked 1.120.2.

For players, either mod can power the other's supported machines: motors turn Create machinery, pumps move fluid, lamps light factories and heaters run processing equipment. Mixed circuits support storage, AC, transformers, voltage regulation, FE conversion and stationary Nether links. Both handheld meters work across the wire systems. All 11 built-in CEE panel attachments and 28 built-in PG board components are covered, including saved design editing, copying and assembly. Pinout keeps its native Lua API and pin numbering.

For add-on authors, the public API describes stable terminals and electrical elements, then publishes accepted readings for their own gameplay. A separate CEE API adapts an add-on's custom property classes; PG components keep PG's native route. Add-ons own recipes, assembly, visuals, damage, inventory, synchronization and saving. The two independently buildable examples and eleven-station API Workshop demonstrate those boundaries.

CEE prepares its models; WWPG maps CEE and general extension devices into persistent PG objects; PG owns solving and the substep schedule. Prepare → solve → commit runs synchronously. CEE retains gameplay, heat, damage and protection. Unavailable measurements are explicit, and an invalid circuit cannot reuse its previous successful reading. Numerical rollback covers WWPG-owned history and checkpointed CEE property state. Native PG retains its accepted-step behavior; arbitrary gameplay side effects are not a world transaction.

Native v7 is primary. WWPG supplies the missing binaries from official PG 0.6.1 after verifying hashes and the byte-identical JNI interface; PG 0.6.2 remains the runtime dependency. PG's license and attribution are included. Java receives equal functional coverage.

Final acceptance retains 204 base and 208 Pinout regression checks, plus the recorded API fixtures, in all eight platform/backend/profile combinations. It covers three cold restarts, five actual assembly reloads, saved-wire replacement, a save made before entity readiness, real backend rejection and paused measurements. Exact worlds reopen, both downloadable SDK examples build after extraction, and Windows graphical/two-client Workshop sessions check native tools and editing in each profile/backend combination. [Verification](../release/0.2.0/verification.json) and [CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38143489285) retain the evidence.

Four downloadable saves make native content and extensions testable with signs, tools and visible outputs. Historical failures are retained where their original cause remains unproven; [current status](STATUS.md) separates those records from reproduced corrections. Moving systems, other add-ons, scale certification and asynchronous execution remain deferred.

WWPG's own code and documentation are [MIT licensed](../LICENSE). We welcome upstream review of the simulation boundary, model assumptions, terminal identities and lifecycle ownership. [Supported content](SUPPORT.md) defines the claim.
