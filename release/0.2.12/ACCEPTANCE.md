# 0.2.12 release acceptance

The headless and graphical results come from the local acceptance dashboard
campaign `261121-133542-4f6302`, run on main at
`dbaaa173b3b1c7cfcc5c7d174372ccff8f985900`. That source includes the CEE
wire-contact fix and the isolated CEE transformer winding reference fix.

All 258 required headless checks passed across Windows and Ubuntu, Native and
Java, and BASE and ADDONS. All 12 Windows graphical scenarios passed, including
integrated play, two-client sessions and clean saves. The tested stack used
Minecraft 1.21.1, Java 21, NeoForge 21.1.262, Power Grid 0.6.2, CEE 1.1.4, Create
6.0.10 and Architectury 13.0.12.

The release reuses those results. Archive comparisons checked that only the
declared versions changed in the gameplay and test jars and that only release
labels changed in the accepted example worlds. The SDK artifacts are unchanged.
The verification download keeps the original run identities and hashes.
The included 14,650-node benchmark is the previously published save with
updated release labels. This release did not measure its performance.
