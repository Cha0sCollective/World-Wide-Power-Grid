## Public addon API availability

**WWPG {{version}} does not ship a supported public extension API.** Its internal bridge classes and mixins are implementation details, not addon extension points.

The planned addon API will let mod authors integrate Power Grid and CEE addons with the WWPG ecosystem. Its interfaces, dependency coordinates, and compatibility commitments will be documented with the release that introduces it.

## What will accompany the API release

The published developer documentation will include dependency setup, a compiling first integration, supported extension points, lifecycle and threading requirements, version compatibility, and generated Java reference for the explicitly exported API packages. The website's launch check requires the reference and successful example compilation before publication.

No proposed method signatures or unverified Maven coordinates are provided for this beta. See the [version's API availability page](/api/{{version}}/) and [release-pinned project overview]({{source}}/docs/UPSTREAM_OVERVIEW.md).

## Available computer scripting

Players can already use [Pinout's native Lua integration]({{docs}}lua/) with the optional mods. It serves computer-controlled circuits and is separately documented from the future Java addon API.
