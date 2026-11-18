# 0.2.9 release acceptance

The gameplay jar includes the train gauge, catenary restoration and CEE
alternator unload changes from [PR #66](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/66).
The frozen execution source is `67449938de5d916a8b0c024a87393aec0c3508fe`.
The released gameplay SHA-256 is
`506b07948668e08cae21910b2a5c6ccdb3aed965dbcaea0252fcf4bd33c4c94b`.
The release reuses the original passing executions and exact accepted jars and
world exports. The verification bundle records their original identities and
the later documentation changes.

## What passed

- 342 headless jobs across Windows and Ubuntu 24.04 under WSL, with Native and
  Java solvers and BASE and PINOUT installations.
- 12 separate Windows client scenarios, including real two-client sessions,
  synchronized readings and clean saves and exits.
- Independent SDK builds and exact reopens of the combined Stationary Yard
  and both API Workshop exports. The GUI evidence uses those same archives.

The test environment used Minecraft 1.21.1, Java 21, NeoForge 21.1.262, Create
6.0.10, CEE 1.1.4, Power Grid 0.6.2 and Architectury 13.0.12. PINOUT used
CC:Tweaked 1.120.2 and Pinout 0.2.1. The audit verified the actual loaded
dependency versions and hashes in the retained execution records.

The alternator tests kept the generator and drive chunks unloaded while a
loaded resistor changed resistance. They also verified unavailable readings
during restoration and confirmed that a stopped drive did not resume supplying
power on a later unload. The catenary tests preserve genuine restoration holds
for mixed connections.

The 14,650-node benchmark save carries forward its previously published world
data and remains a separate capacity workload. This release does not establish
a new performance result for it.
