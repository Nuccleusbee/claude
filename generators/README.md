# Generators

Paper **1.21.x** plugin with two kinds of infinite generator, admin-only (`generators.admin`, ops by default).

## Block generators: mine forever
Build your ores however you like, select them with the wand, and run `/generator`. Every block you placed
becomes infinite, and each one keeps its own type, so a hand-made mix of ores stays exactly as you built it.
Whatever you mine goes **straight into your inventory**, and **anything that doesn't fit is deleted**.
It works **anywhere, even in protected areas** like spawn (WorldGuard etc.), because the plugin handles
the break before protection plugins see it.

```
/generatorwand                                gives you the wand
  left-click a block                          corner 1
  right-click a block                         corner 2
/generator                                    turns every block in the selection into a generator (gen1, gen2...)
/generator 10                                 same, but mined blocks turn to bedrock and come back after 10s
/generator create diamondmine 5               same, with your own name
```
Air in the selection is ignored, so the selection can be bigger than your build.

You can also have the plugin place the blocks for you: Fortune and Silk Touch work, you get the XP, and
tools lose durability. Explosions and pistons can't break or move generator blocks.

```
/generator create diamond1 diamond_ore        the block you're looking at, never runs out
/generator create iron1 iron_ore 5            turns to bedrock for 5s after mining, then comes back
/generator create gold1 gold_ore 3x3          a 3x3 pad centred on the block you look at
/generator create coal1 coal_ore 5x5 10       5x5, each block regenerates after 10s
/generator create emerald1 emerald_ore 5x3x5  5 wide, 3 deep (goes down), 5 long
/generator area coalmine coal_ore 10          fills the wand selection with coal ore
/generator setdrop diamond1 diamond 2         optional: give 2 diamonds instead of the normal drop
/generator setdrop diamond1 natural           back to the normal drop
/generator setregen iron1 0                   never breaks
/generator setblock iron1 gold_ore            make every block in it gold ore
/generator reset coalmine                     refill everything
/generator remove iron1
```

Sizes: `WxL` is a flat layer (`3x3`, `5x5`, `2x2`...) and `WxDxL` adds depth going down from the
block you look at. Each side can be 1-100; a single generator can hold up to 50,000 blocks.
Every block in a generator regenerates on its own.

## Drop generators: items on a timer
```
/generator drop create iron iron_ingot 1 5    look at a block: drops 1 iron ingot on it every 5s
/generator drop remove iron
```
They only run when a player is within 32 blocks and stop once 64 items are piled up (see `config.yml`).

## Other
- `/generator list` shows everything; `/generator reload` reloads `config.yml`. `/gen` works as a short alias.
- **Vanilla spawn protection** blocks mining before any plugin sees it. Set `spawn-protection=0` in
  `server.properties` and protect spawn with WorldGuard (or similar) instead; generators still work there.

## Install
Download the jar from the **Actions** tab (*Build Generators* → latest run → **Generators-jar**), or run
`./gradlew build` (jar in `build/libs/`). Drop it into `plugins/` and restart.
