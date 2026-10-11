# WWPG 0.2.0 — extension API 1.0.0


WWPG 0.2.0 lets add-on creators connect their stationary machines to a mixed
Create: Electro Energetics and Power Grid factory through a documented public
API. Their machines can use the existing wires and handheld meters, receive
power from either mod, and save their accepted electrical state.

For players, the new Extension API Workshop makes that integration visible:
switch lamps and loads, charge and discharge storage, change a transformer
ratio, watch a CEE panel indicator fade, and operate an assembled PG circuit
board. Eleven stations explain the controls, expected readings, and resets.
The workshop requires the two example add-on jars supplied with the release;
ordinary WWPG factories do not require them.

For developers, API 1.0.0 supplies stable terminals and circuit identities,
sources, loads, switches, storage, AC, transformers, diodes, controlled sources,
and measurements with explicit unavailable states. A separate CEE API supports
an add-on's own property classes. Native PG components remain in PG's native
simulation. One PG schedule drives the mixed electrical system.

The update also hardens saved terminal ownership, late-loading wire entities,
scoped callback failures, rejected numerical steps, and paused meter readings.
An unavailable circuit does not keep displaying its previous successful value.
Replacing an extension machine retires its old wires even if the world is saved
before those wires finish loading. While that cleanup is pending, the native wire
tools show a loading message and allow the player to try again once ready.
Numerical rollback covers compatibility-owned history and checkpointed CEE
properties; native PG keeps its native accepted-step behavior. Arbitrary gameplay
effects from an add-on callback are outside that numerical rollback contract.

Download the runtime jar for play. Developers can additionally download the
API jars, source/Javadoc jars, Maven repository ZIP, and independently buildable
example projects. The API version is independent of the WWPG mod version.
This first public API release establishes its binary contract baseline.

Use Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280,
CEE 1.1.3, PG 0.6.2, and Architectury API 13.0.8. Optional computer control keeps
CC:Tweaked 1.120.2 and CC;CPG: Pinout 0.2.1. Install the same versions on the
server and every client. See the [installation instructions](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/INSTALL.md) and the supplied dependency/checksum manifests.

This release retains the declared stationary scope. Moving contraptions,
broader version support, alternate solver APIs, and large-server certification
remain later work. Existing beta.5 content and identities remain regression
requirements; previous published artifacts are unchanged.


The complete eight-job verification passed on the frozen implementation. [CI run](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38143489285). See the supplied verification record for the executed counts, exact world hashes, independently extracted SDK builds and eight Windows graphical/TCP-client sessions.
