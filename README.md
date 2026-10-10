# VanillaRPG-Core 0.5.0

Target: Paper 26.2 / Java 25

## New in 0.5.0 - Player Market

- `/market` opens a paginated player-market GUI.
- `/market sell <price>` lists the entire item stack held in the main hand.
- `/market buy <listing-id>` buys a listing with VanillaRPG Coin.
- `/market cancel <listing-id>` cancels an owned active listing.
- `/market claim` delivers purchased or cancelled escrow items when inventory space is available.
- `/market mine` lists the player's active listing IDs.
- `/market history` shows the latest purchases and sales.
- SQLite escrow stores the complete serialized `ItemStack`, including VanillaRPG PDC, affixes, names and models.
- A purchase atomically debits the buyer, credits the seller, closes the listing, records the transaction and creates a delivery.
- Sellers may be offline; proceeds are credited directly to their existing Coin balance.
- Existing 0.4.x tables and player data are preserved. Migration only creates new market tables and indexes.

Market limits are configurable in `config.yml`:

```yaml
market:
  enabled: true
  min-price: 1
  max-price: 1000000000
  max-active-listings-per-player: 10
  page-size: 45
```

## New in 0.4.3

- Restores the complete 0.4.2 Gradle source tree so the installed feature set can be rebuilt safely.
- Validates `config.yml`, `items.yml`, `quests.yml`, `dungeons.yml`, `gacha.yml`, and `bosses.yml` before initialization or `/rpg reload`.
- Invalid YAML cancels initialization/reload without replacing the existing file or opening/migrating `rpg.db`.
- Startup version messages now come from `plugin.yml`, preventing hard-coded version drift.
- Pins the compile API to Paper `26.2.build.132-stable` for reproducible builds matching the current server.
- Keeps the 0.4.2 SQLite schema and all existing player, quest, title, blueprint, cooldown, balance, and item PDC data compatible.

Build from the repository root:

```text
build-plugin.bat
```

Or from `plugin/`:

```text
gradle clean build
```

Output: `plugin/build/libs/VanillaRPG-Core-0.5.0.jar`

0.4.0 keeps the 0.3 progression systems and adds the first playable dungeon loop. It deliberately uses vanilla Minecraft mobs with light RPG tuning instead of a custom mob engine.

## New in 0.4.0

- `/dungeons` dungeon finder GUI
- `/dungeon join <id>` / `/dungeon leave` / `/dungeon status`
- Config-driven `dungeons.yml`
- Persistent per-player dungeon cooldowns in SQLite
- Time limits + action-bar progress
- Kill objectives
- Automatic dungeon cleanup
- One active solo run per dungeon in 0.4.0
- Vanilla mobs can be customized by config:
  - display name
  - count
  - health multiplier
  - attack-damage multiplier
  - weapon / armor
- Dungeon mobs drop no vanilla loot or XP, so the RPG reward table is the source of dungeon rewards
- Reward tables support Coin / Token / custom materials with chance and min-max amount
- Dungeon clears feed Daily/Weekly quests and permanent achievements
- Profile GUI now shows dungeon clears and a Dungeons shortcut
- Hero Sword / Demonic Blade resource-pack integration is unchanged

## Default dungeon templates

`dungeons.yml` includes three disabled templates:

- `goblin_camp` - Tier I - Common+ required, Uncommon recommended
- `ruined_temple` - Tier II - Rare+ required
- `crimson_fortress` - Tier III - Epic+ required

They are disabled because every server has different map coordinates.

### Fast setup / test

As OP, stand where you want the dungeon player to enter, then run:

```text
/dungeon setspawn goblin_camp
```

That command saves your exact current location into `plugins/VanillaRPG-Core/dungeons.yml` and enables the dungeon.

For the other templates:

```text
/dungeon setspawn ruined_temple
/dungeon setspawn crimson_fortress
```

You can use any empty test area first. A downloaded/custom dungeon map can be wired to the same IDs later without recompiling the plugin.

## Commands

Player:

```text
/dungeons
/dungeon join <id>
/dungeon leave
/dungeon status
```

Admin/OP:

```text
/dungeon setspawn <id>
/dungeon resetcooldown <id>
/dungeon reload
/rpg reload
```

Existing 0.3 commands remain available:

```text
/profile
/quests
/achievements
/titles
/forge
/blueprints
/coin
/token
```

## Mob config example

```yaml
mobs:
  - type: ZOMBIE
    name: "Temple Guard"
    count: 6
    health-multiplier: 1.55
    damage-multiplier: 1.25
    weapon: IRON_SWORD
    helmet: CHAINMAIL_HELMET
    chestplate: CHAINMAIL_CHESTPLATE
```

No MythicMobs or client mod is required.

## Reward config example

```yaml
rewards:
  coin:
    min: 700
    max: 1200
  token:
    min: 1
    max: 2
  custom-items:
    epic_shard:
      chance: 0.60
      min: 1
      max: 2
    legendary_shard:
      chance: 0.05
      min: 1
      max: 1
```

`chance: 0.05` means 5%.

## Upgrade from 0.3.0

1. Stop the server.
2. Back up the whole server folder.
3. Build 0.4.0 with `build-plugin.bat` or `gradle clean build`.
4. Remove the old VanillaRPG Core JAR from `plugins/`.
5. Copy `VanillaRPG-Core-0.4.0.jar` into `plugins/`.
6. Keep `plugins/VanillaRPG-Core/rpg.db`.
7. Keep your existing `items.yml` and resource pack.
8. Start the server.

Migration is automatic:

- SQLite creates the dungeon cooldown table automatically.
- Old placeholder `dungeons.yml` is replaced by the new 0.4 templates.
- Existing 0.3 `quests.yml` is preserved; missing dungeon quests/achievements/titles are merged into it.

## Quick test

```text
/rpg givegear tradao EPIC NETHERITE_SWORD
/dungeon setspawn goblin_camp
/dungeons
```

Click Goblin Camp. Expected:

1. Player teleports to the saved location.
2. Vanilla mobs spawn around the dungeon spawn point with custom names/equipment.
3. Action bar shows kills and time remaining.
4. Dungeon mobs drop no normal loot/XP.
5. Killing all dungeon mobs clears the dungeon.
6. Player returns to the location they entered from.
7. Coin/Token/material rewards are granted.
8. `/profile` shows the new dungeon clear count.
9. `/quests` can progress `DUNGEON_CLEAR` quests when assigned.
10. Re-entering before cooldown expires is blocked.

For repeated testing:

```text
/dungeon resetcooldown goblin_camp
```

## 0.4.0 limitation by design

There is no party/instance system yet. Only one player can actively run a specific dungeon ID at a time. This avoids players mixing mobs/objectives in the same physical room. Party support and isolated instances belong in 0.4.1.

## Resource pack

Continue using the working `Blades-of-Majestica-VanillaRPG-26.2.zip`. 0.4.0 does not change Hero Sword or Demonic Blade model mapping.

## 0.4.1 dungeon world test

0.4.1 adds direct loading/teleporting for separate dungeon worlds.
The included preset expects a world folder named `dungeon_one_chunk` beside the normal server `world` folder.
It is designed to be used with the user's original One Chunk Dungeon map archive; the map itself is not bundled with VanillaRPG.

Flow:
`/dungeons` -> click `One Chunk Dungeon` -> world auto-load -> teleport to map spawn -> VanillaRPG custom vanilla mobs -> clear -> rewards -> teleport back.

If the map's default spawn is not the desired entrance, enter that world once, stand at the desired entrance and run:
`/dungeon setspawn one_chunk_dungeon`
This stores the exact location and disables `use-world-spawn` for that dungeon.

## 0.4.2 Paper 26.2 dungeon world loader

0.4.2 no longer opens downloaded dungeon maps using the legacy
`WorldCreator(String)` path. It imports the map's overworld chunk data into a
VanillaRPG namespaced dimension in Paper 26.2 storage and loads that dimension
with `WorldCreator.ofKey(...)`. This avoids `LegacyCraftBukkitWorldMigration`
collisions and also repairs a partially migrated first attempt by copying the
original source chunks over the incomplete target before the first successful
load.

For One Chunk Dungeon, keep the extracted source folder named
`dungeon_one_chunk` beside the main world. The configured entrance is
`0.5, 65, 0.5`.
