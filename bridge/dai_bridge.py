#!/usr/bin/env python3
"""
DAI-LITE PC Bridge  ·  MIT License  ·  (c) 2026 DAI-LITE contributors

Bridges a WebSocket (what the DAI-LITE web app speaks over WiFi) to a serial
port (what your Flipper Zero speaks over USB).

Why this exists
---------------
Stock Flipper firmware speaks a *binary RPC* protocol over Bluetooth, so the
web app's plain-text commands get no reply over BLE. But over **USB serial the
Flipper runs its real text CLI** — exactly what DAI-LITE speaks. Run this
script on the PC the Flipper is plugged into and the app can drive it for real,
with no firmware flashing and no ESP32.

It also works with any other text-CLI serial gadget (ESP32 dev boards, Arduino,
your own hardware).

Install
-------
    pip install websockets pyserial

Run
---
    python3 dai_bridge.py                 # auto-detect the port
    python3 dai_bridge.py --port COM3     # Windows
    python3 dai_bridge.py --port /dev/ttyACM0 --baud 115200
    python3 dai_bridge.py --list          # show available serial ports

Then in DAI-LITE: Settings -> Connection -> WiFi (WebSocket bridge),
URL ws://<this-pc-ip>:8765 (or ws://localhost:8765 on the same machine).

Serve the DAI-LITE page over http://localhost (not https://) so the browser
allows a plain ws:// connection.

SAFETY: this gives an AI assistant a path to your hardware's command line. Every
command still requires you to press "Run" in the app. You are responsible for
legal, authorized use.
"""

import argparse
import asyncio
import sys

try:
    import websockets
except ImportError:
    sys.exit("Missing dependency. Run:  pip install websockets pyserial")

try:
    import serial
    from serial.tools import list_ports
except ImportError:
    sys.exit("Missing dependency. Run:  pip install websockets pyserial")


# Serial IDs that usually mean "a Flipper Zero is plugged in".
FLIPPER_HINTS = ("flipper", "0483:5740", "stmicroelectronics", "st-link")


def find_ports():
    """Return a list of (device, description) for all serial ports."""
    return [(p.device, (p.description or "").strip()) for p in list_ports.comports()]


def autodetect_port():
    """Pick the most likely device port: prefer something that looks like a Flipper."""
    ports = list(list_ports.comports())
    if not ports:
        return None
    for p in ports:
        blob = " ".join(str(x) for x in (p.description, p.manufacturer, p.hwid)).lower()
        if any(h in blob for h in FLIPPER_HINTS):
            return p.device
    return ports[0].device  # fall back to the first port


class Bridge:
    """Relays bytes between one serial port and any number of WebSocket clients."""

    def __init__(self, port, baud, newline):
        self.port = port
        self.baud = baud
        self.newline = newline
        self.serial = None
        self.clients = set()

    def open_serial(self):
        # timeout=0 -> non-blocking reads, so the poll loop stays responsive.
        self.serial = serial.Serial(self.port, self.baud, timeout=0)
        print(f"[bridge] serial open: {self.port} @ {self.baud}")

    async def broadcast(self, text):
        """Send device output to every connected app client."""
        if not self.clients:
            return
        dead = set()
        for ws in self.clients:
            try:
                await ws.send(text)
            except Exception:
                dead.add(ws)
        self.clients -= dead

    async def pump_serial(self):
        """Poll the serial port and forward anything it prints to the clients."""
        while True:
            try:
                waiting = self.serial.in_waiting
                if waiting:
                    data = self.serial.read(waiting)
                    if data:
                        text = data.decode("utf-8", errors="replace")
                        sys.stdout.write(text)
                        sys.stdout.flush()
                        await self.broadcast(text)
            except (OSError, serial.SerialException) as e:
                print(f"\n[bridge] serial error: {e}")
                await self.reconnect()
            await asyncio.sleep(0.02)  # ~50Hz poll; gentle on the CPU

    async def reconnect(self):
        """If the cable is yanked, keep trying to reopen the port."""
        try:
            if self.serial:
                self.serial.close()
        except Exception:
            pass
        self.serial = None
        while self.serial is None:
            await asyncio.sleep(2)
            try:
                self.open_serial()
                await self.broadcast("\r\n[bridge] serial reconnected\r\n")
            except Exception:
                print("[bridge] waiting for the device…")

    async def handle_client(self, ws, path=None):
        """One connected DAI-LITE app session.

        `path` is optional so this works with both old websockets versions
        (which call handler(ws, path)) and new ones (which call handler(ws)).
        """
        peer = getattr(ws, "remote_address", ("?",))[0]
        self.clients.add(ws)
        print(f"[bridge] app connected from {peer} ({len(self.clients)} total)")
        try:
            await ws.send(f"[bridge] connected to {self.port} @ {self.baud}\r\n")
            async for message in ws:
                if isinstance(message, bytes):
                    message = message.decode("utf-8", errors="replace")
                # The app already appends its own line ending; normalize to the
                # one this device expects so both BLE and WiFi behave the same.
                cmd = message.rstrip("\r\n")
                if not cmd:
                    continue
                if self.serial:
                    self.serial.write((cmd + self.newline).encode("utf-8"))
                    print(f"[bridge] > {cmd}")
                else:
                    await ws.send("[bridge] serial not connected\r\n")
        except Exception:
            pass
        finally:
            self.clients.discard(ws)
            print(f"[bridge] app disconnected ({len(self.clients)} left)")


async def main_async(args):
    newline = {"cr": "\r", "lf": "\n", "crlf": "\r\n"}[args.newline]
    port = args.port or autodetect_port()
    if not port:
        sys.exit("No serial ports found. Plug in your device, or use --list.")

    bridge = Bridge(port, args.baud, newline)
    try:
        bridge.open_serial()
    except Exception as e:
        sys.exit(f"Could not open {port}: {e}\nTry --list to see available ports.")

    print(f"[bridge] WebSocket listening on ws://0.0.0.0:{args.ws_port}")
    print(f"[bridge] In DAI-LITE use:  ws://<this-pc-ip>:{args.ws_port}")
    print("[bridge] Ctrl+C to quit.\n")

    async with websockets.serve(bridge.handle_client, "0.0.0.0", args.ws_port):
        await bridge.pump_serial()


def main():
    ap = argparse.ArgumentParser(description="DAI-LITE WebSocket <-> serial bridge")
    ap.add_argument("--port", help="serial port (e.g. COM3, /dev/ttyACM0). Default: auto-detect")
    ap.add_argument("--baud", type=int, default=115200, help="serial baud rate (default 115200)")
    ap.add_argument("--ws-port", type=int, default=8765, help="WebSocket port (default 8765)")
    ap.add_argument("--newline", choices=("cr", "lf", "crlf"), default="cr",
                    help="line ending sent to the device (default cr, which Flipper uses)")
    ap.add_argument("--list", action="store_true", help="list serial ports and exit")
    args = ap.parse_args()

    if args.list:
        ports = find_ports()
        if not ports:
            print("No serial ports found.")
        for dev, desc in ports:
            print(f"  {dev}\t{desc}")
        return

    try:
        asyncio.run(main_async(args))
    except KeyboardInterrupt:
        print("\n[bridge] bye")


if __name__ == "__main__":
    main()
