# Documentation

These guides describe **WWPG 0.2.1**, its supported stationary factories, and
**extension API 1.0.0**. Start with the page that matches what you want to do.

## Play and run a server

| I want to… | Read |
| --- | --- |
| Install or update the mod | [Installation](INSTALL.md) |
| Check NeoForge versions or try newer upstream mods | [Version compatibility](VERSION_COMPATIBILITY.md) |
| See which machines and tools work together | [Supported content](SUPPORT.md) |
| Try working circuits with instructions | [Stationary Test Yard](STATIONARY_YARD.md) |
| Learn the native wire and repair controls | [Wiring and repair](STATIONARY_WIRING.md) |
| Try the example add-on machines, panel and board | [Extension API Workshop](EXTENSION_API_WORKSHOP.md) |
| Check current limitations and reported problems | [Current status](STATUS.md) |

The Stationary Yard demonstrates the upstream mods' supported equipment. The
API Workshop is a separate world and requires the two supplied example add-ons.
Each world has a base edition and an edition requiring Pinout and CC:Tweaked.

## Develop and maintain WWPG

| I want to… | Read |
| --- | --- |
| Make an add-on | [Extension API and SDK](EXTENSION_API.md) |
| Build the independent examples | [General example](../examples/extension-addon/README.md) and [CEE/PG example](../examples/cee-addon/README.md) |
| Build WWPG or run development checks | [Development](DEVELOPMENT.md) |
| Submit a change | [Contributing](../CONTRIBUTING.md) |
| Understand API release requirements | [API release checks](API_RELEASE_CHECKS.md) and [completed API plan](EXTENSION_API_PLAN.md) |
| Package and publish the five downloads with short notes | [Release process](RELEASING.md) |
| Inspect a published release's files and test records | [Release records](../release/README.md) |
| Understand the eventual full product | [Product specification](../SPEC.md) |

The mod, public API and product specification have separate version numbers.
API 1.0.0 is a released extension contract; the full product's future 1.0 goals
include work beyond the current stationary release.

## Archived guides and investigations

- [Release history](RELEASE_HISTORY.md) explains older fixes, test failures and
  limits of what the original logs could establish.
- [First beta plan](FIRST_RELEASE.md) records the original beta.1 scope.
- [Earlier expanded fixture world](FIXTURE_WORLD.md) documents the older test
  yard for that release.
- [Changelog](../CHANGELOG.md) lists changes by version.

Old test results and development notes belong to the version named on the page.
Use the current installation, support and status guides when deciding what to
install or test today.

For add-on authors: [create an adjustable load](CREATE_ADDON.md), [build/install/debug](ADDON_WORKFLOW.md), [validation limits](API_LIMITS.md), and [testing and repair](ADDON_TESTING.md). These maintained guides clarify the published API 1.0.0 contract without changing the release snapshots.
