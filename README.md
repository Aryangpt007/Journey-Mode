# Journey Mode

A Minecraft mod that allows you to unlock unlimited access to items after collecting enough of them. Available for **both Fabric and NeoForge** on **Minecraft 1.21 - 1.21.10**.

[![NeoForge](https://img.shields.io/badge/NeoForge-1.21.x-orange.svg)](https://neoforged.net/)
[![Fabric](https://img.shields.io/badge/Fabric-1.21.x-blue.svg)](https://fabricmc.net/)
[![License](https://img.shields.io/github/license/Aryangpt007/Journey-Mode)](LICENSE)
[![Downloads](https://img.shields.io/github/downloads/Aryangpt007/Journey-Mode/total)](https://github.com/Aryangpt007/Journey-Mode/releases)

## 🎮 What is Journey Mode?

Journey Mode transforms your Minecraft experience by allowing you to **unlock items permanently** after collecting enough of them. Once unlocked, you can retrieve unlimited copies of that item whenever you need them!

**Perfect for:**
- 🏗️ **Builders** - Never run out of building materials
- ⚗️ **Modpack Players** - Streamline repetitive crafting
- 🎯 **Completionists** - Track your item collection progress
- 🎮 **Casual Players** - Reduce grinding after initial collection

## ✨ Features

### Core Gameplay
- **Smart Unlock System**: Collect items to unlock infinite access
  - Dynamic thresholds based on item complexity
  - Tools & armor: Only 1 required
  - Raw materials: Full stack (64)
  - Crafted items: Scaled by recipe depth
- **Dual-Tab GUI**: Clean interface with Deposit and Journey tabs
- **Search & Filter**: Quickly find unlocked items in Journey tab
- **Smart Sorting**: Items sorted by unlock time (newest first)
- **Progress Tracking**: See exactly how many more items you need

### Player Control
- **Toggle System**: Enable/disable Journey Mode per player
  - `/journeymode on` - Enable Journey Mode
  - `/journeymode off` - Disable Journey Mode
  - `/journeymode status` - Check current status
- **Keybind Support**: Press `J` to open the GUI (customizable)
- **Persistent Data**: Your progress saves across sessions

### Configuration
- **Item Blacklist**: Prevent specific items from being unlockable
- **Custom Thresholds**: Override unlock requirements per item
- **Hot-Reload**: Config changes apply without restart
- **JSON Format**: Easy to read and edit

### Multi-Loader Support
- ✅ **NeoForge 1.21.x** - Full support for 1.21 through 1.21.10
- ✅ **Fabric 1.21.x** - Full support for 1.21 through 1.21.10
- 🔄 **90% Shared Code** - Identical features and behavior across loaders

## 📦 How It Works

1. **Deposit Items**: Open the Journey Mode menu (press `J`) and place items in the deposit slot
2. **Track Progress**: Each item you deposit counts toward the unlock threshold (30 items)
3. **Unlock Items**: Once you've deposited enough of an item type, it becomes unlocked
4. **Infinite Retrieval**: Switch to the Journey tab and click any unlocked item to retrieve it
   - Left-click: Get 1 item
   - Shift + Left-click: Get 64 items

## 🚀 Installation

### Requirements

#### For NeoForge
- **Minecraft**: 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, or 1.21.10
- **NeoForge**: 21.1.72 or higher
- **Java**: 21 or higher

#### For Fabric
- **Minecraft**: 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, or 1.21.10
- **Fabric Loader**: 0.16.5 or higher
- **Fabric API**: 0.105.0+1.21.1 or higher (required dependency)
- **Java**: 21 or higher

### Download

**Choose your mod loader:**
- 🟠 [**NeoForge Version**](https://github.com/Aryangpt007/Journey-Mode/releases/latest) - `journeymode-1.6.0.jar`
- 🔵 [**Fabric Version**](https://github.com/Aryangpt007/Journey-Mode/releases/tag/fabric-v1.0.0) - `journeymode_fab-1.0.0.jar`

**Also available on:**
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/journey-mode)
- [Modrinth](https://modrinth.com/mod/journey-mode)

### Installation Steps

1. Download the appropriate JAR for your mod loader
2. Place it in your `.minecraft/mods` folder
3. **(Fabric only)** Download and install [Fabric API](https://modrinth.com/mod/fabric-api)
4. Launch Minecraft with the correct profile
5. Press `J` in-game to open Journey Mode!

## 📖 How to Use

### Getting Started

1. **Enable Journey Mode** (default: enabled)
   ```
   /journeymode on
   ```

2. **Open the GUI** - Press `J` (or your custom keybind)

3. **Deposit Items**
   - Switch to the **Deposit** tab
   - Place items in the deposit slot
   - See the required threshold and your progress
   - Click **Submit** to deposit
   - Repeat until unlocked!

4. **Retrieve Items**
   - Switch to the **Journey** tab
   - See all your unlocked items
   - **Left-click**: Get 1 item
   - **Shift + Left-click**: Get 64 items
   - Use the search box to filter items

### Understanding Thresholds

Journey Mode uses **smart thresholds** based on item complexity:

| Item Type | Example | Threshold |
|-----------|---------|-----------|
| **Tools & Armor** | Diamond Pickaxe | 1 item |
| **Complex Crafted** | Redstone Comparator | 1 item |
| **Raw Materials** | Iron Ore, Logs | 64 items (full stack) |
| **Crafted (Depth 1)** | Iron Ingot, Planks | 32 items (50%) |
| **Crafted (Depth 2)** | Iron Block | 16 items (25%) |
| **Non-Stackable** | Bucket, Shears | 1 item |

*Thresholds can be customized in config for individual items!*

### Commands

```bash
/journeymode on       # Enable Journey Mode for yourself
/journeymode off      # Disable Journey Mode for yourself
/journeymode status   # Check if Journey Mode is enabled
/journeymode          # Same as status
```

## ⚙️ Configuration

Config files are located in `config/journeymode/` (or `config/Journey Mode/` for older versions)

### blacklist.json

Prevent specific items from being unlockable:

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

### custom_thresholds.json

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

All config changes **hot-reload** - no server restart needed!

## 🗺️ Roadmap

### Current Status
- ✅ **Phase 1**: NeoForge 1.21.1 Core Features (Complete)
- ✅ **Phase 2**: Multi-Loader Architecture (Complete)
- ✅ **Phase 3**: NeoForge 1.21.x Full Family Support (Complete)
- ✅ **Phase 4**: Fabric 1.21.x Implementation (Complete)

### Future Plans

#### Phase 5: Multi-Version Support
- 🔄 **Forge 1.20.1, 1.19.2, 1.16.5** - Backport to popular Forge versions
- 🔄 **Fabric 1.20.1, 1.19.2, 1.16.5** - Backport to popular Fabric versions
- 🔄 **NeoForge 1.20.1** - Backport to NeoForge's first version
- 🔄 **Forge 1.12.2** - Legacy support for long-term packs

#### Phase 6: Polish & Features
- ✨ Custom GUI textures (replace placeholder rendering)
- ✨ Export/import unlocked items
- ✨ Statistics tracking dashboard
- ✨ Sound effects for unlock events
- ✨ Particle effects on deposit/unlock
- ✨ Achievement system for milestones

#### Phase 7: Multiplayer Features
- 👥 Team/shared unlocks
- 🔄 Server-side config sync
- 📊 Leaderboards
- 🎁 Trade unlocked items

#### Phase 8: API & Integration
- 🔌 Public API for other mods
- 🤝 EMI/JEI integration
- 🛠️ Better modpack maker tools

## 📊 Version Support Matrix

| Minecraft Version | Forge | Fabric | NeoForge | Status |
|-------------------|-------|--------|----------|---------|
| **1.12.2** | ✅ Planned | ❌ N/A | ❌ N/A | Legacy Support |
| **1.16.5** | ✅ Planned | ✅ Planned | ❌ N/A | Wide Adoption |
| **1.19.2** | ✅ Planned | ✅ Planned | ❌ N/A | Stable & Popular |
| **1.20.1** | ✅ Planned | ✅ Planned | ✅ Planned | Recent Stable |
| **1.21.x** | ⚠️ Limited | ✅ **Current** | ✅ **Current** | Modern |

**Legend**:
- ✅ Planned/Supported
- ⚠️ Limited Support (Forge ending for 1.21+)
- ❌ Not Available

**Important Notes**:
- **Forge 1.21.1+**: Forge support is ending for Minecraft 1.21+. While technically possible, NeoForge is the recommended loader for 1.21.1+ versions.
- **NeoForge**: Only exists for Minecraft 1.20.1 and newer (forked from Forge in 2023)
- **Fabric**: Available and actively supported for all listed versions

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

### Project Structure

```
journey-mode/
├── common/                    # 90% - Shared code
│   ├── core/                 # JourneyData model
│   ├── logic/                # ConfigHandler, Calculators
│   └── platform/             # PlatformHelper interface
├── neoforge-1.21.1/          # 10% - NeoForge wrappers
└── fabric-1.21.x/            # 10% - Fabric wrappers
```

## 🐛 Known Issues

- Data persistence on player death/respawn needs enhancement
- No configuration file yet for customizing the unlock threshold
- GUI texture uses placeholder rendering (no custom texture PNG)

## 🤝 Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📋 Changelog

### Fabric v1.0.0 (October 31, 2025)
**First full Fabric release! 🎉**

#### Features
- ✅ Full menu and screen system with deposit and retrieval tabs
- ✅ Complete networking (4 packets) using Fabric API
- ✅ Player data persistence with NBT storage
- ✅ Command system (`/journeymode on/off/status`)
- ✅ Keybind support (J key)
- ✅ Platform abstraction implementation
- ✅ Common module integration
- ✅ Search and filter functionality
- ✅ Smart item sorting by unlock time
- ✅ Dynamic threshold system
- ✅ Configuration support (blacklist, custom thresholds)

---

### NeoForge v1.6.0 (October 31, 2025)
**Extended 1.21.x family support**

#### Changes
- ✅ Extended Minecraft version support to entire 1.21.x family
- ✅ Now supports: 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10
- ✅ Updated version range from `[1.21.1,1.22)` to `[1.21,1.22)`

---

### NeoForge v1.5.0 (October 31, 2025)
**Multi-Loader Architecture**

#### Major Changes
- 🏗️ 90/10 Multi-Loader Architecture
- Created `common` module (platform-agnostic)
- Created `neoforge-1.21.1` module (loader-specific)
- Service Loader pattern for platform detection

#### Bug Fixes
- 🔧 Fixed deposit slot visibility
- 🔧 Fixed AttachmentType serialization
- 🔧 Player data saves/loads correctly

---

### NeoForge v1.4.0 (October 31, 2025)
**Phase 1 Complete**

#### Features
- ✅ Journey Mode toggle (`/journeymode on/off/status`)
- ⚙️ JSON configuration system
  - `blacklist.json` - Item blacklist
  - `custom_thresholds.json` - Custom thresholds
  - Hot-reload support

---

### Version 1.3.4 - v1.0.0

See [full version history](https://github.com/Aryangpt007/Journey-Mode/releases)

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🔗 Links

- **GitHub**: [Aryangpt007/Journey-Mode](https://github.com/Aryangpt007/Journey-Mode)
- **CurseForge**: [Journey Mode](https://www.curseforge.com/minecraft/mc-mods/journey-mode)
- **Modrinth**: [Journey Mode](https://modrinth.com/mod/journey-mode)
- **Issues**: [Report a Bug](https://github.com/Aryangpt007/Journey-Mode/issues)

## 👤 Author

**Aryangpt007** - [@Aryangpt007](https://github.com/Aryangpt007)

## 🙏 Acknowledgments

- NeoForge team for the excellent modding framework
- Fabric team for the modding API
- Minecraft modding community for documentation and support

---

**Made with ❤️ for the Minecraft community**
