# 0.2.8-beta.2 prerelease acceptance

The tested runtime source is `c6f55f77dd929195fef43d2aadd16550db3ce7d0`.
Its jar SHA256 is
`7bc9be24019c5966d8417549b684d08afe66f6799c4d144e6d5f20a24060124e`.
The release tag retains this exact source. Later guide/publication commits do
not change runtime, API, native libraries, fixtures or acceptance outcomes.
The verification manifest separately identifies its guide snapshot.

## Reused completed acceptance

- All 558 required headless jobs passed coverage validation: local Windows 11
  and Ubuntu 24.04/WSL, Native and Java, BASE and PINOUT. NeoForge 21.1.265 is
  the complete primary runtime; 21.1.231 and 21.1.248 use compatibility lanes.
- All 12 separate real Windows client scenarios passed: integrated yard and
  Workshop, two actual TCP clients, BASE Workshop and the independent SDK
  starter. Controls, measurements, synchronization and clean shutdown passed.
- The primary run used PG 0.6.2, CEE 1.1.4, Create 6.0.10, Architectury 13.0.11,
  and the recorded optional dependency set. Extracted SDK projects were built
  independently on both operating systems. API 1.0.0 remains unchanged.

These are completed beta.2 observations reused for publication, not new runs
triggered by pushing, merging or publishing. Lightweight GitHub PR checks are
separate from full headless and graphical acceptance.

Linux's original coordinator passed all 279 jobs. A Windows progress-file lock
interrupted orchestration before a JVM launched. Its original report remains
failed; 40 complete passing jobs were retained, six missing jobs passed in
fresh directories, and the whole 279-job Windows plan was validated. No failed
gameplay assertion was blindly retried. Preparation/assembly attempts and their
assessments remain preserved. A shared evidence store removes duplicate jar
copies while preserving original JSON and execution identities.

## Runtime corrections and remaining limits

CEE rollback retains rejected dynamics and shared rotor checkpoints. Checked
endpoint identities isolate unsupported adapters before their callbacks.
Restoration selects its compatible fallback when bounded batching is disabled.
Native matrix allocation/growth errors are checked transactionally on Windows
and Linux. Archive comparisons reject duplicate entries, and meters reject
nonfinite input without replacing their previous selection. GameTests and
fixtures remain in the separate companion jar.

The historical beta1 Linux Native/BASE/.248 motor assertion remains unexplained.
It did not recur in this campaign's SETUP and three cold VERIFY phases; those
passes do not provide a causal explanation or erase the original failure.
The published generator timing correction and its documented overload response
remain intentional. No new capacity benchmark ran; 20 TPS on the 14,650-node
yard is still uncertified. Historical beta1 records remain unchanged.

The verification download contains the locked manifests, original process
records, logs and selected screenshots. Manifest paths resolve inside that
download; they are not a request to rerun either complete suite.
