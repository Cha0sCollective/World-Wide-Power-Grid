# Version compatibility

The **0.2.8-rc.1 candidate** uses NeoForge 21.1.265, published Create 6.0.10,
CEE 1.1.4 and Architectury 13.0.11 for its new release campaign. It retains
21.1.231 and 21.1.248 as compatibility lanes. The optional computer profile
uses CC:Tweaked 1.121.0 and Pinout 0.2.1. Acceptance is pending; exact files
and hashes are in the [runtime selection](../release/0.2.8-rc.1/runtime-selection.json).
The table below records the published 0.2.7 snapshot.

WWPG **0.2.7** targets Minecraft **1.21.1**. Use matching mod versions on the
server and every client, and follow the [installation guide](INSTALL.md).

| Dependency | Minimum accepted by WWPG | Checked versions |
| --- | --- | --- |
| NeoForge | 21.1.231 | 21.1.231 and 21.1.248 in full release acceptance |
| Create | 6.0.10 | 6.0.10-280 |
| CEE | 1.21.1-1.1.3 | 1.1.4 in full headless and graphical acceptance; 1.1.3 retains focused compatibility coverage |
| Power Grid | 0.6.2 | 0.6.2 for Minecraft 1.21.1 |

CEE 1.1.4 corrected its variac voltage lookup. WWPG recognizes that corrected
call and the older 1.1.3 call, preserving the correct local voltage and heating
reading for each. Focused checks cover both solvers and the panel/tool changes;
the 0.2.7 complete acceptance campaign uses 1.1.4. Minimum SDK compile dependencies remain pinned separately.

Newer NeoForge, Create, CEE and Power Grid versions are allowed to load so players
can try them. Being allowed to load does not establish compatibility. Consult the
release's actual results before updating a server. Upstream mods also have their
own requirements.

Maintainers can request the manual [loader compatibility action](../.github/workflows/loader-compatibility.yml)
to test selected NeoForge 21.1 versions in parallel across Windows/Linux,
Native/Java and BASE/ADDONS. ADDONS includes the pinned optional integrations
and SDK examples. The action checks the exact frozen jars; its focused result is
separate from [full headless and graphical acceptance](TESTING.md).

Historical releases retain their original requirements and records. In
particular, the unchanged WWPG 0.2.0 jar requires NeoForge 21.1.231.
