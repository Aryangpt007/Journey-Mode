# Journey Mode — Project Reference

This document is the source of truth for architecture and process. It replaces the earlier `SOLE_TRUTH.md` draft (discarded per maintainer instruction, 2026-07-25) — content below has been re-verified against the actual codebase rather than carried over blind. Companion tracker: [JOURNEY_MODE_CHECKLIST.md](JOURNEY_MODE_CHECKLIST.md).

**Current shipped version: 1.7.1** (all 9 environments, confirmed in each `gradle.properties`).

---

## 1. Project Overview & Philosophy

Journey Mode brings Terraria's Journey Mode "research and duplicate" loop to Minecraft: players deposit (sacrifice) items until a threshold is met, permanently unlocking infinite withdrawal of that item.

**Core principle — not an automation mod.** No blocks, no interfaces, no pipes. It exists purely to remove late-game grind, not to automate collection. Any feature proposal that introduces an automated input/output block or interface is out of scope by design, not by oversight.

---

## 2. Environment Architecture — Unified Directory Strategy (since 1.7.0)

Nine fully autonomous Gradle projects live side by side in one repo, one branch, no shared `settings.gradle`:

```
Fabric_1_16_5   Fabric_1_19_2   Fabric_1_20_1   Fabric_1_21_1
Forge_1_12_2    Forge_1_16_5    Forge_1_19_2    Forge_1_20_1
NeoForge_1_21_1
```

**Non-negotiable project rule: every feature and every bugfix ships to all 9 environments.** There is no "modern-only" or "backport later" tier — that was a recommendation in the old handover doc and has been overridden. If a feature depends on an API that doesn't exist on an older version (e.g. vanilla Tags don't exist pre-1.13), the older version gets a documented equivalent (e.g. OreDictionary mapping on 1.12.2), not an exemption.

Code is duplicated across the 9 folders by necessity (isolated Gradle toolchains), but logic is kept structurally parallel to ease porting. A `check-parity.ps1` script (planned, see tracker) is meant to catch silent divergence.

---

## 3. Core Mechanics

1. **The Dictionary** — `Set<String>` of unlocked item keys.
2. **The Counter** — `Map<String, Integer>` of partial deposit progress per key.
3. **The Calculator** — `RecipeDepthCalculator` decides thresholds from crafting depth.
4. **The UI** — client `Screen` (`JourneyModeScreen`) with a deposit slot + searchable unlocked-item grid.

---

## 4. Data Storage & Cross-Loader Nuances

### Forge 1.12.2 – 1.20.1 (Capabilities)
`IJourneyData` / `JourneyDataAttachment` / `JourneyDataCapabilityProvider` implement `ICapabilitySerializable<NBTTagCompound>` (or `CompoundTag` in later Forge), attached via `AttachCapabilitiesEvent<Entity>`. 1.12.2 hand-writes NBT; later Forge versions lean on `Codec`.

### NeoForge 1.21.1 (Data Attachments)
Data is registered to the entity type and read via `player.getData(JOURNEY_DATA)` — no capability boilerplate.

### Fabric 1.16.5 – 1.21.1
No native capability system. `GlobalDataHandler` is a static manager holding `Map<UUID, JourneyDataAttachment> serverPlayersData` (server) and a single `clientPlayerData` (client, synced via packets). Loaded from a JSON file in the world directory on join, or via Mixin into `PlayerEntity`.

### Hard invariant: old JSON files must never become invalid, unreadable, or lossy.
Every schema change to `journeymode_unlocks.json` requires:
- a `"schema_version"` bump (start at `2` for the first change made after this document),
- a migration path that loads every prior schema version without data loss,
- a unit test that loads a fixture of the previous schema and asserts the migrated result is complete.

This is a hard release gate, not a nice-to-have — a corrupted or downgraded profile is a permanent loss of a player's unlock progress, which is the entire point of the mod.

---

## 5. Sub-Type Serialization (Hybrid Key System, since 1.7.1)

Format: `base_item_id|nbt_or_components_string`, e.g. `minecraft:potion|{Potion:"minecraft:jump"}`.

`getNormalizedStack()` strips everything except a whitelisted set of meaningful keys, so durability/names/lore never fragment the key space:

- **Pre-1.20.5 (NBT):** keeps `Potion`, `Enchantments`, `StoredEnchantments`; discards `Damage`, `display`, custom tags.
- **1.20.5+ (Data Components, 1.21.1):** copies `DataComponents.POTION_CONTENTS`, `STORED_ENCHANTMENTS`, `INSTRUMENT`, `SUSPICIOUS_STEW_EFFECTS` onto a fresh stack.

Any future `NormalizationRule` extension point (see tracker §3) must never be allowed to whitelist `BLOCK_ENTITY_DATA` / `CONTAINER` — doing so reopens the shulker-box duplication vector this system was built to close.

---

## 6. Recipe Depth Calculator — Threshold Engine

`RecipeDepthCalculator.calculateThreshold(Item)` (e.g. `Forge_1_20_1/.../data/RecipeDepthCalculator.java:41`):

```java
int stackSize = item.getMaxStackSize();
if (stackSize == 1) return 1;
int depth = getRecipeDepth(item);
return switch (depth) {
    case 0 -> stackSize;
    case 1 -> Math.max(1, stackSize / 2);
    case 2 -> Math.max(1, stackSize / 4);
    default -> 1;
};
```

- Depth 0 (no recipe): full stack size.
- Depth 1: 50% of stack size.
- Depth 2: 25% of stack size.
- Depth 3+: 1.
- Unstackable items (`getMaxStackSize() == 1`): always 1, regardless of depth.

**Depth resolution:** `getRecipeDepth` walks `RecipeManager` recipes producing the item, takes the MIN depth across all recipes that craft it (easiest path wins), memoizes in `depthCache`, and breaks cycles via a `calculating` guard set (an item that recurses into itself mid-resolution is treated as depth 0 to terminate).

**Known defect — see tracker "Confirmed Bugs":** `stackSize` is read live from `item.getMaxStackSize()`. Mods that alter max stack size at runtime (e.g. Bigger Stacks) inflate depth-0 thresholds to whatever the modified stack size is (observed: 9,999). The calculator must reference a stable baseline stack size, not whatever a third-party mod has mutated it to.

---

## 7. Deposit Flow

Client places item in deposit slot → `SubmitDepositPacket` (no item payload, server already owns slot 0 truth) → server executes on main thread → normalizes stack → `JourneyDataAttachment.depositItem()` adds count, checks threshold via calculator, unlocks + timestamps if crossed → clears slot → `SyncJourneyDataPacket` pushes full state back to client.

## 8. Fetching Flow

Client requests a hybrid key + count (`RequestItemPacket`) → server checks `isUnlocked(itemId)` (rejects + logs on failure — this is the anti-cheat boundary) → reconstructs `ItemStack` from the key (`CompoundTag` parse pre-1.21, `ItemStack.parse()`/Data Components on 1.21+) → adds to inventory, or drops on the ground if full.

---

## 9. Networking Per Environment

| Environment | System |
|---|---|
| Forge 1.12.2 | `SimpleNetworkWrapper` / `IMessage` / `IMessageHandler`, manual `ByteBuf` encoding |
| Forge 1.16.5–1.20.1 | `SimpleChannel`, `NetworkEvent.Context#enqueueWork()` for main-thread safety |
| NeoForge 1.21.1 | `CustomPacketPayload` + `StreamCodec`, `RegisterPayloadHandlersEvent` |
| Fabric 1.16.5–1.21.1 | `ClientPlayNetworking`/`ServerPlayNetworking`; 1.21+ converges on `PayloadTypeRegistry` + `StreamCodec`, same shape as NeoForge |

Any new packet type is 4 separate implementations, one per row above. Budget accordingly in every estimate.

---

## 10. Build, Versioning & Release

### Versioning policy (set 2026-07-25)
- Every change that produces a released jar build gets a **new version number** across all 9 `gradle.properties` — never re-release under a version already shipped.
- Bug-fix-only releases bump the patch digit (e.g. `1.7.1` → `1.7.2`).
- Feature-batch releases bump the minor digit and reset patch (e.g. `1.7.x` → `1.8.0`).
- The next feature batch (roadmap items in the tracker) ships as **1.8.0**.

### CI/CD
- `upload-all.ps1` iterates all 9 dirs, runs `./gradlew build`, stages jars, invokes upload scripts.
- `upload-modrinth.ps1` — Modrinth API, generic loader/version strings.
- `upload-curseforge.ps1` — CurseForge API, maintains a manual `gameVersionTypeID` mapping table per environment.
- Release process: bump `mod_version` in all 9 `gradle.properties`, set `CURSEFORGE_TOKEN`/`MODRINTH_TOKEN`, run `upload-all.ps1`.
- Planned addition: `check-parity.ps1` gate in `upload-all.ps1` (see tracker cross-cutting work).

---

## 11. Superseded Proposals

The old "GregTech Integration" proposal (bespoke `GregTechDepthCalculator`, GT-tier-specific thresholds) is **retired**. It's generalized and subsumed by the datapack threshold-pack system (tracker §3): a `data/journeymode/thresholds/*.json` layer lets any pack — GT included — ship its own threshold overrides without a bespoke calculator subclass.

---

*Amend this file in the same PR as any architectural change it describes. Feature-level task tracking, open decisions, and status live in [JOURNEY_MODE_CHECKLIST.md](JOURNEY_MODE_CHECKLIST.md), not here.*
