# DAI-LITE ESP32-S3 Bridge

The **module** half of DAI-LITE. The browser app is the AI + voice brain
(AIPI-Lite style); this firmware is the thing a Flipper Zero — or any serial
gadget — plugs into. It gives you a device you own and can extend.

```
  DAI-LITE (browser: voice + AI)
        │  BLE (Nordic UART)  or  WiFi (WebSocket)
        ▼
  ESP32-S3 bridge  ── hardware UART ──►  Flipper Zero GPIO serial (text CLI)
        │
        └─ answers its own commands too (works with no Flipper wired)
```

## Why this exists (the honest Flipper truth)

Stock Flipper firmware speaks a **binary RPC protocol over Bluetooth**, not a
raw text CLI — so DAI-LITE's plain-text commands get **no response** over BLE and
the link often drops. Over the Flipper's **GPIO serial** the CLI is reachable as
plain text, which is exactly what DAI-LITE speaks. This board bridges that gap:
the app talks to the ESP32 (which *does* speak text over BLE/WiFi), and the ESP32
relays to the Flipper's UART.

> ### Why the Flipper may not respond
> If you connect DAI-LITE **directly** to a Flipper over Bluetooth and see the
> `>` echo but no output, that's the RPC-vs-text mismatch above — not a bug. Use
> this bridge (or wire the Flipper's UART to the ESP32) to get the real CLI.

## Flash it

1. Arduino IDE → install **esp32** boards (Boards Manager → "esp32" by Espressif).
2. If using WiFi, install **"WebSockets" by Markus Sattler** (Library Manager).
   To skip WiFi, set `#define WIFI_ENABLED 0` at the top of the sketch.
3. Open `esp32s3-dai-bridge/esp32s3-dai-bridge.ino`.
4. Edit the **USER CONFIG** block: WiFi SSID/password, UART pins, baud.
5. Tools → Board → your **ESP32-S3** board. Upload.
6. Open Serial Monitor at 115200 — it prints the BLE name and `ws://<ip>:81`.

## Connect from DAI-LITE

- **Bluetooth:** just hit **Connect** — Auto-detect finds the `ESP32-DAI` board
  via the Nordic UART Service and selects the ESP32 profile automatically.
- **WiFi:** ⚙️ Settings → Connection → **WiFi (WebSocket bridge)**, enter
  `ws://<board-ip>:81`, Save, Connect. (Serve DAI-LITE over http/localhost to use
  plain `ws://`; an `https://` page can only reach `wss://`.)

Try `help`, `id`, `ping`, `led on` — these answer from the ESP32 itself, so you
can confirm the whole pipeline before any Flipper is attached.

## Wire it to a Flipper (optional)

Connect the ESP32 UART pins to the Flipper GPIO header and share ground:

| ESP32-S3            | Flipper Zero GPIO |
|---------------------|-------------------|
| `UART_TX_PIN` (17)  | pin 14 · RX (`RX`)|
| `UART_RX_PIN` (18)  | pin 13 · TX (`TX`)|
| `GND`               | pin 8 or 18 · GND |

TX↔RX are crossed on purpose. Then set the baud to match your Flipper firmware's
UART CLI. Enabling CLI-over-UART depends on the firmware you run (stock vs.
Momentum/Unleashed and their expansion-module settings) — check your firmware's
docs. Anything the Flipper prints streams back into DAI-LITE's terminal, and the
**→ AI** button hands that output to the assistant.

## Extend it (make it your own device)

`handleLocalCommand()` in the sketch is where the board answers its own commands.
Add cases for your hardware — sensors, relays, RGB LEDs, whatever you build — and
DAI-LITE can drive them by voice with the same `RUN:` confirm-before-run flow.

> ⚠️ You are responsible for legal, authorized use of any RF/hardware this
> controls. Only operate devices and signals you own or may test.
