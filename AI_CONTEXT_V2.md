# AI Context V2 - Journey Mode Mod

**DO NOT COMMIT - AI assistance only**

---

## Quick Start

**Tech Stack**: MC 1.21.1 | NeoForge 21.1.72+ | Java 22 | Gradle 8.10.2  
**MODID**: `journeymode`  
**Build**: `$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"; .\gradlew build`  
**Current Version**: 1.5.0 ✅ Multi-Loader Architecture Implemented

---

## 🏗️ Architecture Overview - Multi-Loader (Phase 2 Complete)

### Project Structure (90/10 Split)
```
journey-mode/
├── common/                           # 90% - Platform-agnostic code
│   └── src/main/java/com/aryangpt007/journeymode/
│       ├── core/
│       │   └── JourneyData.java              # Pure Java data model (Gson)
│       ├── logic/
│       │   ├── ConfigHandler.java            # Config management
│       │   ├── ThresholdCalculator.java      # Recipe depth calculation
│       │   └── UnlockLogic.java              # Business logic
│       └── platform/
│           ├── PlatformHelper.java           # Platform abstraction interface
│           └── Platform.java                 # Service loader
│
├── neoforge-1.21.1/                 # 10% - NeoForge-specific wrappers
│   └── src/main/java/com/aryangpt007/journeymode/
│       ├── JourneyMode.java                  # Main mod class
│       ├── platform/neoforge/
│       │   ├── NeoForgeDataHandler.java      # AttachmentType wrapper
│       │   ├── NeoForgePlatformImpl.java     # Platform implementation
│       │   └── NeoForgeConfigHandler.java    # Config initialization
│       ├── menu/JourneyModeMenu.java         # Server container
│       ├── client/gui/JourneyModeScreen.java # GUI rendering
│       ├── network/packets/                  # 4 packets (Open, Request, Sync, Submit)
│       ├── events/JourneyModeEvents.java     # Event handlers
│       └── commands/JourneyModeCommand.java  # Commands
│
└── build.gradle                     # Root multi-project configuration
```

### Design Principles
- **Common Module**: Pure Java, zero loader dependencies, uses Gson for serialization
- **Platform Modules**: Thin wrappers around common code, loader-specific APIs only
- **Service Loader Pattern**: Automatic platform detection at runtime
- **90/10 Split**: 90% shared code in common, 10% platform-specific wrappers

---

## Core Concept

Players deposit items → reach threshold → unlock infinite access. Thresholds dynamically calculated based on recipe depth, overridable via JSON config.

---

## Key Files & Responsibilities

### Common Module (Platform-Agnostic)

#### JourneyData.java
- **Location**: `common/src/main/java/.../core/`
- **Purpose**: Pure Java data model with JSON serialization
- **Fields**: `collectedCounts` (Map<String, Integer>), `unlockedItems` (Set<String>), `unlockTimestamps` (Map<String, Long>)
- **Serialization**: Gson-based (toJsonString/fromJsonString)
- **Methods**: Business logic methods (no platform dependencies)

#### ConfigHandler.java
- **Location**: `common/src/main/java/.../logic/`
- **Pattern**: ProjectE-inspired JSON config
- **Files**: `blacklist.json`, `custom_thresholds.json`
- **Features**: Auto-generation with examples, platform-agnostic file I/O
- **Example**:
  ```json
  // blacklist.json
  {"blacklisted_items": ["minecraft:bedrock", "minecraft:barrier"]}
  
  // custom_thresholds.json
  {"thresholds": {"minecraft:diamond": 10, "minecraft:netherite_ingot": 5}}
  ```

#### ThresholdCalculator.java
- **Location**: `common/src/main/java/.../logic/`
- **Logic**: Config override check → recipe depth calculation → threshold
- **Thresholds**:
  - Stack=1: 1 item
  - Depth 0 (raw): 64
  - Depth 1: 32
  - Depth 2: 16
  - Depth 3+: 1
- **Features**: Cyclic recipe handling, result caching

#### UnlockLogic.java
- **Location**: `common/src/main/java/.../logic/`
- **Purpose**: Business logic for deposits, unlocks, retrievals
- **Methods**: `canDeposit()`, `processDeposit()`, `canRetrieve()`, `isUnlocked()`

#### PlatformHelper.java
- **Location**: `common/src/main/java/.../platform/`
- **Purpose**: Platform abstraction interface
- **Methods**: `getJourneyData()`, `saveJourneyData()`, `getConfigPath()`, `isJourneyModeEnabled()`, `setJourneyModeEnabled()`

---

### NeoForge Platform Module

#### NeoForgeDataHandler.java
- **Location**: `neoforge-1.21.1/src/main/java/.../platform/neoforge/`
- **Purpose**: Wraps JourneyData with AttachmentType
- **AttachmentType**: `JOURNEY_DATA` with Codec serialization
- **Codec**: `Codec.STRING.xmap()` for JSON string serialization/deserialization
- **Methods**: `getJourneyData()`, `saveJourneyData()`

#### NeoForgePlatformImpl.java
- **Location**: `neoforge-1.21.1/src/main/java/.../platform/neoforge/`
- **Purpose**: Implements PlatformHelper for NeoForge
- **Service Provider**: Registered via `META-INF/services/`
- **Features**: Delegates to NeoForgeDataHandler, provides platform-specific paths

#### JourneyModeScreen.java
- **Location**: `neoforge-1.21.1/src/main/java/.../client/gui/`
- **Dimensions**: 204×176px
- **Tabs**: Deposit (slot y=18, submit button) | Journey (3×9 grid, search y=86, scrollable)
- **Critical**: Override `removed()` to return deposit slot items to inventory
- **Layout**: Title y=-30, inventory y=110-164, hotbar y=168

#### JourneyModeMenu.java
- **Location**: `neoforge-1.21.1/src/main/java/.../menu/`
- **Slots**: 0=deposit (ConditionalSlot), 1-36=player inventory/hotbar
- **ConditionalSlot**: Custom slot with `isActive()` override to disable in Journey tab
- **Key Methods**: `processDeposit()` (uses UnlockLogic), `quickMoveStack()`, `syncDataToClient()`, `setDepositSlotEnabled()`

#### JourneyModeCommand.java
- **Location**: `neoforge-1.21.1/src/main/java/.../commands/`
- **Command**: `/journeymode on|off|status`
- **Storage**: Uses PlatformHelper methods
- **Default**: Enabled

---

## Standard Workflows

### Build & Test (Multi-Project)
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"
.\gradlew clean build        # Clean build (builds common + neoforge-1.21.1)
.\gradlew build              # Quick build
.\gradlew :common:build      # Build only common module
.\gradlew :neoforge-1.21.1:build  # Build only NeoForge module
```

**Output**: `neoforge-1.21.1/build/libs/journeymode-1.5.0.jar`

### Version Update
1. Edit `gradle.properties`: `mod_version=X.Y.Z`
2. Update `common/build.gradle` version (if needed)
3. Update `neoforge-1.21.1/build.gradle` version (if needed)
2. Update `README.md` changelog at top see already written changelog in README.md (must include "---" separator)
3. Build, test JAR

### Git Release
```powershell
git add -A
git commit -m "vX.Y.Z: Description"
git tag -a vX.Y.Z -m "Version X.Y.Z"
git push origin main --tags
```

### GitHub Release
```powershell
# Refresh PATH first
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
gh release create vX.Y.Z build\libs\journeymode-X.Y.Z.jar --title "Title" --notes "Notes"
```

### Upload to Platforms
```powershell
.\upload-all.ps1  # CurseForge + Modrinth (auto-reads version & changelog from README.md)
```

**Upload scripts** (gitignored, contain API tokens):
- Auto-detect version from `gradle.properties`
- Auto-extract changelog from `README.md` (regex: `### Version X.Y.Z[\r\n]+\*\*Release Date:\*\*[^\r\n]*[\r\n]+[\r\n]+([\s\S]*?)(?:[\r\n]+---|\z)`)
- Find JAR at `build\libs\journeymode-X.Y.Z.jar`
- **CurseForge**: Project 1375363, Game version 11779 (MC 1.21.1)
- **Modrinth**: Project OMRJLjO7

---

## NeoForge 1.21.1 API Patterns

```java
// MenuType registration
new MenuType<>((id, inv) -> new JourneyModeMenu(id, inv), FeatureFlags.VANILLA_SET)

// AttachmentType registration
AttachmentType.builder(() -> new JourneyDataAttachment())
    .serialize(JourneyDataAttachment.CODEC)
    .build()

// Packet registration
registrar.playToServer(TYPE, STREAM_CODEC, Handler::handle);

// KeyMapping (DO NOT use hardcoded GLFW checks!)
KeyMapping key = new KeyMapping("key.journeymode.open_menu", InputConstants.Type.KEYSYM, 
                                GLFW.GLFW_KEY_J, "key.categories.journeymode");
// Register in RegisterKeyMappingsEvent
// Check with key.consumeClick()
```

---

## Common Issues & Solutions (Already implemented)

| Issue | Solution |
|-------|----------|
| GUI overlapping | Update ALL Y positions when changing `imageHeight` |
| Items lost on close | Override `removed()` in Screen to return items |
| Data not syncing | Send `SyncJourneyDataPacket` after server changes |
| Config errors | Use JSON for maps/lists, not TOML |
| Shift-click crash | Implement `quickMoveStack()` properly |

---

## Multi-Loader Architecture V2 (Phase 2 - NEXT MAJOR STEP)

**Goal**: Support NeoForge, Forge, Fabric across MC 1.12.2-1.21.1 with minimal code duplication.

### Directory Structure

```
journey-mode/
├── common/                          # 90% of code - pure Java, no loader APIs
│   └── src/main/java/com/aryangpt007/journeymode/
│       ├── core/                    # Data structures
│       │   ├── JourneyData.java     # Unlocks, counts, timestamps
│       │   ├── ThresholdCalculator.java
│       │   └── ItemRegistry.java
│       ├── logic/                   # Game logic
│       │   ├── UnlockLogic.java
│       │   ├── ConfigHandler.java
│       │   └── DataSerializer.java
│       └── platform/                # Platform abstraction
│           └── PlatformHelper.java  # Interface for loader-specific operations
│
├── platform/                        # 10% of code - loader-specific wrappers
│   ├── neoforge-1.21.1/
│   │   └── src/main/java/.../
│   │       ├── JourneyModeNeoForge.java
│   │       ├── PlatformImpl.java    # Implements PlatformHelper
│   │       ├── data/                # AttachmentType wrapper
│   │       ├── network/             # NeoForge packets
│   │       └── gui/                 # MenuType, Screen
│   │
│   ├── forge-1.20.1/
│   │   └── src/main/java/.../
│   │       ├── JourneyModeForge.java
│   │       ├── PlatformImpl.java
│   │       ├── data/                # Capability wrapper
│   │       └── network/             # Forge SimpleChannel
│   │
│   └── fabric-1.21.1/
│       └── src/main/java/.../
│           ├── JourneyModeFabric.java
│           ├── PlatformImpl.java
│           ├── data/                # Cardinal Components wrapper
│           └── network/             # Fabric networking
│
└── versions/                        # Version-specific API differences
    ├── 1.12.2/                      # Pre-flattening, major changes
    ├── 1.16.5/
    ├── 1.19.2/
    ├── 1.20.1/
    └── 1.21.1/                      # Base implementation
```

### Platform Abstraction Pattern

**Core Principle**: Write game logic ONCE in `common/`, wrap with loader APIs in `platform/`.

#### Example: Platform Helper Interface

```java
// common/src/.../platform/PlatformHelper.java
public interface PlatformHelper {
    JourneyData getPlayerData(Player player);
    void savePlayerData(Player player, JourneyData data);
    void sendToClient(Player player, Object packet);
    void sendToServer(Object packet);
    void openJourneyGui(Player player);
    List<Recipe<?>> getAllRecipes(Level level);
    String getItemId(Item item);
    Item getItemById(String id);
}

// Service loader pattern - auto-detect at runtime
public class Platform {
    private static PlatformHelper INSTANCE = ServiceLoader.load(PlatformHelper.class).findFirst().orElseThrow();
    public static PlatformHelper get() { return INSTANCE; }
}
```

#### Usage in Common Code

```java
// common/src/.../logic/UnlockLogic.java
public class UnlockLogic {
    public static boolean processDeposit(Player player, ItemStack stack) {
        JourneyData data = Platform.get().getPlayerData(player);
        String itemId = Platform.get().getItemId(stack.getItem());
        int threshold = ThresholdCalculator.getThreshold(itemId, player.level());
        
        boolean unlocked = data.depositItem(itemId, stack.getCount(), threshold);
        Platform.get().savePlayerData(player, data);
        Platform.get().sendToClient(player, new SyncDataPacket(data));
        
        return unlocked;
    }
}
```

#### Platform Implementation Examples

**NeoForge** (AttachmentType):
```java
// platform/neoforge-1.21.1/src/.../data/JourneyDataAttachment.java
public class JourneyDataAttachment {
    private JourneyData data = new JourneyData(); // Common logic
    
    public static final Codec<JourneyDataAttachment> CODEC = 
        Codec.STRING.xmap(
            json -> fromJson(json),
            attachment -> toJson(attachment.data)
        );
}
```

**Forge** (Capability):
```java
// platform/forge-1.20.1/src/.../data/JourneyDataCapability.java
public class JourneyDataCapability implements ICapabilitySerializable<CompoundTag> {
    private JourneyData data = new JourneyData(); // Same common logic
    
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("data", new Gson().toJson(data.toJson()));
        return nbt;
    }
}
```

**Fabric** (Cardinal Components):
```java
// platform/fabric-1.21.1/src/.../data/JourneyDataComponent.java
public class JourneyDataComponent implements Component {
    private JourneyData data = new JourneyData(); // Same common logic
    
    @Override
    public void readFromNbt(NbtCompound tag) {
        this.data = JourneyData.fromJson(new Gson().fromJson(tag.getString("data"), JsonObject.class));
    }
}
```

### Gradle Multi-Project Setup

```gradle
// settings.gradle
rootProject.name = 'journey-mode'
include 'common'
include 'neoforge-1.21.1'
include 'neoforge-1.20.1'
include 'forge-1.20.1'
include 'forge-1.19.2'
include 'fabric-1.21.1'

// common/build.gradle
plugins { id 'java-library' }
dependencies {
    compileOnly 'com.google.code.gson:gson:2.10.1'  // Only pure Java deps
}

// neoforge-1.21.1/build.gradle
plugins { id 'net.neoforged.gradle.userdev' version '7.0.163' }
dependencies {
    implementation project(':common')  // Include common module
}
tasks.named('jar') {
    from(project(':common').sourceSets.main.output)  // Bundle common code
}
```

### Version-Specific Handling

**Use source sets for API differences:**

```gradle
// forge-1.16.5/build.gradle
sourceSets {
    main {
        java {
            srcDirs = ['src/main/java', '../versions/1.16.5/java']
        }
    }
}
```

**Example - Item Registration:**
```java
// versions/1.21.1/java/.../compat/ItemRegistration.java
public class ItemRegistration {
    public static void register(String id, Item item) {
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation("journeymode", id), item);
    }
}

// versions/1.16.5/java/.../compat/ItemRegistration.java
public class ItemRegistration {
    public static void register(String id, Item item) {
        item.setRegistryName(new ResourceLocation("journeymode", id));
        ForgeRegistries.ITEMS.register(item);
    }
}
```

### Key Principles

1. **90/10 Rule**: 90% code in `common/`, 10% in `platform/`
2. **No Loader APIs in Common**: Zero Forge/Fabric/NeoForge dependencies in `common/`
3. **Service Loader Pattern**: Auto-detect platform at runtime
4. **Version Overlays**: Use source sets to override only differences
5. **Shared Resources**: All assets in one location

### Implementation Order

1. ✅ **NeoForge 1.21.1** (Current - v1.4.0)
2. Create `common/` module (refactor existing code)
3. Implement `PlatformHelper` service loader
4. NeoForge 1.20.1 backport (test architecture)
5. Fabric 1.21.1 (new loader)
6. Forge 1.20.1 (similar to NeoForge)
7. Older versions (1.19.2, 1.16.5, 1.12.2)

### Backporting Notes

| Version Jump | Difficulty | Key Changes |
|--------------|-----------|-------------|
| 1.21.1 → 1.20.1 | Easy | Minimal API changes |
| 1.20.1 → 1.19.2 | Easy | Menu/Screen tweaks, packet updates |
| 1.19.2 → 1.16.5 | Medium | Component changes, registry differences |
| 1.16.5 → 1.12.2 | Hard | Pre-flattening, numeric item IDs, old capability system |

---

## Development Roadmap

### ✅ Phase 1 Complete (v1.4.0)
- Toggle system (`/journeymode on|off|status`)
- JSON config (blacklist, custom thresholds)
- Hot-reload support
- Recipe-based thresholds with overrides

### 🚧 Phase 2 (NEXT - Multi-Loader Architecture)
**Target**: Set up common module and platform abstraction

**Tasks**:
1. Create `common/` module with pure Java logic
2. Refactor existing NeoForge code into platform wrapper
3. Implement `PlatformHelper` interface and service loader
4. Set up Gradle multi-project structure
5. Test with NeoForge 1.20.1 backport
6. Implement Fabric 1.21.1
7. Gradual expansion to older versions

**Deliverables**:
- `common/` module with 90% of code
- `platform/neoforge-1.21.1/` wrapper
- `platform/fabric-1.21.1/` implementation
- Gradle multi-project setup
- Documentation for adding new platforms

### Phase 3 (Future)
- Enhanced features (stats, export/import)
- Custom GUI textures
- Admin tools
- Performance optimizations

---

## Version History

- **v1.0.0**: Initial release (basic deposit/unlock)
- **v1.1.0**: Dynamic thresholds via RecipeDepthCalculator
- **v1.2.0**: Submit button, item preview
- **v1.3.0**: Search, timestamp sorting
- **v1.3.4**: Configurable keybind
- **v1.4.0**: Toggle system, JSON config, blacklist ✅

---

## Important Notes

### User Preferences
- Windows PowerShell (pwsh.exe)
- JDK 22 at `C:\Program Files\Java\jdk-22`
- NeoForge 21.1.213 installed
- **Documentation**: Only README.md and AI_CONTEXT files (no extra files)

### Installation Requirements
- **Both client AND server** need the mod (GUI + data persistence)
- Won't work server-only or client-only

### Gitignore
- Upload scripts are gitignored (contain API tokens)
- `AI_CONTEXT.md` / `AI_CONTEXT_V2.md` are gitignored (local only)
- Never commit tokens/credentials

### Code Style
- Classes: PascalCase (`JourneyModeScreen`)
- Methods: camelCase (`depositItem`)
- Constants: UPPER_SNAKE_CASE (`UNLOCK_THRESHOLD`)
- Packets: Suffix with `Packet` (`SyncJourneyDataPacket`)
- JavaDoc on public APIs

---

## Quick Reference Commands

```powershell
# Build
$env:JAVA_HOME = "C:\Program Files\Java\jdk-22"
.\gradlew.bat clean build

# Version check
Get-Content gradle.properties | Select-String "mod_version"

# Git tag
git tag -a v1.x.x -m "Version 1.x.x"
git push origin main --tags

# Upload (after updating gradle.properties and README.md)
.\upload-all.ps1

# GitHub release
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
gh release create v1.x.x build\libs\journeymode-1.x.x.jar --title "..." --notes "..."
```

---

## Resources

- **GitHub**: https://github.com/Aryangpt007/Journey-Mode
- **CurseForge**: Project 1375363
- **Modrinth**: Project OMRJLjO7
- **NeoForge Docs**: https://docs.neoforged.net/
- **Author**: Aryangpt007

---

*Last Updated: October 31, 2025 (v1.4.0)*  
*Next AI: Read this fully before starting. Phase 2 (Multi-Loader) is the next major milestone!*
