# WWPG 0.2.4 release preparation

The merged dev.106 train fixes are being packaged as 0.2.4. This directory is
a preparation record, not a published release or a claim of full acceptance.

The update preserves continuous overhead contacts across wire joints and
adjacent contact crossings, confines shared train results and storage updates
to the train's dimension, and prevents unavailable voltage from becoming an
invalid load coefficient. Genuine unavailable measurements remain visible.

Preserve the dev.106 passing focused Native/Java checks and their original
source/artifact identities. Version-only equivalence must be verified before
carrying them forward. Older 0.2.3 full acceptance describes a different runtime
and cannot be relabelled as acceptance of the train changes.

API 1.0.0, save formats, the native binaries, equations and all 16 substeps stay
unchanged. Test fixtures remain in the separate companion jar. Do not publish
until the independent release gates in [RELEASING](../../docs/RELEASING.md) pass.
