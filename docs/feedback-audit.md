# Storage safeguards and troubleshooting

Inmis 2.9.4 improves backpack storage, menus and equipment handling across the supported ports. Update Inmis on both the client and server together. Custom backpack names and tier order must agree between them; storage dimensions may differ because the server supplies the dimensions when opening a backpack.

## Storage and recovery

- Automatic pickup follows the same insertion restrictions as the backpack menu, including restrictions on nested backpacks and shulker boxes.
- Previously nested backpacks remain removable. Only the backpack currently open is protected against moving, swapping or dropping.
- Reducing configured storage preserves occupied overflow as recovery slots. Items can be removed from these slots, but new items cannot be inserted.
- Augments share the open backpack's storage so that menu clicks cannot restore consumed items or overwrite automatic pickup.
- Invalid configuration files are preserved and produce an error. Correct the file before restarting; keep a backup when changing registered backpack names.
- Where supported, Backpacked migration preserves source data when conversion encounters unsupported or malformed contents. Back up a save before enabling migration. Forge 1.7.10 does not provide migration; the 1.12.2 importer supports legacy NBT formats rather than a verified Backpacked dependency.

The NeoForge item blacklist governs backpack storage. Whether another mod permits equipping its own item is a separate setting in that mod.

## Equipment and augments

On Minecraft 1.12.2 and newer, Immortal can use a backpack totem while the player holds a shield or tool in the offhand. Held totems keep priority. The backpack must unlock Immortal, and its augment must be enabled; vanilla damage rules still apply. Forge 1.7.10 omits Immortal and Reforge because Totems and Mending are absent from that vanilla version.

Optional equipment integration respects the equipment mod's decisions about retaining, destroying or dropping a backpack on death. Disabling integration prevents new equipment admission while allowing existing equipped backpacks to be removed. Install only the equipment mods supported by the selected Minecraft port.

## Reporting a problem

Include the Minecraft, loader and Inmis versions, optional equipment mods, backpack tier and location, enabled augments, and steps that reproduce the problem. For menu or storage problems, include the relevant client and server configuration. Attach the full crash report or relevant log for crashes, removing account identifiers and server addresses first.

For missing items, state whether ordinary inventory and world changes also reverted, whether the process crashed or froze, and whether a save completed before the problem. Successful short restart checks do not establish long-session or crash durability. A modpack report needs reproduction with its exact dependencies; passing a clean-loader test does not establish compatibility with an entire pack.

## Contributor validation

Run `scripts/validate-feedback.ps1` to build, inspect and stage production jars. It checks release metadata, bytecode, resources and exclusion of test fixtures. Generated artifacts, hashes and build reports are stored in the ignored `temp/` directory.

Gameplay checks run separately from compilation in native Minecraft servers and clients. See the [version guide](version-expansion.md) for the relevant runner and equipment profiles. Test storage restrictions, recovery after capacity changes, active backpack locking, augments during an open menu, death drops, differing client/server dimensions, and saved-world restarts. Use disposable worlds or copies of saves for further modpack testing.
