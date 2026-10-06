# Feedback audit and runtime validation

Reviewed on 5 October 2026 against repository base `21faaf9`, with Minecraft runtime verification on 6 October after the machine was upgraded to 16 GB RAM. The supplied comments are reports from unspecified released builds and modpacks. A report is evidence of a symptom, not proof of its cause. Disposable GameTest worlds and a native Minecraft client connected to a loopback dedicated server are used below; existing Prism instances and saves are untouched.

## Findings and changes

| Feedback or risk | Code evidence | 2.9.4 change / remaining uncertainty |
| --- | --- | --- |
| Funnelling collected another backpack | All four branches used `SimpleContainer.addItem`, which does not call the overridden insertion predicate. | Automatic insertion enforces the same storage rules as the menu, including rejecting backpacks, prohibited shulkers and stackable items when configured. |
| A collected backpack could not be removed | Forge menus rejected pickup of every backpack item, including backpack contents. | Only the active backpack's player slot is locked. Old nested backpacks remain extractable. Hotbar/offhand swaps also respect the active slot. |
| Client crashes with different slot config | The opening packet previously sent only a stack; both sides separately derived menu slots from their local config. | The server now sends width, effective rows, configured capacity and active slot. The client constructs a display inventory without resizing saved data. Custom registered item names must still agree across client/server. |
| Backpack rollback / resources disappearing | NeoForge components shared mutable stack references, enabled encoded-data caching, and truncated contents when configured capacity decreased. Menus and augments also used separate inventory snapshots. | Components copy stacks at their boundaries and compare contents by value; contents encoding cache is disabled. Augments reuse the open inventory. Capacity reductions retain occupied overflow in extraction-only recovery slots, including through crafting upgrades. These defects do not establish the cause of a freeze or a specific 30-minute rollback. |
| NeoForge mod item blacklist | Automatic insertion bypassed all rules. Separately, its predicate returned early for ordinary block items while shulkers were disabled, before checking their ID. | Blacklist checks apply to ordinary block items too. The setting governs **backpack storage**, not equipment slots belonging to other mods. The report that a Supplementaries sack could still be equipped is insufficient to establish a storage failure. Forge configs currently do not expose this setting. |
| Immortal fails with occupied offhand | All four mixins consulted backpack totems only when the offhand was empty. | Backpack totems may be used with a shield/tool in the offhand. Held totems keep priority; vanilla bypass-damage and loader cancellation handling still apply. Use a tier that unlocks Immortal (Withered or Endless in the default order), with the augment enabled. |
| Registry already frozen at load | NeoForge 1.21.1 and Forge 1.20.1 eagerly constructed backpack items in the mod constructor, before deferred registration. Older Forge branches already constructed them in suppliers. | Item construction now happens inside the registration supplier. A full stack trace is still needed to attribute the historical report. Sinytra Connector is not declared as an Inmis dependency; the feedback does not establish that it is required. |
| Backpack no longer opens | Report gives no version, location, config or error. | Opening uses server dimensions and checks continued ownership. Oversized saved contents produce a readable error instead of being discarded. Keybind conflicts, equipment configuration and third-party compatibility still need reproduction. |
| Request for a 3D addon | Existing equipment renderers draw the item models; shipped backpack models use `item/generated`. | Treat this as a feature request. No new 3D addon or asset set is included in this correction. |
| Additional review: client equality | The old client-only `ItemStackMixin` treated any two backpacks as equal while retaining identity hashes. | Removed the override across all branches. Active backpack protection uses explicit inventory indexes and identity, not global stack equality. |
| Additional review: malformed config | Existing malformed JSON silently fell back to defaults and overwrote the file, potentially changing registered custom items. | Existing invalid files are preserved and loading stops with an actionable error. Names must be valid/unique after case normalization and dimensions must be positive and fit the menu's signed-short slot indexes. |
| Additional review: compatibility toggle | Skipping Curios/Accessories callbacks left default slot admission active, including Forge's item tags. | Callbacks register when the optional mod is present, and explicitly reject equip when compatibility is disabled. Existing equipped items remain removable. NeoForge's Curios tag uses the correct singular `tags/item` path. |
| Additional review: migration / malformed slots | Backpacked imports removed the original `Items` list after dropping out-of-capacity or undecodable entries. Forge also tried to load empty entries into invalid slots. | Imports preserve overflow and retain source data on unsupported formats, duplicate occupied slots or decode failures. Forge skips empty entries and refuses invalid occupied slots without partial saves. Legacy source retained after a failed NeoForge import may still need manual recovery because the fallback component can become initialized; do not remove that source blindly. |
| Runtime discovery: vanilla death spilling | Real death-drop tests failed because vanilla clears its inventory before `LivingDropsEvent`. The old handler inspected empty lists. | All four branches now spill immediately before `Inventory.dropAll`, after vanilla's keep-inventory and Vanishing Curse handling. Contents join the captured death drops; vanilla drops each exact, empty backpack once. Regression tests check retention, category flags and cancellation. |
| Additional review: optional equipment death spilling | Curios cleared dropped slots before the old Inmis handler read them; Accessories depended on listener order. Reading retained slots and removing equal drops could alter a retained or unrelated identical backpack. | Release 2.9.4 transforms exact native Curios drop entities and Accessories selected drop stacks after native retention/destruction decisions. It no longer reads or clears retained equipment or removes drops by stack equality. Expanded verification is recorded separately below. |

The registration and component changes follow the primary [NeoForge item documentation](https://docs.neoforged.net/docs/1.21.1/items/) and [data-component immutability requirements](https://docs.neoforged.net/docs/1.21.1/items/datacomponents/). The [NeoForge loading implementation](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/internal/CommonModLoader.java) initializes mods before unfreezing registries and firing registration events. Neither a successful compile nor a unit test proves modpack compatibility.

## Lightweight build and regression checks

From the repository root in Windows PowerShell:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-feedback.ps1
```

The script selects JDK 21 for NeoForge and JDK 17 for Forge, uses one Gradle worker and a 1 GB Gradle heap, runs projects sequentially, and stages playable JARs and SHA-256 hashes under a unique `temp/feedback-validation/<run-id>/` directory. Each run retains its build logs, extracted mod metadata, available test reports, `artifacts.json` and `provenance.json`, including source revision/worktree state, JDK, loader, dependencies and completion. The root `temp/feedback-validation/artifacts.json` indexes the latest successful artifacts; `latest.json` identifies the current run and completion state. Existing run directories are retained. The script leaves installed Prism instances alone and launches no Minecraft game. Initial Minecraft decompilation and compilation can still have a higher transient memory cost than the Gradle heap limit.

To validate only one branch, or supply another JDK location:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-feedback.ps1 -Versions neoforge-1.21.1 -Java21Home 'C:\path\to\jdk-21'
```

NeoForge `build` includes headless JUnit regression tests through [ModDevGradle's JUnit integration](https://github.com/neoforged/ModDevGradle#unit-testing-with-junit), with a single 512 MB test fork. Forge builds check compilation, resources, packaging and reobfuscation. Runtime GameTests are separate from `build` and use test-only source sets; they are not packaged in the player JAR. Archived build logs use `temp/feedback-validation/<run-id>/<branch>-build.log`; playable JARs use `artifacts/<branch>/`, metadata uses `metadata/<branch>/`, and available test reports/results use `reports/<branch>/tests` and `reports/<branch>/test-results` within that run. The current NeoForge HTML report also remains in `neoforge-1.21.1/inmis/build/reports/tests/test/`.

Release 2.9.4 covers all four supported branches. The menu protocol is now version 2: update both client and server together; old/new mod combinations should fail their protocol check. Registered item identities and tier order should still agree for accurate registry synchronization and augment UI. Storage dimensions may differ.

The source commit is `b4f86fe1acd0f4d2d1916f1ec224ae3ee27f9285`, pushed with tag `v2.9.4`. Final clean-source builds passed in run `20261006-194519-a5b8cfd3`; provenance is `temp/feedback-validation/20261006-194519-a5b8cfd3/provenance.json`. All four artifacts have been uploaded to CurseForge; persisted metadata/changelogs were verified and their public CDN downloads match the staged SHA-256 hashes. At 20:04 UTC on 6 October, NeoForge 1.21.1 and Forge 1.20.1 were approved; Forge 1.19.2 and 1.18.2 were processing and will publish automatically once approved. The [release receipt](releases/2.9.4-receipt.md) records file IDs, hashes and status.

Release notes: [Forge 1.18.2](releases/2.9.4-1.18.2.md), [Forge 1.19.2](releases/2.9.4-1.19.2.md), [Forge 1.20.1](releases/2.9.4-1.20.1.md), and [NeoForge 1.21.1](releases/2.9.4-1.21.1.md).

Initial build validation was refreshed successfully on 6 October 2026 after the runtime-discovered vanilla death-spill repair:

| Project | Loader used for build | Java | Result |
| --- | --- | --- | --- |
| NeoForge 1.21.1 | 21.1.217 | 21 | Build passed; 27 JUnit tests passed, none skipped |
| Forge 1.20.1 | 47.2.0 | 17 | Build and reobfuscation passed |
| Forge 1.19.2 | 43.2.0 | 17 | Build and reobfuscation passed |
| Forge 1.18.2 | 40.2.0 | 17 | Build and reobfuscation passed |

At that stage all 201 production resource JSON files parsed successfully. Packaged JARs were checked for removal of the obsolete stack-equality mixin, NeoForge's corrected Curios tag, absence of test fixtures, and matching staged SHA-256 hashes. Gradle and Java deprecation notices remain in the logs.

## Initial Minecraft runtime verification, 6 October 2026

| Runtime | Optional mods | Required GameTests |
| --- | --- | --- |
| Forge 1.18.2 / 40.2.0 | Curios 5.0.9.2 | 11 passed |
| Forge 1.19.2 / 43.2.0 | Curios 5.1.4.2 | 11 passed |
| Forge 1.20.1 / 47.2.0 | Curios 5.14.1 | 11 passed |
| NeoForge 1.21.1 / 21.1.217 | None | 22 passed |
| NeoForge 1.21.1 / 21.1.217 | Curios 9.5.1+1.21.1 | 22 passed |
| NeoForge 1.21.1 / 21.1.217 | Accessories 1.1.0-beta.52+1.21.1, owo 0.12.15.1-beta.6+1.21 | 22 passed |
| NeoForge 1.21.1 / 21.1.217 | Curios and Accessories/owo above | 22 passed |

These are 121 successful test executions across the initial matrix, with shared cases repeated in each NeoForge profile. Tests exercise real world item pickup, lethal player damage and Immortal redirects, loaded crafting recipes, open-menu augment updates, storage restrictions, nested-item extraction, active-slot protection, overflow recovery and vanilla captured death drops. At this stage NeoForge's optional-mod tests called native equipment admission APIs with compatibility enabled/disabled and allowed removal of legacy filled backpacks. They did not test death spilling from optional equipment slots.

Forge fixtures invoke vanilla `dropAllDeathLoot` from a test player; NeoForge death fixtures use vanilla `Player.die` with the drop-insertion behavior of `ServerPlayer`. They exercise production mixins and loader events, but do not cover the full server-player respawn/scoreboard lifecycle. Client/server smoke checks use an actual connected `ServerPlayer` and actual menu packets.

Before the repair, Forge's death-spill regression and four controlled NeoForge death-spill cases failed. Disabled spilling, `keepInventory` and Vanishing Curse controls established that the NeoForge fixture was valid. After the repair all death-spill and cancellation cases passed. ForgeGradle 5 inherited a `forceExit=true` setting that terminated its own daemon after successful GameTests; the two older branches override that setting so successful runs return normally. ForgeGradle 6 has no such setter and needs no override.

The initial native NeoForge client/server smoke run passed both phases: a client with default Withered dimensions 11x6 opened a server 9x6 menu, transferred named armor, protected the active backpack against a number-key swap, closed/reopened, and shut down cleanly. After a full server/client restart and server capacity reduction to 9x3, all 71 diamonds and the armor's custom name/damage survived. Overflow was extractable and rejected new insertion; after extraction the reopened menu used the smaller configured capacity. Logs and screenshots are under `temp/runtime-validation/native-client/none/`.

The completed automated smoke matrix for that validation stage also passed all eight phases with clean client/server exits and zero failed checks:

| NeoForge profile | Initial write / menu transfers | Restart / overflow extraction |
| --- | --- | --- |
| None | Passed | Passed |
| Curios | Passed | Passed |
| Accessories + owo | Passed | Passed |
| Curios + Accessories + owo | Passed | Passed |

Every profile retained the named chestplate's damage value 23 and Protection III enchantment after restart, conserved exactly one chestplate and 71 diamonds, and rendered the server's geometry despite the differing local client config. Screenshots were inspected. The authoritative completed report is `temp/runtime-validation/20261006-170226-e6e88433/summary.json`, with configuration snapshots, logs, screenshots and actual saved player-data files beside it. `temp/runtime-validation/verification-results.json` also indexes the seven successful GameTest runs and staged JARs. Earlier runner-only attempts remain archived: Windows process exit-code and shared-log-read issues prevented client verification, and were fixed before the successful matrix. Audio initialization fails on this machine's unavailable OpenAL device; Minecraft continues with sound disabled, and the menu/game tests pass.

A focused follow-up strengthened Accessories coverage beyond constructed slot references: native player entity binding, a usable back container, actual backpack/pouch equipment admission, filled legacy storage and disabled admission all passed in both Accessories profiles (22 required tests each). Report: `temp/runtime-validation/20261006-171200-fa3f9dc8/summary.json`. The pinned Accessories dependency already supplies the player back binding, so no speculative extra mapping was added. Its early client initialization warning does not prove a missing server slot; the actual container tests establish that the server binding exists. The datapack binding format is described in the [official Accessories documentation](https://docs.wispforest.io/legacy/accessories/general/binding_slots_to_entities/).

## Release 2.9.4 equipment-death verification, 6 October 2026

Release 2.9.4 uses native `CurioDropsEvent` at lowest priority and Accessories' `OnDeathCallback` after the default callback phase. Only backpacks already selected for dropping are emptied; native retained/destroyed equipment and cancelled drops keep their native behavior. Forge 1.18.2 and 1.19.2 also register the previously missing Curios back slot. The expanded GameTest suite checks native equipment admission, retention/destruction rules, Vanishing Curse, both keep-inventory gamerules, disabled spilling/compatibility, explicit native drop rules, identical unrelated backpacks, Accessories cosmetic slots and cancellation.

All expanded GameTest runs passed after correcting an assertion API mismatch in the older Forge test fixtures:

| Runtime | Optional mods | Required GameTests |
| --- | --- | --- |
| Forge 1.18.2 / 40.2.0 | Curios 5.0.9.2 | 17 passed |
| Forge 1.19.2 / 43.2.0 | Curios 5.1.4.2 | 17 passed |
| Forge 1.20.1 / 47.2.0 | Curios 5.14.1 | 17 passed |
| NeoForge 1.21.1 / 21.1.217 | None | 35 passed |
| NeoForge 1.21.1 / 21.1.217 | Curios 9.5.1+1.21.1 | 35 passed |
| NeoForge 1.21.1 / 21.1.217 | Accessories 1.1.0-beta.52+1.21.1, owo 0.12.15.1-beta.6+1.21 | 35 passed |
| NeoForge 1.21.1 / 21.1.217 | Curios and Accessories/owo above | 35 passed |

These are 191 successful required GameTest executions across the expanded matrix, with shared cases repeated in each NeoForge profile. Six Curios tests and seven Accessories tests exercise native optional equipment death handling when their respective mod is loaded; absent-mod profiles finish those adapters without linking the missing API. The preceding 121-execution table remains the evidence for the earlier validation stage.

Forge 1.20.1 and all four NeoForge profiles passed in `temp/runtime-validation/20261006-193132-f3b5bb68/summary.json`. That unmodified report also retains the two initial older-Forge fixture compilation failures. The corrected Forge 1.18.2 and 1.19.2 reruns each passed 17 tests and exited successfully; their logs are `temp/runtime-validation/forge-1.18.2-release-rerun.log` and `temp/runtime-validation/forge-1.19.2-release-rerun.log`. The fixture failures occurred before game startup and are separate from the successful reruns.

All eight expanded native client/server phases passed with clean exits: write/menu-transfer and restart/read checks with no optional mods, Curios, Accessories plus owo, and both integrations. Each profile used a client Withered size of 11x6 against server sizes of 9x6 before restart and 9x3 afterward. The checks preserved exactly 71 diamonds and one named chestplate, including damage 23 and Protection III, protected the active backpack, allowed overflow extraction and then reopened at the reduced capacity.

The completed release verification index is `temp/runtime-validation/20261006-release-2.9.4-verification.json`. It consolidates the 191 successful GameTest executions, all eight successful client phases, both corrected older-Forge reruns and the unchanged original report. The final release also has 200 validated production JSON resources after removal of the obsolete NeoForge Minecraft plural dyeable tag file. Logs, configuration snapshots, screenshots and saved player-data evidence remain in the referenced runtime directories.

To rerun the automated runtime matrix in Windows PowerShell:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-runtime.ps1
```

For client restart checks with every NeoForge compatibility combination, run from a PowerShell session:

```powershell
& .\scripts\verify-runtime.ps1 -Versions neoforge-1.21.1 -ClientProfiles none,curios,accessories,both
```

The runner exports Gradle's complete launch settings, stops the build JVM, then starts an isolated native client and dedicated server with 2 GB heaps each. Each profile uses a new disposable world, loopback port 25576 and separate archived reports. It writes a named, damaged, Protection III chestplate and checks its metadata after restart. `-SkipClient` runs only GameTests; `-SkipGameTests` runs only client smoke checks. A JSON summary, logs, screenshots and player-data evidence are archived beneath a unique `temp/runtime-validation/<run-id>/` directory. No Sinytra Connector is part of any profile.

Remaining limits: the historical freeze/30-minute rollback has not been reproduced, and short clean-restart checks do not establish crash durability or long-session stability. Forge client GUI runs, optional equipment rendering/opening, migration against real third-party saves and Supplementaries-specific storage still need the manual matrix. Native equipment-death retention, destruction, drop selection, Accessories cosmetic slots and cancellation are covered by the expanded GameTests above.

## Additional manual / modpack coverage

Create disposable Prism instances for Minecraft 1.18.2, 1.19.2, 1.20.1 and 1.21.1 with their corresponding loader and JDK. Use a new world or a copy of a save. Install exactly one staged Inmis JAR in each instance. Test NeoForge without accessory mods first, then with Curios alone, Accessories plus its declared native dependencies alone, and both together. On Forge, repeat with Curios absent/present. Record exact loader/mod versions for every run; do not add Connector solely on the basis of the comment.

| Scenario | Procedure | Expected result |
| --- | --- | --- |
| Startup and server loading | Start clean client and dedicated server with each compatibility combination above. | No frozen registry, missing optional class, or mixin failure; menu registration succeeds. |
| Funnelling restrictions | With an enabled Funnelling backpack equipped/carried, drop an equal-tier backpack, a lower-tier backpack, a prohibited shulker and ordinary allowed items. Repeat with `unstackablesOnly`. | Restricted stacks follow normal pickup rules and never enter the backpack. Allowed stacks conserve their total counts, including partially full inventories. |
| Nested-item recovery | In a copied save containing a previously nested backpack, extract it by click and shift-click. | Item, contents and augment settings survive; inserting it again is rejected. |
| Blacklist | NeoForge: blacklist a normal vanilla block item first, then `supplementaries:sack` when Supplementaries is present. Test click, shift-click, Funnelling and hopper insertion. | Storage rejects the listed IDs through every insertion route. Equipping the sack is a separate equipment rule and is not the assertion. |
| Immortal | Withered/Endless, enabled Immortal, backpack totem; hold sword plus shield, then take ordinary lethal damage. Repeat with a held totem, disabled augment and vanilla bypass damage. | One backpack totem is consumed and resurrection succeeds with shield. Held totems win; disabled augment and bypass damage follow vanilla rules. Reopen/rejoin to verify consumed count. |
| Different dimensions | Dedicated server: configure a tier to 9x3 while client retains defaults; then test a differing width too. Open by right-click and B key. | Client displays server slots without crash or desync. Counts and stack metadata agree after transfer, close, reconnect and server restart. |
| Capacity reduction | Fill late slots with diamonds and named/enchanted items; save/stop; reduce rows and width; restart. Open, extract overflow, trigger augments, then craft an upgrade. | Saved contents remain accessible. Recovery slots reject new insertion. Upgrade, reopen and restart conserve all remaining stacks and their metadata. |
| Menu plus augments | Keep inventory open while Funnelling adds items, Quiverlink consumes ammunition and Immortal consumes a totem. Move another stack immediately afterward. | GUI reflects the changes; later clicks/close do not restore consumed items or erase newly collected items. |
| Active backpack protection | Open a backpack in each hotbar slot, main inventory, offhand, armor and supported accessory slot. Try number-key swap, F swap, shift-click, drag, drop and double-click collection. Move unrelated backpacks. | Active backpack stays protected; unrelated backpacks are movable; losing ownership through another mod closes the menu safely. |
| Configuration controls | Toggle `requireArmorTrinketToOpen`, `allowBackpacksInChestplate`, `enableTrinketCompatibility`, `requireEmptyForUnequip` and render setting independently. | Existing gameplay choices are respected. Verify open behavior with conflicting keybinds removed. |
| Persistence / freeze report | Place distinctive contents in backpack; `/save-all flush`; reopen, disconnect/reconnect, stop/start server and repeat during augment activity. If a freeze recurs, retain logs before restarting. | Counts and names persist across successful saves. Any rollback investigation compares backpack contents with ordinary inventory/world state to distinguish a save rollback from backpack-only loss. |
| Invalid config | In a disposable instance, try truncated JSON, null backpacks, duplicate names and zero/negative dimensions. | Clear startup error, with original config bytes retained; correcting the file restores loading. |
| Legacy migration | Enable Backpacked import in a copied save with occupied slots beyond the target tier; repeat with an unavailable item mod, duplicate slots and an unsupported list format. | Valid overflow survives. Failed conversion leaves the original legacy `Items` untouched for recovery; compare raw saved tags rather than relying on an empty GUI. |
| Death spilling | Enable armor/main spilling; test vanilla slots and Curios/Accessories equipment, including cosmetic and identical bags. Repeat with `keepInventory`, native keep/destroy/drop rules, Vanishing Curse and cancelled drops. | Native rules determine retention, destruction and dropping. Retained storage stays filled; only exact backpacks selected for dropping spill once. Normal drops conserve bag/content totals without altering an unrelated identical bag, and cancelled events do not leak contents into the world. |

For unresolved reports, capture exact Minecraft/loader/Inmis versions, optional-mod versions, the relevant `inmis.json`, backpack tier/location and enabled augments, reproduction steps, `logs/latest.log` and any full crash report. For rollback, add whether ordinary inventory and world blocks rolled back too, whether the process crashed or froze, and when the last completed save occurred. Redact account identifiers and server addresses before sharing logs.
