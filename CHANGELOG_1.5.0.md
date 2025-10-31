# Journey Mode v1.5.0 - Multi-Loader Architecture

**Release Date:** October 31, 2025

## 🏗️ Major Architectural Changes - Phase 2 Complete

Journey Mode has been completely restructured with a **multi-loader architecture**, setting the foundation for future Fabric and Forge ports!

### Architecture Highlights
- ✅ **90/10 Split**: 90% of code is now platform-agnostic, 10% loader-specific
- ✅ **Common Module**: Pure Java code with zero loader dependencies
- ✅ **Platform Abstraction**: Clean separation between game logic and platform APIs
- ✅ **Service Loader Pattern**: Automatic platform detection at runtime
- ✅ **Future-Proof**: Ready for Fabric and Forge ports in Phase 3

### Project Structure
```
journey-mode/
├── common/                    # Platform-agnostic code (90%)
│   └── Core logic: JourneyData, ConfigHandler, ThresholdCalculator, UnlockLogic
├── neoforge-1.21.1/          # NeoForge-specific wrappers (10%)
│   └── Platform implementation, GUI, networking, events
```

## 🐛 Bug Fixes

### Fixed: Deposit Slot Visibility in Journey Tab
- **Problem**: Deposit slot was visible and clickable in the Journey tab
- **Solution**: Created `ConditionalSlot` class with `isActive()` override
- **Result**: Slot is now properly hidden and disabled when Journey tab is active
- **Impact**: Prevents accidental item placement in wrong tab

### Fixed: AttachmentType Serialization Error
- **Problem**: `AttachmentType.Builder$1.buildException` error on player data load
- **Solution**: Rewrote Codec using `Codec.STRING.xmap()` for proper JSON serialization
- **Result**: Player data now saves and loads correctly without errors
- **Impact**: No more crashes or data loss on player login

## 🎯 Benefits for Users

- **Same Features**: All functionality from v1.4.0 is preserved
- **Better Stability**: Fixed critical serialization and UI bugs
- **Future Updates**: Easier to add new features and support more loaders
- **Cleaner Code**: More maintainable and reliable

## 🔧 Technical Details

### Common Module Components
- `JourneyData.java` - Pure Java data model with Gson serialization
- `ConfigHandler.java` - Platform-agnostic config management
- `ThresholdCalculator.java` - Recipe depth calculation logic
- `UnlockLogic.java` - Business logic for deposits and unlocks
- `PlatformHelper.java` - Platform abstraction interface

### NeoForge Platform Components
- `NeoForgeDataHandler.java` - AttachmentType wrapper with fixed Codec
- `NeoForgePlatformImpl.java` - Platform implementation
- `ConditionalSlot` - Tab-aware slot with enable/disable logic

## 📦 Installation

### Requirements
- Minecraft 1.21.1
- NeoForge 21.1.72 or later
- Java 21 or later

### Steps
1. Download `journeymode-1.5.0.jar` from this release
2. Place in your `.minecraft/mods` folder
3. Launch Minecraft with NeoForge 1.21.1

## ⬆️ Upgrading from v1.4.0

- ✅ **Drop-in replacement** - No changes needed
- ✅ **Config files** - Fully compatible
- ✅ **Player data** - Automatically migrates
- ✅ **All features** - Work exactly as before

## 🚀 What's Next?

**Phase 3**: Multi-version support (Fabric and Forge ports)
- Fabric 1.21.1, 1.20.1, 1.19.2, 1.16.5
- Forge 1.20.1, 1.19.2, 1.16.5, 1.12.2
- NeoForge 1.20.1

The new architecture makes these ports much easier to implement!

## 📝 Full Changelog

### Added
- Multi-loader architecture with common + platform modules
- `ConditionalSlot` class for tab-aware slot control
- Service loader pattern for platform detection
- Proper Gson-based serialization in common module

### Fixed
- Deposit slot visibility in Journey tab (now properly hidden)
- AttachmentType Codec serialization errors
- Item slot interaction in wrong tab

### Changed
- Project structure reorganized into multi-module Gradle project
- Core logic moved to platform-agnostic common module
- NeoForge-specific code isolated to platform module
- Codec implementation rewritten for reliability

### Technical
- Build output: `neoforge-1.21.1/build/libs/journeymode-1.5.0.jar`
- Common module uses only Java + Gson dependencies
- Platform detection via `META-INF/services/`
- Cleaner separation of concerns

---

**Questions or issues?** Report them on the [GitHub Issues](https://github.com/Aryangpt007/Journey-Mode/issues) page!
