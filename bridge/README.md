# DAI-LITE PC Bridge

**The fastest way to make DAI-LITE actually control a real Flipper Zero.**
No firmware to flash, no ESP32 — just a USB cable and one Python script.

```
DAI-LITE (browser: voice + AI)
      │  WiFi / WebSocket  (ws://your-pc:8765)
      ▼
 dai_bridge.py  ── USB serial ──►  Flipper Zero CLI (real text commands)
```

## Why this works when Bluetooth doesn't

Stock Flipper firmware speaks a **binary RPC protocol over BLE**, so the app's
plain-text commands get no reply there. Over **USB serial the Flipper runs its
real text CLI** — which is exactly what DAI-LITE speaks. This bridge relays
between the two.

It also works with any other text-CLI serial gadget (ESP32 boards, Arduino, your
own hardware) — just set `--baud` and `--newline` to match.

## Setup

```sh
pip install websockets pyserial
python3 dai_bridge.py            # auto-detects the Flipper's port
```

Useful flags:

```sh
python3 dai_bridge.py --list                 # show serial ports
python3 dai_bridge.py --port COM3            # Windows
python3 dai_bridge.py --port /dev/ttyACM0    # Linux
python3 dai_bridge.py --newline lf --baud 115200   # most ESP32 firmware
```

It prints the WebSocket URL to use, e.g. `ws://192.168.1.50:8765`.

## Connect from DAI-LITE

1. ⚙️ Settings → **Connection** → **WiFi (WebSocket bridge)**
2. **WebSocket URL**: `ws://<your-pc-ip>:8765` (or `ws://localhost:8765` on the
   same machine)
3. Save → **Connect** → try `device_info`

> **Serve the page over `http://localhost`, not `https://`.** A page loaded over
> https can only open `wss://` (encrypted) sockets, and this bridge speaks plain
> `ws://`. From the folder holding `index.html`:
> `python3 -m http.server 8000` → open `http://localhost:8000`.

## Leave it running (the AIPI-Lite pattern)

By default the bridge listens on **localhost only** — safe, and enough when the
browser and the Flipper are on the same machine. To drive it from your phone:

```sh
python3 dai_bridge.py --host 0.0.0.0 --token mysecret
```

Then use `ws://<pc-ip>:8765/?token=mysecret` in DAI-LITE. It survives cable
unplugs (retries every 2s, and re-detects the port if the device re-enumerates
under a new name), and accepts multiple app clients at once.

### Security

A WebSocket is **not** covered by the browser's same-origin policy, so without a
check *any website you happen to visit* could connect to this bridge and issue
commands to your hardware. The bridge therefore:

- **binds to `127.0.0.1` by default** (`--host 0.0.0.0` to expose it to your LAN),
- **checks the `Origin` header** — pages served from localhost are allowed;
  private-LAN pages are allowed once you bind to the LAN; everything else is
  refused (add trusted ones with `--allow-origin http://192.168.1.20:8000`),
- supports an optional **`--token`** shared secret on the URL.

`--allow-any-origin` exists as a last resort and prints a warning — it lets any
site you visit reach the bridge, so only use it on a machine you trust.

## Test it

A self-contained end-to-end test creates a virtual serial device and checks both
directions of the relay:

```sh
python3 test_bridge.py
```

## Which path should I use?

| Path | Flashing | Flipper replies? | Best for |
|---|---|---|---|
| **PC bridge** (this) | none | ✅ yes (USB CLI) | Trying it today, desk setup |
| **ESP32-S3 bridge** (`../firmware`) | yes | ✅ yes (GPIO UART) | Untethered/portable module |
| Direct BLE to Flipper | none | ❌ no (RPC, not text) | — |

> ⚠️ This hands an AI assistant a path to real hardware. Every command still
> requires you to press **Run** in the app. You are responsible for legal,
> authorized use.
