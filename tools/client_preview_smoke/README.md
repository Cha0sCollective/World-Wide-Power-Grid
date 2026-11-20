# Focused CEE preview loading check

This helper starts an isolated client, waits for the title screen, forces CEE's
wire-preview class through the real mixin loader, checks that both WWPG preview
handlers applied, invokes its no-world `tick()` path, then exits. It never opens
a save. It is separate from full graphical acceptance and does not validate
visible terminal geometry or moving sublevels.

Build the candidate first, then run:

```powershell
.\gradlew.bat -I tools/client_preview_smoke/smoke.gradle runPreviewMixinSmoke `
  '-PpreviewArtifact=<frozen candidate jar>' `
  '-PpreviewDirectory=<new isolated directory>' `
  '-PceeRuntimeJar=<CEE 1.2 runtime jar>'
```

Use a fresh directory for each observation. Optional compatibility mods can be
copied into that directory's `mods/` folder before launching. Retain its logs,
mod versions and input hashes. Success requires
`WWPG_PREVIEW_MIXIN_SMOKE_PASSED`; an absent marker or failure marker rejects the
check. The helper jar is separate from the player jar.
