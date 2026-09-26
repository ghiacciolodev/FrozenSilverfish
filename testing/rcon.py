"""Minimal RCON client used by the test scripts."""
import re
import socket
import struct


class Rcon:
    def __init__(self, host, port, password):
        self.sock = socket.create_connection((host, port))
        self._send(1, 3, password)
        if self._read()[0] == -1:
            raise SystemExit('RCON login failed, check the password')

    def _send(self, request_id, kind, body):
        data = struct.pack('<ii', request_id, kind) + body.encode() + b'\x00\x00'
        self.sock.sendall(struct.pack('<i', len(data)) + data)

    def _read(self):
        length = struct.unpack('<i', self._recv(4))[0]
        data = self._recv(length)
        request_id = struct.unpack('<i', data[:4])[0]
        return request_id, data[8:-2].decode(errors='replace')

    def _recv(self, n):
        buf = b''
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError('RCON connection closed')
            buf += chunk
        return buf

    def __call__(self, command):
        """Runs a command and returns its output without color codes."""
        self._send(2, 2, command)
        return re.sub('§.', '', self._read()[1]).strip()


def add_arguments(parser):
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=25575, help='RCON port (default 25575)')
    parser.add_argument('--password', required=True, help='RCON password')
    parser.add_argument('--config', required=True,
                        help='path to plugins/FrozenSilverfish/config.yml on the test server')


def write_config(path, enabled=True, collisions_off=True, drowning=True, worlds='[]'):
    with open(path, 'w', encoding='utf-8') as f:
        f.write(f'enabled: {str(enabled).lower()}\n'
                f'disable-collisions: {str(collisions_off).lower()}\n'
                f'prevent-drowning: {str(drowning).lower()}\n'
                f'worlds: {worlds}\n')
