// DAI-LITE unit tests — run with:  node --test   (or)   npm test
//
// These exercise the pure logic exported on window.DAILITE by index.html.
// Goal: prove the tricky, safety-critical bits (BLE framing/chunking, the
// RUN: command parser, request building, and device profiles) behave exactly
// as intended — kept as simple and readable as possible.

import { test } from "node:test";
import assert from "node:assert/strict";
import { loadApp, decodeChunks } from "./load-app.mjs";

const D = loadApp();

/* ========================================================================
   Device profiles + the exact Flipper UUIDs (regression guard)
   ======================================================================== */
test("Flipper UUIDs match the required spec values exactly", () => {
  assert.equal(D.SERIAL_SERVICE, "8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000");
  assert.equal(D.TX_CHAR,        "19ed82ae-ed21-4c9d-4145-228e62fe0000");
  assert.equal(D.RX_CHAR,        "19ed82ae-ed21-4c9d-4145-228e61fe0000");

  const f = D.DEVICE_PROFILES.flipper;
  assert.equal(f.service, D.SERIAL_SERVICE);
  assert.equal(f.txChar,  D.TX_CHAR);
  assert.equal(f.rxChar,  D.RX_CHAR);
  assert.equal(f.namePrefix, "Flipper");
  assert.equal(f.lineEnding, "\r");
  assert.equal(f.chunkSize, 20);
});

test("ESP32-S3 profile uses the Nordic UART Service UUIDs", () => {
  const e = D.DEVICE_PROFILES.esp32;
  assert.equal(e.service, "6e400001-b5a3-f393-e0a9-e50e24dcca9e");
  assert.equal(e.txChar,  "6e400002-b5a3-f393-e0a9-e50e24dcca9e"); // we write here
  assert.equal(e.rxChar,  "6e400003-b5a3-f393-e0a9-e50e24dcca9e"); // notifications
  assert.equal(e.lineEnding, "\n");
});

test("getActiveProfile defaults to Flipper", () => {
  assert.equal(D.getActiveProfile(undefined).key, "flipper");
  assert.equal(D.getActiveProfile({}).key, "flipper");
  assert.equal(D.getActiveProfile({ deviceProfile: "nope" }).key, "flipper");
});

test("getActiveProfile returns the built-in ESP32 profile", () => {
  const p = D.getActiveProfile({ deviceProfile: "esp32" });
  assert.equal(p.key, "esp32");
  assert.equal(p.service, "6e400001-b5a3-f393-e0a9-e50e24dcca9e");
});

test("getActiveProfile builds a custom profile from settings (lowercased, trimmed)", () => {
  const p = D.getActiveProfile({
    deviceProfile: "custom",
    custom: {
      label: "MyBoard", namePrefix: "MyBoard",
      service: "  ABCD  ", txChar: "1234", rxChar: "5678", lineEnding: "lf",
    },
  });
  assert.equal(p.key, "custom");
  assert.equal(p.label, "MyBoard");
  assert.equal(p.service, "abcd");     // trimmed + lowercased
  assert.equal(p.txChar, "1234");
  assert.equal(p.rxChar, "5678");
  assert.equal(p.lineEnding, "\n");    // "lf" -> \n
});

test("profileIsComplete requires all three UUIDs", () => {
  assert.equal(D.profileIsComplete(D.DEVICE_PROFILES.flipper), true);
  assert.equal(D.profileIsComplete({ service: "x", txChar: "y" }), false);
  assert.equal(D.profileIsComplete({ service: "", txChar: "", rxChar: "" }), false);
  assert.equal(D.profileIsComplete(null), false);
});

test("lineEndingFromCode maps codes to characters", () => {
  assert.equal(D.lineEndingFromCode("cr"), "\r");
  assert.equal(D.lineEndingFromCode("lf"), "\n");
  assert.equal(D.lineEndingFromCode("crlf"), "\r\n");
  assert.equal(D.lineEndingFromCode(undefined), "\r"); // default
});

/* ========================================================================
   BLE framing + 20-byte chunking
   ======================================================================== */
test("frameCommand appends the line ending and encodes UTF-8", () => {
  assert.deepEqual([...D.frameCommand("ab", "\r")], [97, 98, 13]);
  assert.deepEqual([...D.frameCommand("ab", "\n")], [97, 98, 10]);
  assert.deepEqual([...D.frameCommand("ab", "\r\n")], [97, 98, 13, 10]);
  assert.deepEqual([...D.frameCommand("x")], [120, 13]); // default CR
});

test("chunkBytes splits into <=20-byte chunks by default", () => {
  const bytes = new Uint8Array(45);
  const chunks = D.chunkBytes(bytes);
  assert.equal(chunks.length, 3);            // 20 + 20 + 5
  assert.equal(chunks[0].length, 20);
  assert.equal(chunks[1].length, 20);
  assert.equal(chunks[2].length, 5);
});

test("chunkBytes handles boundaries: empty, exact, and one-over", () => {
  assert.equal(D.chunkBytes(new Uint8Array(0)).length, 0);
  assert.equal(D.chunkBytes(new Uint8Array(20)).length, 1);
  const over = D.chunkBytes(new Uint8Array(21));
  assert.equal(over.length, 2);
  assert.equal(over[1].length, 1);
});

test("chunkBytes respects a custom chunk size", () => {
  const chunks = D.chunkBytes(new Uint8Array(10), 4);
  assert.deepEqual(chunks.map((c) => c.length), [4, 4, 2]);
});

test("frame -> chunk -> reassemble round-trips a long command", () => {
  const cmd = "storage read /ext/subghz/a_very_long_capture_name.sub";
  const chunks = D.chunkBytes(D.frameCommand(cmd, "\r"), 20);
  assert.ok(chunks.length > 1, "should span multiple BLE writes");
  assert.equal(decodeChunks(chunks), cmd + "\r");
});

test("chunking never corrupts multi-byte UTF-8 on reassembly", () => {
  // Emoji + accents deliberately straddle 20-byte chunk boundaries.
  const cmd = "note café ☕ signal 📡 straddling the twenty byte edge!!";
  const chunks = D.chunkBytes(D.frameCommand(cmd, "\n"), 20);
  assert.equal(decodeChunks(chunks), cmd + "\n");
});

/* ========================================================================
   RUN: command parser — the ONLY path from AI text to hardware
   ======================================================================== */
test("parseAssistant returns a single text segment for plain prose", () => {
  const segs = D.parseAssistant("Your battery is fine.");
  assert.deepEqual(segs, [{ type: "text", text: "Your battery is fine." }]);
});

test("parseAssistant extracts a RUN: command", () => {
  const segs = D.parseAssistant("Checking battery.\nRUN: power info");
  assert.deepEqual(segs, [
    { type: "text", text: "Checking battery." },
    { type: "run", command: "power info" },
  ]);
});

test("parseAssistant handles multiple commands interleaved with prose", () => {
  const segs = D.parseAssistant("First:\nRUN: device_info\nThen list files:\nRUN: storage list /ext");
  assert.equal(segs.length, 4);
  assert.deepEqual(segs.map((s) => s.type), ["text", "run", "text", "run"]);
  assert.equal(segs[1].command, "device_info");
  assert.equal(segs[3].command, "storage list /ext");
});

test("parseAssistant trims surrounding whitespace on the command", () => {
  const segs = D.parseAssistant("RUN:    vibro 1   ");
  assert.deepEqual(segs, [{ type: "run", command: "vibro 1" }]);
});

test("parseAssistant is case-sensitive: lowercase 'run:' is NOT a command", () => {
  const segs = D.parseAssistant("run: not a command");
  assert.deepEqual(segs, [{ type: "text", text: "run: not a command" }]);
});

test("parseAssistant tolerates empty / null input", () => {
  assert.deepEqual(D.parseAssistant(""), []);
  assert.deepEqual(D.parseAssistant(null), []);
  assert.deepEqual(D.parseAssistant(undefined), []);
});

test("parseAssistant ignores a bare 'RUN:' with no command", () => {
  const segs = D.parseAssistant("RUN:");
  assert.deepEqual(segs, [{ type: "text", text: "RUN:" }]); // no command captured
});

test("stripRunLines removes commands and collapses whitespace for TTS", () => {
  const spoken = D.stripRunLines("Let me check.\nRUN: power info\nDone.");
  assert.equal(spoken, "Let me check. Done.");
});

/* ========================================================================
   AI request building
   ======================================================================== */
test("resolveChatUrl joins the path without doubling slashes", () => {
  assert.equal(D.resolveChatUrl("https://openrouter.ai/api/v1"), "https://openrouter.ai/api/v1/chat/completions");
  assert.equal(D.resolveChatUrl("https://openrouter.ai/api/v1/"), "https://openrouter.ai/api/v1/chat/completions");
  assert.equal(D.resolveChatUrl("http://LAN-IP:11434/v1///"), "http://LAN-IP:11434/v1/chat/completions");
});

test("buildHeaders sets Authorization only when a key is present", () => {
  assert.equal(D.buildHeaders({ apiKey: "sk-123", baseUrl: "x" }).Authorization, "Bearer sk-123");
  assert.equal(D.buildHeaders({ apiKey: "", baseUrl: "x" }).Authorization, undefined);
  assert.equal(D.buildHeaders({ baseUrl: "x" })["Content-Type"], "application/json");
});

test("buildHeaders adds OpenRouter attribution headers only for openrouter.ai", () => {
  const or = D.buildHeaders({ apiKey: "k", baseUrl: "https://openrouter.ai/api/v1" }, "https://site.test");
  assert.equal(or["HTTP-Referer"], "https://site.test");
  assert.equal(or["X-Title"], "DAI-LITE");

  const other = D.buildHeaders({ apiKey: "k", baseUrl: "https://integrate.api.nvidia.com/v1" }, "https://site.test");
  assert.equal(other["HTTP-Referer"], undefined);
  assert.equal(other["X-Title"], undefined);
});

test("buildRequestBody puts the system prompt first and bounds max_tokens", () => {
  const body = D.buildRequestBody("SYS", [{ role: "user", content: "hi" }], { model: "m" });
  assert.equal(body.model, "m");
  assert.equal(body.messages[0].role, "system");
  assert.equal(body.messages[0].content, "SYS");
  assert.equal(body.messages[1].content, "hi");
  assert.equal(body.max_tokens, 800);   // prevents 402 on low-credit accounts
  assert.equal(body.stream, false);
});

test("buildRequestBody falls back to a default model", () => {
  const body = D.buildRequestBody("SYS", [], {});
  assert.equal(body.model, "gpt-4o-mini");
  assert.equal(body.messages.length, 1); // just the system message
});

test("extractReply pulls content and is safe on malformed responses", () => {
  assert.equal(D.extractReply({ choices: [{ message: { content: "hello" } }] }), "hello");
  assert.equal(D.extractReply({}), "");
  assert.equal(D.extractReply({ choices: [] }), "");
  assert.equal(D.extractReply(null), "");
});

/* ========================================================================
   System prompt is device-aware and always teaches the RUN: protocol
   ======================================================================== */
test("buildSystemPrompt always explains the RUN: protocol and confirmation", () => {
  const p = D.buildSystemPrompt(D.DEVICE_PROFILES.esp32);
  assert.match(p, /RUN: /);
  assert.match(p, /Run" button/);
  assert.match(p, /NEVER invent command output/);
});

test("buildSystemPrompt includes the Flipper cheat-sheet only for Flipper", () => {
  const flip = D.buildSystemPrompt(D.DEVICE_PROFILES.flipper);
  assert.match(flip, /Flipper Zero CLI commands/);
  assert.match(flip, /subghz/);

  const esp = D.buildSystemPrompt(D.DEVICE_PROFILES.esp32);
  assert.doesNotMatch(esp, /subghz/);
  assert.match(esp, /ESP32-S3 \(Nordic UART\)/); // names the active device
});

/* ========================================================================
   Provider presets match the spec
   ======================================================================== */
test("provider presets have the exact spec Base URLs", () => {
  assert.equal(D.PRESETS.openrouter.baseUrl, "https://openrouter.ai/api/v1");
  assert.equal(D.PRESETS.nvidia.baseUrl, "https://integrate.api.nvidia.com/v1");
  assert.equal(D.PRESETS.ollama.baseUrl, "http://LAN-IP:11434/v1");
});

/* ========================================================================
   Settings normalization
   ======================================================================== */
test("normalizeSettings shapes bad / partial input safely", () => {
  assert.deepEqual(D.normalizeSettings(null), {
    baseUrl: "", apiKey: "", model: "", deviceProfile: "flipper", custom: {},
  });
  assert.deepEqual(D.normalizeSettings("not json"), {
    baseUrl: "", apiKey: "", model: "", deviceProfile: "flipper", custom: {},
  });
  const s = D.normalizeSettings('{"baseUrl":"u","deviceProfile":"esp32"}');
  assert.equal(s.baseUrl, "u");
  assert.equal(s.deviceProfile, "esp32");
  assert.deepEqual(s.custom, {});
});
