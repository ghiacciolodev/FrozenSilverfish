"""Performance benchmark for FrozenSilverfish, run against a live Paper test server.

Builds a closed glass box, puts the player on its roof in survival, and for
each number of silverfish measures /mspt with AI on, AI off, and AI off with
no collisions. Needs a flat world (surface at y -60), RCON enabled and the
player online. See testing/README.md.

The player is teleported to the box and back, and gets Resistance and
Saturation during the test. The config file is restored at the end.
"""
import argparse
import re
import time

from rcon import Rcon, add_arguments, write_config

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
add_arguments(parser)
parser.add_argument('--player', required=True, help='player who stands on the roof of the box')
parser.add_argument('--reverse', action='store_true',
                    help='run water before land, more silverfish before fewer, and AI on last')
parser.add_argument('--wait', type=int, default=70, help='seconds to wait before reading /mspt (default 70)')
parser.add_argument('--x', type=int, default=200, help='center of the box (default 200)')
parser.add_argument('--z', type=int, default=200, help='center of the box (default 200)')
args = parser.parse_args()

rc = Rcon(args.host, args.port, args.password)
CX, CZ = args.x, args.z
FLOOR = f'{CX - 29} -60 {CZ - 29} {CX + 29} -60 {CZ + 29}'

with open(args.config, encoding='utf-8') as f:
    original_config = f.read()


def count():
    m = re.search(r'Count: (\d+)', rc('execute if entity @e[type=silverfish,tag=bench]'))
    return int(m.group(1)) if m else 0


def spawn(n):
    # 100 markers spread around the floor, one silverfish on each, repeated.
    for _ in range(100):
        rc(f'summon marker {CX} -60 {CZ} {{Tags:["bm"]}}')
    for _ in range(n // 100):
        rc(f'spreadplayers {CX} {CZ} 0 27 under -58 false @e[type=marker,tag=bm]')
        rc('execute at @e[type=marker,tag=bm] run summon silverfish ~ ~ ~ {Tags:["bench"],PersistenceRequired:1b}')
    rc('kill @e[type=marker,tag=bm]')


def phase(scenario, label, n, enabled, collisions_off):
    rc('kill @e[type=silverfish,tag=bench]')
    rc(f'fill {FLOOR} air replace water')
    write_config(args.config, enabled, collisions_off, True)
    rc('fsf reload')
    time.sleep(3)
    if n:
        spawn(n)
    if scenario == 'water':
        rc(f'fill {FLOOR} water replace air')
    time.sleep(args.wait)
    mspt = ' '.join(rc('mspt').split())
    print(f'{scenario} | {label} | {n} silverfish | {count()} alive | {mspt}', flush=True)


phases = [('AI on', False, False), ('AI off', True, False), ('AI off + no collisions', True, True)]
scenarios = [('land', [500, 1000, 2000]), ('water', [1000, 2000])]
if args.reverse:
    phases.reverse()
    scenarios = [(name, counts[::-1]) for name, counts in reversed(scenarios)]

position = re.findall(r'-?[\d.]+(?=d)', rc(f'data get entity {args.player} Pos'))
if len(position) != 3:
    raise SystemExit(f'player {args.player} is not online')
mode = rc(f'data get entity {args.player} playerGameType')

rc(f'fill {CX - 30} -61 {CZ - 30} {CX + 30} -56 {CZ + 30} glass hollow')
rc(f'fill {CX - 29} -60 {CZ - 29} {CX + 29} -57 {CZ + 29} air')
rc(f'tp {args.player} {CX}.5 -55 {CZ}.5')
rc(f'gamemode survival {args.player}')
rc(f'effect give {args.player} resistance infinite 4 true')
rc(f'effect give {args.player} saturation infinite 0 true')
rc('gamerule max_entity_cramming 0')
time.sleep(10)

try:
    for scenario, counts in scenarios:
        phase(scenario, 'baseline', 0, True, False)
        for n in counts:
            for label, enabled, collisions_off in phases:
                phase(scenario, label, n, enabled, collisions_off)
finally:
    rc('kill @e[type=silverfish,tag=bench]')
    rc(f'fill {FLOOR} air replace water')
    with open(args.config, 'w', encoding='utf-8') as f:
        f.write(original_config)
    rc('fsf reload')
    rc('gamerule max_entity_cramming 24')
    rc(f'effect clear {args.player}')
    modes = {'0': 'survival', '1': 'creative', '2': 'adventure', '3': 'spectator'}
    rc(f'gamemode {modes.get(mode.split()[-1], "survival")} {args.player}')
    rc(f'tp {args.player} {position[0]} {position[1]} {position[2]}')
    print('done', flush=True)
