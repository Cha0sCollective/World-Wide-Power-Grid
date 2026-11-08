# Packed prototype rollback

The packed serial result-processing and worker prototype was reverted at the
user's request. The clean two- and four-worker comparisons added about 8 ms per
tick versus the batching control. See the preserved
[quiet-host measurements](PACKED_RESULTS_QUIET.md).

Runtime, build configuration, fixtures and active experiment tools are restored
to `89e1b8a0570affc73c9367ba964af302d75f977d`. This retains both plain-wire and
CEE property-source wire batching, version `0.2.7-experimental.plainwire.2`, on
`dev/experimental-plain-wire-batching`. Packed buffers, workers, lifecycle hooks,
test selection and launch flags are removed. Normal development is untouched.

The offline `jar gameTestJar -x test` build passed. Both rebuilt artifacts are
byte-for-byte identical to `local/frozen/prototype-02c`:

| Artifact | SHA-256 |
| --- | --- |
| Runtime | `8f59be1043aaaf47c33561f3327ef5f52b5ca01eac8aef7039bd1066cb849aa2` |
| Focused fixtures | `7a70d08aae6ec0ae5d757322d3881e22629e813075309fe3a4d0c8003a2a1904` |

All 361 source/build hashes in the original frozen inventory match. The six
previous passing focused launches, totaling 74 checks, are reused after
verifying their evidence and runtime, fixture and CEE artifact hashes. They
are not new test runs. No full suite or graphical acceptance was launched.
The machine-readable comparisons are in [packed-rollback.json](packed-rollback.json).

All packed reports, raw measurements, frozen jars, copied worlds and failed
attempts remain preserved. The rejected implementation and its dedicated tools
remain available at `b6e5876`; Git history was not rewritten. No benchmark saves
were loaded or changed to perform this rollback.
