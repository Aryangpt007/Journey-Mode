# Journey Mode

A Minecraft mod for NeoForge 1.21.1 that allows you to unlock unlimited access to items after collecting enough of them.

## 🎮 Features

- **Item Collection Tracking**: Deposit items to track your progress toward unlocking them
- **Unlock Threshold**: Collect 30 of any item type to unlock unlimited access
- **Journey Tab**: Access all unlocked items infinitely through a special inventory tab
- **Persistent Storage**: Your unlocked items are saved with your player data
- **Easy Access**: Press `J` to open the Journey Mode menu

## 📦 How It Works

1. **Deposit Items**: Open the Journey Mode menu (press `J`) and place items in the deposit slot
2. **Track Progress**: Each item you deposit counts toward the unlock threshold (30 items)
3. **Unlock Items**: Once you've deposited 30 of an item type, it becomes unlocked
4. **Infinite Retrieval**: Switch to the Journey tab and click any unlocked item to retrieve it
   - Left-click: Get 1 item
   - Shift + Left-click: Get 64 items

## 🛠️ Installation

### Requirements
- Minecraft 1.21.1
- NeoForge 21.1.72 or later
- Java 21 or later

### Steps
1. Download the latest `journeymode-X.X.X.jar` from [Releases](https://github.com/Aryangpt007/Journey-Mode/releases)
2. Place the JAR file in your `.minecraft/mods` folder
3. Launch Minecraft with the NeoForge 1.21.1 profile

## 🔧 Development Setup

### Prerequisites
- JDK 21 or later (JDK 22 recommended)
- Git

### Building from Source

```powershell
# Clone the repository
git clone https://github.com/Aryangpt007/Journey-Mode.git
cd Journey-Mode

# Build the mod
.\gradlew.bat build

# The built JAR will be in build/libs/
```

### Running in Development

```powershell
# Run Minecraft client in development mode
.\gradlew.bat runClient

# Run dedicated server in development mode
.\gradlew.bat runServer
```

## 📝 Configuration

Currently, the unlock threshold is hardcoded to 30 items. Future versions may include a configuration file.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🐛 Known Issues

- Data persistence on player death/respawn needs enhancement
- No configuration file yet for customizing the unlock threshold
- GUI texture uses placeholder rendering (no custom texture PNG)

## �️ Development Roadmap

### Phase 1: Core Enhancement (Current - NeoForge 1.21.1)
**Objective**: Enhance the existing NeoForge implementation with essential features

#### Features
- ✅ Journey Mode Toggle System
  - Command: `/journeymode on|off`
  - Per-player capability to enable/disable Journey Mode
  - Prevents auto-access for all players
  
- ✅ Configuration System
  - Config file for threshold overrides
  - Item blacklist (prevent specific items from being unlockable)
  - Keep RecipeDepthCalculator as smart default
  - Allow modpack makers to customize per-item thresholds

- ✅ Enhanced Data Management
  - Improved death/respawn data persistence
  - Player data migration tools

#### Deliverables
- `journeymode-common.toml` configuration file
- `/journeymode` command implementation
- Config-based item blacklist
- Threshold override system

---

### Phase 2: Multi-Loader Architecture Setup
**Objective**: Establish shared codebase structure for Forge, Fabric, and NeoForge

#### Architecture
- Common module for shared game logic
- Loader-specific modules for platform APIs
- Gradle multi-project setup with subprojects
- Automated version management

#### Structure
```
journey-mode/
├── common/          # Shared game logic (90% of code)
├── neoforge/        # NeoForge-specific code
├── forge/           # Forge-specific code
├── fabric/          # Fabric-specific code
└── build.gradle     # Root build configuration
```

---

### Phase 3: Multi-Version Support (Forge)
**Objective**: Port to Forge for multiple Minecraft versions

#### Target Versions
- ⚠️ **Forge 1.12.2** - Legacy, uses old API patterns
- ✅ **Forge 1.16.5** - Stable, widely used
- ✅ **Forge 1.19.2** - Modern, good support
- ✅ **Forge 1.20.1** - Recent stable
- ⚠️ **Forge 1.21.1+** - Limited (Forge support ending, use NeoForge instead)

**Note**: Forge support is ending for 1.21+. NeoForge is the recommended loader for 1.21.1+

#### Implementation Strategy
- Start with 1.20.1 (closest to current NeoForge)
- Backport to 1.19.2
- Backport to 1.16.5
- Backport to 1.12.2 (requires significant API changes)

---

### Phase 4: Multi-Version Support (Fabric)
**Objective**: Port to Fabric for multiple Minecraft versions

#### Target Versions
- ✅ **Fabric 1.16.5** - Available and stable
- ✅ **Fabric 1.19.2** - Available and stable
- ✅ **Fabric 1.20.1** - Available and stable
- ✅ **Fabric 1.21.1+** - Available and actively supported

**Note**: All target Fabric versions are available and well-supported

#### Implementation Strategy
- Start with 1.21.1 (leverage existing logic)
- Backport to 1.20.1
- Backport to 1.19.2
- Backport to 1.16.5

---

### Phase 5: Multi-Version Support (NeoForge)
**Objective**: Port to NeoForge for additional versions

#### Target Versions
- ✅ **NeoForge 1.20.1** - Available (NeoForge exists from 1.20.1+)
- ✅ **NeoForge 1.21.1+** - Current implementation (already done)

**Note**: NeoForge only exists for Minecraft 1.20.1 and newer

#### Implementation Strategy
- Backport current 1.21.1 implementation to 1.20.1
- Minimal changes expected (NeoForge API is consistent)

---

### Phase 6: Polish & Release
**Objective**: Finalize and release all versions

#### Features
- ✅ Custom GUI textures (replace placeholder rendering)
- ✅ Export/import unlocked items
- ✅ Statistics tracking (items unlocked, items deposited, etc.)
- ✅ Sound effects for unlock events
- ✅ Particle effects on deposit/unlock

#### Platform Releases
- CurseForge for all loaders/versions
- Modrinth for all loaders/versions
- GitHub Releases with organized file structure

---

## 📊 Version Support Matrix

| Minecraft Version | Forge | Fabric | NeoForge | Status |
|-------------------|-------|--------|----------|---------|
| **1.12.2** | ✅ Planned | ❌ N/A | ❌ N/A | Legacy Support |
| **1.16.5** | ✅ Planned | ✅ Planned | ❌ N/A | Wide Adoption |
| **1.19.2** | ✅ Planned | ✅ Planned | ❌ N/A | Stable & Popular |
| **1.20.1** | ✅ Planned | ✅ Planned | ✅ Planned | Recent Stable |
| **1.21.1+** | ⚠️ Limited | ✅ Planned | ✅ **Current** | Modern (NeoForge recommended) |

**Legend**:
- ✅ Planned/Supported
- ⚠️ Limited Support (Forge ending for 1.21+)
- ❌ Not Available

**Important Notes**:
- **Forge 1.21.1+**: Forge support is ending for Minecraft 1.21+. While technically possible, NeoForge is the recommended loader for 1.21.1+ versions.
- **NeoForge**: Only exists for Minecraft 1.20.1 and newer (forked from Forge in 2023)
- **Fabric**: Available and actively supported for all listed versions
- All loaders are available for the planned versions (1.12.2 Fabric doesn't exist, but that's expected)

---

## 🔮 Future Features (Post Multi-Loader)

- Server-side configuration sync
- Team/shared unlocks for multiplayer
- Achievement system for milestones
- API for other mods to integrate

---

## 📋 Changelog

### Version 1.5.0 - Multi-Loader Architecture
**Release Date:** October 31, 2025

#### 🏗️ Major Architectural Changes - Phase 2 Complete
- ✅ **Multi-Loader Architecture Implementation**
  - Established 90/10 split: Common module (platform-agnostic) + Platform modules (loader-specific)
  - Created `common` module with pure Java code - zero loader dependencies
  - Migrated all core logic to common module (JourneyData, ConfigHandler, ThresholdCalculator, UnlockLogic)
  - Created platform abstraction layer with `PlatformHelper` interface
  - Implemented Service Loader pattern for automatic platform detection
  - Set up Gradle multi-project structure with proper dependency management

#### 📁 New Project Structure
```
journey-mode/
├── common/                    # 90% - Platform-agnostic code
│   └── src/main/java/com/aryangpt007/journeymode/
│       ├── core/             # JourneyData (pure Java data model)
│       ├── logic/            # ConfigHandler, ThresholdCalculator, UnlockLogic
│       └── platform/         # PlatformHelper interface, Platform service loader
├── neoforge-1.21.1/          # 10% - NeoForge-specific wrappers
│   └── src/main/java/com/aryangpt007/journeymode/
│       ├── platform/neoforge/ # NeoForge platform implementation
│       ├── menu/             # JourneyModeMenu (container)
│       ├── client/gui/       # JourneyModeScreen (rendering)
│       ├── network/packets/  # Network packet handlers
│       ├── events/           # Event handlers
│       └── commands/         # Command implementations
└── build.gradle              # Root multi-project configuration
```

#### 🔧 Technical Implementation
- **Common Module**:
  - `JourneyData.java`: Pure Java data model with JSON serialization (Gson)
  - `ConfigHandler.java`: Config management (JSON file I/O)
  - `ThresholdCalculator.java`: Recipe depth calculation logic
  - `UnlockLogic.java`: Business logic for deposits and unlocks
  - `PlatformHelper.java`: Platform abstraction interface
  - `Platform.java`: Service loader for runtime platform detection
  
- **NeoForge Platform Module**:
  - `NeoForgeDataHandler.java`: AttachmentType wrapper with Codec serialization
  - `NeoForgePlatformImpl.java`: Platform implementation (data access, config paths)
  - `NeoForgeConfigHandler.java`: Config initialization at mod startup
  - All existing GUI, networking, events, and commands (unchanged functionality)

#### 🐛 Bug Fixes
- 🔧 **Fixed Deposit Slot Visibility**: Slot no longer appears in Journey tab
  - Created `ConditionalSlot` class with `isActive()` override
  - Slot is properly disabled when Journey tab is active
  - Prevents both visual rendering and interaction (clicks, shift-clicks)
  - Tab switching now enables/disables slot dynamically
  
- 🔧 **Fixed AttachmentType Serialization Error**: Player data now saves/loads correctly
  - Fixed Codec implementation using `Codec.STRING.xmap()` for bidirectional transformation
  - Proper JSON string serialization/deserialization
  - No more `AttachmentType.Builder$1.buildException` errors on player load

#### 🎯 Benefits of New Architecture
- **Easier Multi-Loader Support**: Common code can now be reused for Fabric and Forge ports
- **Cleaner Codebase**: Clear separation between game logic and platform-specific code
- **Better Maintainability**: Bug fixes in common module automatically apply to all loaders
- **Future-Proof**: Adding new Minecraft versions requires only updating platform modules

#### 📦 Deliverables
- Multi-project Gradle build system
- Common module JAR (reusable across loaders)
- NeoForge 1.21.1 implementation JAR
- Foundation ready for Phase 3 (Fabric/Forge ports)

#### ⚙️ Migration Notes
- All existing features from v1.4.0 preserved
- No changes to user experience or gameplay
- Config files remain compatible
- Player data automatically migrates
- Build output: `neoforge-1.21.1/build/libs/journeymode-1.5.0.jar`

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

#### Technical Implementation
- `ConfigHandler.java`: JSON file handling (following ProjectE pattern)
- `JourneyDataAttachment`: Added `enabled` boolean field with Codec serialization
- `JourneyModeCommand`: Brigadier command with on/off/status subcommands
- `RecipeDepthCalculator`: Config override check before recipe calculation
- `JourneyModeMenu`: Blacklist validation in `processDeposit()`
- `OpenJourneyMenuPacket`: Toggle state check before opening GUI
- All features properly localized in `en_us.json`

#### Configuration Examples

**blacklist.json**:
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

**custom_thresholds.json**:
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

#### User Experience
- Commands show colored feedback messages
- Blacklisted items display clear error message
- Disabled state prevents GUI opening with explanation
- Config changes hot-reload without server restart
- Recipe-based thresholds remain default behavior

---

### Version 1.3.4
**Release Date:** October 30, 2025

#### Major Feature
- ⌨️ **Configurable Keybind**: Journey Mode menu key is now customizable!
  - Default key: `J` (unchanged)
  - Configurable in: Options → Controls → Key Binds → Journey Mode
  - Can rebind to any key you prefer
  - Appears in dedicated "Journey Mode" category in controls menu

#### Technical Implementation
- Added `KeyMapping` for proper Minecraft keybind integration
- Registered keybind in `RegisterKeyMappingsEvent`
- Replaced hardcoded `GLFW_KEY_J` with `KeyMapping.consumeClick()`
- Added localization for keybind name and category
- Keybind state is saved in Minecraft's options.txt

#### User Experience
- Navigate to Options → Controls → Key Binds
- Scroll to "Journey Mode" category
- Click on "Open Journey Mode Menu" binding
- Press your desired key to rebind
- Changes save automatically

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

#### Layout Changes
- GUI height: 180px → 204px (24 pixels taller)
- Inventory slots: y=110 (was y=98)
- Hotbar slots: y=168 (was y=156)
- Inventory label: imageHeight - 104 (was - 80)
- Search box: y=86 (unchanged, now with proper spacing)

#### Visual Improvements
- Proper spacing between search box and inventory label
- Inventory label no longer overlaps with slots
- Search box no longer overlaps with inventory slots
- Consistent spacing throughout the GUI

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

#### Technical Changes
- Inventory slots moved from y=84/142 to y=98/156
- Search box moved from y=72 to y=86
- Inventory label Y position updated from -94 to -80
- Added proper cleanup in screen `removed()` method

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

#### Technical Improvements
- Added unlock timestamp tracking to `JourneyDataAttachment`
  - Stores millisecond timestamp when each item is unlocked
  - Persistent across game sessions via Codec serialization
- Enhanced `SyncJourneyDataPacket` to sync timestamps
- Added `getUnlockedItemsSorted()` method for timestamp-based sorting
- Search box appears only in Journey tab (auto-hides in Deposit tab)

#### User Experience
- Search box positioned below item grid (bottom of Journey tab)
- Type to filter items instantly
- Scroll wheel works with filtered results
- Item tooltips work correctly with search/sort

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

#### Bug Fixes
- Fixed title overlapping with tabs
- Items no longer disappear when placed in deposit slot
- Clear visual feedback for all deposit states

#### User Experience
- Place item in slot → See requirements and progress → Click Submit → Item deposited
- Much clearer what's needed for each item type
- No more confusion about dynamic thresholds

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

#### Implementation Details
- Added `RecipeDepthCalculator` class for analyzing recipe complexity
- Caches recipe depths for performance
- Detects and breaks recipe cycles
- Finds minimum depth when multiple recipes exist
- Dynamic threshold calculation per item

#### UI Changes
- Updated deposit tab to show "Dynamic per item" threshold
- Improved unlock messages to show required count
- Better progress messages showing current/needed amounts

#### Examples
- Diamond Pickaxe (stack 1): 1 required ✨
- Iron Ore (raw, stack 64): 64 required 
- Iron Ingot (depth 1, stack 64): 32 required
- Iron Block (depth 2, stack 64): 16 required
- Redstone Comparator (depth 3+, stack 64): 1 required ✨

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

#### Technical Changes
- Added `SyncJourneyDataPacket` for client-server data sync
- Added `updateFromSync()` method to `JourneyDataAttachment`
- Enhanced `renderBg()` to draw slot backgrounds with borders
- Improved screen layout and text positioning
- Updated network packet registration

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

#### Technical Implementation
- NeoForge 1.21.1 compatibility (NeoForge 21.1.72)
- Custom `AttachmentType` for player data storage
- Custom `MenuType` and screen for GUI
- Network packet system for client-server communication
- Event system for player data cloning and menu opening

#### Known Limitations
- Unlock threshold is hardcoded to 30 items (no config file yet)
- Player data clone on death/respawn needs improvement
- GUI uses placeholder rendering (no custom texture file)
- No item filtering or search in Journey tab

---

## 👤 Author

**Aryangpt007**
- GitHub: [@Aryangpt007](https://github.com/Aryangpt007)

## 🙏 Acknowledgments

- NeoForge team for the excellent modding framework
- Minecraft modding community for documentation and support
