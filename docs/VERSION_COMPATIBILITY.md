# Version compatibility

**WWPG 0.2.8-beta.3** targets Minecraft **1.21.1**. Use matching mod versions on
server and clients, following the [installation guide](INSTALL.md).

| Dependency | Minimum accepted by WWPG | Checked release input |
| --- | --- | --- |
| NeoForge | 21.1.231 | 21.1.266 primary; separate .231, .248 and .265 compatibility lanes |
| Create | 6.0.10 | Published NeoForge 1.21.1 Create 6.0.10 |
| CEE | 1.21.1-1.1.3 | 1.1.4 complete campaign; 1.1.3 retains focused compatibility coverage |
| Power Grid | 0.6.2 | 0.6.2 for Minecraft 1.21.1 |
| Architectury | Required by the upstream mods | 13.0.11 NeoForge |

The optional computer profile uses CC:Tweaked **1.121.0** and Pinout **0.2.1**.
CC:Tweaked 1.121.0 requires NeoForge 21.1.265; the older loader lanes use the
separately locked CC:Tweaked 1.120.2. The [beta.3 acceptance record](../release/0.2.8-beta.3/ACCEPTANCE.md)
identifies the newly frozen dependency set and fresh complete campaign. Earlier
release records retain their own dependency and execution identities.

CEE 1.1.4 corrected its variac voltage lookup. WWPG recognizes that corrected
call and the older 1.1.3 call, preserving the correct local voltage and heating
reading for each. Focused checks cover both solvers and the panel/tool changes;
the retained 0.2.8 campaign uses 1.1.4. Minimum SDK compile dependencies remain pinned separately.

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
