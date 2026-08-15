# Flipper Upstream Map & Compatibility Matrix

**Purpose:** map what the official Flipper ecosystem already provides against what
DAI-LITE implements, so we *reuse* upstream infrastructure instead of reinventing
it. Per the architecture spec: **inspect first, reuse second, abstract third.**

**Pinned source:** `flipperdevices/flipperzero-protobuf`, shallow clone, protobuf
**v0.25** (per its `Changelog`). Everything marked ✅ below was read from that
source in-repo — not from memory. Anything unverified is marked **TBD** on
purpose; do not fill these in by guessing.

---

## 1. The core correction

DAI-LITE originally assumed:

```
BLE == wireless USB CLI      ← WRONG
```

Reality (confirmed):

| Transport | Carries | Format |
|---|---|---|
| **USB** | VCP → CLI service | plain text commands ✅ |
| **BLE** | RPC session | varint-delimited protobuf ✅ |

Consequence we observed and can now explain: sending text over BLE produces a
malformed RPC stream, and the firmware **closes the session** — the "connects,
then randomly disconnects" behavior.

---

## 2. RPC wire format (verified)

`PB.Main` envelope — from `flipper.proto`:

| Field | # | Type |
|---|---|---|
| `command_id` | 1 | uint32 |
| `command_status` | 2 | `CommandStatus` enum |
| `has_next` | 3 | bool |
| `content` | oneof | one of the tags below |

Content tags DAI-LITE encodes today (all ✅ verified):

| Message | Tag |
|---|---|
| `Empty` | 4 |
| `system_ping_request` / `_response` | 5 / 6 |
| `storage_list_request` / `_response` | 7 / 8 |
| `storage_read_request` / `_response` | 9 / 10 |
| `storage_write_request` | 11 |
| `storage_mkdir_request` | 13 |
| `stop_session` | 19 |
| `system_device_info_request` / `_response` | 32 / 33 |
| `system_play_audiovisual_alert_request` | 38 |

Framing: each `Main` message is prefixed with a **varint byte length**
(nanopb delimited encoding). Example, `rpcPing(1)` → `04 08 01 2A 00`
(asserted byte-for-byte in `tests/dai-lite.test.mjs`).

---

## 3. Available RPC surface (message names verified from source)

| Area | Messages available upstream | DAI-LITE today |
|---|---|---|
| **System** | Ping, DeviceInfo, PowerInfo, ProtobufVersion, Reboot, FactoryReset, Get/SetDateTime, PlayAudiovisualAlert, Update | encodes Ping, DeviceInfo, Alert |
| **Storage** | Info, Timestamp, Stat, List, Read, Write, Delete, Mkdir, Md5sum, Rename, BackupCreate/Restore, TarExtract | encodes List, Read (CLI path used for write today) |
| **Application** | Start, LockStatus, AppExit, AppLoadFile, AppButtonPress/Release/PressRelease, AppState, GetError, DataExchange | none |
| **GUI** | StartScreenStream, StopScreenStream, ScreenFrame, SendInputEvent, Start/StopVirtualDisplay | none |
| **GPIO** | SetPinMode, SetInputPull, GetPinMode, ReadPin, WritePin, Get/SetOtgMode | none |
| **Desktop** | IsLocked, Unlock, StatusSubscribe/Unsubscribe, Status | none |
| **Property** | Get | none |

---

## 4. Capability matrix

| DAI capability | Upstream implementation | Transport | DAI-LITE status |
|---|---|---|---|
| Device discovery | BLE advertisement / USB enumeration | BLE / USB | ✅ BLE chooser + auto-detect; USB via bridge |
| Terminal / CLI | CLI service, `cli_vcp` | USB | ✅ works through `bridge/dai_bridge.py` |
| Structured ops | RPC service | USB / BLE | ⚠️ codec built + tested; **transport not wired** |
| Device identity | `DeviceInfoRequest` (32) | RPC | ⚠️ encoder ready, unsent |
| Storage | Storage service + RPC | RPC / CLI | ⚠️ CLI prototype (`/ext/dai/memory.txt`); RPC pending |
| Notifications | Notification service, `PlayAudiovisualAlert` (38) | RPC | ⚠️ CLI `vibro` hack today; RPC encoder ready |
| Power / battery | `PowerInfoRequest` (44) | RPC | ❌ not implemented |
| Applications / FAP | Loader + app RPC | RPC | ❌ not implemented |
| GPIO / GUI / Desktop | respective services | RPC | ❌ not implemented |
| ESP32 bridge | (ours) NUS + WebSocket | BLE / WiFi | ✅ shipped, untested on hardware |
| BLE pairing/bonding UX | `Flipper-Android-App` reference | BLE | **TBD** — not yet inspected |
| RPC session start over BLE | firmware `rpc` service | BLE | **TBD** — whether a session must be opened explicitly |
| Max frame / MTU limits | firmware BLE serial service | BLE | **TBD** |

---

## 5. Target architecture (from the spec)

```
              DAI (AI · memory · voice · persona)
                            │
                     DAI Device API
                            │
                    Device Manager
              (discovery · sessions · capabilities)
                            │
                   Flipper Adapter
        ┌───────────────────┼───────────────────┐
       USB                 BLE                 WiFi
        │                   │                   │
       CLI                 RPC              ESP32 bridge
        └───────────────────┼───────────────────┘
                       Flipper Zero
        (Furi · Storage · Notification · Apps · GPIO · GUI)
```

The UI should say **"Flipper Zero — Connected"** and never leak which transport
is underneath. LITE (operator) and DAI (companion) modes both sit *above* this
shared device layer and must stay distinct.

---

## 6. Ordered next steps

1. Wire `FlipperBleRpcTransport` to the existing codec; prove with `ping`.
2. On success: `DeviceInfo` → populate identity + capabilities.
3. Move notifications from the `vibro` CLI hack to `PlayAudiovisualAlert`.
4. Move Flipper memory storage from CLI `storage write` to Storage RPC.
5. Capability discovery object; UI shows transport + protocol + capabilities.
6. Refactor USB bridge to sit behind the same Device API.
7. Only then consider a DAI FAP (via `ufbt`, no firmware fork).

## 7. Rules carried from the spec

- Never invent an API upstream already provides.
- Don't fork the firmware; a FAP is an optional later extension.
- Pin and record the upstream revision analyzed (done: protobuf v0.25).
- Small commits, tests green after each.
- Keep LITE and DAI modes separate.
- Capability/permission boundaries before exposing hardware ops to the AI.
