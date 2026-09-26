# Generators

Paper **1.21.x** plugin with two kinds of infinite generator, admin-only (`generators.admin`, ops by default).

## Block generators: mine forever
Turn a block, or a whole area, into an infinite ore/block. Whatever you mine goes **straight into your
inventory**, and it works **even in protected areas** like spawn (WorldGuard etc.), because the plugin
handles the break before protection plugins see it. Fortune and Silk Touch work, you get the XP, and
tools lose durability. Explosions and pistons can't break or move generator blocks.

```
/generator create diamond1 diamond_ore        the block you're looking at, never runs out
/generator create iron1 iron_ore 5            turns to bedrock for 5s after mining, then comes back
/generator pos1 / pos2                        look at two corners of a mine
/generator area coalmine coal_ore 10          fills the whole box with coal ore
/generator setdrop diamond1 diamond 2         optional: give 2 diamonds instead of the normal drop
/generator setdrop diamond1 natural           back to the normal drop
/generator setregen iron1 0                   never breaks
/generator setblock iron1 gold_ore            change the block
/generator reset coalmine                     refill everything
/generator remove iron1
```

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
