# VanillaRPG 0.1.0

A portable Vanilla+ RPG foundation for a small private Paper server.

Target stack:
- Minecraft Java 26.2
- Paper 26.2
- Java 25
- Vanilla clients; no client mods required

## What 0.1.0 already implements

- SQLite player data (`plugins/VanillaRPG-Core/rpg.db`)
- Coin and Token balances
- `/coin`, `/coin pay`, `/token`, `/profile`
- Item rarity: Common, Uncommon, Rare, Epic, Legendary
- Real weapon affixes:
  - Attack Damage %
  - Critical Chance %
  - Critical Damage %
  - Lifesteal %
- `/forge upgrade`
- `/forge reroll`
- `/forge inspect`
- Configurable vanilla-material upgrade costs
- Legendary crafting locked by default, ready for the later Blueprint/Boss module
- Persistent item IDs and stats using PersistentDataContainer, not display-name checks
- Admin commands for testing/balancing
- Portable server template and backup script
- Placeholder configuration files for quests, dungeons, gacha, bosses and named items

## Progression currently configured

COMMON -> UNCOMMON
- 500 Coin
- 2 Emerald

UNCOMMON -> RARE
- 1,500 Coin
- 1 Netherite Ingot
- 1 Netherite Upgrade Smithing Template

RARE -> EPIC
- 5,000 Coin
- 2 Token
- 1 Nether Star
- 4 Echo Shard

EPIC -> LEGENDARY
- intentionally locked until Blueprints/Boss integration

All costs are editable in `config.yml`.

## Build

Requirements: JDK 25 and Gradle.

From `plugin/`:

```text
gradle build
```

Output:

```text
plugin/build/libs/VanillaRPG-Core-0.1.0.jar
```

Copy the JAR into `server-template/plugins/` (or the `plugins/` directory of a Paper server).

At runtime Paper loads `org.xerial:sqlite-jdbc` using the `libraries:` field in `plugin.yml`, so the plugin JAR itself does not need to shade SQLite.

## Commands

Player:

```text
/coin
/coin pay <player> <amount>
/token
/profile
/forge inspect
/forge upgrade
/forge reroll
```

Admin (`vanillarpg.admin`, OP by default):

```text
/rpg reload
/rpg givecoin <player> <amount>
/rpg givetoken <player> <amount>
/rpg setrarity <COMMON|UNCOMMON|RARE|EPIC|LEGENDARY>
```

## V1 limitation

Only swords, axes, tridents and maces receive RPG affixes in 0.1.0. Armor/tool affixes, GUI/NPC menus, quests, dungeons, market, gacha, blueprints and external boss adapters are intentionally staged for later versions so each layer can be tested before the next is added.

## Planned next steps

0.2: Forge inventory GUI + shard items + blueprint database

0.3: Daily/weekly quests + achievements + titles

0.4: Dungeon warp/cooldown/reward framework

0.5: Player market + transaction log

0.6: Adventure/Cosmetic gacha

0.7: NPC menu integration

0.8: External boss adapters + Legendary blueprint drops
