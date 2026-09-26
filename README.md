# FrozenSilverfish

A small Paper plugin that removes the AI from silverfish while keeping their physics. It was written to reduce the lag caused by armadillo farms, without changing how many silverfish they produce.

## Why

Our server has armadillo farms: 12 stations with 20 armadillos each, all with the Infested effect. When an armadillo gets hit it spawns silverfish. The silverfish are carried by water to a killing chamber, where a player kills them with a Sweeping Edge sword by hitting an armor stand.

That works, but it produces a lot of silverfish, and every one of them runs its AI every tick: looking for players, pathfinding towards them, looking for blocks to hide in, waking up other silverfish. With that many of them the server TPS drops.

Reducing the spawns was not an option, because the whole point of the farm is to produce silverfish. So the idea was to keep the same number of silverfish and make each one cheaper for the server.

## What it does

Every silverfish that enters a world gets its AI turned off. A frozen silverfish no longer:

- looks for, chases or attacks players
- walks around on its own
- hides inside blocks
- wakes up other silverfish
- swims up to the surface of water

These things keep working as before:

- gravity
- being pushed by flowing water
- knockback
- taking damage, including sweeping edge damage
- XP when a player kills it

So the farm works the same way: the silverfish still fall, still travel along the water streams and still die to the sword. They just stop thinking.

The plugin only changes silverfish. Armadillos and every other mob are left alone.

## How it works

### setAware instead of NoAI

The plugin calls `Mob#setAware(false)` on each silverfish.

The vanilla `NoAI` tag (`setAI(false)` in the API) is not the same thing. It turns off the AI and also the movement of the mob: a NoAI mob doesn't fall, isn't pushed by water and stays where it is. That would break the water transport of the farm.

`setAware(false)` only turns off the AI goals. The mob still runs its physics every tick, so it falls, floats along the water and can be knocked back.

### When the plugin applies it

- When a silverfish is added to a world. This covers every way a silverfish can appear: the Infested effect, infested blocks, spawn eggs, spawners, commands, and silverfish loaded from chunks that were saved earlier. The plugin listens to Paper's `EntityAddToWorldEvent` for this.
- When the plugin starts, to all silverfish that are already loaded.
- When you run `/fsf reload`, to all loaded silverfish.

Each silverfish changed by the plugin gets the scoreboard tag `frozen_silverfish`, so you can always tell which ones were modified. For example `/kill @e[type=silverfish,tag=frozen_silverfish]` kills only those.

### What is saved on the entity

This matters for reloading and uninstalling.

- The aware flag is saved with the entity. A frozen silverfish stays frozen after a restart, after its chunk is unloaded and loaded again, and even after the plugin is removed.
- The `frozen_silverfish` tag is saved with the entity.
- The collision setting is not saved. The plugin sets it again every time a silverfish is loaded.
- Drowning prevention is not saved. The plugin does it while it is running.

When the plugin is turned off in the config, or a world is removed from the `worlds` list, every silverfish with the tag is restored: its AI is turned back on, its collisions are turned back on and the tag is removed. This happens to loaded silverfish when you run `/fsf reload`, and to the others when their chunk is loaded.

### Drowning

Swimming up to the surface is also part of the AI. A frozen silverfish sinks in water, and silverfish have their eyes very close to the ground, so even shallow flowing water covers their head. Without help they drown after about 15 to 20 seconds in water, which can happen in a long water stream or while they wait in the killing chamber. They would die without giving XP.

With `prevent-drowning: true` (the default) the plugin cancels drowning damage for frozen silverfish. They still run out of air, they just don't take damage from it. This costs almost nothing: the damage event only fires about once per second for a silverfish that is actually drowning. Silverfish with their AI on are not affected, because they swim up by themselves.

### Collisions

With `disable-collisions: true` the plugin also calls `setCollidable(false)` on frozen silverfish. They stop pushing each other and other mobs, and they are no longer affected by entity cramming. In the benchmark below this saved a lot of time when many silverfish were packed together.

Two things to know:

- Without cramming, nothing limits how many silverfish can pile up in one block. That's fine if the farm kills them as soon as they arrive, but if nobody is in the killing chamber while the armadillos are being hit, they will keep piling up.
- Collisions are turned off on the server only. The Minecraft client doesn't know about it, so if you stand among the silverfish your own player may still look like it's being pushed. That push is calculated by your client and doesn't cost the server anything.

## Requirements

- Paper 26.2
- Java 25 (Paper 26.2 needs it anyway)

The plugin uses only the Paper API. It has no other dependencies.

## Installation

1. Download the jar from the [releases page](https://github.com/ghiacciolodev/FrozenSilverfish/releases), or build it yourself (see [Building from source](#building-from-source)).
2. Put `FrozenSilverfish-<version>.jar` in the `plugins` folder of your server.
3. Restart the server.

On the first start the plugin creates `plugins/FrozenSilverfish/config.yml` and freezes all loaded silverfish.

## Configuration

The config file is `plugins/FrozenSilverfish/config.yml`. After changing it, run `/fsf reload`. You don't need to restart the server.

This is the default config:

```yaml
enabled: true
disable-collisions: false
prevent-drowning: true
worlds: []
```

The numbers below come from the [performance benchmark](#performance-benchmark) with 2000 silverfish on land, compared with silverfish that have their AI on.

### enabled

Default: `true`

Turns the plugin on or off.

- `true`: every silverfish in the active worlds gets its AI turned off. On its own this saved about 30% of the time silverfish cost.
- `false`: the plugin stops freezing silverfish, and the ones it froze get their AI back. Loaded silverfish are restored on `/fsf reload`, the others when their chunk is loaded. Use this before uninstalling, see [Uninstalling](#uninstalling).

### disable-collisions

Default: `false`

Also turns off collisions for frozen silverfish. See [Collisions](#collisions) for the details.

- `false`: frozen silverfish push each other and other mobs like normal, and entity cramming still kills them when more than 24 are in the same spot.
- `true`: frozen silverfish don't push each other or other mobs, and entity cramming doesn't affect them. Together with the AI off this saved about 57% on land and 49% in water, compared with about 30% and 26% with the AI off alone. The difference grows with the number of silverfish that are close together.

The risk is that nothing limits how many silverfish can pile up. This is why the default is `false`. Turn it on only if silverfish are killed soon after they arrive, and check that your farm still works.

### prevent-drowning

Default: `true`

Frozen silverfish can't swim up, so they sink and drown after about 15 to 20 seconds in water. See [Drowning](#drowning).

- `true`: the plugin cancels drowning damage for frozen silverfish. In the tests a frozen silverfish stayed 30 seconds under water at full health, and 2000 frozen silverfish stayed alive under water during the whole benchmark. Its cost is already included in the water numbers of the benchmark.
- `false`: frozen silverfish drown like any mob that can't swim. Only use this if you actually want that, for example if you don't care about XP and want silverfish to disappear by themselves.

Silverfish with their AI on are not affected by this option, because they swim up by themselves.

### worlds

Default: `[]`

The worlds where the plugin is active, by name, for example `[world, world_nether]`.

- `[]` (empty list): the plugin is active in all worlds.
- A list of names: the plugin is active only in those worlds. Silverfish in other worlds keep their AI, and the ones the plugin had frozen get it back.

Keep in mind that the plugin freezes every silverfish in an active world, not only the ones from farms. Silverfish from strongholds, infested blocks in the mountains or spawners will also be harmless there. If that matters for your server and the farm is in a separate world, list only that world.

### Recommended configuration

For a farm like ours, where silverfish travel through water and a player kills them as soon as they reach the killing chamber:

```yaml
enabled: true
disable-collisions: true
prevent-drowning: true
worlds: []
```

This is what the tests support:

- It gave the biggest saving in the benchmark: 37% to 57% less time spent on silverfish on land and 42% to 49% in water, depending on how many there were.
- In a test farm with 4 stations of armadillos, silverfish still travelled along the water streams to the killing chamber and died to a Sweeping Edge hit on the armor stand with these settings.
- With `prevent-drowning: true` no frozen silverfish drowned, in the farm or in the benchmark.
- With `disable-collisions: true`, 40 silverfish in the same block all survived. This is the part to watch: if nobody kills them, they won't be limited by cramming.

If you can't be sure that silverfish are always killed soon after they arrive, or if your farm relies on mobs pushing each other, use the default config instead. It still saves about 20% to 30% with no change in how silverfish interact with other mobs.

Set `worlds` if you want silverfish outside your farm world to keep their AI, as explained above.

## Commands and permissions

All commands need the `frozensilverfish.admin` permission, which ops have by default. `/fsf` is a short alias for `/frozensilverfish`.

- `/fsf reload` reloads the config, applies it to all loaded silverfish and tells you how many were updated. Silverfish that were already in the right state are not counted.
- `/fsf status` shows whether the plugin is enabled and in which worlds, how many silverfish are loaded, how many of them have no AI, and whether collisions are disabled and drowning is prevented.

## Test results

All tests were run on a local test server: Paper 26.2 build 129, Java 25.0.4, Windows 11, AMD Ryzen 5 5600, 32 GB RAM (2 GB for the server), flat world, simulation distance 4.

### Functional tests

| What was tested | How | Result |
|---|---|---|
| Startup | Start the server with the plugin | No errors or warnings from the plugin in the console |
| Gravity | Summon a silverfish 5 blocks above the ground | It falls to the ground, takes fall damage (8 to 6 health) and then doesn't move by itself. It has the `frozen_silverfish` tag and no NoAI tag |
| Water transport | Summon a silverfish in a flowing water channel | It is carried along the channel (from x 6.5 to x 13.2 in 5 seconds) |
| Damage | `/damage` on a frozen silverfish | Damage is applied normally |
| Farm | A farm with 4 stations of armadillos with Infested, water streams and a killing chamber | Silverfish reach the killing chamber and die to a Sweeping Edge sword hit on the armor stand |
| Status | `/fsf status` with 3 and 4 loaded silverfish, and after unloading a chunk | Counts are correct, including after the chunk unload |
| Disable | `enabled: false` and `/fsf reload` | Loaded silverfish get their AI back, lose the tag and walk away (10 to 20 blocks in a few seconds) |
| Disable, unloaded chunk | Freeze a silverfish, unload its chunk, disable the plugin, load the chunk | The silverfish is restored when the chunk loads |
| World filter | `worlds: [world_nether]` and `/fsf reload` | Silverfish in the overworld get their AI back. Setting `worlds: []` freezes them again |
| Restart | Stop and start the server | Silverfish are still frozen after the restart |
| Collisions and cramming | 40 frozen silverfish in the same block, `max_entity_cramming` at 24 | With `disable-collisions: true` all 40 are still alive after 15 seconds. With `false`, cramming brings them down to 24 within 5 seconds |
| Drowning, AI on | A normal silverfish in water 2 blocks deep | It swims up and still has full air and health after 25 seconds |
| Drowning, frozen | A frozen silverfish in the same water, `prevent-drowning: false` | It sinks and drowns after about 19 seconds |
| Drowning prevented | Same test with `prevent-drowning: true` | After 30 seconds it is out of air but still at full health |

### Performance benchmark

The goal was to measure how much time silverfish cost per server tick without the plugin, with the plugin, and with the plugin and all its options on. This was run with the final version of the plugin, with `prevent-drowning: true` in every phase.

Setup:

- A closed glass box of 60 by 60 blocks, far from anything else, with glass floor and roof so silverfish can't hide in blocks or escape.
- One player standing on the roof in survival mode. Silverfish ignore players in creative, so with their AI on they keep trying to reach this player without success. This is close to what happens in the farm while someone is in the killing chamber.
- Monster spawning off, and entity cramming off during the test, so the number of silverfish stays the same in every phase.

There were two scenarios:

- Land: silverfish on the dry floor of the box.
- Water: the floor of the box covered with one layer of still water after the silverfish are summoned. This is closer to a farm, where silverfish spend their time in water. Silverfish with AI swim at the surface. Frozen silverfish sink, run out of air and are kept alive by drowning prevention, so its cost is included in these numbers.

For each scenario and number of silverfish there were three phases:

1. AI on: the plugin disabled. This is how silverfish behave without the plugin.
2. AI off: the plugin enabled with `disable-collisions: false` (the default config).
3. AI off and no collisions: the plugin enabled with `disable-collisions: true` (all options on).

In every phase the silverfish from the previous phase were removed and new ones were summoned at random positions on the floor. After 70 seconds the tick time was read with Paper's `/mspt` command (average over the last 10 seconds). The number of silverfish alive was checked at the end of each phase, and none had died in any phase.

Results, in milliseconds per tick. A tick can take up to 50 ms before the TPS drops below 20. The percentages compare each column with AI on.

Land (without silverfish the server used 1.0 ms per tick):

| Silverfish | AI on | AI off | AI off, no collisions |
|---|---|---|---|
| 500 | 5.1 ms | 3.8 ms (25% less) | 3.2 ms (37% less) |
| 1000 | 9.8 ms | 6.7 ms (32% less) | 5.7 ms (42% less) |
| 2000 | 22.1 ms | 15.4 ms (30% less) | 9.6 ms (57% less) |

Water (without silverfish the server used 0.8 ms per tick):

| Silverfish | AI on | AI off | AI off, no collisions |
|---|---|---|---|
| 1000 | 12.2 ms | 9.9 ms (19% less) | 7.1 ms (42% less) |
| 2000 | 28.1 ms | 20.9 ms (26% less) | 14.2 ms (49% less) |

Two earlier runs, on land and with an earlier version of the plugin, gave similar numbers: 23% to 33% less with AI off and 34% to 49% less with collisions off too. The differences between runs are normal measuring noise of a few points.

What this means:

- With the default config (AI off), silverfish cost about 20% to 30% less than normal ones.
- With all options on (AI off and no collisions), they cost about 40% to 57% less. With many silverfish close together, that is about half.
- Silverfish still run their physics, and collisions between them get more expensive the more they are packed together. That is why turning off collisions matters more at 2000 silverfish than at 500.
- Silverfish in water cost more than on land in every phase. The saving from AI off is a bit smaller in water, but collisions off still cuts the cost roughly in half at 2000.
- In the water scenario every frozen silverfish was out of air, so drowning prevention was working for all of them at the same time. Its cost is already included in the water numbers.
- The plugin doesn't make an unlimited number of silverfish free. If a farm produces them faster than they are killed, the lag will come back.

Limits of this test:

- It is a synthetic test in a box, not a real farm. Silverfish in a farm move through water streams and gather in the killing chamber, so the numbers on a real server will be different.
- The absolute numbers depend on the hardware. A slower server will show higher values, but the differences between the columns should be similar.

## Checking the result on your own server

Measure the server while the farm is running, once with the plugin disabled and once with it enabled. Paper's `/mspt` command gives a quick number. For more detail use [spark](https://spark.lucko.me/):

```
/spark profiler start
```

Let it run for a few minutes with the farm active, then stop it with `/spark profiler stop` and compare the two reports. With the plugin enabled, the time spent on silverfish AI (pathfinding and goal selectors) should mostly disappear.

## Uninstalling

The aware flag is saved with the entity, so if you just remove the plugin, the silverfish that were frozen stay frozen forever. Without the plugin they would also drown in water, because drowning prevention stops with it.

To remove it cleanly:

1. Set `enabled: false` in the config and run `/fsf reload`. This restores all loaded silverfish.
2. Leave the plugin installed for a while, so that silverfish in chunks that load later are restored too. If all your silverfish are in areas that are usually loaded, this step is quick. `/fsf status` shows how many loaded silverfish still have no AI.
3. Stop the server and remove the jar and the `plugins/FrozenSilverfish` folder.

## Building from source

You need Java 25. If it isn't installed, Gradle downloads it automatically the first time.

```
./gradlew build
```

On Windows use `gradlew.bat build`. The jar ends up in `build/libs`.

## License

FrozenSilverfish is released under the MIT License. See [LICENSE](LICENSE).
