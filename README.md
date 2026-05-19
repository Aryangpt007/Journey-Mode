# Journey Mode

A Minecraft mod for both **NeoForge** and **Fabric** (1.21.1) that allows you to unlock unlimited access to items after collecting enough of them. 

Inspired by Terraria's Journey Mode and ProjectE, this mod features dynamic per-item thresholds, smart recipe-complexity analysis, client-server json synchronization, hot-reloadable item blacklists, and a sleek tabbed interface to manage your catalog of items.

---

## 🎮 Features

* **Multi-Loader Support:** Fully native builds for **NeoForge 21.1.72+** and **Fabric Loader 0.19.2+** (Minecraft 1.21.1).
* **Item Collection Tracking:** Deposit items to track your progress toward unlocking them in your Journey catalog.
* **Dynamic Smart Thresholds:** 
  * Stack size 1 items (tools, armor, weapons): Only **1 item** required.
  * Raw materials (ores, wood, dirt): Requires **full stack** (64 for most, 16 for ender pearls).
  * Crafted Items (Depth 1): Requires **50% stack size** (32 for most).
  * Crafted Items (Depth 2): Requires **25% stack size** (16 for most).
  * Complex Items (Depth 3+): Only **1 item** required.
* **Smart Recipe-Complexity Analysis:** Runs a dynamic cycle-breaking recipe tree solver (`RecipeDepthCalculator`) to automatically scale item thresholds based on crafting difficulty.
* **Sleek Dual-Tab Search GUI:**
  * **Deposit Tab:** Placed with live requirement progress reporting, percentage calculator, and "Submit" button to confirm deposits safely.
  * **Journey Tab:** Scrollable grid showing all unlocked items with real-time text filtering search and sorting by the most recently unlocked items first.
* **Instant Inventory Utilities:** 
  * **Bulk Dump:** Shift-click an item in your inventory while inside the Journey tab to instantly delete the stack if it's already unlocked.
  * **Drag Grid Deletion:** Drag a stack and drop it directly onto its unlocked grid slot to throw away the carried cursor stack immediately.
* **Immune to Dimension-Swap Bugs:** Core player attachments are coordinated via a persistent server engine, making player data 100% safe across dimension swaps, respawning, and death.
* **Global Portability:** Saved player data scales dynamically keyed by player UUIDs under `journeymode_unlocks.json` in the base game directory, allowing you to carry unlocks seamlessly across worlds and servers.
* **Hot-Reloadable Configs:** Customize requirements instantly under `config/Journey Mode/` with `blacklist.json` and `custom_thresholds.json`.

---

## 📦 How It Works

1. **Open the Catalog:** Press `J` (default keybind, fully customizable in controls settings under "Journey Mode" category).
2. **Submit Progress:** Place items in the Deposit slot to see their exact thresholds, current progress, and percentage. Click **Submit** to confirm.
3. **Pull Unlocked Items:** Switch to the Journey Tab, use the case-insensitive search bar to locate your item, and pull items infinitely:
   * **Left-click:** Retrieves 1 item.
   * **Shift + Left-click:** Retrieves a full stack of 64.

---

## 🛠️ Installation

### 🔴 NeoForge Version
#### Requirements
* Minecraft `1.21.1`
* NeoForge `21.1.72` or later
* Java 21 or later

#### Steps
1. Download `journeymode-1.6.0N-1.21.1.jar` from [Releases](https://github.com/Aryangpt007/Journey-Mode/releases).
2. Place the JAR file inside your `.minecraft/mods` folder.
3. Launch Minecraft using the NeoForge profile.

---

### 🔵 Fabric Version
#### Requirements
* Minecraft `1.21.1`
* Fabric Loader `0.19.2` or later
* Fabric API `0.102.1+1.21.1` or later
* Java 21 or later

#### Steps
1. Download `journeymode-fabric-1.6.0N-1.21.1.jar` from [Releases](https://github.com/Aryangpt007/Journey-Mode/releases).
2. Place both the mod JAR and the **Fabric API** JAR inside your `.minecraft/mods` folder.
3. Launch Minecraft using the Fabric profile.

---

## 🔧 Development Setup & Compilation

### Prerequisites
* JDK 21 or later
* Git

### Cloning the Project
```powershell
git clone https://github.com/Aryangpt007/Journey-Mode.git
cd Journey-Mode
```

### 🔴 Compiling the NeoForge Mod
You can build the default `1.21.1` NeoForge build or compile all target version JARs (`1.21.1` to `1.21.10`) simultaneously:

```powershell
# Compile the default 1.21.1 NeoForge mod JAR
.\gradlew.bat clean build
# Output will be located in: build/libs/journeymode-1.6.0N-1.21.1.jar

# Run clients in development environment
.\gradlew.bat runClient
```

To compile all 10 Minecraft versions at once (with their correct customized NeoForge versions and ranges injected automatically into their respective mod descriptors):
```powershell
# Run the batch compiler script
powershell -ExecutionPolicy Bypass -File .\build_all_versions.ps1
# Preserved output JARs will be generated in: libs_dist/
```

### 🔵 Compiling the Fabric Mod
The Fabric project is configured as a standalone Gradle project nested inside the `Fabric/` sub-directory:

```powershell
cd Fabric

# Compile the remapped Fabric mod JAR
.\gradlew.bat clean build
# Output will be located in: Fabric/build/libs/journeymode-fabric-1.6.0N-1.21.1.jar
```

---

## 📝 Configuration

All configuration files are automatically generated under your Minecraft installation's `config/Journey Mode/` directory and work identically on both modloaders:

### 1. `blacklist.json`
Add item IDs here to completely block them from being deposited or unlocked (e.g. bedrock, barriers, command blocks).
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

### 2. `custom_thresholds.json`
Override the recipe-based threshold scaling for specific items by specifying custom values:
```json
{
  "_comment": "Override unlock thresholds for specific items",
  "thresholds": {
    "minecraft:diamond": 10,
    "minecraft:netherite_ingot": 5,
    "minecraft:elytra": 1
  }
}
```

Both configurations support **hot-reloads** dynamically, meaning changes will apply instantly in-game without restarting the game client or server!

---

## 📋 Changelog

### Version 1.6.0N
**Release Date:** May 19, 2026

#### 🚀 Complete Fabric 1.21.1 Mod Port
* **Dual Modloader Native Platform Support:** Fully implemented a secondary standalone Fabric port nested inside the `Fabric/` directory targeting **Minecraft 1.21.1**.
* **Unified 1.6.0N Release Boundaries:** Synced the versions of both the Fabric and NeoForge codebases to `1.6.0N`.
* **Enforced Gradle Mappings Isolation:** Re-configured Gradle scripts to run official Mojang mappings (`mappings loom.officialMojangMappings()`) natively alongside Fabric Loader `0.19.2`, ensuring perfect compile-time type-safety.
* **Safe Container Mixins:** Ported screen mixins (`HandledScreenMixin.java`) to inject cleanly on top of `AbstractContainerScreen` in the Mojmap environment.
* **dimension-swap Immune Core:** Fabric player unlock attachments utilize a robust static JVM map tracking handler inside `GlobalDataHandler.java`, keeping player data fully safe across dimension boundaries or deaths without relying on heavy platform serialization systems.

#### 🔧 Multi-Version NeoForge Batch Updates
* **Unified 1.6.0N Batch Builds:** Updated the `build_all_versions.ps1` script to cleanly swap, build, and label all 10 distinct Minecraft versions (`1.21.1` to `1.21.10`) under the updated `1.6.0N` version tag inside the `libs_dist/` directory.

---

### Version 1.5.0N
**Release Date:** May 19, 2026

> [!IMPORTANT]
> **Tested Platform:** Minecraft 1.21.1 (NeoForge 21.1.72) only. 

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

> [!IMPORTANT]
> **Tested Platform:** Minecraft 1.21.1 (NeoForge 21.1.72) only.

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

### Version 1.3.4
**Release Date:** October 30, 2025

#### Major Feature
- ⌨️ **Configurable Keybind**: Journey Mode menu key is now customizable!
  - Default key: `J` (unchanged)
  - Configurable in: Options → Controls → Key Binds → Journey Mode
  - Can rebind to any key you prefer
  - Appears in dedicated "Journey Mode" category in controls menu

---

### Version 1.3.3
**Release Date:** October 30, 2025

#### Bug Fix
- 🔧 **Fixed Slot Outlines**: Slot borders/backgrounds now match actual slot positions
  - Updated inventory slot outlines: y=84 → y=110
  - Updated hotbar slot outlines: y=142 → y=168
  - Outlines now perfectly align with clickable slot areas
  - Visual consistency restored

---

### Version 1.3.2
**Release Date:** October 30, 2025

#### Major Improvements
- 🎨 **Dynamic GUI Height**: Increased GUI height to 204 pixels for proper spacing
  - Search box, inventory label, and all slots now have adequate room
  - No more overlapping elements in any tab
  - Clean, professional layout with breathing room

---

### Version 1.3.1
**Release Date:** October 30, 2025

#### Bug Fixes
- 🔧 **Fixed GUI Layout Issues**:
  - Moved inventory slots down by 14 pixels to match taller GUI (180px height)
  - Fixed "Inventory" label overlapping with slots in Deposit tab
  - Fixed search box overlapping with inventory slots in Journey tab
  - Search box now positioned at y+86 (above inventory)
  - Inventory label now at proper position (imageHeight - 80)
- 🔧 **Fixed Item Loss on GUI Close**:
  - Items left in deposit slot are now returned to player inventory when GUI closes
  - Added `removed()` override to handle cleanup
  - Prevents accidental item loss if you close GUI without submitting

---

### Version 1.3.0
**Release Date:** October 30, 2025

#### Major Features
- 🔍 **Item Search**: Search box in Journey tab to quickly find unlocked items
  - Real-time filtering as you type
  - Case-insensitive search
  - Matches item display names
  - Shows "No items match search" when no results
- 📊 **Smart Sorting**: Items automatically sorted by unlock time
  - Most recently unlocked items appear first
  - Easier to find your latest unlocks
  - Combined with search for powerful item finding

---

### Version 1.2.0
**Release Date:** October 30, 2025

#### Major UI/UX Improvements
- 🔘 **Submit Button**: Items no longer auto-deposit - click "Submit" button to confirm
  - Green button appears when item is in deposit slot
  - Prevents accidental deposits
  - Shows clear visual feedback
- 📊 **Live Item Info**: When item is placed in deposit slot (before submitting):
  - Shows required threshold for that specific item
  - Shows current collected count vs. required
  - Shows progress percentage
  - Displays "Already Unlocked!" for unlocked items
- 🚫 **Unlocked Item Protection**: Cannot deposit items that are already unlocked
  - Shows green checkmark if item is already unlocked
  - Prevents wasting items on already-unlocked entries
- 🎯 **Better Title Positioning**: "Journey Mode" title moved higher to avoid tab overlap

---

### Version 1.1.0
**Release Date:** October 30, 2025

#### Major Features
- 🎯 **Dynamic Unlock Thresholds**: Unlock requirements now adapt to each item!
  - **Stack size 1 items** (tools, armor, etc.): Require only **1 item**
  - **Raw materials** (ores, wood, etc.): Require **full stack size** (64 for most, 16 for ender pearls)
  - **Crafted items (Depth 1)**: Require **50% of stack size** (32 for most items)
  - **Crafted items (Depth 2)**: Require **25% of stack size** (16 for most items)
  - **Complex items (Depth 3+)**: Require only **1 item**
- 🔍 **Recipe Depth Analysis**: Automatically calculates crafting complexity
  - Depth 0: Raw materials with no recipe
  - Depth 1: Items crafted from raw materials
  - Depth 2+: Items requiring multiple crafting steps
  - Handles cyclic recipes and multiple recipe paths

---

### Version 1.0.1
**Release Date:** October 30, 2025

#### Bug Fixes
- 🐛 **Fixed GUI Rendering Issues**:
  - Added proper slot outlines for all inventory slots (now visible with dark borders)
  - Fixed text overlap - moved "Journey Mode" title above tabs
  - Fixed deposit tab instruction text positioning
  - Added proper borders and backgrounds for Journey tab item slots
- 🔄 **Fixed Data Synchronization**:
  - Unlocked item count now properly updates on client
  - Added `SyncJourneyDataPacket` for server-to-client data sync
  - Data syncs when menu opens and after each deposit
  - Journey tab now correctly shows unlocked items count

---

### Version 1.0.0 (Initial Release)
**Release Date:** October 30, 2025

#### Features
- ✨ **Core Journey Mode System**: Track item collection and unlock infinite access after depositing 30 items
- 🎨 **Dual-Tab GUI**: 
  - Deposit tab with single slot for item deposits
  - Journey tab displaying all unlocked items in a scrollable grid
- 🔄 **Client-Server Networking**: Synced item unlocking and retrieval system
- 💾 **Data Persistence**: Player data attachment with Codec serialization for save/load
- ⌨️ **Keybind**: Press `J` to open Journey Mode menu
- 📊 **Progress Tracking**: Visual feedback showing collection progress and unlock status
- 🎁 **Infinite Item Retrieval**: Click items in Journey tab to retrieve them (1x or 64x with Shift)

---

## 👤 Author

**Aryangpt007**
- GitHub: [@Aryangpt007](https://github.com/Aryangpt007)

## 🙏 Acknowledgments

- NeoForge team for the excellent modding framework
- FabricMC team for the Fabric loader and API
- Minecraft modding community for documentation and support
