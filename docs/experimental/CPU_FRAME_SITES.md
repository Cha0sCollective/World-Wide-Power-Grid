# CPU frame-location follow-up

Experimental development snapshot, November 9, 2026. The retained gameplay
runtime remains `0.2.7-experimental.wiretree.11`, frozen in `prototype-12g`.
This investigation changes diagnostic tooling only, on
`dev/experimental-plain-wire-batching`. Original saves and normal development
remain untouched. No performance improvement is claimed.

The [audited recording](cpu-sites-results-20261109.json) and
[frame-site export](cpu-frame-sites-20261109.json) preserve the new observation.
The [verification record](cpu-sites-verification-20261109.json) records frozen
artifact/source comparisons and focused tool evidence.

## Completed observation

`goal-cpu-sites-01` used a fresh benchmark-save copy, the unchanged affinity
helper, CPUs 12–23 (`fff000`), both wire batches, the 20 ms restoration budget,
unresolved-wire priority, the 2 ms chunk-task window, CEE state geometry reuse
and scoped restoration. Warmup and steady sampling each lasted 120 seconds.
Detailed phase timers, substep tracing and RHS counters were disabled.

| Measurement | Diagnostic result |
| --- | ---: |
| Steady ticks | 2,399 |
| TPS | 20.031 |
| Mean tick | 49.341 ms |
| p95 / p99 | 56.623 / 61.218 ms |
| Maximum | 72.880 ms |
| Server CPU per tick | 49.083 ms |
| Allocation per tick | 12.378 MiB |
| Compilation during sample | 1.776 s |
| GC pauses inside filtered interval | 88.295 ms |

The owned JVM exited normally at `2026-11-09T20:04:20Z`, after 286.042 seconds.
The benchmark retained 14,650 nodes, 20,637 edges, 14,925 matrix rows, one native
solver network, 1,050 storage devices and all 16 electrical substeps. Its
topology, save/shutdown, affinity and original-reference checks passed. The
p95 50 ms gate remains unmet. This diagnostic is not a clean speed comparison
or another client-loading observation. The retained 44.399-second client
result at 1920×1080 / 12 chunks still belongs to its named scoped-restoration
run and unchanged runtime.

## Additional compiled-code metadata

`--cpu-jfr-non-safepoints` adds `-XX:+UnlockDiagnosticVMOptions` and
`-XX:+DebugNonSafepoints` from JVM startup, and requires `--cpu-jfr`.
The runner reads `VM.flags -all` from its owned JVM during warmup and rejects
missing or disabled flags. Both were observed enabled on the selected JDK
21.0.12. The ordinary runner defaults remain unchanged.

Oracle's [JFR runtime guide, section 1.4.1](https://docs.oracle.com/javacomponents/jmc-5-5/jfr-runtime-guide/JFRRT.pdf)
describes these flags as providing additional compiled-code locations for
profiling. That guide documents an older JDK; current flag support and values
were checked locally. Additional metadata does not turn sampling into exact
exclusive instruction timing.

Recording startup completed 115.843 seconds before the first steady tick.
The analyzed inner interval is 119.712 seconds, with the same two-millisecond
boundary margins and first-tick omission used by the earlier CPU-only profile.
The wall/monotonic offset spread was 2.1364 ms. Raw recordings, filtered
exports, flags and commands are retained separately with hashes.

`JfrSites.java` retains method descriptors, line numbers, bytecode indices and
frame types. Its population exactly matches the audited compact export:
8,576 server Java samples and 188 native-method samples. Java and native sample
periods differ; their percentages are not combined. Twelve profiling-tool
checks passed. Three additional checks verified interval boundaries,
preservation of an existing output and rejection of a reversed interval.
The earlier recording's full 8,886-event CPU population also matched its
frame-site export. No gameplay fixture suite was rerun for unchanged jars.

## What the samples establish

| Exclusive enclosing scope | Java samples | Share |
| --- | ---: | ---: |
| CEE preparation | 1,574 | 18.35% |
| Terminal preparation | 1,003 | 11.70% |
| CEE after-solve processing | 845 | 9.85% |
| Wire-power hooks | 771 | 8.99% |
| Wire temperature | 753 | 8.78% |
| Terminal waveform sampling | 628 | 7.32% |
| Other electrical work | 583 | 6.80% |
| Other wire-entity updates | 512 | 5.97% |

These broad scopes resemble the preceding recording. The leaf locations differ
substantially: across all server Java samples, `ArrayList$Itr.next` fell from
539 to 16, `IdentityHashMap.get` from 357 to 23, and
`ThreadLocal$ThreadLocalMap.getEntry` from 234 to one. These are separate
recordings, with different metadata options and retained runtime snapshots
(`wiretree.8` versus `wiretree.11`); they do not isolate a flag effect or show
that those operations became cheaper. The indexed-waveform trial already
failed to demonstrate an elapsed-time gain and remains retired.

The newer detail is still not a trustworthy exclusive-cost estimate. Of 845
after-solve samples, 713 name `WorldBridge.afterSolve` line 674 / bytecode 56,
the backend-counter merge performed once per network/substep. Likewise,
433 CEE-preparation samples end inside the generated winding-key hash path
below the second-pass winding lookup. Neither count proves that the named
small operation itself consumed the enclosing loop's CPU time. Do not remove
validation, counters or thread isolation based on these labels.

Terminal sampling now shows the owner check, assigned-row checks and native
buffer reads. Temperature samples include current resistance and fresh biome
lookup; other wire-entity samples include spatial entity-section searches.
These remain leads for source review, with no measured removable-cost claim.

## Slow-tick investigation and next step

An offline join of the existing per-tick counters examined this diagnostic,
the retained scoped-restoration clean run and the disabled indexed-waveform
control. Allocation/time correlations were approximately 0.010, -0.069 and
-0.020. In each recording, ticks allocating more than 15 MiB had a lower mean
and p95 than the remaining ticks. Source half-cycles and tick residues did not
single out a dominant periodic spike. These are exploratory associations,
not proof about allocation causality. Raw groups and code are preserved in
`local/preparation-review-01/tick-variation.json` and its adjacent script.

The next diagnostic should separately time the existing CEE preparation
sections: fresh owner checks, descriptor/topology validation, branch-parameter
validation, rebinding/application and transformer lookup/update. Use sparse
whole-section timing, no per-element timers, and retain an unchanged clean
control. This can test whether a preparation-local capture of already
validated properties/windings would avoid meaningful repeated work before
building another representation change. Preserve every fresh validation,
parameter update, callback, failure path and substep.

The prior worker and per-substep packing implementation stays retired.
Persistent result storage is only a later candidate: it must reduce the
existing serial passes and retain the separate PG, API/CEE and terminal
callback boundaries. This recording does not establish a parallel speedup.

## Reproduce the diagnostic

```text
python -B tools/experimental/plain_wire_benchmark.py --reference-root G:/WWPG --runtime local/frozen/prototype-12g/wwpg-0.2.7-experimental.wiretree.11.jar --helper-inputs local/frozen/affinity-helper-02/inputs.json --cpu-mask fff000 --batch --property-batch --tree-batch --tree-budget-ms 20 --tree-prioritize-unresolved --chunk-task-budget-ms 2 --cee-state-geometry --scoped-restoration --cpu-jfr --cpu-jfr-non-safepoints --name cpu-sites-new
python -B tools/experimental/summarize_cpu_profile.py --attempt cpu-sites-new --inputs local/frozen/prototype-12g/inputs.json --clean-report docs/experimental/scoped-restoration-results-20261109.json --output docs/experimental/cpu-sites-new.json
python -B tools/experimental/summarize_cpu_sites.py --report docs/experimental/cpu-sites-new.json --inputs local/frozen/prototype-12g/inputs.json --output docs/experimental/cpu-frame-sites-new.json
```

Use new attempt and output names. The two summarizers analyze preserved files;
they do not launch Minecraft. Neither a full acceptance suite nor a new client
run is implied by these focused diagnostic results.
