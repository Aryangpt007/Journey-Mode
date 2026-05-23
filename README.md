# 🎮 Journey Mode

A premium Minecraft utility mod that introduces a Terraria-style research and progression system. Permanent item unlocks, dynamic crafting recipe-tree analysis, customizable JSON thresholds, and a robust global save profile system that ensures unlocks persist across multiple single-player worlds and multiplayer servers.

***

[![Supported Versions](https://img.shields.io/badge/Minecraft-1.12.2%20%7C%201.16.5%20%7C%201.19.2%20%7C%201.20.1%20%7C%201.21.1-brightgreen.svg)]()
[![NeoForge Badge](https://img.shields.io/badge/NeoForge-✅-yellow.svg)](https://neoforged.net/)
[![Forge Badge](https://img.shields.io/badge/Forge-✅-orange.svg)](https://files.minecraftforge.net/)
[![Fabric Badge](https://img.shields.io/badge/Fabric-✅-blue.svg)](https://fabricmc.net/)
[![License](https://img.shields.io/github/license/Aryangpt007/Journey-Mode)](LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/Aryangpt007/Journey-Mode)](https://github.com/Aryangpt007/Journey-Mode)

***

Available for:
*   **Minecraft 1.21.1** (Fabric, NeoForge)
*   **Minecraft 1.20.1** (Forge, Fabric)
*   **Minecraft 1.19.2** (Forge, Fabric)
*   **Minecraft 1.16.5** (Forge, Fabric)
*   **Minecraft 1.12.2** (Forge only)

***

## 🚀 Key Features

*   ✨ **Smart Research Engine**: Deposit items to track your collection progress. Once a threshold is reached, you gain permanent access to retrieve unlimited copies of that item!
*   🗂️ **Global Unlocks Portability**: Unlocks are persisted globally in a central JSON profile. Hard-earned research carries over automatically across different worlds and servers, completely immune to world corruption or modpack wipes.
*   📐 **Dynamic Recipe Solver**: Automatically scales unlock requirements from 1 to 64 depending on item complexity and crafting depth, powered by a cycle-breaking recursive recipe tree calculator.
*   ⚡ **Fluid Inventory Utilities**:
    *   _Shift-Click Dump_: Clean up your inventory instantly while inside the Journey tab by shift-clicking any item already unlocked in your catalog.
    *   _Drag Grid Deletion_: Drag an item stack and click it directly on its matching unlocked icon to instantly discard the stack.
*   🎨 **Immersive Dual-Tab GUI**: Intuitive Deposit and Journey panels featuring built-in item searching, interactive catalog grids, and real-time sorting (newly unlocked items appear first).
*   🛡️ **Dimension & Server Safe**: Bulletproof player-attachment tracking keeps research safe during death, respawning, or dimension travel.

***

## 📦 How It Works

Embark on your item collection journey with a simple four-step process. No duplicate steps or cluttered visual assets:

```
[ Deposit Item ] ──> [ Track Progress ] ──> [ Unlock Permanently ] ──> [ Retrieve Infinitely ]
```

### 1\. Deposit Items

Press **`J`** in-game to open your Catalog. Place items in the Deposit slot to count them toward your research progress.

![Deposit Window Empty](https://res.cloudinary.com/dx16xt23r/image/upload/v1761997883/IMG1_l41hi6.png) _The default deposit panel where your item progression starts._

### 2\. Track Unlock Progress

The interface dynamically shows your progress and how many more items of this type are needed to fully unlock it.

![Deposit Window with Item Progress](https://res.cloudinary.com/dx16xt23r/image/upload/v1761997882/IMG2_pvxj3n.png) _Deposit items in the slot to see their exact requirement thresholds._

### 3\. Retrieve Infinite Copies

Switch to the **Journey** tab once items are fully unlocked. You can pull infinite stacks of unlocked items directly into your inventory!

![Journey Tab and Catalog Grid](https://res.cloudinary.com/dx16xt23r/image/upload/v1761997882/IMG3_dguymq.png) _Retrieve infinite copies of unlocked items (Left-Click for 1, Shift + Left-Click for 64)._

***

## 📖 Commands & Controls

### Command Interface

Interact with Journey Mode via Brigadier commands:

| Command             |Action  |Description                                                         |
| ------------------- |------- |------------------------------------------------------------------- |
| <code>/journeymode on</code> |<strong>Enable</strong> |Enable Journey Mode for yourself (allows GUI access &amp; deposits). |
| <code>/journeymode off</code> |<strong>Disable</strong> |Disable Journey Mode (prevents GUI access &amp; deposits).          |
| <code>/journeymode status</code> |<strong>Status</strong> |Check your current toggle state.                                    |
| <code>/journeymode reset</code> |<strong>Reset</strong> |Permanently wipes your personal catalog and restarts your progress. |

### Key Bindings

*   **Open Catalog**: Press **`J`** (rebindable under _Options ──> Controls ──> Key Binds ──> Journey Mode_).

***

## 📐 Smart Thresholds & Crafting Depth

Thresholds are calculated automatically based on the complexity of the item's recipe:

| Item Type                    |Example Item               |Default Threshold    |
| ---------------------------- |-------------------------- |-------------------- |
| <strong>Tools, Weapons, &amp; Armor</strong> |Diamond Pickaxe            |<strong>1</strong> item |
| <strong>Complex Crafted Mechanics</strong> |Redstone Comparator        |<strong>1</strong> item |
| <strong>Non-Stackable Goods</strong> |Bucket, Shears             |<strong>1</strong> item |
| <strong>Raw Materials</strong> |Iron Ore, Log, Cobblestone |<strong>64</strong> items (1 stack) |
| <strong>Simple Crafted (Depth 1)</strong> |Iron Ingot, Wooden Planks  |<strong>32</strong> items (50% stack) |
| <strong>Secondary Crafted (Depth 2+)</strong> |Iron Block, Chest          |<strong>16</strong> items (25% stack) |

> [!TIP]
> You can easily override these dynamic calculations with specific static values inside `custom_thresholds.json`.

***

## 💾 Global Profile & Save Portability

Rather than locking progress to a single save world, Journey Mode saves unlocks to a centralized **Terraria-Style** profile. This means your progression is tied to your player profile, not the map!

### Directory Structure

```
.minecraft/ (or your server root folder)
├── config/
│   └── Journey Mode/               <-- Mod Configurations
│       ├── blacklist.json
│       └── custom_thresholds.json
├── journeymode_unlocks.json        <-- CENTRAL SAVE PROFILE (Player Unlocks)
└── saves/
    ├── World_A/
    └── World_B/
```

### 🔍 Storage Operations & Portability

*   **Where to Find It**: Located directly in the root folder of your Minecraft installation (for singleplayer) or server installation (for multiplayer) as `journeymode_unlocks.json`.
*   **Cross-World Portability**: Storing unlocks outside of individual world folders means you can safely create, delete, update, or backup worlds without losing your catalog progress.
*   **Backup & Migration**: Safely copy or transfer the `journeymode_unlocks.json` file to migrate progress to a new PC, back up your progression history, or share researched libraries with friends.
*   **Offline Editing Friendly**: Admins or players can manually modify this JSON file while the game or server is shut down to edit progress, inject items, or reset players.

### 📄 Central Profile Schema (`journeymode_unlocks.json`)

The file is organized as a dynamic JSON dictionary mapped by unique player **UUIDs** (Universal Unique Identifiers), ensuring multiplayer safety and compatibility:

```
{
  "4042cfd6-0c6a-4d2b-aa90-b1ffbc018c64": {
    "collected_counts": {
      "minecraft:diamond": 5,
      "minecraft:dirt": 64,
      "minecraft:oak_log": 12
    },
    "unlocked_items": [
      "minecraft:dirt"
    ],
    "unlock_timestamps": {
      "minecraft:dirt": 1716154382000
    },
    "enabled": true
  }
}
```

### 🔬 Key-Value Schema Breakdown

Inside each player's UUID entry, the following parameters are tracked:

*   **`collected_counts`**: A dictionary mapping item namespaced IDs (e.g. `"minecraft:diamond"`) to the exact number of items the player has deposited. This tracks active progress before an item is fully unlocked.
*   **`unlocked_items`**: A list of fully researched item IDs. Items in this list are fully unlocked and can be retrieved infinitely from the **Journey** tab catalog.
*   **`unlock_timestamps`**: A dictionary mapping each unlocked item to the exact Unix Epoch millisecond timestamp of when it was unlocked. Perfect for tracking progress over time.
*   **`enabled`**: A boolean (`true`/`false`) tracking whether the player currently has Journey Mode active. Controlled dynamically in-game via the `/journeymode on|off` commands.

***

## ⚙️ Configuration

Configurations are automatically generated under `config/Journey Mode/`. Edits apply dynamically **in-real-time** without restarting the client.

### `blacklist.json`

Prevent specific items from being deposited or researched:

```
{
  "_comment": "Add item IDs to blacklist them from Journey Mode",
  "blacklisted_items": [
    "minecraft:bedrock",
    "minecraft:barrier",
    "minecraft:command_block",
    "minecraft:structure_void"
  ]
}
```

### `custom_thresholds.json`

Override the recipe calculator with direct unlock targets:

```
{
  "_comment": "Override unlock thresholds for specific items",
  "thresholds": {
    "minecraft:diamond": 10,
    "minecraft:netherite_ingot": 5,
    "minecraft:elytra": 1,
    "minecraft:enchanted_golden_apple": 3
  }
}
```

***

## 🚀 Installation & Support Matrix

Ensure you download the correct loader file based on your environment:

| Minecraft Version | Java Version | Target Mod Loaders | Key Requirements | File Name |
| :--- | :--- | :--- | :--- | :--- |
| **1.21.1** | Java 21 | NeoForge, Fabric | NeoForge `21.1.72+`<br>Fabric Loader `0.19.2+` & Fabric API | `journeymode-1.7.0-1.21.1.jar`<br>`journeymode-fabric-1.7.0-1.21.1.jar` |
| **1.20.1** (LTS) | Java 17 | Forge, Fabric | Forge `47.4.10+`<br>Fabric Loader `0.19.2+` & Fabric API | `journeymode-1.7.0-1.20.1.jar`<br>`journeymode-fabric-1.7.0-1.20.1.jar` |
| **1.19.2** | Java 17 | Forge, Fabric | Forge `41.1.0+`<br>Fabric Loader `0.14.9+` & Fabric API | `journeymode-1.7.0-1.19.2.jar`<br>`journeymode-fabric-1.7.0-1.19.2.jar` |
| **1.16.5** | Java 8 | Forge, Fabric | Forge `36.2.39+`<br>Fabric Loader `0.11.7+` & Fabric API | `journeymode-1.7.0-1.16.5.jar`<br>`journeymode-fabric-1.7.0-1.16.5.jar` |
| **1.12.2** | Java 8 | Forge only | Forge `14.23.5.2860+` | `journeymode-1.7.0-1.12.2.jar` |

***

## 🛠️ Developer Setup & Project Structure

### Repository Architecture

#### 1\. Main Branch (`main` / `1.6.0N`) - Minecraft 1.21.1 (Java 21)
```
journey-mode/                      # Root NeoForge 1.21.1 Project
├── src/                          # NeoForge source files
├── build.gradle                  # NeoForge gradle configurations
└── Fabric/                       # Standalone Fabric 1.21.1 Project
```

#### 2\. Legacy Port Branches (e.g. `1.20.1`, `1.12.2`)
Each legacy version is maintained inside its respective git branch using dedicated build subdirectories to prevent environment collisions:
*   **Branch `1.20.1`**: subdirectories `Forge_1_20_1/` and `Fabric_1_20_1/` (Java 17)
*   **Branch `1.19.2`**: subdirectories `Forge_1_19_2/` and `Fabric_1_19_2/` (Java 17)
*   **Branch `1.16.5`**: subdirectories `Forge_1_16_5/` and `Fabric_1_16_5/` (Java 8)
*   **Branch `1.12.2`**: subdirectory `Forge_1_12_2/` (Java 8, Forge-only)

***

## 🗺️ Development Roadmap

### Completed Milestones

*   **Milestone 1: Native 1.21.1 Release** 🟢
    *   Core logic reconstruction using stable `1.4.0` codebase baseline.
    *   Complete dual-platform compilation (Fabric 1.21.1 and NeoForge 1.21.1).
    *   Terraria-Style global syncing and real-time backup safety.
    *   Interactive grid deletion and Shift-Click inventory dumping utilities.
*   **Milestone 2: Legacy 1.20.1 LTS Porting** 🟢
    *   Native compilation environments targeting Java 17.
    *   Stable dual-platform support for **Forge (47.4.10)** and **Fabric (0.19.2)**.
    *   Adapted recipe calculator to run against Mojang's pre-1.20.2 raw `Recipe<?>` types.
*   **Milestone 3: Legacy Backports (1.19.2, 1.16.5, 1.12.2)** 🟢
    *   Adapted modern capabilities and network packet systems to legacy equivalents.
    *   Downported code structures to target Java 8 compatibility for 1.16.5 and 1.12.2.
    *   Added metadata-based subtype progression (`getItemKey`) to resolve stone, wool, and dye variant unlocks in 1.12.2.
    *   Completed compile pipelines, verified in-game, and published stable releases for all target versions!

### In Progress & Future Milestones

*   **Milestone 4: Visual Polish & FX** 🟡 _In Progress_
    *   🎨 _Custom Textures_: Immersive, vanilla-styled visual GUI catalog and custom scrollbar assets.
    *   🔊 _Sound Design_: Custom auditory cues on unlocking new item categories or milestone completions.
    *   ✨ _Unlock Particles_: Spark and firework burst effects upon unlocking a catalog item.
    *   📊 _Catalog Statistics_: Interactive in-game menu panel tracking collection percentages.
*   **Milestone 5: Advanced Multiplayer Faction Integration** 🚀 _Planned_
    *   👥 _Shared Team Catalogs_: Create build factions or teams that pool unlocks together.
    *   🔄 _Real-time Config Sync_: Host-side blacklists and overrides synced instantly to clients.
    *   🔌 _External Integration API_: Developer-facing hooks allowing other mods to inject custom calculations.

***

## 📋 Changelog

### Version `1.6.0N-1.12.2` (1.12.2 Forge Port & Subtypes Progression)

**Release Date:** May 23, 2026

*   **Forge Native Support**: Fully ported the mod to Minecraft **1.12.2** under standalone **Forge (14.23.5.2860)**.
*   **Java 8 Compilation Target**: Enforced strictly Java 8 compiler targets for full legacy compatibility, powered by RetroFuturaGradle compilation pipelines.
*   **Metadata-Based Subtypes Support**: Engineered robust unique item key tracking (`getItemKey`) combining the registry name and metadata (e.g. `minecraft:stone:4` for polished diorite) to separate progression and unlocks for stone types, wood types, colored wool, and dyes. Overloaded capabilities to accept both `Item` and `ItemStack` signatures.
*   **GUI Lighting and Visibility Polishing**: Explicitly managed standard GUI item lighting (`RenderHelper.enableGUIStandardItemLighting()`) to resolve dark items in the Journey catalog grid. Overrode `isEnabled()` on container slots to cleanly hide/draw slots during tab switches.
*   **Decoupled Gradle Workspace**: Built inside decoupled `Forge_1_12_2/` directory with independent configs.

### Version `1.6.0N-1.20.1` (1.20.1 Multi-Loader LTS Port)

**Release Date:** May 20, 2026

*   **Forge & Fabric Native Support**: Fully ported the mod to Minecraft **1.20.1** under standalone **Forge (47.4.10)** and **Fabric (loader 0.19.2)** configurations.
*   **Java 17 Compilation Target**: Shifted compiler toolchains and build targets to Java 17 for full pre-1.20.5 runtime compatibility.
*   **Legacy Recipe Calculator Adaptation**: Re-engineered `RecipeDepthCalculator` across both platforms to directly query raw pre-1.20.2 `Recipe<?>` types from `RecipeManager`, resolving modern `RecipeHolder` classloading conflicts.
*   **Dimension-Safe Fabric Storage**: Created a thread-safe `Map<UUID, JourneyDataAttachment>` tracking capability to safeguard Fabric players from dimension-swap or respawn desyncs.
*   **Clean Isolated Directories**: Configured decoupled folders `Forge_1_20_1/` and `Fabric_1_20_1/` with independent buildscripts.

### Version `1.6.0N` (Core Reconstruction)

**Release Date:** May 19, 2026

*   **Reconstructed Core Engine**: Revamped and rebuilt all loader projects back from the verified stable **`1.4.0`** codebase, completely resolving previous experimental multi-module startup crashes and desync issues.
*   **Global Unlocks Portability**: Integrated and polished the global `journeymode_unlocks.json` save handler. In-game operations leverage modern attachments (NeoForge) and static tracking maps (Fabric) for lag-free in-memory lookups, while all progress dynamically saves to the central JSON file.
*   **Fabric 1.21.1 Native Port**: Fully implemented the Fabric version inside the `Fabric/` directory with 100% feature-parity.
*   **Enforced Gradle Mappings Isolation**: Re-configured Gradle scripts to run official Mojang mappings (`mappings loom.officialMojangMappings()`) natively alongside Fabric Loader `0.19.2`, ensuring perfect compile-time type-safety.
*   **Safe Container Mixins**: Ported screen mixins (`HandledScreenMixin.java`) to inject cleanly on top of `AbstractContainerScreen` in the Mojmap environment.

### Version `1.5.0N` (Dedicated Server Stability)

**Release Date:** May 19, 2026

*   **Physical Side-Safety Fixes**: Nested `ClientKeyHandler` inside client-only classes, isolating server runs from graphics packages (preventing crashes on dedicated servers).
*   **Cleanups & Optimizations**: Removed unused classes, outdated reference files (such as obsolete key binding systems), and refined networking packet channels.

### Version `1.4.1N` (Dumping & Drag Grid Deletion)

**Release Date:** May 19, 2026

*   **Shift-Click Inventory Dump**: Allows players to shift-click items inside their inventory while looking at the catalog grid to discard them if they have already been unlocked.
*   **Drag-to-Delete Grid Handler**: Integrated inventory drag-slot listener to let players drag and drop items straight on their unlocked slots to delete them instantly.

### Version `1.4.0` (First Stable Baseline Release)

**Release Date:** October 31, 2025

*   **Journey Mode Toggle System**: Brigadier `/journeymode on|off|status` toggle handles client catalog restrictions dynamically.
*   **Custom Threshold Overrides**: Created customizable JSON configurations for `custom_thresholds.json` and `blacklist.json`.

***

## 📄 License & Links

*   **License**: Licensed under the [MIT License](LICENSE).
*   **GitHub Repository**: [Aryangpt007/Journey-Mode](https://github.com/Aryangpt007/Journey-Mode)
*   **CurseForge Mod Portal**: [Journey Mode on CurseForge](https://www.curseforge.com/minecraft/mc-mods/journey-mode)
*   **Modrinth Mod Portal**: [Journey Mode on Modrinth](https://modrinth.com/mod/journey-mode)
*   **Issue Tracker**: [Report Bugs & Request Features](https://github.com/Aryangpt007/Journey-Mode/issues)

**Created by Aryangpt007** • Made with ❤️ for the Minecraft community.