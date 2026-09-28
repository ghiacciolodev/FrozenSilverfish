# Testing

These are the scripts used to test FrozenSilverfish on a real Paper server. They are not unit tests: they send commands to a running test server through RCON and check what happens to real silverfish, including physics, water and collisions.

Don't run them on a server with players. They build structures, summon and kill silverfish, change gamerules and rewrite the plugin's config file.

## What's here

- `suite.py`: the functional tests. It checks freezing, gravity, water transport, damage, collisions and cramming, silverfish changed by other plugins, `/fsf status`, the world filter and warnings, chunk unload and load, drowning, campfires, and restarts.
- `benchmark.py`: the performance benchmark described in the main README.
- `campfire_farm.py`: the campfire test described in the main README, with 12 stations of 20 armadillos on campfires.
- `helper/`: a small plugin used only by the tests. Commands can't show whether an entity is collidable or had its AI turned off through the API, so the helper reads those values. It can also act as "another plugin" that turns off the AI or the collisions of a silverfish, count the silverfish spawned by Infested and how silverfish die (`/fsftest stats`), and write the blocks of an area to a file (`/fsftest scan`). Never install it on a real server.
- `rcon.py`: the RCON client shared by the scripts.

## Setting up the test server

1. Use a fresh Paper 26.2 server with a flat world. The scripts expect the ground at y -60, which is the default flat world. In `server.properties`:

   ```
   level-type=minecraft\:flat
   enable-rcon=true
   rcon.password=<something>
   ```

2. Build the plugin and the helper from the root of the repository:

   ```
   ./gradlew build
   ./gradlew -p testing/helper build
   ```

3. Put `build/libs/FrozenSilverfish-<version>.jar` and `testing/helper/build/libs/FsfTest.jar` in the server's `plugins` folder and start the server.

4. Turn off monster spawning, so other mobs don't get in the way:

   ```
   /gamerule spawn_monsters false
   ```

The scripts need Python 3.10 or newer, with no extra packages.

## Running the functional tests

From the `testing` folder:

```
python suite.py --password <rcon password> --config <server>/plugins/FrozenSilverfish/config.yml
```

It takes about 6 minutes and prints PASS or FAIL for each check. Then restart the server and run the last check:

```
python suite.py --password <rcon password> --config <server>/plugins/FrozenSilverfish/config.yml --after-restart
```

The tests use the area around x 400, z 400 and a chunk at x 800, z 800. The exit code is 0 when everything passed.

## Running the campfire test

```
python campfire_farm.py --password <rcon password> --config <server>/plugins/FrozenSilverfish/config.yml
```

It builds 12 stations around x 600 to 624, z 600 to 616, runs 45 seconds with `push-off-campfires: false` and 45 with `true`, and prints for each phase how many silverfish Infested spawned, how many died on a campfire, how many are alive and the output of `/mspt`. Use `--seconds` to change the length of each phase and `--player <name>` to be teleported above the stations to watch. The mobs are left in place at the end, remove them with `/kill @e[type=!player]`.

## Running the benchmark

A player has to be online, because silverfish with AI behave differently when there is a player to chase. The player is teleported onto the roof of a glass box at x 200, z 200 and put in survival with Resistance, and is sent back at the end.

```
python benchmark.py --password <rcon password> --config <server>/plugins/FrozenSilverfish/config.yml --player <name>
```

Add `--reverse` to run the phases in the opposite order. It takes about 23 minutes and prints one line per phase with the output of `/mspt`. The main README uses the 10 second average.
