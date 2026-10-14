# Tutorial Adjustable Load

One independently named add-on built against **WWPG API 1.0.0**, Java 21 and NeoForge for Minecraft 1.21.1. Its own version is 0.1.0 and its mod ID is `tutorial_load`. It runs beside the existing examples.

Follow the [complete author tutorial](../../docs/CREATE_ADDON.md). It explains every registration, model, resource and saved-setting file. This starter is included in the WWPG 0.2.2-beta.2 developer SDK; its own mod version and extension API 1.0.0 remain unchanged.

Download the [WWPG 0.2.2-beta.2 developer SDK](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.2-beta.2/wwpg-0.2.2-beta.2-developer-sdk.zip). Extract the SDK's Maven ZIP into a repository containing `org/cha0scollective/wwpg`. From this project root:

```sh
chmod +x gradlew
./gradlew build "-PapiRepository=/absolute/path/to/wwpg-api-maven" --no-daemon
```

```powershell
.\gradlew.bat build "-PapiRepository=C:/Dev/WWPG API Maven" --no-daemon
```

Output: `build/libs/tutorial-load-0.1.0.jar`. Install it into a separate instance with WWPG 0.2.2-beta.2 and the [tested dependencies](../../docs/INSTALL.md). Install the general example for its 20 V source. No project-managed game runs or WWPG implementation dependency are provided. API/source/Javadoc jars belong only in the build environment.

At 20 V, connect terminal 0 to the source's 0 and terminal 1 to its 1 using either native wire system. The enabled 100 Ω load draws about 0.2 A and 4 W and lights. Empty-hand front/side right-click reads without changing settings; top-face right-click toggles enable; sneak-right-click cycles 100, 200 and 400 Ω. Held wire tools and meters keep their native behavior. OFF retains terminal identities and opens a switch; unavailable results clear the lamp/readout without resetting settings. Save OFF / 400 Ω and verify those settings after restart.

`TutorialLoadMod` is NeoForge registration; `LoadBlock` is interaction/render state; `LoadDevice` is the WWPG capability plus native saving. Everything under `assets/tutorial_load` is an ordinary Minecraft resource. See [validation](../../docs/API_LIMITS.md), [debugging](../../docs/ADDON_WORKFLOW.md) and [testing](../../docs/ADDON_TESTING.md).

Run `./gradlew build sdkProvenance "-PapiRepository=/absolute/path/to/wwpg-api-maven" --no-daemon` (Windows: `.\gradlew.bat`) to capture provenance in `build/sdk-provenance.json`. The `sdkProvenance` Gradle task records the actual resolved API hash and output jar hash. It must resolve only `org.cha0scollective.wwpg:wwpg-api:1.0.0`, with no project dependencies. Gameplay acceptance lives in WWPG's documentation test harness, separately from this independently compilable project. Dedicated-server checks pass on Windows/Linux and both backends. Earlier documentation work also checked Linux software-rendered clients on both backends through Dev Suite; that historical result does not certify beta.2 graphical coverage on Linux or Steam Deck hardware. The [beta.2 verification archive](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.2-beta.2/wwpg-0.2.2-beta.2-verification.zip) identifies this SDK's exact source, compiled starter and acceptance records.
