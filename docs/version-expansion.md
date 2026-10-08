# Version setup and development

Each Minecraft target is an independent Gradle project. Choose matching loader and equipment dependencies when building or playing.

| Minecraft | Project | Loader | Minecraft Java | Build Java | Optional equipment mod |
|---|---|---|---|---|---|
| 1.7.10 | `forge-1.7.10/inmis` | Forge 10.13.4.1614 | 8 | JDK 17 with an installed JDK 8 toolchain | Baubles 1.0.1.10 |
| 1.12.2 | `forge-1.12.2/inmis` | Forge 14.23.5.2864 | 8 | JDK 8 | Baubles 1.5.2 |
| 1.16.5 | `forge-1.16.5/inmis` | Forge 36.2.42 | 8 | JDK 17 with an installed JDK 8 toolchain | Curios 1.16.5-4.1.0.1 |
| 1.18.2 | `forge-1.18.2/inmis` | Forge 40.2.0 | 17 | JDK 17 | Curios 1.18.2-5.0.9.2 |
| 1.19.2 | `forge-1.19.2/inmis` | Forge 43.2.0 | 17 | JDK 17 | Curios 1.19.2-5.1.4.2 |
| 1.20.1 | `forge-1.20.1/inmis` | Forge 47.2.0 | 17 | JDK 17 | Curios 5.14.1+1.20.1 |
| 1.21.1 | `neoforge-1.21.1/inmis` | NeoForge 21.1.217 | 21 | JDK 21 | Curios 9.5.1+1.21.1; Accessories 1.1.0-beta.52+1.21.1 |
| 26.1.2 | `neoforge-26.1.2/inmis` | NeoForge 26.1.2.114 | 25 | JDK 25 | Curios 15.0.0+26.1.2 |

## Installation and configuration

Choose the Inmis file for your Minecraft version and loader from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/inmis-forge-port). Install the same Inmis version on the client and server. Curios and Baubles are optional; install the equipment mod matching your port to use its equipment slot.

Accessories integration is available on the existing NeoForge 1.21.1 project. The 26.1.2 port supports Curios and does not include Accessories integration.

Edit `config/inmis.json` in the game or server directory and restart Minecraft or the server to apply changes. Custom backpack names and tier order must match between client and server. Storage dimensions can differ; the server supplies the dimensions when a backpack opens.

Relevant settings include:

- `enableTrinketCompatibility`: enables or disables the port's optional equipment integration.
- `trinketRendering`: controls rendering of backpacks in optional equipment slots.
- `allowBackpacksInChestplate`: permits backpacks in the chest armor slot.
- `requireArmorTrinketToOpen`: requires an equipped backpack to open it.
- `importBackpackedItems`: enables Backpacked migration on supported targets and defaults to `false`. Forge 1.7.10 does not provide migration. Forge 1.12.2 includes an importer for supported legacy NBT formats; compatibility with a specific Backpacked release requires separate testing.

The ports retain the 2.9.4 storage recovery behavior: saved items outside a reduced capacity remain removable, and recovery slots reject new items. Individual backpack augments can be configured through the upgrades panel.

Forge 1.7.10 and 1.12.2 use crafting materials available in their respective versions. Their Quiverlink augment supports bows. Forge 1.7.10 provides eight augments and omits Immortal and Reforge because vanilla Totems and Mending do not exist in that version. Passing standalone Forge checks does not establish compatibility with every modpack or a pack's modified loader and equipment APIs.

## Building

Use the Gradle wrapper included in each project; a separate Gradle installation is unnecessary. Select JDK 17 as the Gradle launcher for Forge 1.16.5 and make an installed JDK 8 available as its Java toolchain. Select JDK 25 for NeoForge 26.1.2.

Forge 1.7.10 uses RetroFuturaGradle with JDK 17 as the Gradle launcher and JDK 8 for compilation and Minecraft. Forge 1.12.2 uses ForgeGradle 3 and launches both Gradle and Minecraft with JDK 8. No additional mixin or coremod dependency is required to install either Inmis jar.

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

Equipment compile dependencies are resolved by each project's build. NeoForge uses ModDevGradle with JDK 21 or 25. Forge uses ForgeGradle, or RetroFuturaGradle on 1.7.10, and targets the Minecraft Java version listed above.

## Regression tests

NeoForge's `build` task runs headless JUnit regressions. Gameplay tests run separately in a native Minecraft server. Forge 1.16.5 predates GameTest, so its gameplay checks use an opt-in test mod in a separate source set.

From the repository root in Windows PowerShell:

```powershell
.\scripts\validate-feedback.ps1 -Versions forge-1.16.5,neoforge-26.1.2
.\scripts\validate-feedback.ps1 -Versions forge-1.7.10,forge-1.12.2
.\scripts\verify-legacy-runtime.ps1
.\scripts\verify-forge16-runtime.ps1
.\scripts\verify-runtime.ps1 -Versions neoforge-26.1.2 -NeoProfiles none,curios -ClientProfiles none,curios
```

The build validator checks loader and Minecraft metadata, Java bytecode, resource packaging and exclusion of test code. The native runners test baseline and optional equipment profiles: Baubles on 1.7.10 and 1.12.2; Curios on 1.16.5, 1.18.2, 1.19.2, 1.20.1 and both NeoForge targets; Accessories and the combined equipment profile on NeoForge 1.21.1. Their client phases exercise real menu packets, differing client/server storage dimensions, active-backpack locking, extraction, reopening, saved-world restart, the upgrades panel and equipped backpack rendering.

To run the server regressions without client phases:

```powershell
.\scripts\verify-forge16-runtime.ps1 -SkipClient
.\scripts\verify-legacy-runtime.ps1 -SkipClient
.\scripts\verify-runtime.ps1 -Versions neoforge-26.1.2 -SkipClient -NeoProfiles none,curios
```

Client phases require a working graphical desktop. Run the client matrices sequentially because their isolated servers share the default loopback port. The Forge runner accepts `-Port` to select a different port. Each runner prints its report location and returns a nonzero exit code if a required check fails.

Test mods and fixtures are excluded from playable jars. The runners use generated test worlds under each project's `build/` directory and do not use existing game instances or saves.

## Reporting a problem

Include the Minecraft version, loader version, Inmis version, optional equipment mods and a clear reproduction sequence. For storage or menu issues, include the relevant client and server backpack settings. Attach the relevant crash report or log when reporting a crash.

Per-target release notes are available in [the release directory](releases/).
