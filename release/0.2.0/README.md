# WWPG 0.2.0 and extension API 1.0.0

This directory records implementation and acceptance for the public stationary
extension API. It is a development record until the frozen release gates pass.

- [Developer contract](../../docs/EXTENSION_API.md)
- [Stage plan and completion rule](../../docs/EXTENSION_API_PLAN.md)
- [Current progress](progress.json)
- [Playable API Workshop](../../docs/EXTENSION_API_WORKSHOP.md)

The first API release establishes a classfile contract snapshot. It cannot claim
binary compatibility with an earlier public API that did not exist. Subsequent
compatible API releases must compare against the actual published artifact and
run its independently compiled example.

The final release record must lock runtime, API, sources, Javadoc, independent
example, local Maven repository, worlds, and all acceptance evidence by SHA256.
SDK candidates remain explicitly non-publishable. Historical beta.5 records and
published artifacts are not expanded retroactively.
