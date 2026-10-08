# Forge 1.16.5 and NeoForge 26.1.2

These ports provide Inmis backpacks, configurable storage, backpack augments and optional Curios integration for two additional Minecraft versions. Each target is an independent Gradle project.

| Minecraft | Project | Loader | Minecraft Java | Build Java | Optional Curios version |
|---|---|---|---|---|---|
| 1.16.5 | `forge-1.16.5/inmis` | Forge 36.2.42 | 8 | JDK 17 with an installed JDK 8 toolchain | 1.16.5-4.1.0.1 |
| 26.1.2 | `neoforge-26.1.2/inmis` | NeoForge 26.1.2.114 | 25 | JDK 25 | 15.0.0+26.1.2 |

## Installation and configuration

Choose the Inmis file for your Minecraft version and loader from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/inmis-forge-port). Install the same Inmis version on the client and server. Curios is optional; install its matching version to equip backpacks in its back slot.

Accessories integration is available on the existing NeoForge 1.21.1 project. The 26.1.2 port supports Curios and does not include Accessories integration.

Edit `config/inmis.json` in the game or server directory and restart Minecraft or the server to apply changes. Custom backpack names and tier order must match between client and server. Storage dimensions can differ; the server supplies the dimensions when a backpack opens.

Relevant settings include:

- `enableTrinketCompatibility`: enables or disables Curios equipment integration.
- `trinketRendering`: controls rendering of equipped Curios backpacks.
- `allowBackpacksInChestplate`: permits backpacks in the chest armor slot.
- `requireArmorTrinketToOpen`: requires an equipped backpack to open it.
- `importBackpackedItems`: enables Backpacked migration and blocks its backpack and shelf recipes. It defaults to `false`.

The new targets retain the existing 2.9.4 storage recovery behavior: saved items outside a reduced capacity remain removable, and recovery slots reject new items. Individual backpack augments can be configured through the upgrades panel.

## Building

Use the Gradle wrapper included in each project; a separate Gradle installation is unnecessary. Select JDK 17 as the Gradle launcher for Forge 1.16.5 and make an installed JDK 8 available as its Java toolchain. Select JDK 25 for NeoForge 26.1.2.

From the repository root:

```sh
cd forge-1.16.5/inmis
./gradlew build
```

Or build the NeoForge target:

```sh
cd neoforge-26.1.2/inmis
./gradlew build
```

On Windows, use `.\gradlew.bat build` from the corresponding project directory. Playable jars are written to `build/libs/`; files ending in `-sources.jar` contain source code. The Forge build also runs `reobfJar` to prepare the jar for the installed loader.

Curios compile dependencies are resolved from Maven. NeoForge uses ModDevGradle and Java 25; Forge uses ForgeGradle and Java 8 bytecode.

## Regression tests

NeoForge's `build` task runs headless JUnit regressions. Gameplay tests run separately in a native Minecraft server. Forge 1.16.5 predates GameTest, so its gameplay checks use an opt-in test mod in a separate source set.

From the repository root in Windows PowerShell:

```powershell
.\scripts\validate-feedback.ps1 -Versions forge-1.16.5,neoforge-26.1.2
.\scripts\verify-forge16-runtime.ps1
.\scripts\verify-runtime.ps1 -Versions neoforge-26.1.2 -NeoProfiles none,curios -ClientProfiles none,curios
```

The build validator checks loader and Minecraft metadata, Java bytecode, resource packaging and exclusion of test code. The native runners test both the baseline and Curios profiles. Their client phases exercise real menu packets, differing client/server storage dimensions, active-backpack locking, extraction, reopening, saved-world restart, the upgrades panel and equipped backpack rendering.

To run the server regressions without client phases:

```powershell
.\scripts\verify-forge16-runtime.ps1 -SkipClient
.\scripts\verify-runtime.ps1 -Versions neoforge-26.1.2 -SkipClient -NeoProfiles none,curios
```

Client phases require a working graphical desktop. Run the client matrices sequentially because their isolated servers share the default loopback port. The Forge runner accepts `-Port` to select a different port. Each runner prints its report location and returns a nonzero exit code if a required check fails.

Test mods and fixtures are excluded from playable jars. The runners use generated test worlds under each project's `build/` directory and do not use existing game instances or saves.

## Reporting a problem

Include the Minecraft version, loader version, Inmis version, optional equipment mods and a clear reproduction sequence. For storage or menu issues, include the relevant client and server backpack settings. Attach the relevant crash report or log when reporting a crash.

Per-target release notes are available for [Forge 1.16.5](releases/2.9.4-1.16.5.md) and [NeoForge 26.1.2](releases/2.9.4-26.1.2.md).
