"""Functional tests for FrozenSilverfish, run against a live Paper test server.

Needs a flat world (surface at y -60), RCON enabled, the plugin and the
FsfTest helper plugin installed. See testing/README.md.

Run it once, restart the server, then run it again with --after-restart.
"""
import argparse
import re
import sys
import time

from rcon import Rcon, add_arguments, write_config

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
add_arguments(parser)
parser.add_argument('--after-restart', action='store_true', help='run the check that follows a restart')
args = parser.parse_args()

rc = Rcon(args.host, args.port, args.password)
results = []


def cfg(enabled=True, col=False, drown=True, worlds='[]'):
    write_config(args.config, enabled, col, drown, worlds)
    return rc('fsf reload')


def q(tag):
    return rc(f'fsftest query {tag}')

def check(name, cond, detail=''):
    results.append((name, cond))
    print(('PASS ' if cond else 'FAIL ') + name + ('' if cond else '  -> ' + detail), flush=True)

def state(tag, **want):
    out = q(tag)
    ok = all(f'{k}={str(v).lower()}' in out for k, v in want.items())
    return ok, out

def summon(tag, x, y, z, extra=''):
    rc(f'summon silverfish {x} {y} {z} {{Tags:["{tag}","fsft"],PersistenceRequired:1b{extra}}}')

def num(cmd):
    m = re.search(r'Count: (\d+)', rc(cmd))
    return int(m.group(1)) if m else 0

def pos(tag):
    m = re.findall(r'-?[\d.]+(?=d)', rc(f'data get entity @e[tag={tag},limit=1] Pos'))
    return [float(v) for v in m]

def health(tag):
    m = re.search(r'([\d.]+)f', rc(f'data get entity @e[tag={tag},limit=1] Health'))
    return float(m.group(1)) if m else None

def setup():
    rc('kill @e[tag=fsft]')
    rc('forceload add 384 384 431 431')
    time.sleep(2)
    # water pool and flowing channel
    rc('fill 398 -62 398 402 -60 402 glass')
    rc('fill 399 -61 399 401 -60 401 water')
    rc('fill 409 -61 404 422 -60 406 stone')
    rc('fill 410 -60 405 422 -60 405 air')
    rc('setblock 410 -60 405 water')
    time.sleep(3)

def run_main():
    setup()

    # basic freeze, collisions left alone
    cfg(col=False)
    summon('a', 405.5, -60, 400.5)
    ok, out = state('a', aware=False, collidable=True, frozenTag=True, marker=False)
    check('new silverfish is frozen and tagged, collisions untouched', ok, out)

    # gravity
    summon('g', 407.5, -55, 400.5)
    time.sleep(3)
    p = pos('g')
    check('frozen silverfish falls (gravity)', p and abs(p[1] + 60) < 0.01, str(p))
    time.sleep(3)
    p2 = pos('g')
    check('frozen silverfish does not walk by itself', p2 and abs(p2[0] - p[0]) < 0.01 and abs(p2[2] - p[2]) < 0.01, f'{p} {p2}')
    check('fall damage is applied', health('g') is not None and health('g') < 8, str(health('g')))

    # water transport
    summon('w', 411.5, -60, 405.5)
    time.sleep(5)
    p = pos('w')
    check('frozen silverfish is carried by flowing water', p and p[0] > 414, str(p))

    # damage
    rc('damage @e[tag=a,limit=1] 3 minecraft:generic')
    check('damage is applied', health('a') == 5.0, str(health('a')))

    # collisions on and off, only on our silverfish
    cfg(col=True)
    ok, out = state('a', collidable=False, marker=True)
    check('disable-collisions true turns collisions off and marks it', ok, out)
    cfg(col=False)
    ok, out = state('a', collidable=True, marker=False)
    check('disable-collisions false restores collisions and removes the marker', ok, out)

    # cramming
    rc('gamerule max_entity_cramming 24')
    cfg(col=True)
    for _ in range(40):
        summon('cram', 425.5, -60, 425.5)
    time.sleep(15)
    check('no collisions: 40 in one block survive cramming', num('execute if entity @e[tag=cram]') == 40, str(num('execute if entity @e[tag=cram]')))
    cfg(col=False)
    time.sleep(8)
    n = num('execute if entity @e[tag=cram]')
    check('with collisions: cramming works again', n <= 24, str(n))
    rc('kill @e[tag=cram]')

    # another plugin turned the AI off before us
    cfg(enabled=False)
    summon('b', 405.5, -60, 410.5)
    rc('fsftest aware false b')
    cfg(enabled=True, col=True)
    ok, out = state('b', aware=False, frozenTag=False, collidable=True, marker=False)
    check('silverfish made unaware by another plugin is not tagged and its collisions are untouched', ok, out)
    cfg(enabled=False)
    ok, out = state('b', aware=False, frozenTag=False)
    check('disabling the plugin does not turn its AI back on', ok, out)

    # another plugin turned collisions off on one of our silverfish, option off
    cfg(enabled=True, col=False)
    summon('c', 405.5, -60, 415.5)
    rc('fsftest collide false c')
    cfg(enabled=True, col=False)
    ok, out = state('c', aware=False, frozenTag=True, collidable=False, marker=False)
    check('disable-collisions false does not force collisions back on', ok, out)
    cfg(enabled=False)
    ok, out = state('c', aware=True, frozenTag=False, collidable=False, marker=False)
    check('restore turns the AI back on but leaves the other plugin\'s collisions', ok, out)

    # another plugin turned collisions off, option on then off
    cfg(enabled=True, col=False)
    summon('d', 405.5, -60, 420.5)
    rc('fsftest collide false d')
    cfg(enabled=True, col=True)
    ok, out = state('d', collidable=False, marker=False)
    check('disable-collisions true does not claim collisions turned off by someone else', ok, out)
    cfg(enabled=True, col=False)
    ok, out = state('d', collidable=False)
    check('turning the option off leaves them off', ok, out)

    # status
    out = rc('fsf status')
    loaded = num('execute if entity @e[type=silverfish]')
    tagged = num('execute if entity @e[type=silverfish,tag=frozen_silverfish]')
    m = re.search(r'Loaded silverfish: (\d+), frozen by this plugin: (\d+)', out)
    check('status counts match', m and int(m.group(1)) == loaded and int(m.group(2)) == tagged,
          f'{out} loaded={loaded} tagged={tagged}')
    m = re.search(r'Without AI but not frozen by this plugin: (\d+)', out)
    check('status counts silverfish made unaware by another plugin', m and int(m.group(1)) >= 1, out)

    # unknown world name
    out = cfg(enabled=True, worlds='[wrold]')
    check('unknown world in the list gives a warning', "World 'wrold'" in out, out)

    # world filter
    cfg(enabled=True, worlds='[world_nether]')
    ok, out = state('a', aware=True, frozenTag=False)
    check('world not in the list: silverfish restored', ok, out)
    cfg(enabled=True, col=True)
    ok, out = state('a', aware=False, frozenTag=True, collidable=False, marker=True)
    check('world back in the list: frozen again', ok, out)

    # chunk unload and load
    rc('forceload add 800 800')
    time.sleep(2)
    summon('e', 800.5, -60, 800.5)
    ok, out = state('e', aware=False, collidable=False, marker=True)
    check('far silverfish frozen with collisions off', ok, out)
    rc('forceload remove 800 800')
    time.sleep(10)
    check('far chunk unloaded', 'none' in q('e'), q('e'))
    rc('forceload add 800 800')
    time.sleep(3)
    ok, out = state('e', aware=False, frozenTag=True, collidable=False, marker=True)
    check('after chunk load: still frozen, collisions off again', ok, out)
    rc('forceload remove 800 800')
    time.sleep(10)
    cfg(enabled=False)
    rc('forceload add 800 800')
    time.sleep(3)
    ok, out = state('e', aware=True, frozenTag=False, collidable=True, marker=False)
    check('disabled while unloaded: fully restored on chunk load', ok, out)
    rc('kill @e[tag=e]')
    rc('forceload remove 800 800')

    # drowning
    cfg(enabled=True, drown=False)
    summon('dr1', 400.5, -61, 400.5)
    time.sleep(25)
    check('prevent-drowning false: frozen silverfish drowns', 'none' in q('dr1'), q('dr1'))
    cfg(enabled=True, drown=True)
    summon('dr2', 400.5, -61, 400.5)
    time.sleep(30)
    air = rc('data get entity @e[tag=dr2,limit=1] Air')
    check('prevent-drowning true: out of air but full health', health('dr2') == 8.0 and '-' in air, f'{air} {health("dr2")}')
    rc('kill @e[tag=dr2]')

    cfg(enabled=False)
    summon('ai', 400.5, -61, 400.5)
    time.sleep(25)
    check('AI on: silverfish swims and keeps its air', health('ai') == 8.0, str(health('ai')))
    rc('kill @e[tag=ai]')

    # leave some silverfish for the restart test
    cfg(enabled=True, col=True)
    rc('kill @e[tag=fsft]')
    summon('r', 405.5, -60, 400.5)
    ok, out = state('r', aware=False, collidable=False, marker=True)
    check('before restart: frozen with collisions off', ok, out)

def run_restart():
    ok, out = state('r', aware=False, frozenTag=True, collidable=False, marker=True)
    check('after restart: still frozen, collisions off again', ok, out)
    rc('kill @e[tag=fsft]')
    rc('forceload remove all')
    for x1, z1, x2, z2 in ((398, 398, 402, 402), (409, 404, 422, 406)):
        rc(f'fill {x1} -62 {z1} {x2} -62 {z2} dirt')
        rc(f'fill {x1} -61 {z1} {x2} -61 {z2} grass_block')
        rc(f'fill {x1} -60 {z1} {x2} -60 {z2} air')

(run_restart if args.after_restart else run_main)()
failed = [name for name, ok in results if not ok]
print(f'{len(results) - len(failed)}/{len(results)} passed', flush=True)
sys.exit(1 if failed else 0)
