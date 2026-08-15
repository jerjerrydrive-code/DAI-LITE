# DAI-LITE — project memory

Always-on rules. Big references live in linked docs, not here:
- @ROADMAP.md — phased plan and status
- @docs/flipper-upstream-map.md — verified upstream map + compatibility matrix

## What this repo contains

| Path | What it is |
|---|---|
| `index.html` | **DAI-LITE** — the active project. Single-file, no-build, no-backend browser app (AI + voice + device control). |
| `bridge/` | PC WebSocket↔serial bridge (**verified working**). USB path to the Flipper CLI. |
| `firmware/` | ESP32-S3 bridge sketch (BLE NUS + WiFi WebSocket). **Not hardware-tested.** |
| `tests/` | Zero-dependency Node tests (`npm test`) that load the real `index.html`. |
| `app/`, `mentra-bridge/`, `docs/architecture.md`, root `README.md` | **Legacy Vesper Android app.** Different project, same repo. Do not confuse with DAI-LITE; don't edit unless asked. |

## The one-sentence architecture

> DAI is the intelligence, LITE is the device/operator interface, the Device API is
> the abstraction layer, and Flipper Zero is a physical endpoint whose existing USB
> CLI, BLE/RPC, storage, notification, application and hardware services should be
> **leveraged rather than reinvented**.

```
        DAI (AI · memory · voice · persona)   |   LITE (operator/terminal)
                          └────────┬────────┘
                             Device API
                                   │
                            Device Manager
                     (discovery · session · capabilities)
                                   │
                           Flipper Adapter
                ┌──────────────────┼──────────────────┐
               USB                BLE                WiFi
               CLI                RPC            ESP32 bridge
```

## Non-negotiable facts (verified from upstream source)

1. **BLE ≠ wireless USB CLI.** Stock Flipper BLE carries a **protobuf RPC session**.
   A malformed stream makes the firmware **close the session** — that is the
   "connects then disconnects" bug. Never push CLI text at BLE and hope.
2. **USB → VCP → CLI is plain text** and is the firmware's intended interface.
   `bridge/dai_bridge.py` is a legitimate adapter, not a hack. Keep it.
3. **Never invent a protocol.** Use `flipperzero-protobuf` definitions
   (pinned v0.25; tags recorded in `docs/flipper-upstream-map.md`).
4. **Storage and Notification services already exist.** Prefer Storage RPC over
   ad-hoc `memory.txt`, and `PlayAudiovisualAlert` (tag 38) over the `vibro` CLI hack.
5. **No firmware fork.** A DAI FAP via `ufbt` is an optional later extension.
6. Upstream moves — **pin and record** any revision analyzed.

## Working rules

- **Inspect first. Reuse second. Abstract third.** Build new infrastructure only
  when upstream genuinely lacks it.
- **No giant rewrites.** Small commits: inspect → change one subsystem → test →
  commit → next. Run `npm test` after every meaningful change (all must pass).
- **Keep LITE and DAI modes distinct.** They share the device layer; don't merge them.
- **The UI must not leak transport details** — say "Flipper Zero — Connected"
  and expose transport/protocol/capabilities as data, not as separate flows.
- **The AI should reason about capabilities, not command syntax.** Prefer
  `storage.read(path)` over teaching it to type `storage read /ext/foo`.
- **Safety:** every device command still requires the user's explicit **Run**.
  Don't auto-expose low-level capability to the model; keep permission tiers.
- Secrets (API keys, memory) stay in the browser. **Never commit personal data.**
- Prefer serving over `http://localhost` — mic, Web Bluetooth and `ws://` all
  depend on a secure/allowed origin.

## Capability awareness (for me)

Before solving something manually, check whether a Claude Code capability fits:
Skills (e.g. `/code-review`), subagents, Plan mode, worktrees, hooks, MCP tools,
web research, Git/GitHub automation. Use it instead of hand-rolling the work.
The review skill has repeatedly caught real bugs here — run it after each feature
batch.

## Current status

- 101 tests passing; PR #1 open on `claude/dai-lite-flipper-voice-3myxjc`.
- Built: two modes, memory (browser + Flipper-CLI beta), voice (device/Puter/
  neural TTS), streaming, WiFi+BLE transports, key library, connections panel,
  PC bridge, ESP32 sketch, **RPC codec (byte-exact tested, not yet wired)**.
- Next: transport abstraction → wire BLE RPC (`ping` proves it) → capability
  discovery → move notify/storage to RPC.
- Unverified on hardware: everything except the PC bridge.
