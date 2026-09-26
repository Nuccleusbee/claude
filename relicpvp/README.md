# RelicPvP

Paper **1.21.x** plugin for a PvP / rare-item-collecting map:

- **Infinite generators**: blocks that drop an item on a timer, forever
- **Block generators** (`/generator`): blocks or whole mines you can mine forever. What you mine goes straight into your inventory, and they work even inside protected spawn.
- **Starter kit**: given on first join and every respawn (kit items vanish on death so nobody farms them)
- **Bosses → keys → levels**: bosses guard a spot, have a boss bar and abilities, and drop a **Level N Key** when killed. Right-click the key to unlock Level N.
- **Level-locked zones**: areas you can't walk (or ender pearl) into until you reach their level. Zones can also be **no-PvP** (spawn/hub).
- **Rare item spawns**: every few minutes a glowing rare item appears at a random point and the whole server gets told roughly where. First to grab it wins.
- **Custom world** (`/relic createworld`): spawn plains in the middle, **4 mega biomes** around it (N/E/S/W), and a **Wasteland** ring behind them. It uses normal Minecraft terrain with this biome layout.
- **Spawn plaza** (`/relic buildspawn`): a round plaza with a fountain, 4 portal arches (one facing each biome), 4 generator pads and lanterns. It's automatically a safe zone with no PvP and no building.
- **Portals** (`/portal`): walk-through portals to anywhere, to a boss, or back to spawn.
- **The Wasteland**: custom monsters keep spawning around you and chase you down, and rare loot keeps dropping near players out there.
- **PvP stats**: kills, deaths, K/D, kill streak announcements (`/stats`)

## Install

1. Get the jar: open the **Actions** tab on GitHub → *Build RelicPvP* → latest run → download **RelicPvP-jar**.
   Or build it yourself with `./gradlew build` (jar lands in `build/libs/`).
2. Drop it in your server's `plugins/` folder and start the server (Paper 1.21.x, Java 21).
3. Edit `plugins/RelicPvP/config.yml` to taste, then `/relic reload`.

## Setting up the map (as op)

### 1. The world
```
/relic createworld relic
```
This makes the world, builds the spawn plaza at 0,0, sets the world border and makes it the main world.
From then on, players are sent there when they join and when they respawn without a bed.
Biomes and the wasteland size are set under `world-layout:` in the config.

(Already have a spawn map instead? Stand in it and run `/relic buildspawn` to skip the plaza, or just
use the zone commands below on your own build.)

### 2. Portals in the arches
Fly into an arch and look at the **inside face** of the bottom-left block of the opening, then run `/portal pos1`.
Look at the inside face of the top-right block and run `/portal pos2`. Then:
```
/portal create north                  (fills the opening with purple portal blocks)
(fly to where it should send people, e.g. the middle of the north biome)
/portal setdest north
/portal create bossportal            (a portal somewhere else...)
/portal setdest bossportal boss grave_knight
/portal setdest back spawn            (for a return portal)
```

### 3. Everything else
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
| `/relic createworld [name]` | Make the spawn + 4 biomes + wasteland world |
| `/relic buildspawn [radius]` | Build the spawn plaza at 0,0 of the world you're in |
| `/relic wasteland spawn <monster>` | Spawn a wasteland monster to test it |
| `/portal pos1` · `pos2` · `create <id> [fill]` | Make a portal (fill: `nether_portal` (default), `none`, or any block) |
| `/portal setdest <id> [here\|spawn\|boss <id>]` · `tp` · `remove` · `list` | Where it sends you |
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
