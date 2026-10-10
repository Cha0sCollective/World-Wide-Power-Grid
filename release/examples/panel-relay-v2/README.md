# Panel and circuit-board example, revision 2

This revised save uses the published **WWPG 0.1.0-beta.2 jar** with the same pinned dependencies. It is an additional world download, with its own save folder and world-list name. The earlier jar, worlds, bundles, checksums, and acceptance records remain unchanged.

The CEE panel's **Factory enable** switch powers a **PG relay and capacitor board**. The board's native relay contacts route a **300 V PG supply** to two **CEE lamps**. The green RUN lamp means enabled; the red OFF lamp means disabled. Opening the panel switch leaves RUN lit for roughly three seconds while the capacitor discharges, then transfers power to OFF. Closing it restores RUN after a short charging delay. Signs and goggles in the spawn chest make the controls and readings accessible.

See [installation and test instructions](../../../docs/INSTALL.md#example-world). The source fixture checks actual native interactions, relay state, lamp light levels, capacitor delay, panel readings, and saved capacitor charge before recharge, then leaves the exported save enabled.

[verification.json](verification.json) records local native/Java creation/restart and reopening the exported ZIP, equation checks, CI, and artifact hashes. The test harness has updated GameTest classes; every other jar entry is compared byte for byte with the published beta.2 runtime. [ci.json](ci.json) retains the four Windows/Linux backend jobs, including the full 118-check regression suite and upstream references.

This example revision does not resolve the earlier intermittent reload/restart assertions. Their records remain in [current status](../../../docs/STATUS.md).
