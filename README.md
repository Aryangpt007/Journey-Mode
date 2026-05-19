# Journey Mode

A Minecraft mod that allows you to unlock unlimited access to items after collecting enough of them. Available natively for **both Fabric and NeoForge** on **Minecraft 1.21.1**.

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange.svg)](https://neoforged.net/) [![Fabric](https://img.shields.io/badge/Fabric-1.21.1-blue.svg)](https://fabricmc.net/) [![License](https://img.shields.io/github/license/Aryangpt007/Journey-Mode)](LICENSE) [![Downloads](https://img.shields.io/github/downloads/Aryangpt007/Journey-Mode/total)](https://github.com/Aryangpt007/Journey-Mode/releases)

## 🎮 What is Journey Mode?

Journey Mode transforms your Minecraft experience by allowing you to **unlock items permanently** after collecting enough of them. Once unlocked, you can retrieve unlimited copies of that item whenever you need them!

**Perfect for:**

*   🏗️ **Builders** - Never run out of building materials
*   ⚗️ **Modpack Players** - Streamline repetitive crafting
*   🎯 **Completionists** - Track your item collection progress
*   🎮 **Casual Players** - Reduce grinding after initial collection

## 📸 Screenshots

### Deposit Tab - Empty

![Empty Deposit Window](https://res.cloudinary.com/dx16xt23r/image/upload/v1761997883/IMG1_l41hi6.png) _The deposit tab where you begin your journey_

### Deposit Tab - With Item

![Deposit Window with Item](https://res.cloudinary.com/dx16xt23r/image/upload/v1761997882/IMG2_pvxj3n.png) _Place an item to see how many you need to unlock it_

### Journey Tab - Unlocked Items

![Journey Tab with Unlocked Items](https://res.cloudinary.com/dx16xt23r/image/upload/v1761997882/IMG3_dguymq.png) _Access all your unlocked items with infinite retrieval_

## ✨ Features

### Core Gameplay

*   **Reconstructed Core Engine**: Revamped and rebuilt back from the verified stable **`1.4.0`** codebase, completely resolving previous experimental multi-module startup crashes and desync issues.
*   **Smart Unlock System**: Deposit items to track your collection progress toward unlocking them permanently.
    *   Dynamic thresholds scaled automatically based on item complexity via a cycle-breaking recipe tree solver.
    *   Tools, weapons, & armor: Only 1 required.
    *   Raw materials (ores, logs, dirt): Full stack (64).
    *   Crafted items (Planks, blocks): Scaled by recipe depth (50% or 25% stack size).
    *   Complex crafted items: Only 1 required.
*   **Global Profile Portability & Safety**: Unlocks are persisted globally inside a centralized `journeymode_unlocks.json` profile in the root Minecraft folder, combined with fast in-memory attachment tracking. This Terraria-style research architecture allows player unlocks to automatically carry over across new singleplayer worlds or server instances, while completely insulating hard-earned progress from world corruption, server rollbacks, or modpack updates.
*   **Dual-Tab GUI**: Beautiful custom layout featuring separate Deposit and Journey tabs with integrated inventory labels.
*   **Real-Time Search & Filtering**: Locate items instantly via a case-insensitive search bar in the Journey tab.
*   **Smart Sorting**: Items are automatically sorted with your most recently unlocked items displayed first.
*   **Bulk Inventory Utilities**: 
    *   *Bulk Dump:* Shift-click items in your inventory while inside the Journey tab to instantly delete the stack if that item is already unlocked.
    *   *Drag Grid Deletion:* Drag an item stack from inventory and left-clicking it directly on its matching unlocked icon in the Journey grid deletes the carried cursor stack immediately.
*   **Dimension-Swap & Death Immunity**: Attachments are protected via a robust persistent server engine, keeping player unlocks fully safe across dimension boundaries, respawning, and death desyncs.

### Commands & Controls

*   **Journey Mode Toggle**: Enable/disable Journey Mode per player with simple Brigadier commands:
    *   `/journeymode on` - Enable Journey Mode (allows opening GUI and depositing)
    *   `/journeymode off` - Disable Journey Mode
    *   `/journeymode status` - Check current toggle status
    *   `/journeymode reset` - Wipe your personal unlock progress instantly
*   **Customizable Keybind**: Default key `J` opens the catalog interface (fully bindable under Options → Controls → Key Binds → Journey Mode).

### Configuration

*   **Hot-Reloadable Configs**: Instantly customize mod configurations located under `config/Journey Mode/`:
    *   `blacklist.json` - Prevent specific items from being deposited or unlocked (e.g. bedrock, barriers).
    *   `custom_thresholds.json` - Override recipe-based calculations with custom thresholds for specific items.
*   **No Restart Required**: Config edits apply dynamically in-game without restarting the client or server.

### Native Multi-Loader Support

*   ✅ **NeoForge 1.21.1** - Native compilation using official Mojang mappings.
*   ✅ **Fabric 1.21.1** - Standalone secondary project using official Mojang mappings and native Fabric API networking.
*   🔄 **100% Feature Parity** - Identical mechanics, interfaces, and file formats across both loaders.

## 📦 How It Works

1.  **Deposit Items**: Open the Journey Mode menu (press `J`) and place items in the deposit slot
    
    ![Empty Deposit Window](https://res.cloudinary.com/dx16xt23r/image/upload/v1761997883/IMG1_l41hi6.png)
    
2.  **Track Progress**: Each item you deposit counts toward the unlock threshold
    
    ![Deposit Window with Progress](https://res.cloudinary.com/dx16xt23r/image/upload/v1761997882/IMG2_pvxj3n.png)
    
3.  **Unlock Items**: Once you've deposited enough of an item type, it becomes unlocked
    
4.  **Infinite Retrieval**: Switch to the Journey tab and click any unlocked item to retrieve it
    
    ![Journey Tab](https://res.cloudinary.com/dx16xt23r/image/upload/v1761997882/IMG3_dguymq.png)
    
    *   Left-click: Get 1 item
    *   Shift + Left-click: Get 64 items

## 🚀 Installation

### Requirements

#### For NeoForge

*   **Minecraft**: `1.21.1`
*   **NeoForge**: `21.1.72` or higher
*   **Java**: `21` or higher

#### For Fabric

*   **Minecraft**: `1.21.1`
*   **Fabric Loader**: `0.19.2` or higher
*   **Fabric API**: `0.102.1+1.21.1` or higher (required dependency)
*   **Java**: `21` or higher

### Download

**Choose your mod loader:**

*   🟠 [**NeoForge Version**](https://github.com/Aryangpt007/Journey-Mode/releases/latest) - `journeymode-1.6.0N-1.21.1.jar`
*   🔵 [**Fabric Version**](https://github.com/Aryangpt007/Journey-Mode/releases/latest) - `journeymode-fabric-1.6.0N-1.21.1.jar`

**Also available on:**

*   [CurseForge](https://www.curseforge.com/minecraft/mc-mods/journey-mode)
*   [Modrinth](https://modrinth.com/mod/journey-mode)

---

## 📖 How to Use

### Commands

```
/journeymode on       # Enable Journey Mode for yourself
/journeymode off      # Disable Journey Mode for yourself
/journeymode status   # Check if Journey Mode is enabled
/journeymode reset    # Reset your unlock progress
```

### Understanding Thresholds

Journey Mode automatically calculates **smart thresholds** based on crafting complexity:

| Item Type | Example | Threshold |
| :--- | :--- | :--- |
| **Tools & Armor** | Diamond Pickaxe | 1 item |
| **Complex Crafted** | Redstone Comparator | 1 item |
| **Raw Materials** | Iron Ore, Logs | 64 items (full stack) |
| **Crafted (Depth 1)** | Iron Ingot, Planks | 32 items (50%) |
| **Crafted (Depth 2)** | Iron Block | 16 items (25%) |
| **Non-Stackable** | Bucket, Shears | 1 item |

*Thresholds can be overridden inside `custom_thresholds.json`!*

---

## ⚙️ Configuration

Config files are automatically generated under `config/Journey Mode/`

### `blacklist.json`
Prevent specific items from being deposited:
```json
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
Override unlock requirements for specific items:
```json
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

---

## 🗺️ Roadmap

### Current Status

*   ✅ **Phase 1**: NeoForge 1.21.1 Core Features (Complete)
*   ✅ **Phase 2**: Reconstructed Stable Codebase (Complete)
*   ✅ **Phase 3**: Standalone Fabric Port (Complete)
*   ✅ **Phase 4**: Automated Multi-Platform Deployments (Complete)

### Future Plans

*   ✨ Custom GUI textures (vanilla styled tabs and scrollbar rendering)
*   ✨ Export/import unlocked items catalog via text/JSON clips
*   ✨ Sound effects for unlocking events and deposits
*   ✨ Shared locks / team progress sync for multiplayer servers

---

## 🔧 Development Setup

### Prerequisites

*   JDK 21 or later
*   Git

### Building NeoForge Mod (Root Project)

```powershell
# Clone the repository
git clone https://github.com/Aryangpt007/Journey-Mode.git
cd Journey-Mode

# Compile NeoForge JAR
.\gradlew.bat clean build
# Built JAR resides inside: build/libs/
```

### Building Fabric Mod (Nested Project)

```powershell
cd Fabric

# Compile Fabric JAR
.\gradlew.bat clean build
# Built JAR resides inside: Fabric/build/libs/
```

### Project Structure

```
journey-mode/                  # Root NeoForge 1.21.1 Project
├── src/                      # NeoForge source code
├── build.gradle              # NeoForge build configuration
├── Fabric/                   # Nested Standalone Fabric Project
│   ├── src/                  # Fabric source code
│   └── build.gradle          # Fabric build configuration
└── libs_dist/                # Pre-built release distribution JARs
```

---

## 📋 Changelog

### Version 1.6.0N
**Release Date:** May 19, 2026

#### 🚀 Complete Mod Reconstruction & Porting
* **Experimental Recovery & Revamp:** Previous experimental builds of both NeoForge and Fabric were broken due to the introduction of a complex multi-module architecture. We have completely revamped and reconstructed both loader projects back from the **`1.4.0`** codebase, which was the last verified stable build before introducing multi-module setups.
* **100% Bug-Free Native JARs:** Both the Fabric 1.21.1 and NeoForge 1.21.1 mods are now compiled natively and work perfectly without any runtime bugs or crashes.
* **Global Profile Syncing & Redundancy:** Re-integrated and polished the global `journeymode_unlocks.json` save handler. In-game operations leverage modern attachments (NeoForge) and static tracking maps (Fabric) for lag-free in-memory lookups, while all progress dynamically saves to the central JSON file. This dual architecture ensures unlocks automatically carry over to new worlds (like Terraria characters) and remains 100% immune to world corruption or modpack update wipes.
* **Fabric 1.21.1 Native Port:** Fully implemented the Fabric version inside the `Fabric/` directory with 100% feature-parity.
* **Enforced Gradle Mappings Isolation:** Re-configured Gradle scripts to run official Mojang mappings (`mappings loom.officialMojangMappings()`) natively alongside Fabric Loader `0.19.2`, ensuring perfect compile-time type-safety.
* **Safe Container Mixins:** Ported screen mixins (`HandledScreenMixin.java`) to inject cleanly on top of `AbstractContainerScreen` in the Mojmap environment.
* **dimension-swap Immune Core:** Fabric player unlock attachments utilize a robust static JVM map tracking handler inside `GlobalDataHandler.java`, keeping player data fully safe across dimension boundaries or deaths without relying on heavy platform serialization systems.

---

### Version 1.5.0N
**Release Date:** May 19, 2026

#### Major Physical Side-Safety & Code Polish
- 🛡️ **Physical Side-Safety Fixes:** Nested `ClientKeyHandler` inside `ClientSetup.java` (a client-only class) and removed all client-side imports (`Minecraft`, `ClientTickEvent`, GLFW, etc.) from `JourneyModeEvents.java` (a common class loaded on the dedicated server). This guarantees **zero startup crashes or class-loading errors on dedicated servers**.
- 🧹 **Unused Import Cleanups:** Removed dead `Minecraft` client class references from the common `SyncJourneyDataPacket.java`.
- 🗑️ **Redundant File Deletion:** Removed the obsolete and unused `KeyBindings.java` file to clean the codebase.

#### Real-Time Global Save Portability & Sync
- 💾 **Global Unlocks Portability:** Created `GlobalDataHandler.java` to read and write player unlocks to a central `journeymode_unlocks.json` file in the base Minecraft installation directory (`FMLPaths.GAMEDIR`).
- 🔄 **Real-Time World Portability:** Unlocks are dynamically keyed by player UUIDs. Moving or sharing `journeymode_unlocks.json` enables carrying forward unlocks across completely different worlds, servers, or modpack instances.
- 🔑 **Real-Time Action Sync:** 
  - **Login Sync:** Automatically loads a player's global saves on joining a world via the `PlayerLoggedInEvent` listener.
  - **Dimension & Death Sync:** Added a robust `PlayerEvent.Clone` handler that copies player attachment data across dimensions or respawns via the new `copyFrom()` helper and syncs to the client.
  - **Real-Time Writes:** Saves the player's updated unlocks instantly to the global JSON file whenever an item is deposited or Journey Mode status is command-toggled.

#### Journey Mode Reset Command
- 🧹 **Reset Command:** Implemented a new command `/journeymode reset` to instantly wipe a player's progress and unlocks.
- ⚡ **Real-Time Reset Sync:** Wipes the attachment data, saves the empty state to `journeymode_unlocks.json`, and updates the client screen instantaneously.

---

### Version 1.4.1N
**Release Date:** May 19, 2026

#### Journey Tab Dumping & Deletion Mechanics
- 🗑️ **Shift-Click Dump:** Shift-clicking items in player inventory while viewing the Journey tab instantly deletes the stack if that item has already been unlocked in their Journey catalog.
- 🫳 **Drag-and-Drop Grid Deletion:** Dragging an item stack from inventory and left-clicking it directly on its matching unlocked icon in the Journey grid deletes the carried cursor stack immediately.
- 🌐 **Menu Tab Syncing:** Added `SyncTabPacket` and `DeleteCarriedPacket` to track the player's active tab and process deletes securely on the server-side.

#### Critical Bug Fixes (v1.4.0 Baseline Resolution)
- 🚫 **Slot Visibility & Clicks blocked:** Deactivated the deposit slot and hid its rendering in the Journey tab client interface via `ConditionalSlot` and `renderSlot` override filters.
- 🛡️ **Item-Loss Prevention on Close:** Relocated the deposit slot cleanup code to the server-side menu `removed()` container method, preventing client packet bypasses.
- 💥 **Attachment Serialization Crash Resolved:** Switched the capability codec serialization from mojang's record codebuilder to flat GSON-based `Codec.STRING.xmap(...)` flat serialization.

---

### Version 1.4.0
**Release Date:** October 31, 2025

#### Major Features - Phase 1 Complete
- ✅ **Journey Mode Toggle System**
  - Per-player enable/disable via `/journeymode on|off` command
  - Default: Enabled for all players
  - Disabled players cannot open GUI or deposit items
  - Status check: `/journeymode` without arguments
  - Toggle state persists across sessions
- ⚙️ **Configuration System** (JSON-based like ProjectE)
  - Config folder: `config/Journey Mode/`
  - **blacklist.json**: Block specific items from being deposited
    - Auto-generates with common examples (bedrock, barrier, command blocks)
    - Add/remove item IDs as needed
  - **custom_thresholds.json**: Custom unlock requirements per item
    - Override recipe-based calculations
    - Examples included (diamond, netherite, elytra)
  - **journeymode-common.toml**: Main config (currently minimal)
  - All configs hot-reload without server restart
- **JourneyModeCommand**: Brigadier command with on/off/status subcommands
- **RecipeDepthCalculator**: Config override check before recipe calculation
- **JourneyModeMenu**: Blacklist validation in `processDeposit()`
- **OpenJourneyMenuPacket**: Toggle state check before opening GUI
- All features properly localized in `en_us.json`

---

### Version 1.3.4 - v1.0.0

See [full version history](https://github.com/Aryangpt007/Journey-Mode/releases)

## 🗺️ Development Roadmap

### Phase 1: Core Enhancements (1.21.1) — 🟢 Completed
*   **Per-Player Toggles:** Brigadier-style `/journeymode on|off|status` commands.
*   **Recipe Depth Calculator:** Smart thresholds generated dynamically based on crafting complexity.
*   **Hot-Reloadable Configuration:** Custom overrides via `blacklist.json` and `custom_thresholds.json` under `config/Journey Mode/`.
*   **Crash-Proof Core:** Standardized side-safe client-server handlers ensuring zero dedicated server startup crashes.

### Phase 2: Polish & Aesthetics — 🟡 In Progress
*   🎨 **Custom GUI Textures:** Replace default container panels with beautiful, immersive custom artwork.
*   🔊 **Auditory Feedback:** Satisfying custom sound effects on successful items deposit and unlock events.
*   ✨ **Visual Sparks:** Bursting particle effects around the player when new items are added to their infinite library.
*   📊 **Statistics Tracking:** In-game interface or command tracking total items deposited, category statistics, and unlocks.

### Phase 3: Multi-Version Support (Forge & Fabric) — 📅 Planned
Targeting stable and popular legacy and modern modding environments:
*   ✅ **Minecraft 1.21.1 (NeoForge & Fabric)** — *Current Stable Release*
*   📅 **Minecraft 1.20.1 (Forge, Fabric, NeoForge)**
*   📅 **Minecraft 1.19.2 (Forge & Fabric)**
*   📅 **Minecraft 1.16.5 (Forge & Fabric)**
*   📅 **Minecraft 1.12.2 (Forge)**

### Phase 4: Future Features & Multiplayer Integrations — 🚀 Planned
*   👥 **Shared Team Unlocks:** Co-op progress syncing allowing server factions or build teams to share researched items.
*   🔄 **Server Config Syncing:** Host-side configurations, custom thresholds, and blacklist updates synced automatically to clients.
*   🏆 **Advancement Milestones:** In-game Minecraft achievements for unlocking large categories of items (e.g. Master Miner, Farmer, Armorer).
*   🔌 **Mod Integration API:** A developer-facing API enabling external mods to register custom calculations or automated blacklists.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🔗 Links

*   **GitHub**: [Aryangpt007/Journey-Mode](https://github.com/Aryangpt007/Journey-Mode)
*   **CurseForge**: [Journey Mode](https://www.curseforge.com/minecraft/mc-mods/journey-mode)
*   **Modrinth**: [Journey Mode](https://modrinth.com/mod/journey-mode)
*   **Issues**: [Report a Bug](https://github.com/Aryangpt007/Journey-Mode/issues)

## 👤 Author

**Aryangpt007** - [@Aryangpt007](https://github.com/Aryangpt007)

## 🙏 Acknowledgments

*   NeoForge team for the excellent modding framework
*   Fabric team for the modding API
*   Minecraft modding community for documentation and support

***

**Made with ❤️ for the Minecraft community**
