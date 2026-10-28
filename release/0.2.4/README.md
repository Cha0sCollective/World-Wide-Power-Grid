# WWPG 0.2.4 release record

The train and API startup fixes passed independent headless and graphical
acceptance. The downloads are prepared for final merge/publication approval;
this record does not by itself make the release public.

The update preserves continuous overhead contacts across wire joints and
adjacent contact crossings, confines shared train results and storage updates
to the train's dimension, and prevents unavailable voltage from becoming an
invalid load coefficient. Genuine unavailable measurements remain visible.

Release acceptance also exposed recursive CEE loading from API terminal
binding during Workshop startup. The corrected candidate persists terminal
identities during binding and collects physical wire ownership during normal
preparation. The original failed campaign remains preserved; fresh acceptance
is required for this runtime correction.

Fresh headless run 40779930742 passed all 342 catalog jobs across Windows/Linux,
Native/Java, BASE/PINOUT and both supported loaders. Twelve client scenarios
passed with exact exported archives and clean shutdowns. A local starter-proof
path stopped the first client attempt before either starter launched; its ten
passing scenarios were retained, and only the two missing starter cases ran in
a fresh attempt after the verified proof was bound correctly. Earlier failures
remain preserved rather than relabelled as passing full campaigns.

API 1.0.0, save formats, the native binaries, equations and all 16 substeps stay
unchanged. Test fixtures remain in the separate companion jar. The independent
release gates in [RELEASING](../../docs/RELEASING.md) validate the frozen inputs;
later guide-only changes retain the actual runtime execution identity.
