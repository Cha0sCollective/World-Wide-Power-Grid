# 0.2.5-rc.1 candidate preparation

This local prerelease candidate combines CEE 1.1.4 compatibility (retaining
1.1.3 support) with PG/Gauged wire-tool panel opening and recessed front-terminal
selection. It includes the previous 0.2.4 fixes. API 1.0.0, saves, native solver
binaries, equations and all 16 substeps are unchanged.

The candidate is not a published or accepted release. The current stable
release remains [0.2.4](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.4).
Its packaging record retains the actual identities of passing focused checks;
version-only artifact comparisons establish reuse without claiming new gameplay
executions. The new cover animation has not been checked in a live client.

Build the gameplay and separate fixture jars with Java 21:

```text
gradlew.bat jar gameTestJar apiJar ceeApiJar --no-daemon
```

The installable file is `build/libs/wwpg-0.2.5-rc.1.jar`. GameTests and fixtures
remain in `wwpg-0.2.5-rc.1-gametests.jar`, for testing only.

This packaging command starts no Minecraft session or acceptance campaign.
Follow [the release procedure](../../docs/RELEASING.md) before public release;
the required [headless and graphical lanes](../../docs/TESTING.md) still apply
and require their own explicit authorization. Prior releases and evidence are
preserved.
