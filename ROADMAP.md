# DAI-LITE Roadmap

Two assistants in one app:

- **Lite mode** — *lights the way through coding with the Flipper Zero.* Focused
  hardware/coding co-pilot: DuckyScript, Furi C + `ufbt`, `.sub`/`.nfc`, ESP32,
  build-mode-default, peer tone.
- **Dai mode** — the daily **companion**. Warm, conversational, remembers you
  across sessions, general life + technical help.

Same brain, different personality + feature emphasis, switchable with one tap.

---

## Phase 1 — Modes + Memory (browser)  ✅ built
No server, no login. Fully inside the single HTML file.

1. **Mode switch** (Dai ⇄ Lite) in the top bar; each mode has its own persona.
2. **Memory store** in `localStorage` (`dailite_memory`), injected into the
   system prompt so the assistant recalls facts across sessions.
3. **Self-writing memory**: the assistant emits `REMEMBER: <fact>` on its own
   line → the app saves it (shown as a 🧠 chip). Like `RUN:` but for memory.
4. **Memory editor** in Settings (view / edit / clear).
5. **Custom persona box** already ships — personal details stay local.

## Phase 2 — Memory on the Flipper (SD card)  ✅ built (beta, needs hardware)
Make the Flipper the memory stick.

1. `storage write /ext/dai/memory.txt` from the app (handle the CLI write
   protocol: send content, terminate with Ctrl+C / 0x03).
2. `storage read` it back on connect to restore memory from the device.
3. "Sync memory ↔ Flipper" buttons; compact/one-fact-per-line format.

## Phase 3 — Backup & portability  ✅ built (full backup; password-lock TODO)
1. **Backup everything** (settings + keys + memory + persona) to one file, and
   restore it — extends the existing key Export/Import. Fixes "I lost it when I
   re-downloaded" for good.
2. Optional password-lock on the exported file.

## Phase 4 — Connections dashboard  ✅ built
The "what's linked" screen, done honestly (status, not fake logins):

1. A panel showing each capability and its state — AI ✓/✗, Voice (device/
   Puter/neural), Device (BLE/WiFi), Memory — with a one-tap fix for each.
2. Feeds off real settings; no accounts required.

## Phase 5 — Cloud sync + Google sign-in (opt-in, Firebase)
Only for cross-device memory. This tier loads external SDKs, so it ships as a
separate opt-in build, not the default self-contained file.

1. Firebase project config (user-supplied) + Google sign-in via Firebase Auth.
2. Sync `dailite_memory` + key sets to Firestore per signed-in user.
3. Same pattern your barcode app uses; login is the door, Firestore is storage.

## Phase 5.5 — Native Flipper RPC over BLE (the real fix)
Research confirms stock Flipper BLE is a **protobuf RPC transport**, not a
wireless CLI — and a malformed stream makes the firmware *close the session*,
which is exactly the "connects then disconnects" behavior we saw. So:

1. Treat Flipper as one device behind a **device integration layer** (already the
   shape of `DEVICE_PROFILES`) rather than special-casing "Flipper support".
2. Implement a minimal RPC transport: varint-delimited protobuf frames from
   `flipperzero-protobuf` (Empty/System ping, DeviceInfo, Storage list/read/write,
   Notification) instead of pushing CLI strings at the BLE characteristics.
3. Keep the text-CLI path for USB/ESP32 bridges and non-Flipper gear; pick the
   transport per device.
4. Reference implementations worth mining: `flipperzero-protobuf` (wire format),
   `Flipper-Android-App` (BLE discovery/pairing/session handling), `qFlipper`
   (proven USB backend).

Until then the honest, working paths remain the USB PC bridge and the ESP32
UART bridge — both already shipped.

## Phase 6 — Voice-box hardware (ESP32-S3)
Give the *device* a mic + speaker (the Flipper has neither).

1. ESP32-S3 + I2S mic (INMP441) + I2S amp/speaker (MAX98357A).
2. Extend the bridge firmware: capture voice, play TTS, relay to the Flipper.

## Phase 7 — Flipper feedback + polish
1. Flipper haptic/LED "ack" when a command runs (buzz/blink).  ✅ buzz built
2. Incremental (sentence-by-sentence) speech for lower latency.
3. Ongoing review passes + hardware verification.

---

### Guardrails kept throughout
- The single `index.html` stays self-contained by default; anything that needs
  an external SDK (Puter, Firebase) is opt-in and clearly labeled.
- Personal data lives on-device unless the user opts into cloud sync.
- Every device command still requires the user's **Run** confirmation.
- Zero-dependency tests stay green (`npm test`).
