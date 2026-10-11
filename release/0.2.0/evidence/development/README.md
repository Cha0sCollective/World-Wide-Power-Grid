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

The final release must use separately recorded frozen artifacts and passing
acceptance runs. A later successful retry does not erase these failures or by itself
close a release gate.
