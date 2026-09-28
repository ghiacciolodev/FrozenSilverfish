"""Campfire farm test for FrozenSilverfish, run against a live Paper test server.

Builds 12 stations around x 600-624, z 600-616. Each station is one campfire
with 20 armadillos on it (Infested, Regeneration, no AI), a glass ring one
block above the campfire that armadillos can't pass under but silverfish can,
and a glass block on top. Runs once with push-off-campfires off and once with
it on, and prints how many silverfish spawned, died on a campfire and are
still alive. Needs the FsfTest helper. See testing/README.md.

The silverfish and armadillos are left in place at the end, so you can look at
them. Remove them with /kill @e[type=!player].
"""
import argparse
import re
import time

from rcon import Rcon, add_arguments, write_config

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
add_arguments(parser)
parser.add_argument('--seconds', type=int, default=45, help='how long each phase runs (default 45)')
parser.add_argument('--player', help='player to teleport above the stations, to watch')
args = parser.parse_args()

rc = Rcon(args.host, args.port, args.password)
STATIONS = [(600 + i * 8, 600 + j * 8) for i in range(4) for j in range(3)]


def count(selector):
    m = re.search(r'Count: (\d+)', rc(f'execute if entity {selector}'))
    return int(m.group(1)) if m else 0


def on_campfires():
    return sum(count(f'@e[type=silverfish,x={x},y=-59.6,z={z},dx=0,dy=0.2,dz=0]') for x, z in STATIONS)


def campfires(lit):
    for x, z in STATIONS:
        rc(f'setblock {x} -60 {z} campfire[lit={str(lit).lower()}]')


def mspt():
    return ' '.join(rc('mspt').split())


def build():
    rc('forceload add 584 584 640 632')
    time.sleep(3)
    for x, z in STATIONS:
        # Same as the stations built by hand: a glass ring one block above the
        # campfire (armadillos can't pass under it, silverfish can) and a glass
        # block on top.
        rc(f'fill {x - 1} -59 {z - 1} {x + 1} -59 {z + 1} glass')
        rc(f'setblock {x} -59 {z} air')
        rc(f'setblock {x} -58 {z} glass')
    campfires(False)
    for x, z in STATIONS:
        for k in range(20):
            rc(f'summon armadillo {x + 0.5} -59.5 {z + 0.5} '
               '{Tags:["farm"],PersistenceRequired:1b,NoAI:1b,'
               'active_effects:[{id:"minecraft:infested",duration:-1,amplifier:0,show_particles:0b},'
               '{id:"minecraft:regeneration",duration:-1,amplifier:4,show_particles:0b}]}')


def phase(push):
    campfires(False)
    rc('kill @e[type=silverfish]')
    write_config(args.config, True, True, True, campfire_push=push)
    rc('fsf reload')
    time.sleep(3)
    before = mspt()
    rc('fsftest resetstats')
    campfires(True)
    time.sleep(args.seconds)
    during = mspt()
    campfires(False)
    time.sleep(3)
    print(f'push={push}: {rc("fsftest stats")}', flush=True)
    print(f'  alive={count("@e[type=silverfish]")} on_campfires={on_campfires()}', flush=True)
    print(f'  mspt before (campfires off): {before}', flush=True)
    print(f'  mspt during (campfires lit): {during}', flush=True)


rc('gamerule spawn_monsters false')
build()
time.sleep(3)
print('armadillos:', count('@e[type=armadillo,tag=farm]'), flush=True)
if args.player:
    rc(f'tp {args.player} 612 -50 596 0 60')

phase(False)
phase(True)
time.sleep(5)
print(f'5s after the end: alive={count("@e[type=silverfish]")} on_campfires={on_campfires()}', flush=True)
