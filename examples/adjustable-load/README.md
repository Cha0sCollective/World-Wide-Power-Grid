# Tutorial Adjustable Load

One independently named add-on built against **WWPG API 1.0.0**, Java 21 and NeoForge for Minecraft 1.21.1. Its own version is 0.1.0 and its mod ID is `tutorial_load`. It runs beside the existing examples.

Follow the [complete author tutorial](../../docs/CREATE_ADDON.md) or [versioned website tutorial](https://wwpg.ch4os.live/docs/0.2.1/create-addon/). It explains every registration, model, resource and saved-setting file. This is a documentation companion added after WWPG 0.2.1; it is not claimed to be inside the original released SDK.

Extract the published SDK's Maven ZIP into a repository containing `org/cha0scollective/wwpg`. From this project root:

```sh
chmod +x gradlew
./gradlew build "-PapiRepository=/absolute/path/to/wwpg-api-maven" --no-daemon
```

```powershell
.\gradlew.bat build "-PapiRepository=C:/Dev/WWPG API Maven" --no-daemon
```

Output: `build/libs/tutorial-load-0.1.0.jar`. Install it into a separate instance with WWPG 0.2.1 and its dependencies. Install the general example for its 20 V source. No project-managed game runs or WWPG implementation dependency are provided. API/source/Javadoc jars belong only in the build environment.

At 20 V, connect terminal 0 to the source's 0 and terminal 1 to its 1 using either native wire system. The enabled 100 Ω load draws about 0.2 A and 4 W and lights. Empty-hand front/side right-click reads without changing settings; top-face right-click toggles enable; sneak-right-click cycles 100, 200 and 400 Ω. Held wire tools and meters keep their native behavior. OFF retains terminal identities and opens a switch; unavailable results clear the lamp/readout without resetting settings. Save OFF / 400 Ω and verify those settings after restart.

`TutorialLoadMod` is NeoForge registration; `LoadBlock` is interaction/render state; `LoadDevice` is the WWPG capability plus native saving. Everything under `assets/tutorial_load` is an ordinary Minecraft resource. See [validation](../../docs/API_LIMITS.md), [debugging](../../docs/ADDON_WORKFLOW.md) and [testing](../../docs/ADDON_TESTING.md).

Run `./gradlew build sdkProvenance "-PapiRepository=/absolute/path/to/wwpg-api-maven" --no-daemon` (Windows: `.\gradlew.bat`) to capture provenance in `build/sdk-provenance.json`. The `sdkProvenance` Gradle task records the actual resolved API hash and output jar hash. It must resolve only `org.cha0scollective.wwpg:wwpg-api:1.0.0`, with no project dependencies. Gameplay acceptance lives in WWPG's documentation test harness, separately from this independently compilable project. Dedicated-server checks pass on Windows/Linux and both backends. Linux software-rendered client checks also pass on both backends in Dev Suite; this does not certify Steam Deck hardware. This teaching download is not final acceptance for the next SDK release.
