# RelicPvP

Paper **1.21.x** plugin for a PvP / rare-item-collecting map:

- **Infinite generators**: blocks that drop an item on a timer, forever
- **Block generators** (`/generator`): blocks or whole mines you can mine forever. What you mine goes straight into your inventory, and they work even inside protected spawn.
- **Starter kit**: given on first join and every respawn (kit items vanish on death so nobody farms them)
- **Bosses → keys → levels**: bosses guard a spot, have a boss bar and abilities, and drop a **Level N Key** when killed. Right-click the key to unlock Level N.
- **Level-locked zones**: areas you can't walk (or ender pearl) into until you reach their level. Zones can also be **no-PvP** (spawn/hub).
- **Rare item spawns**: every few minutes a glowing rare item appears at a random point and the whole server gets told roughly where. First to grab it wins.
- **PvP stats**: kills, deaths, K/D, kill streak announcements (`/stats`)

## Install

1. Get the jar: open the **Actions** tab on GitHub → *Build RelicPvP* → latest run → download **RelicPvP-jar**.
   Or build it yourself with `./gradlew build` (jar lands in `build/libs/`).
2. Drop it in your server's `plugins/` folder and start the server (Paper 1.21.x, Java 21).
3. Edit `plugins/RelicPvP/config.yml` to taste, then `/relic reload`.

## Setting up the map (as op)

```
# Safe spawn area: level 0, no PvP, no building
/relic zone pos1          (stand on one corner)
/relic zone pos2          (stand on the opposite corner)
/relic zone create spawn 0 false false

# Level-locked areas (zones cover the full height of the world)
/relic zone pos1 ... /relic zone pos2 ...
/relic zone create level1 1
/relic zone create level2 2

# Bosses: stand where each should live
/relic boss setspawn grave_knight       (drops Level 1 key)
/relic boss setspawn ashen_reaper       (drops Level 2 key, put it inside level1)
/relic boss setspawn ironclad_warlord   (drops Level 3 key, put it inside level2)

# Generators: look at a block
/relic gen create iron1 IRON_INGOT 1 5
/relic gen create gold1 GOLD_INGOT 2 20

# Block generators: mined items go straight into the inventory, even in spawn
/generator create diamond1 diamond_ore          (the block you're looking at, never runs out)
/generator create iron1 iron_ore 5              (turns to bedrock for 5s after mining)
/generator pos1  /generator pos2                (look at two corners of a mine)
/generator area coalmine coal_ore 10            (fills the whole box with coal ore)
/generator setdrop diamond1 DIAMOND 2           (optional: custom drop instead of the normal one)

# Rare item spawn points: walk around and add a bunch
/relic rare addpoint
/relic rare spawnnow      (test it)
```

Set `spawn-protection=0` in `server.properties` and protect spawn with a no-build zone instead.
Vanilla spawn protection blocks mining before plugins can see it, so generators wouldn't work there.
Other protection plugins like WorldGuard are fine: generators run before they do.

Tip: ops can walk into locked zones only with `relicpvp.bypass.zones`. Ops **don't** have it by default,
so you can test the lock yourself. Use `/relic setlevel <you> 3` or give yourself the permission to build.

## Commands

| Command | What it does |
|---|---|
| `/stats [player]` (alias `/level`) | Level, kills, deaths, K/D, and which boss to kill next |
| `/relic gen create <id> <item> [amount] [seconds]` | Generator on the block you're looking at |
| `/relic gen remove <id>` · `list` | |
| `/relic zone pos1` · `pos2` · `create <id> <level> [pvp]` | Make a zone |
| `/relic zone setlevel <id> <n>` · `setpvp`/`setbuild <id> <true/false>` · `remove` · `list` | |
| `/generator create <id> <block> [regen]` | Infinite block generator on the block you look at |
| `/generator pos1` · `pos2` · `area <id> <block> [regen]` | A whole infinite mine |
| `/generator setdrop <id> <item\|natural> [amount]` · `setregen` · `setblock` · `reset` · `remove` · `list` | |
| `/relic boss setspawn <id>` · `clearspawn` · `spawn` · `kill` · `list` | |
| `/relic rare addpoint` · `removepoint` · `clearpoints` · `spawnnow` · `list` | |
| `/relic give <player> <item> [amount]` | Works with custom item ids from the config |
| `/relic givekey <player> <tier>` · `setlevel <player> <n>` · `kit [player]` | |
| `/relic reload` | Reload config.yml |

## Config

Everything is in `config.yml`, with comments. Custom items (`items:`) can be used anywhere an item is
asked for: kit, boss equipment and drops, generators, rare spawns. Add more bosses by copying a block
under `bosses:` and giving it the next `key-tier`.

Placed stuff (zones, generators, boss spawns, rare points) is saved in `data.yml`; player levels and
stats are in `players.yml`.
