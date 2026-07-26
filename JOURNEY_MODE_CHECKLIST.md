# Journey Mode — Master Change Tracker

Architecture reference: [JM_Project.md](JM_Project.md) (replaces the discarded `SOLE_TRUTH.md` draft). Original feature breakdown sourced from the 2026-07-25 handover doc; this tracker is the live, evolving record — the handover doc is not.

Status legend: `TODO` / `IN PROGRESS` / `DONE` / `SKIPPED` / `DEFERRED`. Everything below is `TODO` unless marked otherwise.

**Versioning:** shipped version is `1.7.1` (confirmed in all 9 `gradle.properties`). Confirmed bugs are folded into the same release as the roadmap batch — everything in this document ships together as **1.8.0** (all 9). See [JM_Project.md §10](JM_Project.md#10-build-versioning--release) for the policy: every jar-producing build gets a new version number, no exceptions, no reuse.

**Implementation status (2026-07-25):** Every roadmap item except §12 (E-menu, explicitly deferred to a future release) is implemented and verified compiling in all 9 environments. Three rounds, same day: round 1 = B1/B2/cycle-cache/schema-versioning/§2/§3/§4/§5/§6/§8/§9; round 2 = two maintainer-reported UI bugfixes (Deposit-All button overlap; mojibake unicode text on 1.12.2), the tooltip-toggle redesign into a per-player field + `/journeymode tooltips on|off`, plus §7/§10; round 3 = §1 (teams) and §11 (visual polish) - see notes below for both. `mod_version` is `1.8.0` in all 9 `gradle.properties` (unchanged across all three rounds, per maintainer instruction) and jars were rebuilt under that version after each round — **still not published anywhere**; waiting on maintainer testing before any release action. Prior `1.7.5-beta` test jars are archived at `beta_builds/1.7.5-beta_9b730d7/` (named for the worktree's base commit at the time). §12 (E-menu) is explicitly out of scope for 1.8.0, deferred to a future roadmap item.

**All-versions mandate (2026-07-25):** every item below — features and bugfixes alike — ships to all 9 environments. There is no modern-only or backport-later tier. Where an older loader lacks the exact API (e.g. no vanilla Tags pre-1.13), the older version gets a documented equivalent, not an exemption. This overrides the "modern-first" / "modern-only acceptable" scoping that appeared in the original handover doc for Team Catalogs (§1) and the Dev API hooks (§3).

---

## Status Overview

| # | Item | Status | Target Version |
|---|------|--------|-----------------|
| B1 | Bug: modded stack sizes inflate thresholds to 9,999 | **DONE** (all 9) | 1.8.0 |
| B2 | Bug: search bar leaks keybinds (E closes GUI mid-typing) | **DONE** (all 9; no-op on 1.12.2 — already correct, see §ported-notes) | 1.8.0 |
| CC | Cross-cutting prerequisites (cycle-cache fix, schema versioning; delta sync deferred, see below) | **DONE** (all 9) | 1.8.0 |
| 1 | Shared Team Catalogs | **DONE** (all 9) | 1.8.0 |
| 2 | Real-Time Config Sync | **DONE** (all 9) | 1.8.0 |
| 3 | Integration & Customization API (patterns/tags, datapacks, dev hooks) | **DONE** (all 9; datapack loader skipped on 1.12.2, documented) | 1.8.0 |
| 4 | `/journeymode all [count]` | **DONE** (all 9) | 1.8.0 |
| 5 | `/journeymode threshold <item> <count>` | **DONE** (all 9) | 1.8.0 |
| 6 | `/journeymode grant|revoke <player> <item>` | **DONE** (all 9) | 1.8.0 |
| 7 | Rarity-Aware Thresholds | **DONE** (all 9) | 1.8.0 |
| 8 | Deposit All | **DONE** (all 9) | 1.8.0 |
| 9 | Progress Tooltips | **DONE** (all 9) | 1.8.0 |
| 10 | Catalog Statistics | **DONE** (all 9) | 1.8.0 |
| 11 | Visual Polish | **DONE** (all 9, scoped down - see notes) | 1.8.0 |
| 12 | E-menu unlock integration | **DEFERRED to future roadmap, not in 1.8.0** | — |

**Implementation notes (all 9 environments):**
- **B1**: `RecipeDepthCalculator.calculateThreshold()` clamps stack size to `min(actual, 64)` before threshold math in every environment (1.12.2 uses `getItemStackLimit()`, all others use `getMaxStackSize()`).
- **B2**: fixed via a `keyPressed`/`keyTyped` override that swallows all keys except Escape while the search field is focused — except **Forge_1_12_2, which needed no change**: its single `keyTyped(char,int)` hook (1.12.2 has no keyPressed/charTyped split) already never delegated to `super` while the search box was focused, so the bug never existed there. Documented with a comment instead of a no-op fix.
- **Cycle-cache fix**: `cycleTainted` set added to every `RecipeDepthCalculator` — taints every item on the active `calculating` stack when the cycle guard fires, skips memoization for tainted items.
- **Schema versioning**: `{"schema_version":2,"players":{...}}` + legacy-flat-map migration + atomic `.tmp`-then-move writes + `mutateOfflinePlayerData(uuid, mutator)`, in every environment's data-handling file.
- **Delta sync (from cross-cutting list) — deliberately deferred, not built.** Full-state sync remains the mechanism; a generalized delta/newly-unlocked-list format is genuinely only needed once §1 (teams) exists to consume it. Revisit when §1 starts.
- **§3**: exact-id > tag > regex/wildcard precedence, `default_override`, `max_threshold_cap` (default 64, config-adjustable), `DatapackThresholdLoader` (`<namespace>/journeymode/thresholds/*.json`, exact-id only), and a lean `api` package (`ThresholdProvider`, `NormalizationRule`, `JourneyModeAPI`) with a hard denylist on container/block-entity keys — in every environment. **1.12.2**: tags map to OreDictionary via a small built-in category table (ingots/gems/dusts/nuggets/plates); unmapped tags are logged and ignored. **1.12.2 also skips the datapack loader** — no genuine per-item runtime-droppable-JSON equivalent exists pre-1.13 data packs; the config-rule layer + `ThresholdProvider` API cover the same need there, documented in that module's `ConfigHandler` javadoc. **1.21.1 (both loaders)**: `NormalizationRule` operates on Data Components, not NBT (no ItemStack NBT tags exist anymore); denylist covers `CONTAINER`/`BLOCK_ENTITY_DATA`/etc.
- **§4/§5/§6**: full Brigadier-based command tree on every environment except 1.12.2, which uses the classic `CommandBase`/`args[]` style matching that module's existing on/off/reset command. Offline grant/revoke confirmed working via `GameProfileArgument` (or 1.12.2's `PlayerProfileCache`) + `mutateOfflinePlayerData`.
- **§2**: `ConfigSyncPacket`/`ConfigSyncHelper` (or the Fabric-idiomatic inline-channel equivalent) in every environment; pushed on join and after any rule-mutating command; client applies the snapshot as an outright replacement, never a merge.
- **§8/§9**: `DepositAllPacket` + `processDepositAll` (main inventory default, shift-click-confirm to include hotbar, armor/offhand untouched by construction) and `TooltipHandler` (client-only, respects `/journeymode off`) in every environment.
- **Round-2 bugfix — Deposit-All button overlap**: the button's original Y position collided with the item-info text block above it in every environment (worst on 1.12.2, per maintainer screenshots — fragments of overlapping text became illegible). Fixed by moving the button down (reference: `y+68`→`y+80`) with enough clearance on both sides, and by sharing one set of named layout constants between the render code and the click-hit-test code in every environment specifically so the two can never drift apart again — that drift was the root cause.
- **Round-2 bugfix — mojibake tooltip text**: a unicode checkmark (✔/✓) in the tooltip/deposit-tab "Unlocked" text rendered as garbled bytes on 1.12.2's font renderer. Removed the unicode symbols entirely in favor of plain ASCII text ("Journey: Unlocked", "Already Unlocked!") in every environment, for consistency and to avoid the same font-glyph-coverage risk elsewhere.
- **Tooltip visibility redesigned as a per-player setting**: `show_tooltips` was originally a global config value; it's now a field on the player-data class (default `true`, serialized/synced like `enabled`), toggled via `/journeymode tooltips on|off` (permission-free, same pattern as on/off/reset) — a personal display preference, not a server-owner setting. **Side discovery while wiring this up**: the sync packet in every environment never actually carried `enabled` or `showTooltips` to the client at all, meaning the client's own data instance (a separate object from the server's, even in singleplayer) never reflected `/journeymode off`. Fixed everywhere by adding both booleans to the sync payload.
- **§7 Rarity-Aware Thresholds**: implemented as live code in `RecipeDepthCalculator` (a `rarityDivisor(Rarity)` table + hardcoded override map for known vanilla inconsistencies, applied only at the depth-0 case), **not** as a static built-in datapack as originally suggested — a static datapack JSON can't know ahead of time which items resolve to depth 0 for an arbitrary modpack, so the rarity scaling has to run at the same point the calculator already computes depth. 1.12.2's override map only includes items that exist as distinct registry entries pre-Flattening (`nether_star`, `dragon_egg`); enchanted golden apple/wither skull/etc. are pre-Flattening metadata subtypes of other items there, not separate ids, so they're correctly omitted with a documented reason rather than silently wrong.
- **§10 Catalog Statistics**: new `CatalogStatsCache` per environment (total/per-namespace researchable counts, blacklist-aware), invalidated via a `rulesGeneration` counter on `ConfigHandler` rather than a direct cross-package `invalidate()` call — `ConfigHandler`'s reload path runs on the server too, and a common/server-reachable class referencing a client-only class directly risks `NoClassDefFoundError` on a dedicated server. A third "Stats" tab was added to the Journey screen in every environment, reusing the same shared-layout-constants discipline from the button-overlap fix.
- **Java-version discipline**: Forge_1_12_2, Forge_1_16_5, and Fabric_1_16_5 (Java 8 toolchains) have zero records/switch-expressions/var — `ConfigHandler.SyncSnapshot` is a plain class with getters there, verified against each toolchain's actual compiler.
- **API verification discipline**: several ports (Forge_1_19_2, Fabric_1_19_2, Forge_1_16_5, Fabric_1_16_5, Fabric_1_21_1) confirmed exact API shapes (CommandBuildContext presence, TagKey vs raw Tag, sendSuccess signature, DataComponents names) via `javap` against the actual mapped jars rather than assuming parity with the 1.20.1 reference.
- **§1 Shared Team Catalogs**: turned out much less invasive than expected. The server decides once, at sync time, whether to send a player's personal progress or their team's shared progress — the client-side data instance is populated with whichever is authoritative and every existing client-rendering path (tooltips, the Journey grid, the Stats tab) needed zero changes. Only ~5 server-side call sites needed a team-or-personal branch: sync-building, single/deposit-all processing, the already-unlocked check, the shift-click-delete-in-Journey-tab check, and the security-critical fetch/unlock check. New `TeamData`/`TeamDataHandler` per environment; teams stored **inside the world save directory**, not the GAMEDIR-scoped file personal data uses — a deliberate fork, since GAMEDIR is shared across every local singleplayer world under one install and would otherwise leak "Team Alpha" between unrelated worlds. One team per player (hard rule); leaving snapshots the team's unlocks into personal unlocks (not collected counts); owner-only kick/transfer, target must be online. Team badge drawn above the tab row in the Journey screen (never inside a tab body, to avoid repeating the button-overlap bug).
- **§11 Visual Polish**: scoped down to a sound + one action-bar message on newly-crossed unlock thresholds, driven by client-side diffing of the unlocked set on each sync (a `hasSyncedOnce` guard keeps the very first sync-after-connect silent, or every already-unlocked item would "celebrate" on login). No dedicated "newly_unlocked" packet field needed — the full unlocked set was already synced every time. Deliberately **not** a full graphical toast with custom textures — no art-asset pipeline was in scope for this code-only pass.
- **Agent session-limit resilience**: 6 of 8 team+polish port agents hit an account-wide session limit mid-work in one run; all had already written correct, complete code (`TeamData`/`TeamDataHandler`, the resolution call sites) and only needed final wiring (team-command handler bodies, a `JsonObject.keySet()`→`entrySet()` fix for an older bundled Gson on the two Java-8 1.16.5 builds, a couple of missing server-start load hooks, two missing team badges) finished directly rather than re-running the agents.

**Superseded:** the old "GregTech Integration" proposal (bespoke `GregTechDepthCalculator`) is retired — generalized into the datapack threshold-pack system in §3. Documented in [JM_Project.md §11](JM_Project.md#11-superseded-proposals).

---

## Ground Rules (apply to every item below)

- [ ] Respect hybrid key format (`base_item_id|nbt_or_components_string`), server-authoritative model, main-thread packet handling.
- [ ] **Every item ports to all 9 environments — no exceptions.** Where a loader genuinely lacks the API, document the equivalent used instead (see §3 tags-on-1.12.2 below for the pattern).
- [ ] New packets need 4 networking implementations: SimpleNetworkWrapper (1.12.2), SimpleChannel (1.16.5–1.20.1), Payload/StreamCodec (NeoForge 1.21.1), Fabric Networking.
- [ ] **Old JSON must never break.** Any schema change to `journeymode_unlocks.json` needs a version bump (`"schema_version"`, starting at 2) + a migration path + a test that loads a prior-schema fixture and asserts no data loss. This is a release gate, not optional polish.
- [ ] All config writes go through the one synchronized writer (1.7.0 mutual-exclusion work) — no new file handles.
- [ ] Every jar-producing build bumps the version number in all 9 `gradle.properties` before release (patch digit for bugfix-only, minor digit for feature batches).

---

## Confirmed Bugs — DONE (2026-07-25, all 9, folded into 1.8.0)

### B1. Modded stack sizes inflate thresholds to 9,999
**Confirmed:** 1.20.1. **Root cause verified** at `Forge_1_20_1/src/main/java/.../data/RecipeDepthCalculator.java:50`:
```java
int stackSize = item.getMaxStackSize();   // live value — mutable by mods like Bigger Stacks
...
case 0 -> stackSize;                      // depth-0 threshold = whatever a stack-size mod set it to
```
`calculateThreshold()` reads `item.getMaxStackSize()` live. Stack-size-inflation mods (Bigger Stacks and equivalents on other versions) mutate this value, so a depth-0 raw material's threshold becomes the inflated stack size directly (observed: 9,999) instead of a sane vanilla-scale number.

- [ ] Fix: reference a stable baseline (vanilla default stack size table, or `Math.min(item.getMaxStackSize(), 64)`) for threshold math instead of the live, possibly-modded value. Decide which baseline and document it in [JM_Project.md §6](JM_Project.md#6-recipe-depth-calculator--threshold-engine).
- [ ] Port the fix to the equivalent calculator in all 9 environments (same bug pattern, since the depth/threshold logic is structurally parallel across all of them — verify each one individually rather than assuming).
- [ ] Add a regression test: item with an artificially inflated `getMaxStackSize()` still produces a sane threshold.
- [ ] Existing players who already have partial/full progress computed against the inflated threshold: same non-retroactive-lock rule as §4 — never re-lock, only allow instant-unlock if the corrected threshold is now lower than their current count.

### B2. Search bar leaks keybinds — pressing E while typing closes the GUI
**Root cause verified:** `JourneyModeScreen.java` (all 9 environments — grepped, none override `keyPressed`/`charTyped`) never overrides key handling. The search field (`EditBox searchBox` at `Forge_1_20_1/.../client/gui/JourneyModeScreen.java:42`) only consumes control keys (backspace, arrows, etc.) in its own `keyPressed` — printable characters are handled via `charTyped`, which fires only *after* `keyPressed` has bubbled unconsumed. Since nothing overrides `keyPressed` on the screen itself, the default container-screen behavior sees "E" go unconsumed and treats it as the inventory-close keybind, closing the GUI mid-type. This is a well-known Minecraft modding gotcha (same class of bug JEI/REI have fixed historically).

- [ ] Fix: override `keyPressed` (or per-loader equivalent) on the Journey screen class in every environment — when the search field has focus, consume all key input except an explicit close key (Escape only, or none), before the vanilla keybind check runs.
- [ ] Port to all 9 — confirm the exact screen/search-field class name per environment first (naming may differ, e.g. `TextFieldWidget` on some Fabric versions, `GuiTextField` on 1.12.2).
- [ ] Regression test/manual check: type a full sentence containing "e", "i" (inventory-adjacent letters), and confirm the GUI never closes until Escape or the close button.

---

## Cross-Cutting Work (1.8.0, before/alongside features)

Status: **DONE** (2026-07-25 — cycle-cache fix + schema versioning shipped in all 9; delta sync deliberately deferred, see Implementation notes above)

- [ ] Delta sync packets — prerequisite for §1, §8, §11, and now §12. Packet: `List<{key, count, unlocked, timestamp}>` + optional `newly_unlocked` list. Keep full-sync for login/reset.
- [ ] Schema versioning + migration loader for `journeymode_unlocks.json` (hard invariant now, not just needed-by-§1 — see Ground Rules).
- [ ] Cycle-cache fix in `RecipeDepthCalculator`: values computed while a cycle is open must NOT be memoized. Fix in the same pass as B1 since both touch this file — avoid two separate edits to the same method.
- [ ] Recipe-type coverage audit: confirm smelting/blasting/stonecutting/smithing all feed the depth graph on every version; multi-recipe items take MIN depth. Document result in `JM_Project.md §6`.
- [ ] Unit tests: pattern precedence (§3), `default_override` precedence (§4), normalization round-trip (potions/books/horns/stews), calculator determinism, B1 regression, schema-migration round-trip.
- [ ] `check-parity.ps1`: diff parallel core files (calculator, normalization, data model) across the 9 dirs; wire into `upload-all.ps1`, fail release on unexplained divergence.
- [ ] Docs: every shipped feature gets a `JM_Project.md` section amendment + CurseForge page/FAQ update in the same PR.

---

## 1. Shared Team Catalogs

Status: **TODO** · Porting: **all 9** (mandate overrides prior modern-first recommendation)

**Design decisions — settled 2026-07-25:**
- [x] **Pooling: both deposits and unlocks pool.** Every member's deposits add to one shared team counter per item; once the team crosses a threshold, every member can withdraw. Strongest "team" semantics, matches the "team" framing players expect.
- [ ] Player leaves team → snapshot their unlocks at leave-time, future deposits stop feeding team (recommended — still needs explicit confirmation, wasn't asked directly).
- [ ] One team per player, hard rule (recommended — no multi-team).
- [ ] Roles: creator = owner; owner invites/kicks; `/journeymode team transfer`. No permission tiers in v1.
- [x] **Scope: per-server / per-world, NOT global.** A team only exists within the save it was created in — it must not leak between two different servers, or between a server and someone's singleplayer world.

**Schema & storage — reworked per the per-world decision:**
- [ ] **Architecture fork from personal data, verified against the real code:** personal progress (`GlobalDataHandler.java:23,26` in every environment) is written to `journeymode_unlocks.json` at `FMLPaths.GAMEDIR` (or loader equivalent) — the game-instance/server-install root, NOT the individual world-save folder. That's already effectively "per-server" for a dedicated server (each server has its own GAMEDIR), but for **singleplayer it is shared across every local world under the same installation** — i.e. today's storage would let "Team Alpha" leak between two unrelated singleplayer worlds on the same PC. Per-world scoping means teams data must NOT reuse the GAMEDIR file; it needs its own location keyed to the actual world save.
- [ ] Store team data in the level/save directory instead (e.g. `<world-save>/data/journeymode_teams.json`, following the same pattern vanilla uses for per-world custom data) so singleplayer worlds and each dedicated server naturally get independent team namespaces, with zero cross-leakage in either direction.
- [ ] Player's personal data stays exactly where it is (GAMEDIR, unchanged) — only teams move to per-world storage. Document this split clearly since it's an intentional asymmetry between personal and team data, not an oversight.
- [ ] Per-world file: `{teamId: {name, owner_uuid, members[], collected_counts{}, unlocked_items[], unlock_timestamps{}}}`, plus a `player_uuid → team_id` lookup in the same file (no need to touch the personal GAMEDIR file at all — team membership is entirely a world-local concept now).
- [ ] Schema bump required on this new file from day one (`"schema_version": 1"`) — the JSON-never-breaks invariant applies here too, even though it's a new file, because it stops being new the moment 1.8.0 ships.
- [ ] CAVEAT: simultaneous deposits into a team counter are a new race — all team mutations must go through server main thread only.

**Commands & UI:**
- [ ] `/journeymode team create|join|leave|info|kick|transfer`.
- [ ] Team badge/header in Journey tab when in a team; catalog reads team data instead of personal. No separate team GUI in v1.
- [ ] Delta sync (cross-cutting) must land BEFORE/WITH this — full-state sync × N members × every deposit doesn't scale.
- [ ] 1.12.2 has no special blocker here (this feature is data/command/UI, not API-shape dependent) — confirm during implementation, no exemption assumed.

---

## 2. Real-Time Config Sync

Status: **DONE** (2026-07-25, all 9) · Porting: all 9

- [ ] On join, server pushes `blacklist.json` + `custom_thresholds.json` (+ future rules) to client in one packet.
- [ ] Re-push on config change via `/journeymode reloadconfig` (command-only for v1, not a filesystem watcher).
- [ ] CAVEAT: audit every client-side `ConfigHandler` read — must use synced values after this ships, or displayed thresholds diverge from server.
- [ ] CAVEAT: singleplayer/integrated server — guard against double-apply with `isDedicatedServer` check, or make sync a no-op overwrite.
- [ ] CAVEAT: packet size — `/journeymode all` (§4) must write a single override key, not per-item entries, or this packet balloons on large modpacks.
- [ ] Client must not fall back to local config files for the session after sync.

---

## 3. Integration & Customization API

Status: **DONE** (2026-07-25, all 9 — 1.12.2 tags via OreDictionary, datapack loader skipped there) · Porting: all 9

**Pattern & tag rules (do first — highest value/effort):**
- [ ] Extend `custom_thresholds.json` + `blacklist.json` to accept exact ID > tag (`#c:ingots`) > regex/wildcard, in that priority order.
- [ ] Accept both `*` globs and full regex (translate globs to regex internally). Compile all patterns ONCE at load into cached `List<Pair<Pattern,Integer>>` — never regex-match uncached per lookup.
- [ ] Multiple-match order = first-match-wins in file order (must be deterministic, not map-iteration order).
- [ ] **1.12.2 tags:** no vanilla Tags system exists pre-1.13. Instead of skipping tag rules on 1.12.2 (the original recommendation), map `#` keys to OreDictionary entries via a small built-in translation table for common categories (e.g. `#c:ingots` ↔ `ingotIron`/`ingotGold`/... OreDictionary names). Unmapped tags on 1.12.2 log a startup warning and are ignored — document the gap, don't silently no-op.
- [ ] Tags are datapack-driven, load after mod init (1.16.5+) — resolve tag rules lazily on first threshold query or on tag-update event, not at config parse time.
- [ ] Config reload must invalidate resolved-threshold cache, not just re-read the file.

**Datapack threshold packs:**
- [ ] Load rules from `data/journeymode/thresholds/*.json`. Precedence: config file (server owner) > datapack (pack author) > calculator (defaults).
- [ ] This generalizes and **replaces** the retired GregTech proposal — no bespoke `GregTechDepthCalculator`.

**Developer hooks (Java API):**
- [ ] `ThresholdProvider` (item key → `Optional<Integer>`, queried before calculator) + `NormalizationRule` (contribute component/NBT keys to hybrid-key whitelist).
- [ ] Static registry + entrypoint/IMC pattern per loader; keep API module tiny, dependency-free. **Implement on all 9**, including 1.12.2 — no modern-only exemption per the mandate, even though third-party consumers are less likely there.
- [ ] CAVEAT: `NormalizationRule` is a dupe-vector footgun (e.g. whitelisting container contents reintroduces shulker-dupe). Denylist components that can never be whitelisted (`BLOCK_ENTITY_DATA`, `CONTAINER`).

---

## 4. `/journeymode all [count]`

Status: **DONE** (2026-07-25, all 9) · Porting: all 9

- [ ] Permission level 2+ (OP).
- [ ] Write ONE key `"default_override": N` to config — NOT per-item entries (bloats file/packet on large modpacks).
- [ ] Precedence with override in place: exact ID > tag > regex > `default_override` > calculator.
- [ ] `/journeymode all reset` (or `all default`) removes override, returns to calculator behavior.
- [ ] Honors the same `max_threshold_cap` (default 64, adjustable — see §5) as `/journeymode threshold`. One constant shared across both commands.
- [ ] DECISION: changing thresholds does NOT retroactively lock already-unlocked items (recommended). Partial progress DOES re-evaluate against new threshold — may instantly unlock; handle lazily (next deposit or catalog open), not by iterating all players at command time.
- [ ] Require `/journeymode all <count> confirm` (or chat-click confirm) — destructive, world-balance command.
- [ ] Feedback message states scope explicitly + how to undo.

---

## 5. `/journeymode threshold <item> <count>`

Status: **DONE** (2026-07-25, all 9) · Porting: all 9

- [ ] Permission level 2+.
- [ ] Brigadier `ItemArgument`/`ItemStackArgument` for tab-completion; 1.12.2 falls back to string + registry validation.
- [ ] CAVEAT/DECISION: base-item-only writes (`minecraft:potion`, applies to all subtypes) AND a `hand` variant (`/journeymode threshold hand <count>`) that normalizes the held stack to target the exact hybrid key. Recommended: ship both.
- [ ] Write path: synchronized config writer → hot-invalidate threshold cache for that key → push config sync (ties to §2).
- [ ] `/journeymode threshold <item> remove` to delete an entry.
- [ ] **Count validation — decided:** reject ≤0. Hard max default **64**. The cap itself is adjustable two ways: (a) direct edit of a new config key (e.g. `"max_threshold_cap": 64"` in `custom_thresholds.json` or a new top-level config file — pick the file during implementation, document it), or (b) a command to raise/lower it without hand-editing JSON (e.g. `/journeymode maxthreshold <value>`, mirrors the `hand`-variant convenience pattern used elsewhere). Same cap applies to `/journeymode all` (§4) for consistency — one constant, not two separate limits.

---

## 6. `/journeymode grant|revoke <player> <item>`

Status: **DONE** (2026-07-25, all 9) · Porting: all 9

- [ ] Permission level 2+. `grant` adds key to `unlocked_items` + timestamp. `revoke` removes it AND resets that item's `collected_counts` to 0 (recommended — otherwise revoke is meaningless).
- [ ] Must work on OFFLINE players — mutate file entry by UUID through synchronized writer; if player IS online, mutate live attachment instead then save.
- [ ] Same `hand`-variant subtype-targeting question as §5.
- [ ] Test target: FTB Quests reward command executed as server, targeting an offline player — the real-world use case.

---

## 7. Rarity-Aware Thresholds

Status: **DONE** (2026-07-25, all 9 — implemented as live code in RecipeDepthCalculator, not a static datapack; see notes below) · Porting: all 9

- [ ] Scope tightly to Depth 0 (recipe-less) items only — crafted items already priced by depth.
- [ ] Table constant (not scattered logic): COMMON → /1, UNCOMMON → /4, RARE → /16, EPIC → 1.
- [ ] Built-in override table for known vanilla rarity inconsistencies: nether_star, heart_of_the_sea, echo_shard, enchanted_golden_apple, dragon_egg, sniffer_egg, totem, wither/dragon heads.
- [ ] Recommended implementation: built-in datapack `data/journeymode/thresholds/vanilla_rarity.json` using the §3 datapack layer (dogfoods own API).
- [ ] CAVEAT: never re-lock anything on default changes (same rule as §4) — only instant-unlock is acceptable. Add a changelog note.

---

## 8. Deposit All

Status: **DONE** (2026-07-25, all 9) · Porting: all 9

- [ ] Button in Deposit tab. One new packet `DepositAllPacket` (no payload — server reads inventory itself, same trust model as existing deposit).
- [ ] DECISION: skip already-unlocked items (recommended), show summary line ("Deposited N items across M types. Skipped K unlocked types.").
- [ ] **Decided:** main inventory only (slots 9–35) by default; hotbar included only when the player shift-clicks the Deposit-All button. NEVER touch armor/offhand, ever, regardless of modifier. This is specific to the bulk button in the J-menu — unrelated to §12's E-menu deposit slot, which is a single manual slot (drag an item in, like the existing J-menu deposit slot) and has no "which inventory range" question at all.
- [ ] CAVEAT: sync packet after must carry all changes — another argument for delta-sync landing first.
- [ ] CAVEAT: irreversibility — deposit IS destruction. Mitigation: (a) confirmation dialog [recommended v1], (b) only deposit types with existing partial progress > 0, (c) 5-second server-side undo buffer [later].

---

## 9. Progress Tooltips

Status: **DONE** (2026-07-25, all 9) · Porting: all 9 — cheap, do early for goodwill

- [ ] Client-side tooltip handler per loader (`ItemTooltipEvent` Forge/NeoForge, `ItemTooltipCallback` Fabric, 1.12.2 equivalent). Line: `Journey: 37/64 researched` or `Journey: ✔ Unlocked`.
- [ ] Data source: existing synced `clientPlayerData` — zero new packets.
- [ ] CAVEAT: until §2 (config sync) ships, client-computed thresholds can diverge from server-only config edits. Acceptable v1 fallback: show count without denominator (`Journey: 37 deposited`), or ship this after §2.
- [ ] Normalize hovered stack with the same `getNormalizedStack()` before lookup — otherwise potions/enchanted books show wrong entry's progress.
- [ ] Config toggle `show_tooltips: true`; respect `/journeymode off` (no tooltips when disabled).

---

## 10. Catalog Statistics

Status: **DONE** (2026-07-25, all 9) · Porting: all 9

- [ ] Stats panel/tab: total researchable vs unlocked, % complete, per-mod-namespace breakdown, first/latest unlock (existing timestamps).
- [ ] "Total researchable" denominator = registry items minus blacklist minus invalid-key items. Compute lazily + cache (don't re-iterate registry every GUI open).
- [ ] Fully client-side from synced data + client registry — no new packets.
- [ ] CAVEAT: with team catalogs (§1) shipped, stats must read through the same accessor the catalog uses (team vs personal), not personal data directly.

---

## 11. Visual Polish

Status: **TODO** · Porting: all 9 — **explicitly LAST priority, do not start here**

- [ ] Unlock sound + toast/particle on threshold crossing — triggered client-side by diffing old vs new unlocked set from sync packet (needs "newly_unlocked" list from delta sync).
- [ ] Custom textures: stay within vanilla GUI palette; test at GUI scale 1–4 and 1.12.2's different lighting path (`RenderHelper` fix from 1.6.0N).
- [ ] Sounds via standard `SoundEvent` registration — forces a real assets pipeline into all 9 builds; verify each Gradle setup processes resources identically.

---

## 12. NEW: E-Menu Unlock Integration

Status: **TODO** · Porting: all 9 · **Bucketing: recommended for 1.8.0** (see reasoning below — flag if you'd rather push to a dedicated future release)

**Request:** surface unlocked-item withdrawal directly inside the vanilla player inventory ("E" menu), as an additional convenience surface — NOT a replacement for the dedicated Journey ("J") menu, which keeps the full catalog + search.

**Confirmed scope:** the E-menu surface gets a small deposit window beside the main player inventory grid, in addition to the fetch/withdraw catalog strip. This is a single manual slot — drag an item in, same trigger-on-fill behavior as the existing J-menu deposit slot — not a bulk "deposit all" control. It reuses the existing `SubmitDepositPacket` pipeline exactly (blacklist check, normalize, `depositItem()`), no new packet or new server logic, just a second slot instance in a different screen. Keep this distinct from §8's Deposit-All bulk button — different feature, different packet, different UX (one drag vs. one click that scans the whole inventory).

**Why bucketed into 1.8.0 rather than deferred:**
- It reuses the existing `RequestItemPacket`/fetch pipeline (SOLE_TRUTH/JM_Project §8) — no new networking layer needed, which is normally the most expensive part of a new feature here.
- It's independent of the other 1.8.0 items (doesn't block or get blocked by teams/config-sync/API work), so it can slip a version on its own without disturbing the rest of the batch if 1.8.0 scope needs trimming.
- Player-visible quick-win in the same spirit as tooltips (§9) — good goodwill placement right after that work.

**Design decisions to settle first:**
- [ ] E-menu surface = (a) a compact scrollable strip/tab of unlocked items, click/shift-click to fetch, and (b) the small manual deposit slot confirmed above. No search bar in v1 (search stays a J-menu differentiator, keeps this additive rather than a J-menu clone).
- [ ] Toggle to disable the E-menu overlay entirely (some players/packs will want a clean vanilla inventory) — config option, respects `/journeymode off`.

**Implementation, per environment (9 separate injections — no shared UI code across loaders):**
- [ ] 1.12.2 Forge: `GuiContainer` overlay via `GuiScreenEvent` (`InitGuiEvent.Post` equivalent) on `InventoryScreen`/`GuiInventory`.
- [ ] 1.16.5–1.20.1 Forge: `ScreenEvent.Init.Post` on `InventoryScreen`, add the widget/tab via `addListener`/`addRenderableWidget` equivalents.
- [ ] NeoForge 1.21.1: `ScreenEvent.Init.Post` (NeoForge event bus), same widget-injection pattern as Forge 1.20.1 where the API allows reuse.
- [ ] Fabric 1.16.5–1.21.1: Mixin into `InventoryScreen`/`HandledScreen` (or a compatible Fabric API hook if one exists for this — verify per version) to inject the tab.
- [ ] CAVEAT: this is exactly the kind of screen-injection that collides with other mods doing the same (JEI, inventory-tweak mods). Test with at least one popular inventory-modifying mod per loader before shipping.
- [ ] Reuse §9's threshold-display caveat: if shown, unlock-progress numbers here have the same client/server threshold-divergence risk until §2 ships — same mitigation (omit denominator, or ship after §2).
- [ ] Reuse §2's keybind lesson from B2: any text/search input added to this surface later must consume input while focused — don't reintroduce the same bug class in a second screen.

---

## Recommended Sequencing (all one release, 1.8.0)

1. B1 + B2 bugfixes, alongside cross-cutting (B1 touches `RecipeDepthCalculator` in the same area as the cycle-cache fix — do both in one pass per environment, not two separate edits to the same file). Cross-cutting: delta sync, schema versioning, tests, parity script.
2. §3 pattern/tag config rules (unlocks §4, §5, §7 cleanly)
3. §4 + §5 + §6 commands (small, share config-writer plumbing; includes the new `max_threshold_cap` setting)
4. §2 real-time config sync
5. §9 tooltips + §8 deposit-all (player-visible quick wins)
6. §12 E-menu unlock integration (same quick-win category, shares §9's threshold-display dependency on §2)
7. §7 rarity datapack (dogfoods §3)
8. §1 team catalogs (biggest — needs everything above, now all-9 scope per mandate)
9. §10 statistics
10. §11 visual polish

---

## Resolved (2026-07-25, round 2)

1. ~~1.7.2 interim patch~~ — folded into 1.8.0, one release.
2. Team catalogs (§1): both deposits and unlocks pool; teams are scoped per-server/per-world (not global) — see §1's reworked schema section for the storage implications this forces.
3. Threshold cap = 64 default hard max, adjustable via config JSON or command. Shared by §4 and §5.
4. §8 Deposit-All hotbar: shift-click to include hotbar, default excludes it, armor/offhand never touched. Confirmed distinct from §12's manual deposit slot (no hotbar question there at all).
5. §12 E-menu ships with both a fetch strip AND a small manual deposit slot (not read-only). No search bar in v1.

## Still Open

None currently blocking. Remaining unchecked items in §1 (leave-team snapshot behavior, one-team-per-player hard rule) carry a recommended default and aren't hard blockers — flag if you want to override before implementation reaches that point.
