# Mixed benchmark yard: 14,650 electrical nodes

The 0.2.4 example-worlds bundle includes `wwpg-0.2.4-benchmark-14650-nodes.zip`.
This retains the original save; 0.2.4 release checks do not include a fresh
capacity timing run. It is separate from the guided
[Stationary Test Yard](STATIONARY_YARD.md) and [API Workshop](EXTENSION_API_WORKSHOP.md).

It contains **14,650 electrical nodes, 20,637 connections, 1,050 stateful devices
and 953 chunks**. The connections comprise 20,362 wires and 275 couplings.
Its former “10000 nodes” name referred to the requested construction tier;
it understated the completed workload. The export preserves that workload.

Install the base [required mods](INSTALL.md#required-versions), Java 21 and
WWPG 0.2.4. Pinout, the SDK example add-ons and a benchmark helper are not
required. Reference measurements used an 8 GiB game heap. Use PG's
`solverBackend = "NATIVE"` and `multiTicks = 16` in
`config/powergrid-common.toml`, then restart the game.

Extract the ZIP's named folder into your instance's `saves` directory and open
it in Creative mode. Spawn is near `(1038,64,1038)`. Saved forced chunk tickets
load the whole yard. Allow loading and electrical restoration to finish before
judging speed. Use native switches, meters and goggles to inspect circuits.
This layout has no guided sequence of stations and does not exercise API add-on
devices. Use the separate Workshop for those devices.

Save and Quit normally and allow saving to finish. Keep the ZIP unchanged;
reset by extracting a fresh copy into a different save folder. Edits and stored
energy affect later comparisons.

The original yard has **not** achieved a certified 19.9 TPS with p95 tick time
at or below 50 ms. This is a heavy workload, not a server-capacity guarantee.
The export reuses the tested reference save; it does not claim fresh beta.4
performance or Java capacity acceptance. Benchmark tools and detailed timing
results are kept local.
