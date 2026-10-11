# Pre-freeze development failures

These records preserve failed build and packaged gameplay runs from API development
on Windows and Ubuntu 24.04 with Java and native backends. They are investigation evidence, not release acceptance or a supported
content claim.

[The index](index.json) records the observed failures, corrective changes, original
file checksums, and the checksum of [the original logs and manifests](failed-development-runs.zip).
The packaged manifests retain the exact runtime and independently built example
hashes used by each failed run. Compile failures are separate from gameplay failures.

The investigations found missing native item tagging, an incorrect board branch
lookup, publication of results before their gameplay commit, startup registration
on a stopped event bus, incomplete electrical or timing expectations in fixtures,
overlapping forced-chunk test tickets, global result suppression by an unrelated
adapter failure, an accepted-frame baseline sampled before native gameplay commit,
delayed saved wires entering a replacement block, and a handheld meter reading a
prior native result during the same frame as a new rejection. The archive also retains compile errors
encountered while integrating the contracts and pinned upstream signatures.

A separate fresh-JVM replacement run confirmed that cleanup depended on an
in-memory geometry holder. Its logs have a separate archive prefix because it
reused an earlier runtime directory. The original earlier log bytes remain
unchanged in the archive. Exact saved wire ownership and reconnect identities
must pass the separate cold-restart fixtures before this defect is closed.

A later 31-test native run passed 30 tests but failed the rejected-wire thermal
check. A source regression reproduces CEE accepting an unrelated cached node
ordinal after graph ordering changes. Compatibility results now validate that
hint against the stable node. The gameplay check also distinguishes a missing
meter selection from an invalid wire temperature. Its completed setup log and
exact packaged manifest are retained; later cold verification reused its runtime
directory, so their overwritten `latest.log` is not attributed to the failed run.
The next native setup passed all 31 checks, including the callback fault and wire
thermal assertions. Its first fresh-JVM verification then failed during initial
native wire placement in the extension five-cycle fixture. That readiness failure
remains under investigation. Both completed build logs and their exact packaged
manifests are retained; the cold verification loop stopped after the failure.

[Preliminary GitHub CI run 38131357758](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38131357758)
at commit `41f40860118a3afe5e9acf62bb784f1f36ed46b9` failed all eight jobs:
Windows/Linux, native/Java, and base/Pinout. Each job passed the 31-test setup,
then failed its first cold API verification. The same five-cycle fixture either
rejected initial native wiring or found extra old PG wire identities. Completed
GitHub run/job metadata, all eight actual job logs, and the downloaded Ubuntu
Java/base artifact subset are retained with checksums. The artifact subset's
current logs belong to the failed verification; compressed earlier logs and
phase-specific packaged manifests retain setup provenance.

Source investigation found that removing a block to air and replacing it with
the same API owner before the deferred scan concealed the physical removal,
leaving old wire identities. Durable removal tracking and selected-source checks
are being implemented. This explanation comes from the investigation; the CI
logs independently record the observed failures. No second or third cold
verification ran in these jobs, and the corrective changes still require the
complete release checks.

The isolated native pending-removal fixture subsequently passed setup but failed
its separate-JVM `ARM` phase because terminal caches were already populated.
Its delayed-loading gate started after early startup restoration had resolved
the devices. The strict precondition prevented that run from claiming a pending
removal before entity readiness. Its failed ARM log, matching passing setup,
successful build log and both exact phase manifests are retained. The fixture's
gate timing is being corrected; the intended persistence check still needs to
run from a proven uncached state.

The full native run using the same jar then passed 31 of 32 checks, including
both five-cycle unload/reload fixtures and the diode's 30 accepted parameter
updates with stable objects and preserved storage history. The cold replacement
fixture failed initial native PG wiring before exercising its replacement
checks. Its fixed initial delay may assume readiness too early under the new
loading guard. An explicit native wiring readiness predicate is being added;
that explanation and the correction still require a packaged rerun. The full
failed log and its exact manifest remain in the archive.

A subsequent isolated native `ARM` log records that its loading gate was
installed before the first server tick, yet still fails the fresh terminal-cache
precondition. It has no API device-position failure warnings before the fixture.
The first cache-creation path is being traced: cached geometry alone does not
prove that accepted electrical objects or saved state advanced. The setup, build
and ARM logs and exact manifests are retained. The early gate marker proves its
installation timing; pending-removal persistence remains unverified.

The next trace-enabled native `ARM` run again stopped at the fresh-cache
precondition. This time it directly recorded unchanged disk history, zero API
electrical objects and unavailable results while load geometry was cached.
Discovery returned `NOT_READY`. The first cache call stacks include PG endpoint
validation on the entity-deserializer worker and CEE geometry resolution on the
server thread. This evidence distinguishes sidecar discovery from accepted
electrical state advancement; it does not revise the original earlier logs.
Server-thread capability guarding and deferred native wire restoration are being
implemented and still need packaged proof. The failed ARM, matching setup/build
logs and exact manifests are retained. Every previously archived member remains
byte-for-byte unchanged.

Candidate 18 uses the traced electrical invariants instead of requiring all
read-only geometry caches to be absent. Its `ARM` run passes unchanged disk
history, zero API model objects and unavailable results, then fails the loading
guard's post-replacement terminal geometry check. Physical removal and immediate
same-owner replacement mark the old cached holder as changed, so that query
returns no holder. The tool readiness path and fixture expectation are under
review. Replacement wire acceptance and subsequent electrical advancement were
not reached; those requirements remain unverified. This failed run and its
matching setup/build logs and exact manifests are retained without changing the
earlier historical records.

Candidate 20 passes the authoritative history, unavailable-result, exact pending
ownership and actual native tool rejection checks. It then finds the exact saved
PG wire entities in the held IO inbox with a native graph wire already bound,
before their server tick. This is a separate graph-readiness defect. Source
investigation found that native storage flushing can deserialize on the server
thread, so a worker-thread guard alone is insufficient. A narrow API-endpoint
readiness check and binding trace are being added. The save-before-ready and
subsequent cold verification were not reached. The failed ARM and matching
setup/build logs and exact manifests are retained; packaged proof remains
required, and every earlier archive member is unchanged.

Candidate 21's bounded diagnostic appendix records passing separate-JVM
`SETUP`, `ARM` and `VERIFY` phases on both native and Java backends. Held saved
PG entities keep null wire references before readiness; exact old PG/CEE IDs are
retired, isolated replacements keep zero charge, and fresh wires power them once
ready. The build log, all six phase logs and their exact manifests are retained.

The explicit trace corrects the earlier threading interpretation: it reports
`serverThread=true` and `entityReady=false` while the temporary thread name is
`entity-deserializer`. That name alone does not establish worker ownership.
The saved NBT `readAdditionalSaveData → setEndpoint2 → makeWire` stack confirms
binding attempts before entity readiness; pinned storage flushing and mailbox
`runAll` explain synchronous server-thread deserialization. The readiness guard
is necessary alongside actual thread-ownership checks. Earlier evidence remains
byte-for-byte unchanged. These instrumented candidate 21 checks are development
proof, separate from final frozen acceptance of the clean release artifact.

Candidate 22, source reference `189f39f`, passed the 32-check Java setup and
three cold verification runs, plus its standalone pause check. These completed
development logs and exact manifests are retained in a separate success appendix.
The native base Workshop setup nevertheless failed native wiring at an API
source: its fixed twenty-tick builder delay preceded entity readiness. The failed
Workshop build output, actual runtime logs/manifest and exact copied runtime jar
are retained. The builder is being changed to wait for real wiring readiness;
the Workshop proof and complete release acceptance remain pending. All earlier
archive members remain unchanged.

Candidate 23's Workshop setup passed its native wiring and visible station
checks, but its first cold verification failed the A10 panel indicator even
after endpoints were ready. Source investigation found that the builder changed
the native switch's live wire without updating its saved `OPEN` block state.
Reload restored the saved open switch and left the panel unpowered. The builder
helper is being corrected to keep both states consistent; native switch runtime
behavior is unchanged. The exact candidate 23 jar, passing setup, failed cold
verification, build output and both phase manifests are retained. Runtime
`latest.log` belongs to the failed verification; the original setup is retained
in its completed build log and compressed logs. Packaged cold verification of
the correction remains required.

Candidate 24's native base Workshop setup and three cold verification runs
passed all eleven station checks. Their exact phase manifests, completed build
outputs and compressed/runtime logs are retained as a separate development
success appendix. This confirms the local builder corrections against that jar;
it does not establish the final cross-platform release gates.

The same candidate's inherited native suite passed 202 of 204 checks. Both
failures stopped at the new resistor heating precondition: the fixture supplied
0.25 W, below CEE's native 3.3 W cooling floor. The corrected fixture supplies
25 W to the tested resistor and 36 W to its independent healthy reference,
keeping the isolation, temperature, healthy-circuit and repair assertions.
The failed run's exact jar, manifest and actual logs remain retained; the
corrected checks still require packaged proof.

The second preliminary GitHub CI run,
[38135640810](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38135640810),
tested source `189f39fc410f78da1b45c1942c9b13641f786953` across all eight
Windows/Linux, native/Java and base/Pinout combinations. Every job passed its
standalone pause and three-phase pending-removal checks. Their later outcomes
differed:

- Six jobs passed the 32-check API setup and all three API cold verifications,
  then failed both inherited unknown/nonfinite isolation fixtures. Those old
  assertions expected zero where a deliberately unavailable native reading
  returns `NaN`. The corrected assertions check unavailable measurements,
  thermal safety, an unaffected healthy circuit and recovery after repair.
- Windows Java/base failed diagnostic initial wiring during API setup.
- Ubuntu native/Pinout passed API setup and two cold verifications, then failed
  the same diagnostic's initial wiring during its third cold verification.

The diagnostic used a fixed initial delay instead of waiting for native endpoint
readiness. Its actual wiring expectation remains; the fixture now waits for
readiness. All fourteen failed assertions, each job's failed step and reached
phases, completed GitHub metadata and all eight actual job logs are retained.
The downloaded Ubuntu Java/base artifact subset includes source provenance and
exact phase manifests; other job artifacts were not downloaded. No job reached
the Workshop gate, so the separately retained local Workshop failures are not
attributed to this CI run. This failed CI was downloaded directly, without
running the strict release acceptance collector. Corrective changes still
require complete frozen acceptance, and all earlier archive bytes are preserved.

The final release must use separately recorded frozen artifacts and passing
acceptance runs. A later successful retry does not erase these failures or by itself
close a release gate.

Candidate 25 passed the base Workshop setup, three source cold restarts and
three restarts after exact ZIP extraction. Its first actual TCP session joined
two real clients, but both failed their first station view: the acceptance
helper created a fresh `BooleanProperty` rather than using the property declared
on the example block. The server correctly rejected their failed acknowledgements.
The same session logged a moved-too-quickly warning during its long initial
view jump. The helper is being corrected to resolve declared properties and
travel through bounded native movement packets. The actual logs, process result,
launch specifications, old helper jar/source and frozen runtime are retained.
These failures do not establish a public API defect, and corrected actual-client
acceptance remains required. Every previous archive member is unchanged.

Batch26 used the same frozen candidate25 runtime and example jars with a corrected,
independently hashed acceptance helper. Both real TCP clients passed all eleven
station views and the server accepted that viewing phase. The actual native PG
wire item powered its API bench at 20 V; the native CEE wire item then left its
bench at zero instead of the required 20 V, failing the wiring phase. This attempt
does not complete client acceptance.

Source investigation reproduced block-first item dispatch: the general example
block returned `SUCCESS` from its ordinary block interaction with a nonempty held
item, consuming native CEE spool and PG multimeter item actions. A narrow example
`useItemOn` override is being added to let nonempty native tools reach their item
workflow while preserving empty-hand readings and configuration. The runtime
wiring hooks are unchanged. Corrected artifacts still require independently built
SDK examples and full real-client acceptance; no passing retry closes this gate
by itself. The immutable launched helper jar, captured Java/init/resource inputs,
all launch files, logs, screenshots, thread dumps, process result, preparation log,
frozen runtime and old example jar are retained. All 475 earlier archive members
remain byte-for-byte unchanged.

The item-dispatch cause now has a class-only negative control. The controlled jar
restores only the old `ExampleBlock` class into the corrected example; all seven
new regression fixtures remain byte-identical. That negative native run passes
six tests and fails exactly the powered native-item workflow at zero current
instead of 0.2 A. The corrected jar passes all seven checks on both native and
Java. The exact old/corrected/negative jars, construction script and hash record,
three completed logs and matching packaged manifests are retained. These causal
controls remain a development appendix, not final frozen SDK or real-client
acceptance. Every previously archived member remains unchanged.

An initial offline example build also used the wrong local Maven repository path
and could not resolve API 1.0.0. Its failed log and the successful corrected build
using `build/maven-repository` are kept as a separate tooling appendix. That
invocation error is not attributed to the public API implementation.

The two superseded extracted-SDK attempts, candidates 22 and 25, are now retained
compactly in this archive. Each independently compiled both projects after
extracting the exact downloadable example source ZIPs and Maven ZIP. Produced
jars match their historical frozen examples. Original attempt/proof JSON, build
logs, dependency records, compile-evidence scripts, output jars, input archives
and runtime artifacts are preserved with their actual hashes. Expanded duplicate
source trees and Gradle outputs are unnecessary to inspect these proofs because
the exact source and Maven archives are included. Both attempts remained
non-publishable and unfrozen; their successful SDK builds do not transfer to a
corrected final release whose artifact set differs. Prior archive bytes remain
unchanged.

Batch27, source `b8e6eaaf838a7d3ea676412ce9ed937df205557f`, reached native
board editing after both real TCP clients passed all eleven station views and
the server accepted wiring, configuration, meters and panel editing. The board
assertion dereferenced a null schematic. Original saved player NBT shows a blank
schematic in slot 0 and the valid native design-table copy in slot 1, preserving
the edited name, custom shunt settings and traces. The native copy workflow
produced that saved item; the assertion must skip blank items and compare full
`serializeSafeNbt` contents because native copying intentionally changes identity
UUIDs. Placed-board identity assertions remain required. This failed attempt is
not client acceptance, and the correction requires a fresh frozen runtime and
complete real-client runs.

The exact launched helper jar and captured inputs, both player `.dat` and
`.dat_old` files, inventory inspection script/output, all launch files, logs,
screenshots, thread dumps, process result, preparation/source/cold-run proofs,
exported world ZIP and frozen candidate27 artifact/ABI files are retained. Earlier
archive member bytes remain unchanged.

The superseded candidate25 CI run [38136995579](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38136995579),
source `2b6974a0b1cc4edb3c22ba26536fd2d1a009c529`, is now retained after its
actual completion. All eight raw job logs and original run/attempt metadata
are archived. The index records each reached phase, failed step, assertion
and pass count rather than assigning a shared outcome to unfinished jobs.
The completed metadata records 8 failed jobs; other outcomes are
[]. A Windows Java/base downloaded artifact subset retains
the actual checkout SHA/tree/attempt, native and compatibility accumulator
traces, other reference tables, phase manifests and original reference logs.
The observed accumulator frame offset led to a fixture timing/readiness
correction. Its passing local reference checks are separate from the new
frozen eight-way acceptance still required. The strict release collector was
not used to relabel this failed run, and every earlier archive byte remains
unchanged.
