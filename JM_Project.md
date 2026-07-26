# Journey Mode — Project Reference

This document is the source of truth for architecture and process. It replaces the earlier `SOLE_TRUTH.md` draft (discarded per maintainer instruction, 2026-07-25) — content below is re-verified against the actual codebase, not carried over blind. Companion tracker: [JOURNEY_MODE_CHECKLIST.md](JOURNEY_MODE_CHECKLIST.md).

**Current shipped version: 1.8.0** — live on [GitHub Releases](https://github.com/Aryangpt007/Journey-Mode/releases/tag/v1.8.0), CurseForge, and Modrinth, across all 9 environments.

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

**Non-negotiable project rule: every feature and every bugfix ships to all 9 environments.** There is no "modern-only" or "backport later" tier. If a feature depends on an API that doesn't exist on an older version (e.g. vanilla Tags don't exist pre-1.13), the older version gets a documented equivalent (e.g. OreDictionary mapping on 1.12.2), not an exemption. This has now been proven out across the full 1.8.0 batch — every item below shipped identically to all 9.

Code is duplicated across the 9 folders by necessity (isolated Gradle toolchains), but logic is kept structurally parallel to ease porting. A `check-parity.ps1` script (still not built — see tracker cross-cutting work) would catch silent divergence; for 1.8.0, parity was maintained by porting from one reference environment (Forge 1.20.1) to the other 8 and compile-verifying each independently.

---

## 3. Core Mechanics

1. **The Dictionary** — `Set<String>` of unlocked item keys (personal, or a team's shared set — see §12).
2. **The Counter** — `Map<String, Integer>` of partial deposit progress per key (personal, or shared with a team).
3. **The Calculator** — `RecipeDepthCalculator` decides thresholds from crafting depth, rarity (§6), and config/datapack/dev-API overrides (§7).
4. **The UI** — client `Screen` (`JourneyModeScreen`) with three tabs: **Deposit** (deposit slot + Deposit-All button), **Journey** (searchable unlocked-item grid), **Stats** (catalog statistics, §10).

---

## 4. Data Storage & Cross-Loader Nuances

### Forge 1.12.2 – 1.20.1 (Capabilities)
`IJourneyData` / `JourneyDataAttachment` / `JourneyDataCapabilityProvider` implement `ICapabilitySerializable<NBTTagCompound>` (or `CompoundTag` in later Forge), attached via `AttachCapabilitiesEvent<Entity>`. 1.12.2 hand-writes NBT; later Forge versions lean on `Codec`.

### NeoForge 1.21.1 (Data Attachments)
Data is registered to the entity type and read via `player.getData(JOURNEY_DATA)` — no capability boilerplate.

### Fabric 1.16.5 – 1.21.1
No native capability system. `GlobalDataHandler` is a static manager holding `Map<UUID, JourneyDataAttachment> serverPlayersData` (server) and a single `clientPlayerData` (client, synced via packets). Loaded from a JSON file in the world directory on join, or via Mixin into `PlayerEntity`.

### Hard invariant: old JSON files must never become invalid, unreadable, or lossy.
`journeymode_unlocks.json`'s root is `{"schema_version": 2, "players": {uuid: playerData}}` (schema_version 2 shipped in 1.8.0; the legacy unversioned flat-map format migrates automatically on first load, in-memory, then persists the upgraded structure — no data loss, no manual step). Any future schema change must:
- bump `schema_version` again,
- add a migration branch (never rewrite the reader/writer shape in place),
- be covered by a fixture-based round-trip check.

All writes (personal data and team data, see §12) go through one synchronized, **atomic** writer per environment: write to a `.tmp` file, then move-replace over the real file. A crash or power loss mid-write leaves the previous, still-valid file in place rather than a half-written one.

---

## 5. Sub-Type Serialization (Hybrid Key System, since 1.7.1)

Format: `base_item_id|nbt_or_components_string`, e.g. `minecraft:potion|{Potion:"minecraft:jump"}`.

`getNormalizedStack()` strips everything except a whitelisted set of meaningful keys, so durability/names/lore never fragment the key space:

- **Pre-1.20.5 (NBT):** keeps `Potion`, `Enchantments`, `StoredEnchantments`; discards `Damage`, `display`, custom tags.
- **1.20.5+ (Data Components, 1.21.1):** copies `DataComponents.POTION_CONTENTS`, `STORED_ENCHANTMENTS`, `INSTRUMENT`, `SUSPICIOUS_STEW_EFFECTS` onto a fresh stack.

**Since 1.8.0:** third-party mods can extend this whitelist via `NormalizationRule` (see §7's dev API) — `getNormalizedStack()` consults every registered rule for additional keys/components to preserve. A hard denylist (`BLOCK_ENTITY_DATA`/`CONTAINER` on NBT versions, `DataComponents.CONTAINER`/`BLOCK_ENTITY_DATA`/etc. on 1.21+) is enforced inside the API itself — no rule can whitelist these, full stop, since doing so would reopen the shulker-box duplication vector this system exists to close.

---

## 6. Recipe Depth Calculator — Threshold Engine

`RecipeDepthCalculator.calculateThreshold(Item)`:

```java
Integer configOverride = ConfigHandler.getThresholdOverride(item); // §7 precedence chain
if (configOverride != null) return Math.max(1, configOverride);

int rawStackSize = item.getMaxStackSize();
if (rawStackSize == 1) return 1;
int stackSize = Math.min(rawStackSize, 64); // baseline clamp, see below
int depth = getRecipeDepth(item);
return switch (depth) {
    case 0 -> applyRarity(item, stackSize); // §6a
    case 1 -> Math.max(1, stackSize / 2);
    case 2 -> Math.max(1, stackSize / 4);
    default -> 1;
};
```

- Depth 0 (no recipe): full (rarity-scaled) stack size.
- Depth 1: 50% of stack size.
- Depth 2: 25% of stack size.
- Depth 3+: 1.
- Unstackable items (`getMaxStackSize() == 1`): always 1, regardless of depth.

**Depth resolution:** `getRecipeDepth` walks `RecipeManager` recipes producing the item, takes the MIN depth across all recipes that craft it (easiest path wins), memoizes in `depthCache`, and breaks cycles via a `calculating` guard set.

**Fixed in 1.8.0 — stack-size inflation bug:** `rawStackSize` used to feed directly into the depth-0 threshold with no ceiling. Stack-size-inflation mods (Bigger Stacks etc.) mutate `getMaxStackSize()` at runtime, which pushed thresholds as high as 9,999. Fixed by clamping to `min(rawStackSize, 64)` before any threshold math — the clamp is on the *math*, not on `rawStackSize` itself (the unstackable check above still uses the raw value, since that's just detecting `== 1`, unaffected by the bug).

**Fixed in 1.8.0 — cycle-cache nondeterminism:** if recipe A needs B and B needs A, resolving A would previously memoize B's depth (and A's) using a depth-0 stand-in for whichever item triggered the cycle guard — a value that depends on resolution order, not the graph's real structure. Fixed via a `cycleTainted` set: when the cycle guard fires, every item currently on the active `calculating` stack is tainted; a tainted item's result is returned for that call but deliberately **not** memoized, so it's recomputed fresh next time rather than trusted as stable.

### 6a. Rarity-Aware Thresholds (since 1.8.0)

Only affects depth-0 (recipe-less) items. `rarityDivisor(Rarity)`: COMMON → 1, UNCOMMON → 4, RARE → 16, EPIC → threshold forced to 1. A hardcoded override table corrects vanilla's own inconsistent rarity assignments for known scarce items (`nether_star`, `heart_of_the_sea`, `echo_shard` → RARE; `enchanted_golden_apple`, `dragon_egg`, `dragon_head` → EPIC; `sniffer_egg`, `wither_skeleton_skull` → RARE, where those items exist on the target version — 1.12.2's override table is trimmed to only items that exist as distinct registry entries pre-Flattening).

Implemented as **live code in the calculator**, not a static datapack, despite the original roadmap proposing a built-in datapack — a fixed datapack file can't know ahead of time which items resolve to depth 0 for an arbitrary modpack, so the rarity scaling has to run at the exact point the calculator already computes depth.

---

## 7. Integration & Customization

### Config-file rules (`blacklist.json`, `custom_thresholds.json`)
Both files accept three key forms, checked in this priority: **exact item id > item tag (`#c:ingots`) > regex/wildcard pattern** (`gtceu:.*_circuit`; a bare `*` glob is translated to `.*` when the key is otherwise glob-safe, else compiled as a raw regex). Patterns are compiled once at load and cached — never re-compiled per lookup.

Thresholds additionally check, in order after the three rule types above: **`default_override`** (a single config key, written by `/journeymode all`, applying a fallback to every item with no more specific rule) → **datapack thresholds** (below) → **dev-API `ThresholdProvider`s** (below) → the calculator itself.

`max_threshold_cap` (default 64, itself config-adjustable) bounds what `/journeymode threshold` and `/journeymode all` will accept, to stop a fat-fingered command from setting an absurd value.

**1.12.2 has no vanilla Tags system.** `#`-prefixed tag rules there map to OreDictionary entries via a small built-in category table (ingots/gems/dusts/nuggets/plates); unmapped tags are logged and ignored rather than silently no-opping.

### Datapack threshold packs
`DatapackThresholdLoader` reads `<namespace>/journeymode/thresholds/*.json` (any namespace, standard vanilla resource-reload convention) — exact item ids only in this first pass, no tag/regex support at the datapack layer (the config file already covers that). This is the generalized replacement for the old GregTech-specific proposal (§11) and **is not implemented on 1.12.2** — there's no genuine per-item runtime-droppable-JSON equivalent pre-1.13 data packs; the config-rule layer + dev API cover the same need there.

### Developer API (`api` package: `ThresholdProvider`, `NormalizationRule`, `JourneyModeAPI`)
Deliberately tiny and dependency-free (no imports outside `net.minecraft`/the package itself) so other mods can `compileOnly` against it cheaply. `ThresholdProvider.getThreshold(itemKey)` is queried after config/datapack rules, before the calculator. `NormalizationRule` contributes additional keys to the hybrid-key whitelist (see §5) — its denylist enforcement lives in `JourneyModeAPI`, not the caller, so a rule can never bypass it.

### Real-Time Config Sync
`ConfigSyncPacket` / `ConfigSyncHelper` push a snapshot of the resolved blacklist/threshold rules to each client on join and after `/journeymode reloadconfig` or any rule-writing command. The client applies the snapshot as an **outright replacement** of its in-memory rules, never a merge — in singleplayer this is a harmless no-op overwrite (client and integrated server share the same static state in one JVM); on a dedicated server it's the only way the client ever learns the server owner's actual rules. Client-side threshold/blacklist queries go through the exact same `ConfigHandler` methods used server-side, so there's nowhere else in the codebase that could read a stale local value.

---

## 8. Command Surface

All under `/journeymode`:

| Command | Notes |
|---|---|
| `on` / `off` / `reset` | Personal toggle + full progress wipe. No permission requirement. |
| `tooltips on` / `off` | Per-player tooltip display preference (§10). No permission requirement. |
| `team create/join/leave/info/kick/transfer <name\|player>` | §12. No permission requirement; kick/transfer are owner-only, enforced in command logic, not via permission level. |
| `reloadconfig` | Re-reads config files, re-pushes to all online clients. OP (level 2). |
| `all <count> confirm` / `all reset` | Global `default_override` fallback threshold. Requires the literal `confirm` argument. OP. |
| `threshold <item\|hand> <count>` / `threshold <item> remove` | Per-item threshold override; `hand` targets the OP's held item's exact hybrid key (e.g. a specific potion). OP. |
| `grant\|revoke <player> <item\|hand>` | Works on offline players (mutates their file entry by UUID through the same synchronized writer). `revoke` also zeroes that key's collected count, or a re-unlock would be instant. OP. |

---

## 9. Deposit & Fetch Flows

**Deposit** (`SubmitDepositPacket`, or `DepositAllPacket` for the bulk button): client places item in the deposit slot (no item payload sent — server already owns slot 0's truth) → server executes on main thread → blacklist check → normalizes stack → resolves team-or-personal target (§12) → `depositItem()` adds count, checks threshold, unlocks + timestamps if crossed → `SyncJourneyDataPacket` pushes state back to the client.

**Deposit All**: iterates main inventory (slots 9–35) by default; **shift-clicking the button** includes the hotbar (0–8) too. Armor/offhand are excluded by construction (not part of `Inventory.items`), never by a special-case check. Already-unlocked item types are skipped. Client-side confirmation is a lightweight "click again within 3 seconds" arm/confirm on the button itself, not a full modal dialog.

**Fetch** (`RequestItemPacket`): client sends a hybrid key + count → server checks `isUnlocked(key)` against the team-or-personal target (the anti-cheat boundary — rejects + logs on failure) → reconstructs the `ItemStack` from the key → adds to inventory, or drops on the ground if full.

---

## 10. Progress Tooltips & Catalog Statistics

**Tooltips** (`TooltipHandler`, client-only, zero new packets): reads the already-synced local data. Normalizes the hovered stack via the same `getNormalizedStack()`/key helpers before lookup, so potions/enchanted books show the right entry's progress. Shows "Unlocked" or "N/threshold researched" (only when collected count > 0, to avoid clutter). Gated on `data.isEnabled() && data.isShowTooltips()` — the latter is a **per-player field** on the data-attachment class (not a global config value; toggled via `/journeymode tooltips on|off`), because it's a personal display preference.

**Catalog Statistics** (`CatalogStatsCache`, new "Stats" tab): total unlocked/researchable, % complete, per-namespace breakdown, most-recently-unlocked item. `getTotalResearchable()`/`getPerNamespaceResearchable()` are cached and invalidated via a `rulesGeneration` counter on `ConfigHandler` (bumped whenever its rules are reassigned), rather than a direct call from `ConfigHandler` into the client-package cache — `ConfigHandler`'s reload path runs on the server too, and a common/server-reachable class referencing a client-only class directly risks `NoClassDefFoundError` on a dedicated server. A full registry scan on every GUI open is exactly the stutter 1.7.0's O(1) recipe indexing was built to avoid; this cache extends that discipline to the stats tab.

---

## 11. Shared Team Catalogs (since 1.8.0)

**Resolved design decisions:** both deposits and unlocks pool into one shared team counter/unlocked-set. Teams are scoped **per-world**, not global — a deliberate fork from personal data (see below). One team per player (hard rule — joining/creating fails if already in one). Leaving a team snapshots its current unlocks into the player's own personal unlocked set (not collected counts — future deposits resume as personal, independent of the team). Owner-only kick/transfer; the target must be online (no offline-name-caching complexity in this first pass).

**The architectural insight that kept this small:** the server decides once, at sync time, whether to send a player's personal progress or their team's shared progress — the client-side data instance is populated with whichever is authoritative, and every existing client-rendering path (tooltips, the Journey grid, the Stats tab) needed **zero changes**. Only a handful of server-side call sites needed a team-or-personal branch: building the sync payload, processing a deposit (single and bulk), the already-unlocked check, the shift-click-delete-in-Journey-tab check, and the security-critical fetch/unlock check.

**Storage — `TeamData` / `TeamDataHandler`:** personal data (`GlobalDataHandler`) lives at the game-instance/server-install root (`FMLPaths.GAMEDIR` or the loader's equivalent). Teams deliberately do **not** use that file — they live inside the **world save directory** (`server.getWorldPath(LevelResource.ROOT)` or the loader's equivalent) as `journeymode_teams.json`, its own `schema_version`-tagged, atomically-written file. Reason: GAMEDIR is shared across every local singleplayer world under one installation, so storing teams there would let "Team Alpha" leak between two unrelated singleplayer worlds — the per-world file avoids that entirely. The in-memory team cache is cleared and reloaded on server start, so a singleplayer client switching worlds never sees a stale previous world's teams.

`JourneyDataAttachment` gains one new persisted field, `teamId` (nullable, drives server-side routing only) and one client-display-only field, `teamDisplayName` (refreshed every sync, never persisted, backs the "Team: `<name>`" badge drawn above the Journey screen's tab row).

---

## 12. Visual Polish (since 1.8.0, scoped down)

Client-side action-bar message on newly-crossed unlock thresholds, driven by diffing the unlocked set on every sync — **no dedicated "newly_unlocked" packet field**, since the full unlocked set is already synced every time. A `hasSyncedOnce` guard keeps the very first sync after connecting silent (the client's data instance starts empty, so without the guard every already-unlocked item would "celebrate" on login). Batches multiple simultaneous unlocks (e.g. from Deposit All) into one message rather than spamming one per item.

**Deliberately does not include a sound effect.** An `UI_TOAST_CHALLENGE_COMPLETE` sound was implemented and then explicitly removed at the maintainer's request post-release — the message-only behavior is the intended final state. **Also deliberately excludes a full graphical toast with custom textures** — no art-asset pipeline was in scope for this (code-only) pass.

---

## 13. Networking Per Environment

| Environment | System |
|---|---|
| Forge 1.12.2 | `SimpleNetworkWrapper` / `IMessage` / `IMessageHandler`, manual `ByteBuf` encoding |
| Forge 1.16.5–1.20.1 | `SimpleChannel`, `NetworkEvent.Context#enqueueWork()` for main-thread safety |
| NeoForge 1.21.1 | `CustomPacketPayload` + `StreamCodec`, `RegisterPayloadHandlersEvent` |
| Fabric 1.16.5–1.20.1 | `ClientPlayNetworking`/`ServerPlayNetworking`, inline channel handlers (no separate packet classes — a deliberate style difference from Forge, not an oversight) |
| Fabric 1.21.1 | Converges on `PayloadTypeRegistry` + `StreamCodec`, same shape as NeoForge |

Any new packet type is 4 separate implementations, one per loader family. 1.8.0 added three: `ConfigSyncPacket`, `DepositAllPacket`, and an extended `SyncJourneyDataPacket` (now also carries `enabled`, `showTooltips`, and `teamDisplayName` — a gap existed pre-1.8.0 where the first two were never actually sent to the client at all, silently breaking client-side `/journeymode off` checks; fixed alongside the tooltip-toggle work).

---

## 14. Build, Versioning & Release

### Versioning policy
- Every change that produces a released jar build gets a **new version number** across all 9 `gradle.properties` — never re-release under a version already shipped.
- Bug-fix-only releases bump the patch digit (e.g. `1.8.0` → `1.8.1`).
- Feature-batch releases bump the minor digit and reset patch (e.g. `1.8.x` → `1.9.0`).
- Local test builds use a `-beta` suffix on the patch version (e.g. `1.7.5-beta`) and are never published — see `beta_builds/` below.

### Release artifacts & staging
- `release_<version>/` at the repo root stages the final, correctly-named jar for each of the 9 environments before upload — naming convention: `journeymode-<Loader>_<modVersion>_<gameVersion>.jar` (e.g. `journeymode-Forge_1.8.0_1.20.1.jar`).
- `beta_builds/<version>_<commit>/` archives pre-release local test builds, named for the worktree commit they were built from, kept for historical reference rather than deleted.

### CI/CD
- `upload-all.ps1` — **contains live CurseForge/Modrinth API tokens hardcoded in plaintext** (`.gitignore`d, never committed — confirmed not present in git history or on GitHub). Reads `$VERSION`/`$CHANGELOG`/`$targets` (one entry per environment: jar path, display name, CurseForge `gameVersionTypeID`s, Modrinth game-version/loader strings), verifies every jar exists, then uploads to both platforms in sequence with a 1-second rate-limit pause between calls.
- GitHub releases are published separately via `gh release create <tag> <jars...> --notes <changelog>` (not scripted — run manually/by-request per release).
- Release process for a new version: bump `mod_version` in all 9 `gradle.properties`, build all 9, stage into `release_<version>/` with the naming convention above, update `$VERSION`/`$CHANGELOG`/`$targets` in `upload-all.ps1`, run it, then `gh release create`.
- Planned addition: `check-parity.ps1` gate in `upload-all.ps1` (see tracker cross-cutting work) — not yet built.

---

## 15. Superseded Proposals

The old "GregTech Integration" proposal (bespoke `GregTechDepthCalculator`, GT-tier-specific thresholds) is **retired and generalized** — the datapack threshold-pack system (§7) lets any pack, GT included, ship its own threshold overrides without a bespoke calculator subclass. This is no longer a proposal; the generalized system shipped in 1.8.0.

---

## 16. Not Yet Implemented

- **E-menu unlock integration** (§12 in the tracker) — explicitly deferred out of 1.8.0 to a future release, per maintainer decision. Confirmed scope when it's picked up: a fetch strip *and* a small manual deposit slot inside the vanilla inventory ("E") screen, additive to the existing Journey ("J") menu, not a replacement.
- `check-parity.ps1` (see §2, §14).

---

*Amend this file in the same PR as any architectural change it describes. Feature-level task tracking, open decisions, and status live in [JOURNEY_MODE_CHECKLIST.md](JOURNEY_MODE_CHECKLIST.md), not here.*
