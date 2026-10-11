# Pre-freeze development failures

These records preserve failed build and packaged gameplay runs from API development
on Windows with Java and native backends. They are investigation evidence, not release acceptance or a supported
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
This correction still requires packaged confirmation.

The final release must use separately recorded frozen artifacts and passing
acceptance runs. A later successful retry does not erase these failures or by itself
close a release gate.
