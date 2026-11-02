# 0.2.5 release preparation

This version packages dev.112's panel and multimeter interactions, CEE control
protection and compatibility with CEE 1.1.3 and 1.1.4. It retains 0.2.4's earlier
fixes, API 1.0.0, saves, native binaries and all 16 electrical substeps.

The build is prepared locally; it is not an accepted or published release.
The current stable release remains
[0.2.4](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.2.4).

Packaging preserves the original dev.112 focused Native/Java and short client
execution identities. The comparison must establish that only version metadata
changes in the gameplay and fixture jars. Those records are not complete
headless or graphical release acceptance.

Build the gameplay jar and separate test companion with Java 21:

```text
gradlew.bat jar gameTestJar apiJar ceeApiJar --no-daemon
```

Install only `wwpg-0.2.5.jar` for ordinary play. The matching
`wwpg-0.2.5-gametests.jar` is test-only and belongs inside the verification
bundle when published.

Before public release, complete the independently requested
[acceptance lanes](../../docs/TESTING.md), then follow the
[five-download release procedure](../../docs/RELEASING.md). A packaging request
does not launch either full suite. Prior releases, RC.1, dev.112 and their
evidence remain unchanged.
