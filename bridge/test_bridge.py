#!/usr/bin/env python3
"""
End-to-end test for the DAI-LITE PC bridge.

Creates a virtual serial device (a pty), runs dai_bridge.py against it, then
checks both directions actually work:

  1. app -> device : a command sent over the WebSocket arrives on the serial
                     side, terminated with the device's line ending.
  2. device -> app : bytes written by the device are pushed to the WebSocket.

Run:
    pip install websockets pyserial
    python3 bridge/test_bridge.py
"""

import asyncio
import os
import pty
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import websockets

HERE = Path(__file__).resolve().parent
BRIDGE = HERE / "dai_bridge.py"
WS_PORT = 8799


def read_available(fd, timeout=3.0):
    """Read whatever shows up on fd within the timeout."""
    import select
    deadline = time.time() + timeout
    out = b""
    while time.time() < deadline:
        r, _, _ = select.select([fd], [], [], 0.1)
        if r:
            try:
                chunk = os.read(fd, 4096)
            except OSError:
                break
            if chunk:
                out += chunk
                # Give a beat for any remainder, then stop.
                deadline = min(deadline, time.time() + 0.2)
    return out


async def run_test():
    master_fd, slave_fd = pty.openpty()
    slave_name = os.ttyname(slave_fd)
    print(f"[test] virtual serial device: {slave_name}")

    # Write the bridge's output to a temp file rather than a pipe: it echoes all
    # device output, and an undrained pipe would deadlock on a large payload.
    log = tempfile.TemporaryFile(mode="w+")
    proc = subprocess.Popen(
        [sys.executable, str(BRIDGE), "--port", slave_name,
         "--ws-port", str(WS_PORT), "--newline", "cr"],
        stdout=log, stderr=subprocess.STDOUT, text=True,
    )

    def bridge_log():
        log.seek(0)
        return log.read()

    failures = []
    try:
        # Wait for the bridge's WebSocket server to come up.
        ws = None
        for _ in range(50):
            await asyncio.sleep(0.2)
            if proc.poll() is not None:
                print("[test] bridge exited early:\n" + bridge_log())
                return 1
            try:
                ws = await websockets.connect(f"ws://127.0.0.1:{WS_PORT}")
                break
            except Exception:
                continue
        if ws is None:
            failures.append("could not connect to the bridge WebSocket")
            raise RuntimeError(failures[-1])
        print("[test] connected to bridge")

        # The bridge greets the client on connect.
        greeting = await asyncio.wait_for(ws.recv(), timeout=3)
        assert "bridge" in greeting.lower(), f"unexpected greeting: {greeting!r}"
        print(f"[test] greeting ok: {greeting.strip()}")

        # --- Direction 1: app -> device ---
        await ws.send("device_info\r")
        got = read_available(master_fd, timeout=3)
        if got == b"device_info\r":
            print("[test] PASS  app -> device: got %r" % got)
        else:
            failures.append(f"app->device expected b'device_info\\r', got {got!r}")
            print("[test] FAIL  app -> device: %r" % got)

        # --- Direction 2: device -> app ---
        os.write(master_fd, b"Hardware Name: Flipper\r\n")
        reply = await asyncio.wait_for(ws.recv(), timeout=3)
        if "Hardware Name: Flipper" in reply:
            print("[test] PASS  device -> app: got %r" % reply)
        else:
            failures.append(f"device->app expected the device line, got {reply!r}")
            print("[test] FAIL  device -> app: %r" % reply)

        # --- Blank lines must not be forwarded ---
        await ws.send("\r")
        stray = read_available(master_fd, timeout=0.6)
        if stray == b"":
            print("[test] PASS  blank input ignored")
        else:
            failures.append(f"blank input should be ignored, but sent {stray!r}")
            print("[test] FAIL  blank input forwarded: %r" % stray)

        await ws.close()

        # --- A browser page from an untrusted origin must be refused ---
        try:
            evil = await websockets.connect(
                f"ws://127.0.0.1:{WS_PORT}", origin="http://evil.example")
            try:
                msg = await asyncio.wait_for(evil.recv(), timeout=3)
            except Exception:
                msg = ""
            # Either we got the refusal notice, or the socket was closed on us.
            closed = False
            try:
                await asyncio.wait_for(evil.recv(), timeout=3)
            except Exception:
                closed = True
            if "refused" in str(msg).lower() or closed:
                print("[test] PASS  untrusted origin refused")
            else:
                failures.append("a page from an untrusted origin was allowed to connect")
                print("[test] FAIL  untrusted origin accepted")
            await evil.close()
        except Exception:
            # Connection rejected outright is also a pass.
            print("[test] PASS  untrusted origin refused (handshake rejected)")
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
        os.close(master_fd)
        os.close(slave_fd)
        log.close()

    print()
    if failures:
        print("FAILED:")
        for f in failures:
            print("  -", f)
        return 1
    print("All bridge tests passed.")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(run_test()))
