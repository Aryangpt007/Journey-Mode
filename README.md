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

## 🔮 Planned Features

- Configurable unlock threshold
- Custom GUI textures
- Item categories/filtering in Journey tab
- Search functionality
- Export/import of unlocked items
- Statistics tracking

---

## 📋 Changelog

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
