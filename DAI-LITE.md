# DAI-LITE — AI voice companion for the Flipper Zero

A **single HTML file**. No backend, no build step, no install. Open it in
Chrome/Edge, talk to an AI, and it proposes device commands you confirm with a
tap. Inspired by the discontinued AIPI Lite.

```
🎙️ you speak → AI proposes "RUN: power info" → you tap Run
     → device replies in the terminal → output goes back to the AI → it answers aloud
```

- **`index.html`** — the whole app (open it, that's it)
- **`bridge/`** — PC script so a real Flipper works today over USB
- **`firmware/`** — ESP32-S3 bridge for an untethered module
- **`tests/`** — `npm test` (63 zero-dependency tests)

---

## Quick start

1. **Open `index.html`** in Chrome or Edge (Android or desktop).
   Better: serve it so Bluetooth and `ws://` both work —
   `python3 -m http.server 8000` → <http://localhost:8000>
2. **⚙️ Settings → AI provider.** Click a preset (OpenRouter / NVIDIA / Ollama),
   paste your API key, click **Load models**, pick one, **Save**.
3. **Connect** your device (see the table below).
4. Hold 🎙️ and talk, or type. Tap **Run** on any command the AI proposes.

---

## Connecting a Flipper Zero — read this first

Stock Flipper firmware speaks a **binary RPC protocol over Bluetooth**, *not* a
text CLI. DAI-LITE sends plain text, so **over BLE the Flipper connects but never
replies.** That's the firmware, not a bug. Two ways to get the real CLI:

| Path | Flashing | Replies? | Best for |
|---|---|---|---|
| **PC bridge** (`bridge/`) | none | ✅ | Trying it today — USB cable + one Python script |
| **ESP32-S3 bridge** (`firmware/`) | yes | ✅ | A portable module, no PC |
| Direct BLE → Flipper | none | ❌ | — |

**Fastest path (recommended):**

```sh
pip install websockets pyserial
python3 bridge/dai_bridge.py          # auto-detects the Flipper's USB port
```

Then ⚙️ Settings → **Connection** → **WiFi (WebSocket bridge)** → `ws://localhost:8765`
→ Save → **Connect** → try `device_info`. Details in [`bridge/README.md`](bridge/README.md).

Other devices work over Bluetooth directly: **ESP32-S3** boards using the Nordic
UART Service are auto-detected, and **Custom** lets you enter your own hardware's
UUIDs and line ending.

---

## Voices

- **Device voices** — free, built into your OS, and robotic. Includes a voice
  picker and style presets.
- **Neural voices** — realistic. Settings → Voice → engine **Neural**, then
  one-tap personas: **Warm woman**, **Flirty**, **Narrator (man)**, **Bright
  woman**, plus a free-text tone box ("calm audiobook narrator"). Hit **🔊 Test**.

> Neural voices need a **TTS-capable key** (e.g. OpenAI). **OpenRouter has no
> TTS** — your chat key won't work for voice. If a neural call fails, the app
> falls back to the device voice and tells you why.

---

## Handy features

- **Auto-detect** picks the right device profile after you connect.
- **Saved providers** — store multiple named key sets and switch between them;
  👁 reveals a saved key.
- **→ AI** hands the latest device output to the assistant; or enable
  **auto-send** for a fully hands-free loop.
- **↑/↓** recalls previous commands in the terminal; **Clear** wipes it.
- Works offline-ish: a local **Ollama** server needs `OLLAMA_ORIGINS=*`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Connect does nothing / no Bluetooth | Use Chrome or Edge; serve over `http://localhost` or `https://` (not a bare `file://`). On Android grant Bluetooth + Location. |
| Connects to Flipper, no output | Expected over BLE — use the PC or ESP32 bridge (above). |
| AI request fails (CORS) | Settings has a "Known fixes" panel: `OLLAMA_ORIGINS=*`, or a proxy that adds `Access-Control-Allow-Origin`. |
| `402` from OpenRouter | Out of credits — the app already caps `max_tokens`; add credit or use a cheaper model. |
| WiFi won't connect | An `https://` page can only reach `wss://`. Serve DAI-LITE over `http://localhost` to use plain `ws://`. |

## Tests

```sh
npm test                      # 63 tests against the shipped index.html
python3 bridge/test_bridge.py # end-to-end bridge test over a virtual serial port
```

## Safety

This controls **real RF and hardware**. Every command the AI proposes requires
your explicit **Run** press — nothing executes on its own. Transmitting on some
frequencies, cloning credentials, or touching systems you don't own may be
illegal. **You are responsible for legal, authorized use.**

MIT licensed — see the header of `index.html`.
