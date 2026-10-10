# WWPG: overview for CEE and Power Grid authors

**World-Wide Power Grid (WWPG)** is a compatibility mod that lets CEE and PG participate in shared electrical circuits. The first beta focuses on stationary factories, with supported devices usable across both mods through their native wires and interaction workflows.

The pinned baseline is **Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280, CEE 1.21.1-1.1.3, and PG 0.6.2**. Architectury 13.0.8 is also required.

WWPG intercepts CEE's simulation boundary, translates its prepared electrical models into persistent PG nodes/elements, and returns PG's solved values to CEE's result queries. PG owns the solve and substep schedule; CEE retains machine behavior, heat, damage, protection, visuals, and synchronization. Preparation, solving, and gameplay commit run synchronously. Integration uses targeted Mixins/accessors and the existing upstream models.

For players, this means either mod can power the other's supported equipment: motors drive Create machinery, pumps move water, lights illuminate factories, and heaters produce heat. Both wire tools connect supported terminals. PG's handheld multimeter reads either mod's terminals and wires; CEE's clamp meter reads either mod's wires. Mixed circuits support generation, controls, meters, CEE accumulator/capacitor storage, inductors, and transformers, including **all 11 built-in CEE panel attachments** and **all 28 built-in PG board components**.

Native v7 is primary. WWPG supplies the binaries omitted from the pinned PG 0.6.2 jar using the official 0.6.1 artifact, with verified hashes and byte-identical JNI interfaces. PG's license and attribution are included; the donor mod is excluded from the runtime. Java is also tested.

**0.1.0-beta.2 adds handheld-meter compatibility and a working burner demonstration to the first stationary beta.** The original 114-test suite expands to 118, including a regression for concurrent mod registration, with real-client checks for meter targeting and displayed readings. The original release has passing Windows/Linux native/Java checks; [current status](STATUS.md) records the hotfix results. One earlier Linux/native test found a circuit without power after part of the world unloaded and reloaded. Later runs passed, but the cause remains unresolved. Moving systems, optional integrations, and large-network testing are deferred.

WWPG's own code and documentation are [MIT licensed](../LICENSE). We welcome contributions and review of the simulation hooks, model assumptions, terminal mappings, and lifecycle ownership. [Current status and evidence](STATUS.md), [supported content](SUPPORT.md), and [beta downloads](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.2) provide the details.
