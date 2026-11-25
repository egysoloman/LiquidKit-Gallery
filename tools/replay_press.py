"""Replay the SwiftUI sample's recorded point paths on an Android emulator, in dp."""
import argparse
import json
import math
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


class Emulator:
    def __init__(self, adb, serial):
        self.command = [adb, '-s', serial]
        density = self.shell('wm', 'density')
        self.scale = int(re.findall(r'density:\s*(\d+)', density)[-1]) / 160

    def call(self, *args):
        return subprocess.check_output([*self.command, *map(str, args)], text=True, timeout=30)

    def shell(self, *args):
        return self.call('shell', *args)

    def tree(self):
        xml = self.call('exec-out', 'uiautomator', 'dump', '/dev/tty')
        return list(ET.fromstring(xml[xml.index('<?xml'):xml.rindex('</hierarchy>') + 12]).iter('node'))

    @staticmethod
    def bounds(nodes, title):
        for node in nodes:
            if title in (node.get('text'), node.get('content-desc')):
                return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))
        return None

    def tap(self, title):
        bounds = self.bounds(self.tree(), title)
        if bounds is None:
            raise RuntimeError(f'找不到 {title}')
        x, y, right, bottom = bounds
        self.shell('input', 'tap', (x + right) // 2, (y + bottom) // 2)
        time.sleep(.35)

    def sample(self):
        self.shell('am', 'force-stop', 'com.kyant.foldfiles')
        self.shell('am', 'start', '-W', '-n', 'com.kyant.foldfiles/.FilesActivity')
        for item in ('显示选项', '组件目录', '全部组件'):
            self.tap(item)
        if not self.bounds(self.tree(), '按压与回弹'):
            size = list(map(int, re.findall(r'(\d+)x(\d+)', self.shell('wm', 'size'))[-1]))
            self.shell('input', 'swipe', size[0] // 2, int(size[1] * .8), size[0] // 2, int(size[1] * .3), 250)
        self.tap('按压与回弹')

    def action_row(self, nodes, title):
        cell = self.bounds(nodes, title)
        if cell is None:
            raise RuntimeError(f'未显示菜单行 {title}')
        # Use the full action row when accessibility exposes only its text subview.
        rows = [tuple(map(int, re.findall(r'\d+', n.get('bounds')))) for n in nodes]
        fitting = [(l, t, r, b) for l, t, r, b in rows if l <= cell[0] and t <= cell[1]
                   and r >= cell[2] and b >= cell[3] and r - l > 165*self.scale and b - t < 55*self.scale]
        return min(fitting, key=lambda v: (v[2]-v[0])*(v[3]-v[1])) if fitting else cell

    def replay(self, origin, trace):
        points = [(0, 0, 0), *trace['path'], (trace['duration'], trace['dx'], trace['dy'])]
        clock = time.monotonic()
        sent = []
        for i, (deadline, dx, dy) in enumerate(points):
            if 0 < i < len(points)-2 and time.monotonic()-clock > points[i+1][0]:
                continue
            time.sleep(max(0, clock + deadline - time.monotonic()))
            x, y = round(origin[0] + self.scale*dx), round(origin[1] + self.scale*dy)
            begin = time.monotonic()-clock
            state = 0 if i == len(points)-1 else 1
            if 'OK' not in self.call('emu', 'event', 'mouse', x, y, 0, state):
                raise RuntimeError('模拟器未接受触点')
            sent.append(dict(expected=deadline, sent=begin, acknowledged=time.monotonic()-clock, x=x, y=y))
        return sent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inputs', type=Path)
    parser.add_argument('output', type=Path, help='Directory for the recorded video and input receipt')
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    emulator = Emulator(args.adb, args.serial)
    traces = json.loads(args.inputs.read_text())
    args.output.mkdir(parents=True, exist_ok=True)
    emulator.sample()
    remote = f'/sdcard/fold-press-{time.time_ns()}.mp4'
    recorder = subprocess.Popen([*emulator.command, 'shell', 'screenrecord', '--time-limit', '180', remote])
    receipts = {}
    active_role = 'button'
    try:
        time.sleep(.75)
        nodes = emulator.tree()
        for key, trace in traces.items():
            role, distance, _, _ = key.split('-')
            if abs(math.hypot(trace['dx'], trace['dy']) - int(distance)) > .5:
                raise ValueError(f'路径终点偏离标称长度 {key}')
            if role != active_role:
                emulator.tap('菜单' if role == 'menu' else '圆钮')
                active_role, nodes = role, emulator.tree()
            if role == 'menu' and not emulator.bounds(nodes, '项目 5'):
                emulator.tap('展开样张菜单')
                nodes = emulator.tree()
            if role == 'menu':
                reference = emulator.action_row(nodes, '项目 3')
                l, _, _, b = emulator.action_row(nodes, '项目 5')
                origin = l + 4*emulator.scale, b - 4*emulator.scale
            else:
                reference = emulator.bounds(nodes, '样张圆钮')
                l, t, r, b = reference
                origin = (l + r) / 2, (t + b) / 2
            events = emulator.replay(origin, trace)
            time.sleep(.8)
            if role == 'menu':
                nodes = emulator.tree()
            visible = role == 'button' or emulator.bounds(nodes, '项目 5') is not None
            receipts[key] = dict(origin=origin, reference=reference, px_per_dp=emulator.scale,
                                 visible=visible, events=events)
            (args.output/'receipts.json').write_text(json.dumps(receipts, ensure_ascii=False, indent=1))
            print(key, 'visible' if visible else 'dismissed', flush=True)
    finally:
        # Stop only the recorder opened for this run.
        matches = emulator.shell('ps', '-A', '-o', 'PID,ARGS').splitlines()
        for line in matches:
            if 'screenrecord ' in line and line.strip().endswith(remote):
                emulator.shell('kill', '-INT', line.strip().split()[0])
        recorder.wait(timeout=10)
        emulator.call('pull', remote, args.output/'Android.mp4')

    dismissed = [case for case, result in receipts.items() if not result['visible']]
    if dismissed:
        raise RuntimeError(f'拉动菜单边缘后菜单关闭了：{", ".join(dismissed)}')


if __name__ == '__main__':
    main()
